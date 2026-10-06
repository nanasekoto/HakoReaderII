package vn.nanase.hako;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Serialized IPC. Gecko lives outside the native reader process. */
public final class GeckoClient {
 private static final Handler ui=new Handler(Looper.getMainLooper());
 private static Context app;private static volatile Messenger server;private static ServiceConnection connection;
 public static volatile boolean authenticating;
 // Gecko shuts its host down before the crash-helper process exits. Cleanup must
 // run in the surviving native process, once, after graceful engine shutdown.
 private static final Runnable cleanup=()->{
  if(app==null||connection!=null||authenticating)return;
  java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=app.getSystemService(android.app.ActivityManager.class).getRunningAppProcesses();
  if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo p:processes)
   if(p.uid==android.os.Process.myUid()&&p.processName.startsWith(app.getPackageName()+":"))android.os.Process.killProcess(p.pid);
 };
 private static final AtomicInteger ids=new AtomicInteger();
 public static void init(Context c){app=c.getApplicationContext();}
 private static void connect()throws Exception{
  if(server!=null)return;
  if(connection!=null)release();
  CountDownLatch ready=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
  ui.post(()->{ui.removeCallbacks(cleanup);connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName n,IBinder b){server=new Messenger(b);ready.countDown();}
   public void onServiceDisconnected(ComponentName n){server=null;}
   public void onNullBinding(ComponentName n){error.set("Không mở được Gecko");ready.countDown();}
  };if(!app.bindService(new Intent(app,GeckoEngineService.class),connection,Context.BIND_AUTO_CREATE)){error.set("Không khởi động được Gecko");ready.countDown();}});
  if(!ready.await(30,TimeUnit.SECONDS)||server==null)throw new IOException(error.get()==null?"Gecko khởi động quá lâu":error.get());
 }
 public static synchronized byte[] request(String url,String script,int max)throws Exception{
  if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("Không tải trên luồng giao diện");
  if(app==null)throw new IOException("Chưa khởi tạo Gecko");
  if(authenticating)throw new IOException("Đang đăng nhập; tải sẽ tiếp tục sau khi quay về app");
  final boolean canceledAtStart=Repository.cancel.get();
  File output=File.createTempFile("gecko-",".tmp",app.getCacheDir());
  try{
   connect();CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
   int id=ids.incrementAndGet();Messenger reply=new Messenger(new Handler(Looper.getMainLooper()){public void handleMessage(Message m){if(m.arg1==id){error.set(m.getData().getString("error"));done.countDown();}}});
   Bundle b=new Bundle();b.putString("url",url);b.putString("origin",HakoParser.ORIGIN);b.putString("script",script);b.putString("file",output.getAbsolutePath());b.putInt("max",max);
   Message msg=Message.obtain(null,1);msg.arg1=id;msg.replyTo=reply;msg.setData(b);server.send(msg);
   long end=SystemClock.elapsedRealtime()+60000;
   while(!done.await(250,TimeUnit.MILLISECONDS)){if(authenticating||(!canceledAtStart&&Repository.cancel.get()))throw new IOException("Đã dừng tải");if(SystemClock.elapsedRealtime()>end)throw new IOException("Gecko quá thời gian chờ");}
   if(error.get()!=null){if(error.get().contains("HTTP 403")||error.get().contains("HTTP 429")||error.get().contains("xác minh")||error.get().contains("giới hạn")||error.get().contains("đăng nhập HAKO trước"))Repository.cooldown(app,30*60*1000L);throw new IOException(error.get());}
   try(InputStream in=new FileInputStream(output);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>max)throw new IOException("Tài nguyên quá lớn");out.write(buf,0,n);}return out.toByteArray();}
  }finally{output.delete();if(!Repository.busy.get())release();}
 }
 public static synchronized void release(){
  ServiceConnection old=connection;connection=null;server=null;
  ui.post(()->{if(old!=null)try{app.unbindService(old);}catch(Exception ignored){}ui.removeCallbacks(cleanup);ui.postDelayed(cleanup,8000);});
 }
}
