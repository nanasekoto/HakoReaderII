package vn.nanase.hako;

import android.app.job.*;
import android.content.*;
import android.net.*;
import android.os.*;

/** Durable mark-all queue. Never applies an old offline request to new unread chapters. */
public final class ReadSync extends JobService {
  public static boolean wifi(Context c){
    ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);
    NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
    return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }
  static JobInfo.Builder wifiJob(Context c,int id,Class<?> service){
    JobInfo.Builder b=new JobInfo.Builder(id,new ComponentName(c,service));
    if(Build.VERSION.SDK_INT>=28)b.setRequiredNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build());
    else b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
    return b;
  }
  public static void schedule(Context c){
    if(c.getSharedPreferences("settings",0).getBoolean("sync_paused",false))return;
    Store store=Store.get(c);boolean eligible=false;for(String id:store.queuedBooks())if(store.canSubmit(id))eligible=true;if(!eligible)return;
    JobScheduler s=c.getSystemService(JobScheduler.class);
    if(s.getPendingJob(42)==null)s.schedule(wifiJob(c,42,ReadSync.class).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
  }
  private static final Object mutex=new Object();
  public static boolean process(Context c){
    synchronized(mutex){
      Repository.init(c);
      if(Repository.syncPaused)return false;
      if(!wifi(c))return false;
      if(!Repository.busy.compareAndSet(false,true))return true;
      try{
      Store s=Store.get(c);boolean retry=false;
      for(String id:s.queuedBooks()){
        Store.Book b=s.book(id);if(b==null||id.equals("demo")||!s.canSubmit(id))continue;
        try{
          Repository.allowed(c);
          c.getSharedPreferences("settings",0).edit().remove("catalog-"+id).commit();
          Repository.catalog(c,b,true);
          if(Repository.syncPaused||!s.canSubmit(id))continue;
          String snapshot=c.getSharedPreferences("read_queue",0).getString(id,"");
          RenderedPage.action(c,HakoParser.ORIGIN+"/ke-sach","read:"+id.substring(id.lastIndexOf('-')+1));
          c.getSharedPreferences("settings",0).edit().remove("catalog-"+id).commit();
          Repository.catalog(c,b,true);
          // New arrivals stay locally unread even if Hako's mark-all raced their publication.
          if(snapshot.equals(c.getSharedPreferences("read_queue",0).getString(id,"")))s.clearQueue(id);
          Repository.notify(c,s.unread(id)>0?"HAKO đã cập nhật · Có chương mới chưa đọc":"Đã đồng bộ đã đọc lên HAKO");
        }catch(Exception e){retry=true;android.util.Log.w("HakoSync","Read queue retained: "+e.getClass().getSimpleName());}
      }
      return retry;
      }finally{Repository.busy.set(false);GeckoClient.release();synchronized(Repository.busy){Repository.busy.notifyAll();}}
    }
  }
  public boolean onStartJob(JobParameters p){
    if(!wifi(this))return false;
    new Thread(()->{boolean retry=process(this);jobFinished(p,retry);},"Hako read queue").start();return true;
  }
  public boolean onStopJob(JobParameters p){return true;}
}
