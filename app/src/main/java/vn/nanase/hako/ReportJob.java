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
 public static boolean configured(Context c){android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);return validEndpoint(p.getString("endpoint",""))&&!p.getString("token","").isEmpty();}
 public static void schedule(Context c,boolean manual){if(!configured(c))return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(manual)jobs.cancel(43);else if(jobs.getPendingJob(43)!=null)return;PersistableBundle extras=new PersistableBundle();extras.putBoolean("manual",manual);jobs.schedule(ReadSync.wifiJob(c,43,ReportJob.class).setExtras(extras).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
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
 public boolean onStartJob(JobParameters params){
  boolean manual=params.getExtras().getBoolean("manual",false);if(!configured(this)||(!manual&&Diagnostics.foreground))return false;stopped.set(false);
  new Thread(()->{boolean retry=false;String status="Đang chờ gửi báo cáo";
   try{android.content.SharedPreferences p=getSharedPreferences("diagnostics",0);if(!ReadSync.wifi(this)){retry=true;return;}
    if(!manual&&System.currentTimeMillis()-p.getLong("lastSent",0)<24*60*60*1000L)return;
    File dir=new File(getFilesDir(),"report-outbox");File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files==null||files.length==0)return;
    String preferred=p.getString("manualReport","");Arrays.sort(files,(a,b)->{int n=Integer.compare(a.getName().equals(preferred)?0:1,b.getName().equals(preferred)?0:1);return n!=0?n:Long.compare(a.lastModified(),b.lastModified());});
    File target=files[0];JSONObject report=new JSONObject(read(new FileInputStream(target)));JSONObject response=upload(p.getString("endpoint",""),p.getString("token",""),report);
    if(acknowledged(response,report.getString("reportId"))){target.delete();Diagnostics.acknowledged(this,report);android.content.SharedPreferences.Editor saved=p.edit().putLong("lastSent",System.currentTimeMillis());if(target.getName().equals(p.getString("manualReport","")))saved.remove("manualReport");saved.apply();status="Đã gửi báo cáo qua dịch vụ email";}
    else{status="Dịch vụ chưa xác nhận gửi báo cáo";retry=response.optBoolean("retryable",true);}
   }catch(Exception error){Diagnostics.failure("report.upload",error);retry=true;status="Chưa gửi được · Báo cáo được giữ để thử lại";}
   finally{getSharedPreferences("diagnostics",0).edit().putString("status",status).apply();if(manual)sendBroadcast(new Intent(Repository.EVENT).setPackage(getPackageName()).putExtra("diagnostic_status",status));jobFinished(params,retry&&!stopped.get());}
  },"Hako report upload").start();return true;
 }
 public boolean onStopJob(JobParameters params){stopped.set(true);return true;}
}
