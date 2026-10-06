package vn.nanase.hako;
import android.content.Context;
import android.os.*;
import org.mozilla.geckoview.*;
/** Only loaded in :gecko. Main reader never creates a GeckoRuntime. */
final class GeckoHost {
 static GeckoRuntime runtime;static int browsers;static boolean bound;
 static GeckoRuntime get(Context c){if(runtime==null)runtime=GeckoRuntime.create(c.getApplicationContext());return runtime;}
 static void idle(){new Handler(Looper.getMainLooper()).postDelayed(()->{
  if(bound||browsers>0)return;
  if(runtime!=null)runtime.shutdown();
  new Handler(Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),1000);
 },2000);}
}
