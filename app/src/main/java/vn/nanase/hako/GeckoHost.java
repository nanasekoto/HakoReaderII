package vn.nanase.hako;
import android.content.Context;
import android.os.*;
import org.mozilla.geckoview.*;
/** Only loaded in :gecko. Main reader never creates a GeckoRuntime. */
final class GeckoHost {
 static GeckoRuntime runtime;static int browsers;static boolean bound,closing;
 static final Handler ui=new Handler(Looper.getMainLooper());
 static final Runnable shutdown=()->{
  if(bound||browsers>0||closing)return;
  closing=true;
  if(runtime==null){android.os.Process.killProcess(android.os.Process.myPid());return;}
  runtime.shutdown();
 };
 static void active(){ui.removeCallbacks(shutdown);}
 static GeckoRuntime get(Context c){
  active();if(closing)throw new IllegalStateException("Gecko đang đóng; thử lại sau vài giây");
  if(runtime==null){boolean debug=(c.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;runtime=GeckoRuntime.create(c.getApplicationContext(),new GeckoRuntimeSettings.Builder().debugLogging(false).consoleOutput(debug).remoteDebuggingEnabled(false).build());
   runtime.setDelegate(new GeckoRuntime.Delegate(){public void onShutdown(){
    // The callback precedes final child-service teardown. Let bindings close first,
    // then release any cached engine children owned by this app and the host.
    ui.postDelayed(()->{
     android.app.ActivityManager am=c.getApplicationContext().getSystemService(android.app.ActivityManager.class);
     java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=am.getRunningAppProcesses();
     if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo p:processes)
      if(p.uid==android.os.Process.myUid()&&p.pid!=android.os.Process.myPid()&&p.processName.startsWith(c.getPackageName()+":"))android.os.Process.killProcess(p.pid);
     android.os.Process.killProcess(android.os.Process.myPid());
    },1500);
   }});
  }return runtime;
 }
 static void idle(){ui.removeCallbacks(shutdown);ui.postDelayed(shutdown,5000);}
}
