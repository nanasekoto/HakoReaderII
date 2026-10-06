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
  if(runtime==null){runtime=GeckoRuntime.create(c.getApplicationContext(),new GeckoRuntimeSettings.Builder().debugLogging(false).consoleOutput(false).remoteDebuggingEnabled(false).build());
   runtime.setDelegate(new GeckoRuntime.Delegate(){public void onShutdown(){android.os.Process.killProcess(android.os.Process.myPid());}});
  }return runtime;
 }
 static void idle(){ui.removeCallbacks(shutdown);ui.postDelayed(shutdown,5000);}
}
