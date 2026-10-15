package vn.nanase.hako;

import android.app.*;
import android.content.*;
import android.os.*;

public class DownloadService extends Service {
  private volatile Thread worker;
  private BroadcastReceiver receiver;

  public void onCreate() {
    super.onCreate();
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("downloads", "Tải truyện", NotificationManager.IMPORTANCE_LOW));
    receiver =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            if(!i.hasExtra("read_sync_book")&&i.getStringExtra("text")!=null)nm.notify(9, notice(i.getStringExtra("text")));
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
      Repository.stopDownloads(this);
      stopSelf();
      return START_NOT_STICKY;
    }
    if(getSharedPreferences("settings",0).getBoolean("sync_paused",false)&&i.getIntExtra("mode",0)!=Repository.MODE_BOOK_ALL){stopSelf();return START_NOT_STICKY;}
    Repository.cancel.set(false);
    startForeground(9, notice("Chuẩn bị tải"));
    String book = i.getStringExtra("book");
    int mode = i.getIntExtra("mode", 0);
    if (mode == 0) {
      boolean all = i.getBooleanExtra("all", false);
      mode = all ? Repository.MODE_SYNC_LIBRARY : Repository.MODE_BOOK_NEXT;
    }
    final int finalMode = mode,acceptedStop=Repository.stopEpoch();
    if (worker != null && worker.isAlive()) {
      if (finalMode == Repository.MODE_SYNC_LIBRARY) Repository.pendingAll = true;
      else if(finalMode==Repository.MODE_SYNC_PERSONAL)Repository.pendingPersonal=true;
      else if (book != null) {if(finalMode==Repository.MODE_BOOK_ALL)Store.get(this).setKeepFull(book,true);Repository.queueBook(book,finalMode);}
      if(finalMode==Repository.MODE_BOOK_ALL || finalMode==Repository.MODE_SYNC_LIBRARY||finalMode==Repository.MODE_SYNC_PERSONAL)Repository.manualOverride=true;
      return START_NOT_STICKY;
    }
    worker =
        new Thread(
            () -> {
              try {
                Repository.awaitIdle();
                Repository.run(this, book, finalMode, false,acceptedStop);
                Repository.awaitIdle();
                while(acceptedStop==Repository.stopEpoch()&&!Repository.cancel.get()&&(!Repository.syncPaused||(!Repository.pendingAll&&!Repository.pendingPersonal&&!Repository.pendingBook.isEmpty()&&Repository.queuedBookMode()==Repository.MODE_BOOK_ALL))&&(Repository.pendingAll||Repository.pendingPersonal||!Repository.pendingBook.isEmpty())){
                  boolean shelf=Repository.pendingAll;boolean personal=!shelf&&Repository.pendingPersonal;
                  Repository.BookRequest request=(shelf||personal)?null:Repository.takeBook();
                  if(shelf)Repository.pendingAll=false;else if(personal)Repository.pendingPersonal=false;
                  Repository.run(this,(shelf||personal)?"":request.book,shelf?Repository.MODE_SYNC_LIBRARY:personal?Repository.MODE_SYNC_PERSONAL:request.mode,false,acceptedStop);
                  Repository.awaitIdle();
                }
              } catch (Exception e) {
                Repository.notify(this, "Tạm dừng: " + e.getMessage());
              } finally {
                stopForeground(true);
                stopSelf(id);worker=null;Repository.resumePending(this);
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
