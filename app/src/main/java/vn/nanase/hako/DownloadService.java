package vn.nanase.hako;

import android.app.*;
import android.content.*;
import android.os.*;

public class DownloadService extends Service {
  private Thread worker;
  private BroadcastReceiver receiver;

  public void onCreate() {
    super.onCreate();
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("downloads", "Tải truyện", NotificationManager.IMPORTANCE_LOW));
    receiver =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            nm.notify(9, notice(i.getStringExtra("text")));
          }
        };
    registerReceiver(receiver, new IntentFilter(Repository.EVENT));
  }

  private Notification notice(String text) {
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent pi =
        PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Intent stop = new Intent(this, DownloadService.class).setAction("STOP");
    PendingIntent sp =
        PendingIntent.getService(
            this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "downloads")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Hako Pocket")
        .setContentText(text)
        .setContentIntent(pi)
        .addAction(new Notification.Action.Builder(null, "Dừng", sp).build())
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build();
  }

  public int onStartCommand(Intent i, int flags, int id) {
    if (i == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("STOP".equals(i.getAction())) {
      Repository.cancel.set(true);
      stopSelf();
      return START_NOT_STICKY;
    }
    Repository.cancel.set(false);
    startForeground(9, notice("Chuẩn bị tải"));
    String book = i.getStringExtra("book");
    boolean all = i.getBooleanExtra("all", false);
    if (worker != null && worker.isAlive()) {
      if (all) Repository.pendingAll = true;
      else if (book != null) Repository.pendingBook = book;
      if(all)Repository.manualOverride = true;
      return START_NOT_STICKY;
    }
    worker =
        new Thread(
            () -> {
              try {
                Repository.run(this, book, all, false);
                while (Repository.busy.get()) Thread.sleep(500);
              } catch (Exception e) {
                Repository.notify(this, "Tạm dừng: " + e.getMessage());
              } finally {
                stopForeground(true);
                stopSelf();
              }
            },
            "Hako downloads");
    worker.start();
    return START_NOT_STICKY;
  }

  public void onDestroy() {
    if (receiver != null) unregisterReceiver(receiver);
    super.onDestroy();
  }

  public IBinder onBind(Intent i) {
    return null;
  }
}
