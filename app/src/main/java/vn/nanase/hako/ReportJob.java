package vn.nanase.hako;
import android.app.job.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Native HTTPS upload: no Gecko, no database or chapter/filesystem scan. */
public final class ReportJob extends JobService {
 private final AtomicBoolean stopped=new AtomicBoolean();
 public static boolean validEndpoint(String url){try{URI uri=URI.create(url);return "https".equals(uri.getScheme())&&"script.google.com".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getPath().matches("/macros/s/[A-Za-z0-9_-]+/exec");}catch(Exception e){return false;}}
 public static void installBundled(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);
  p.edit().putBoolean("auto",false).apply();
  JobScheduler scheduler=c.getSystemService(JobScheduler.class);JobInfo pending=scheduler.getPendingJob(43);if(pending!=null&&!pending.getExtras().getBoolean("manual",false))scheduler.cancel(43);
  if(p.getBoolean("bundled_config_v1",false))return;
  try(InputStream in=c.getAssets().open("private-report-config.json")){JSONObject config=new JSONObject(read(in));String endpoint=config.optString("endpoint",""),token=config.optString("token","");if(!validEndpoint(endpoint)||token.length()<16)throw new IOException("Invalid bundled configuration");p.edit().putString("endpoint",endpoint).putString("token",token).putBoolean("auto",false).putBoolean("bundled_config_v1",true).apply();}catch(FileNotFoundException absent){}catch(Exception error){Diagnostics.failure("report.config",error);}
 }
 public static boolean configured(Context c){android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);return validEndpoint(p.getString("endpoint",""))&&!p.getString("token","").isEmpty();}
 public static void schedule(Context c,boolean manual){if(!manual||!configured(c))return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(manual)jobs.cancel(43);else if(jobs.getPendingJob(43)!=null)return;PersistableBundle extras=new PersistableBundle();extras.putBoolean("manual",manual);jobs.schedule(ReadSync.wifiJob(c,43,ReportJob.class).setExtras(extras).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 private static String read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>131072)throw new IOException("Response too large");out.write(b,0,n);}return out.toString("UTF-8");}}
 public static JSONObject upload(String endpoint,String token,JSONObject report)throws Exception{
  if(!validEndpoint(endpoint))throw new IOException("Endpoint invalid");
  byte[] data=new JSONObject().put("token",token).put("report",report).toString().getBytes("UTF-8");if(data.length>98304)throw new IOException("Report too large");
  URL url=new URL(endpoint);boolean first=true;
  for(int hop=0;hop<4;hop++){
   HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
   try{if(first){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(data.length);try(OutputStream out=connection.getOutputStream()){out.write(data);}}
    int code=connection.getResponseCode();if(code>=300&&code<400){URL next=new URL(url,connection.getHeaderField("Location"));if(!"https".equals(next.getProtocol())||!"script.googleusercontent.com".equalsIgnoreCase(next.getHost())||next.getPort()!=-1||next.getUserInfo()!=null)throw new IOException("Unexpected redirect");url=next;first=false;continue;}
    if(code!=200)throw new IOException("HTTP "+code);return new JSONObject(read(connection.getInputStream()));
   }finally{connection.disconnect();}
  }throw new IOException("Redirect limit");
 }
 public static boolean acknowledged(JSONObject response,String id){return response.optBoolean("ok",false)&&id.equals(response.optString("reportId"));}
 public static synchronized String deliver(Context c)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);
  if(!configured(c))return "Đã lưu trên máy · Chưa có cấu hình email";
  if(!ReadSync.wifi(c)){schedule(c,true);return "Đã lưu trên máy · Chờ Wi-Fi để gửi theo yêu cầu";}
  File dir=new File(c.getFilesDir(),"report-outbox");File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files==null||files.length==0)return "Không có báo cáo đã lưu";
  String preferred=p.getString("manualReport","");Arrays.sort(files,(a,b)->{int n=Integer.compare(a.getName().equals(preferred)?0:1,b.getName().equals(preferred)?0:1);return n!=0?n:Long.compare(a.lastModified(),b.lastModified());});
  File target=files[0];JSONObject report=new JSONObject(read(new FileInputStream(target)));JSONObject response=upload(p.getString("endpoint",""),p.getString("token",""),report);
  if(!acknowledged(response,report.getString("reportId")))return rejection(response.optString("error","unknown"));
  if(!target.delete())return "Email đã gửi · Chưa xóa được bản lưu trên máy";
  Diagnostics.acknowledged(c,report);p.edit().putLong("lastSent",System.currentTimeMillis()).remove("manualReport").apply();c.getSystemService(JobScheduler.class).cancel(43);return "Đã gửi báo cáo đến email của bạn";
 }
 public static String rejection(String reason){
  switch(reason){case "unauthorized":return "Đã lưu · Mã kết nối không khớp dịch vụ";case "configure_destination":return "Đã lưu · Dịch vụ chưa đặt email nhận";case "daily_limit":return "Đã lưu · Google đã hết hạn mức email hôm nay";case "invalid_size":return "Đã lưu · Báo cáo vượt giới hạn dịch vụ";case "invalid_report":return "Đã lưu · Dịch vụ không nhận dạng được báo cáo";case "busy":return "Đã lưu · Dịch vụ đang bận, hãy gửi lại";default:return "Đã lưu · Dịch vụ chưa xác nhận, hãy kiểm tra triển khai Apps Script";}
 }
 public boolean onStartJob(JobParameters params){
  if(!params.getExtras().getBoolean("manual",false)||!configured(this))return false;stopped.set(false);
  new Thread(()->{String status;boolean retry=false;try{status=deliver(this);}catch(Exception error){Diagnostics.failure("report.upload",error);status="Đã lưu · Không kết nối được dịch vụ ("+error.getClass().getSimpleName()+")";retry=true;}getSharedPreferences("diagnostics",0).edit().putString("status",status).apply();sendBroadcast(new Intent(Repository.EVENT).setPackage(getPackageName()).putExtra("diagnostic_status",status));jobFinished(params,retry&&!stopped.get());},"Hako report upload").start();return true;
 }
 public boolean onStopJob(JobParameters params){stopped.set(true);return true;}
}

