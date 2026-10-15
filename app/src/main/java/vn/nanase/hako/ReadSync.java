package vn.nanase.hako;

import android.app.job.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.util.*;
import org.json.*;

/** Durable requests: only their original generation may consume a web reply. */
public final class ReadSync extends JobService {
  public static boolean connected(Context c){ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
  public static boolean wifi(Context c){ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}
  static JobInfo.Builder wifiJob(Context c,int id,Class<?> service){JobInfo.Builder b=new JobInfo.Builder(id,new ComponentName(c,service));if(Build.VERSION.SDK_INT>=28)b.setRequiredNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build());else b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);return b;}
  public static void schedule(Context c){if(c.getSharedPreferences("settings",0).getBoolean("sync_paused",false)||Store.get(c).queuedBooks().isEmpty())return;JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs.getPendingJob(42)==null)jobs.schedule(new JobInfo.Builder(42,new ComponentName(c,ReadSync.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
  private static final Object mutex=new Object();
  private static String label(String state){switch(state){case "WAIT_NETWORK":return "Đã đọc hết · Chờ mạng để gửi HAKO";case "WAIT_VERIFY":return "Đã đọc hết · Đang chờ kiểm tra HAKO";case "NO_BUTTON":return "Đã đọc hết trên máy · HAKO không có nút Đọc hết";case "NEW_UNREAD":return "HAKO có chương mới · Chưa gửi Đọc hết";case "SENDING":return "Đang gửi Đọc hết lên HAKO";case "SENT":return "Đã đồng bộ đã đọc lên HAKO";default:return "Chờ gửi Đọc hết · Mở Tài khoản nếu cần đăng nhập/xác minh";}}
  public static void publish(Context c,String id,String state,String reason){
    // Bounded durable business state, no titles, credentials or exception messages.
    try{synchronized(ReadSync.class){SharedPreferences p=c.getSharedPreferences("read_sync_status",0);JSONObject all=new JSONObject(p.getString("rows","{}"));JSONObject old=all.optJSONObject(id);Store.ReadRequest request=Store.get(c).readRequest(id);String generation=request==null?"":request.generation;
      if(old==null||!state.equals(old.optString("state"))||!reason.equals(old.optString("reason"))||!generation.equals(old.optString("generation"))){
        while(!all.has(id)&&all.length()>=24){String oldest=null;long time=Long.MAX_VALUE;for(Iterator<String> keys=all.keys();keys.hasNext();){String k=keys.next();long stamp=all.getJSONObject(k).optLong("at");if(stamp<time){oldest=k;time=stamp;}}if(oldest==null)break;all.remove(oldest);}
        all.put(id,new JSONObject().put("state",state).put("reason",reason).put("at",System.currentTimeMillis()).put("generation",generation).put("tip",request==null?(old==null?"":old.optString("tip")):request.tip));p.edit().putString("rows",all.toString()).apply();
      }
    }}catch(Exception e){Diagnostics.failure("read.sync.status",e);}
    c.sendBroadcast(new Intent(Repository.EVENT).setPackage(c.getPackageName()).putExtra("text",label(state)).putExtra("read_sync_book",id).putExtra("read_sync_state",state));
  }
  public static JSONObject report(Context c)throws JSONException{Store s=Store.get(c);JSONObject report=new JSONObject().put("schema",1).put("count",s.queuedBooks().size()).put("states",new JSONObject(c.getSharedPreferences("read_sync_status",0).getString("rows","{}")));JSONArray pending=new JSONArray();int n=0;for(Store.ReadRequest q:s.readRequests().values()){if(n++>=24)break;pending.put(new JSONObject().put("bookId",q.id).put("tip",q.tip).put("generation",q.generation));}return report.put("pending",pending);}
  public static void observeShelf(Context c,String html){observeShelf(c,html,Store.get(c).readRequests());}
  public static void observeShelf(Context c,String html,Map<String,Store.ReadRequest> observed){
    org.jsoup.nodes.Document doc=org.jsoup.Jsoup.parse(html);
    if(doc.selectFirst("input[type=password]")!=null||doc.selectFirst("table")==null||doc.selectFirst("#challenge-form,#challenge-running")!=null)return;
    Store s=Store.get(c);
    for(HakoParser.Link row:HakoParser.shelf(html,HakoParser.ORIGIN)){
      if(row.readButtonAvailable)s.readButtonReturned(row.id);
      else if(s.retireReadRequest(observed.get(row.id),true))publish(c,row.id,"NO_BUTTON","verified_shelf_row");
    }
  }
  public static boolean process(Context c){OperationTrace.Span trace=OperationTrace.begin("read.sync");boolean previous=RequestTurn.background.get();RequestTurn.background.set(true);String outcome="no_request";try{
    synchronized(mutex){Repository.init(c);if(Repository.syncPaused){outcome="paused";return false;}Store s=Store.get(c);boolean eligible=false;
      for(String id:s.queuedBooks())if(s.canSubmit(id)){eligible=true;break;}
      if(!eligible)return false;
      if(!connected(c)){for(String id:s.queuedBooks())publish(c,id,"WAIT_NETWORK","network_unavailable");outcome="wait_network";return true;}
      if(!Repository.busy.compareAndSet(false,true)){outcome="wait_owner";return true;}
      boolean finished=false;
      try{Repository.cancel.set(false);Repository.importShelf(c);boolean retry=processQueued(c);outcome=retry?"retry":"processed";finished=!retry;return retry;}
      catch(Exception e){for(String id:s.queuedBooks())publish(c,id,"WAIT_RETRY",e.getClass().getSimpleName());outcome="retry";return true;}
      finally{GeckoClient.release();Repository.syncState=finished?"COMPLETED":"WAITING";Repository.executionStage="IDLE";Repository.busy.set(false);synchronized(Repository.busy){Repository.busy.notifyAll();}Repository.resumePending(c);}
    }
  }finally{RequestTurn.background.set(previous);OperationTrace.end(trace,outcome,0);}}
  /** Web owner calls this after shelf import; never acquires busy recursively. */
  public static boolean processQueued(Context c){
    Store s=Store.get(c);boolean retry=false;
    for(Store.ReadRequest q:s.readRequests().values()){
      Store.Book b=s.book(q.id);if(b==null||q.id.equals("demo")||!s.sameReadRequest(q))continue;
      if(!connected(c)){publish(c,q.id,"WAIT_NETWORK","network_unavailable");retry=true;continue;}
      if(!s.canSubmit(q.id)){if(s.unread(q.id)>0&&s.retireReadRequest(q,false))publish(c,q.id,"NEW_UNREAD","local_watermark_behind");continue;}
      try{
        Repository.allowed(c);publish(c,q.id,"WAIT_VERIFY","fresh_catalog");Repository.catalog(c,b,true);
        if(Repository.syncPaused||Repository.cancel.get()||!s.sameReadRequest(q))continue;
        if(!s.canSubmitServer(q.id)){
          if(s.unread(q.id)>0&&s.retireReadRequest(q,false))publish(c,q.id,"NEW_UNREAD","fresh_catalog_has_new_text");
          else {publish(c,q.id,"WAIT_RETRY","server_tip_unverified");retry=true;}continue;
        }
        publish(c,q.id,"SENDING","verified_tip");
        String action=RenderedPage.action(c,HakoParser.ORIGIN+"/ke-sach","read:"+q.id.substring(q.id.lastIndexOf('-')+1));
        if("NO_READ_BUTTON".equals(action)){if(s.retireReadRequest(q,true))publish(c,q.id,"NO_BUTTON","verified_action_row");continue;}
        if(!"OK".equals(action)&&!"OK_BUTTON_GONE".equals(action))throw new java.io.IOException("Unexpected read-all response");
        boolean retired=s.retireReadRequest(q,"OK_BUTTON_GONE".equals(action));
        if(retired)publish(c,q.id,"SENT","OK_BUTTON_GONE".equals(action)?"button_removed_after_click":"button_unread_zero");
        Repository.catalog(c,b,true);
        if(retired&&s.unread(q.id)>0)publish(c,q.id,"NEW_UNREAD","post_action_new_text");
      }catch(Exception e){retry|=s.sameReadRequest(q);if(s.sameReadRequest(q))publish(c,q.id,"WAIT_RETRY",e.getClass().getSimpleName());Diagnostics.failure("read.sync",e);}
    }
    return retry;
  }
  public boolean onStartJob(JobParameters p){new Thread(()->{boolean retry=process(this);jobFinished(p,retry);},"Hako read queue").start();return true;}
  public boolean onStopJob(JobParameters p){return true;}
}
