package vn.nanase.hako;

import android.app.job.*;
import android.content.*;
import android.os.*;

public class ChargeJob extends JobService {
  private static final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
  public static void schedule(Context c, boolean enabled) {
    JobScheduler s = c.getSystemService(JobScheduler.class);
    if (!enabled||c.getSharedPreferences("settings",0).getBoolean("sync_paused",false)) {
      s.cancel(40);s.cancel(41);
      return;
    }
    JobInfo old=s.getPendingJob(40);
    if(old!=null && (old.getRequiredNetwork()==null||!old.getRequiredNetwork().toString().contains("WIFI"))){s.cancel(40);old=null;}
    if(old==null)s.schedule(ReadSync.wifiJob(c,40,ChargeJob.class).setRequiresCharging(true).setPersisted(true).setPeriodic(60*60*1000L).build());
    kick(c);
  }
  public static void kick(Context c){
    if(c.getSharedPreferences("settings",0).getBoolean("sync_paused",false)||!c.getSharedPreferences("settings",0).getBoolean("charging",true))return;
    JobScheduler s=c.getSystemService(JobScheduler.class);
    if(s.getPendingJob(41)==null)s.schedule(ReadSync.wifiJob(c,41,ChargeJob.class).setRequiresCharging(true).setPersisted(true).build());
  }

  public static boolean isCharging(Context c) {
    Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
  }

  public boolean onStartJob(JobParameters p) {
    if (getSharedPreferences("settings",0).getBoolean("sync_paused",false) || Repository.busy.get() || !ReadSync.wifi(this) || !isCharging(this) || !running.compareAndSet(false,true)) return false;
    new Thread(
            () -> {
              boolean retry = false;
              try {
                Repository.run(this, "", Repository.MODE_SYNC_LIBRARY, true);
              } catch (Exception e) {
                retry = false;
                Repository.notify(this, "Tải khi sạc: " + e.getMessage());
              }
              running.set(false);jobFinished(p, retry);
            },
            "Hako charging")
        .start();
    return true;
  }

  public boolean onStopJob(JobParameters p) {
    if (!Repository.manualOverride) Repository.cancel.set(true);
    return false;
  }
}
