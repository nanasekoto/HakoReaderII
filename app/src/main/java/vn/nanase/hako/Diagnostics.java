package vn.nanase.hako;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Bounded aggregate telemetry. Never records URLs, cookies, novel text or credentials. */
public final class Diagnostics {
 private static final Map<String,long[]> timings=new LinkedHashMap<>();
 private static final Map<String,ArrayDeque<Long>> samples=new LinkedHashMap<>();
 private static final ConcurrentHashMap<String,AtomicLong> counters=new ConcurrentHashMap<>();
 private static final ArrayDeque<String> errors=new ArrayDeque<>();
 private static final ScheduledExecutorService disk=Executors.newSingleThreadScheduledExecutor();
 private static final AtomicBoolean flushQueued=new AtomicBoolean();
 private static volatile Context app;public static volatile boolean foreground;
 private static final Handler mainHandler=new Handler(Looper.getMainLooper());
 private static volatile long probeAt;private static volatile boolean probePending;private static int stallCaptures;
 private static final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
 private static long started,cpuStarted;private static String session="";private static JSONObject previousSession;private static String commit="unknown";
 public static long now(){return SystemClock.elapsedRealtime();}
 public static void count(String name){if(app!=null&&(counters.size()<48||counters.containsKey(name)))counters.computeIfAbsent(name,k->new AtomicLong()).incrementAndGet();}
 public static synchronized void elapsed(String name,long begin){
  if(app==null)return;
  long ms=Math.max(0,now()-begin);long[] s=timings.get(name);if(s==null){if(timings.size()>=32)return;s=new long[]{0,0,Long.MAX_VALUE,0};timings.put(name,s);}s[0]++;s[1]+=ms;s[2]=Math.min(s[2],ms);s[3]=Math.max(s[3],ms);flushLater();
  ArrayDeque<Long> window=samples.computeIfAbsent(name,k->new ArrayDeque<>());window.addLast(ms);while(window.size()>64)window.removeFirst();
 }
 public static synchronized void failure(String stage,Throwable e){errors.addLast(stage+": "+e.getClass().getSimpleName());StackTraceElement[] frames=e.getStackTrace();for(int i=0;i<Math.min(4,frames.length);i++)errors.addLast(frames[i].getClassName()+"."+frames[i].getMethodName()+":"+frames[i].getLineNumber());while(errors.size()>16)errors.removeFirst();flushLater();}
 public static synchronized void start(Context c){
  if(app!=null){foreground=true;return;}
  app=c.getApplicationContext();started=now();cpuStarted=android.os.Process.getElapsedCpuTime();session=UUID.randomUUID().toString();foreground=true;
  monitor.scheduleWithFixedDelay(Diagnostics::probe,3,3,TimeUnit.SECONDS);
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{failure("crash",error);write(new AtomicFile(new File(app.getFilesDir(),"diagnostics-crash.json")),payload());}catch(Exception ignored){}finally{if(previous!=null)previous.uncaughtException(thread,error);else android.os.Process.killProcess(android.os.Process.myPid());}});
  disk.execute(()->{try{
   try(InputStream info=app.getAssets().open("build-info.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=info.read(buffer))!=-1)bytes.write(buffer,0,n);commit=new JSONObject(bytes.toString("UTF-8")).optString("commit","unknown");}catch(Exception ignored){}
   try{JSONObject old=new JSONObject(new String(new AtomicFile(new File(app.getFilesDir(),"diagnostics-last.json")).readFully(),java.nio.charset.StandardCharsets.UTF_8));synchronized(Diagnostics.class){previousSession=old;}}catch(Exception ignored){}
   if(Build.VERSION.SDK_INT>=30){List<ApplicationExitInfo> exits=app.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(app.getPackageName(),0,1);if(!exits.isEmpty()){ApplicationExitInfo exit=exits.get(0);android.content.SharedPreferences p=app.getSharedPreferences("diagnostics",0);if(exit.getTimestamp()>p.getLong("exit_seen",0)){synchronized(Diagnostics.class){errors.addLast("previous_exit_reason="+exit.getReason());}p.edit().putLong("exit_seen",exit.getTimestamp()).apply();}}}
   flush();
  }catch(Exception e){failure("start",e);}});
 }
 public static void captureStall(long delay)throws Exception{
  JSONObject record=new JSONObject().put("schema",1).put("session",session).put("version",app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName).put("commit",commit).put("delayMs",delay).put("capturedAt",System.currentTimeMillis());JSONArray frames=new JSONArray();
  for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace()){if(frames.length()>=24)break;frames.put(frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());}record.put("mainThread",frames);
  write(new AtomicFile(new File(app.getFilesDir(),"diagnostics-stall.json")),record);count("ui.stall");
 }
 private static void probe(){
  if(!foreground){probePending=false;return;}
  if(probePending){long delay=now()-probeAt;if(delay>=6000&&stallCaptures<3){stallCaptures++;try{captureStall(delay);}catch(Exception ignored){}}return;}
  probeAt=now();probePending=true;mainHandler.post(()->probePending=false);
 }
 private static void flushLater(){if(app!=null&&flushQueued.compareAndSet(false,true))disk.schedule(()->{flushQueued.set(false);try{flush();}catch(Exception ignored){}},2000,TimeUnit.MILLISECONDS);}
 public static JSONObject payload()throws Exception{
  JSONObject root=new JSONObject().put("schema",1).put("session",session).put("generatedAt",System.currentTimeMillis()).put("uptimeMs",Math.max(0,now()-started)).put("commit",commit);
  if(app!=null){android.content.pm.PackageInfo info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);root.put("version",info.versionName).put("versionCode",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);}
  root.put("android",Build.VERSION.RELEASE).put("model",Build.MODEL).put("mainProcessCpuMs",Math.max(0,android.os.Process.getElapsedCpuTime()-cpuStarted));
  Runtime r=Runtime.getRuntime();root.put("heapUsed",r.totalMemory()-r.freeMemory()).put("heapMax",r.maxMemory());
  synchronized(Diagnostics.class){JSONObject times=new JSONObject();for(Map.Entry<String,long[]> e:timings.entrySet()){long[] s=e.getValue();times.put(e.getKey(),new JSONObject().put("count",s[0]).put("totalMs",s[1]).put("minMs",s[2]).put("maxMs",s[3]).put("recentMs",new JSONArray(samples.getOrDefault(e.getKey(),new ArrayDeque<>()))));}root.put("timings",times);
  JSONObject counts=new JSONObject();for(Map.Entry<String,AtomicLong> e:counters.entrySet())counts.put(e.getKey(),e.getValue().get());root.put("counts",counts);
  JSONArray issues=new JSONArray();for(String error:errors)issues.put(error);root.put("issues",issues);}root.put("operations",OperationTrace.payload());return root;
 }
 public static void flush()throws Exception{if(app!=null)write(new AtomicFile(new File(app.getFilesDir(),"diagnostics-last.json")),payload());}
 private static void write(AtomicFile target,JSONObject json)throws Exception{FileOutputStream stream=null;try{stream=target.startWrite();stream.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception e){if(stream!=null)target.failWrite(stream);throw e;}}
 public static File queue(Context c)throws Exception{
  JSONObject report=payload();report.put("reportId",UUID.randomUUID().toString());report.put("workflow",UpdateReport.metrics(c));
  try{report.put("mainPssKb",Debug.getPss());}catch(Exception ignored){}synchronized(Diagnostics.class){if(previousSession!=null)report.put("previousSession",previousSession);}
  for(String name:new String[]{"diagnostics-last.json","diagnostics-crash.json"})try{JSONObject old=new JSONObject(new String(new AtomicFile(new File(c.getFilesDir(),name)).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(!session.equals(old.optString("session")))report.put(name.startsWith("diagnostics-crash")?"previousCrash":"previousSession",old);}catch(Exception ignored){}
  try{report.put("uiStall",new JSONObject(new String(new AtomicFile(new File(c.getFilesDir(),"diagnostics-stall.json")).readFully(),java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception ignored){}
  File dir=new File(c.getFilesDir(),"report-outbox");dir.mkdirs();File[] old=dir.listFiles((d,n)->n.endsWith(".json"));if(old!=null&&old.length>=8){Arrays.sort(old,Comparator.comparingLong(File::lastModified));for(int i=0;i<=old.length-8;i++)old[i].delete();}
  File target=new File(dir,report.getString("reportId")+".json");write(new AtomicFile(target),report);return target;
 }
 public static void acknowledged(Context c,JSONObject report){
  synchronized(Diagnostics.class){JSONObject old=report.optJSONObject("previousSession");if(previousSession!=null&&old!=null&&previousSession.optString("session").equals(old.optString("session")))previousSession=null;}
  JSONObject stall=report.optJSONObject("uiStall");if(stall!=null)try{AtomicFile file=new AtomicFile(new File(c.getFilesDir(),"diagnostics-stall.json"));JSONObject existing=new JSONObject(new String(file.readFully(),java.nio.charset.StandardCharsets.UTF_8));if(stall.optString("session").equals(existing.optString("session"))&&stall.optLong("capturedAt")==existing.optLong("capturedAt"))file.delete();}catch(Exception ignored){}
  JSONObject sent=report.optJSONObject("previousCrash");if(sent!=null)try{AtomicFile file=new AtomicFile(new File(c.getFilesDir(),"diagnostics-crash.json"));JSONObject existing=new JSONObject(new String(file.readFully(),java.nio.charset.StandardCharsets.UTF_8));if(existing.optString("session").equals(sent.optString("session")))file.delete();}catch(Exception ignored){}
 }
 public static String summary(Context c)throws Exception{
  JSONObject data=payload();StringBuilder s=new StringBuilder("Hako "+data.optString("version")+" · "+Build.MODEL+"\n");
  s.append("Email được gửi qua dịch vụ bạn cấu hình.\n").append(c.getSharedPreferences("diagnostics",0).getString("status","Chưa gửi báo cáo")).append("\n\n");
  for(Map.Entry<String,long[]> e:timingCopy().entrySet())s.append(e.getKey()).append(": ").append(e.getValue()[0]).append(" lượt · tối đa ").append(e.getValue()[3]).append(" ms\n");
  s.append("\nDB query: ").append(counters.getOrDefault("db.query",new AtomicLong()).get()).append(" · kiểm tra cache: ").append(counters.getOrDefault("cache.check",new AtomicLong()).get());return s.toString();
 }
 private static synchronized Map<String,long[]> timingCopy(){Map<String,long[]> result=new LinkedHashMap<>();for(Map.Entry<String,long[]> e:timings.entrySet())result.put(e.getKey(),e.getValue().clone());return result;}
 public static void background(Context c){foreground=false;disk.execute(()->{try{flush();}catch(Exception ignored){}});}
}

