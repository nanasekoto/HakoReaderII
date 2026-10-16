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
  // Events wake one worker immediately; Job 42 is only the durable OS fallback.
  private static final Object gate=new Object();
  private static boolean workerActive,demand;
  interface Web {boolean connected(Context c);void catalog(Context c,Store.Book b)throws Exception;String action(Context c,String id)throws Exception;}
  static volatile Web web=new Web(){public boolean connected(Context c){return ReadSync.connected(c);}public void catalog(Context c,Store.Book b)throws Exception{Repository.catalog(c,b,true);}public String action(Context c,String id)throws Exception{return RenderedPage.action(c,HakoParser.ORIGIN+"/ke-sach","read:"+id.substring(id.lastIndexOf('-')+1));}};
  private static final ThreadLocal<java.util.concurrent.atomic.AtomicBoolean> stopped=new ThreadLocal<>();
  private final Map<JobParameters,java.util.concurrent.atomic.AtomicBoolean> jobsRunning=new java.util.concurrent.ConcurrentHashMap<>();
  public static void schedule(Context c){kick(c,"event");}
  public static void kick(Context context,String reason){
    Context c=context.getApplicationContext();
    synchronized(gate){demand=true;if(workerActive)return;workerActive=true;}
    new Thread(()->drain(c,null,"event:"+reason),"Hako read queue").start();
  }
  public static void ownerReleased(Context c,String reason){kick(c,reason);}
  private static void metric(Context c,String key,String value){c.getSharedPreferences("read_sync_status",0).edit().putString(key,value).apply();}
  private static void fallback(Context c,boolean retry){
    JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
    if(c.getSharedPreferences("settings",0).getBoolean("sync_paused",false)||Store.get(c).queuedBooks().isEmpty()){
      if(stopped.get()==null)scheduler.cancel(42);return;
    }
    if(retry&&scheduler.getPendingJob(42)==null)scheduler.schedule(new JobInfo.Builder(42,new ComponentName(c,ReadSync.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(60000).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_LINEAR).build());
  }
  private static boolean drain(Context c,java.util.concurrent.atomic.AtomicBoolean token,String reason){
    if(token!=null)stopped.set(token);boolean retry=true;
    try{
      do{
        synchronized(gate){demand=false;}
        c.getSharedPreferences("read_sync_status",0).edit().putLong("lastScheduleAt",System.currentTimeMillis()).putString("lastScheduleReason",reason).putString("lastScheduleOutcome","kicked_worker").apply();
        fallback(c,true);retry=processPass(c);fallback(c,retry);
        synchronized(gate){if(!demand||isStopped())return retry;}
        reason="event_during_worker";
      }while(true);
    }catch(Throwable error){Diagnostics.failure("read.sync.worker",error);metric(c,"lastProcessOutcome","worker_exception");fallback(c,true);return true;}
    finally{
      stopped.remove();boolean again;
      synchronized(gate){workerActive=false;gate.notifyAll();again=demand&&!isPaused(c);}
      if(again)kick(c,"handoff_after_worker");
    }
  }
  private static boolean isPaused(Context c){return c.getSharedPreferences("settings",0).getBoolean("sync_paused",false);}
  private static boolean isStopped(){java.util.concurrent.atomic.AtomicBoolean token=stopped.get();return token!=null&&token.get();}
  public static boolean process(Context c){synchronized(gate){if(workerActive){demand=true;return true;}workerActive=true;}return drain(c,null,"direct");}
  private static String label(String state){switch(state){case "QUEUED":return "Đã đọc hết · Đã xếp hàng gửi HAKO";case "PAUSED":return "Đã đọc hết · Đồng bộ đang tạm dừng";case "WAIT_BUSY":return "Đã đọc hết · Chờ tác vụ HAKO kết thúc";case "LOCAL_ONLY":return "Đã đọc hết trên máy · Chưa theo dõi trên tủ sách HAKO";case "WAIT_NETWORK":return "Đã đọc hết · Chờ mạng để gửi HAKO";case "WAIT_VERIFY":return "Đã đọc hết · Đang chờ kiểm tra HAKO";case "NO_BUTTON":return "Đã đọc hết trên máy · HAKO không có nút Đọc hết";case "NEW_UNREAD":return "HAKO có chương mới · Chưa gửi Đọc hết";case "SENDING":return "Đang gửi Đọc hết lên HAKO";case "SENT":return "Đã đồng bộ đã đọc lên HAKO";default:return "Chờ gửi Đọc hết · Mở Tài khoản nếu cần đăng nhập/xác minh";}}
  public static void publish(Context c,String id,String state,String reason){
    // Bounded durable business state, no titles, credentials or exception messages.
    Store.ReadRequest request=Store.get(c).readRequest(id);
    try{synchronized(ReadSync.class){SharedPreferences p=c.getSharedPreferences("read_sync_status",0);JSONObject all=new JSONObject(p.getString("rows","{}"));JSONObject old=all.optJSONObject(id);String generation=request==null?(old==null?"":old.optString("generation")):request.generation;
      if(old==null||!state.equals(old.optString("state"))||!reason.equals(old.optString("reason"))||!generation.equals(old.optString("generation"))){
        while(!all.has(id)&&all.length()>=24){String oldest=null;long time=Long.MAX_VALUE;for(Iterator<String> keys=all.keys();keys.hasNext();){String k=keys.next();long stamp=all.getJSONObject(k).optLong("at");if(stamp<time){oldest=k;time=stamp;}}if(oldest==null)break;all.remove(oldest);}
        all.put(id,new JSONObject().put("state",state).put("reason",reason).put("at",System.currentTimeMillis()).put("generation",generation).put("tip",request==null?(old==null?"":old.optString("tip")):request.tip).put("attempts",old!=null&&generation.equals(old.optString("generation"))?old.optInt("attempts"):0));p.edit().putString("rows",all.toString()).apply();
      }
    }}catch(Exception e){Diagnostics.failure("read.sync.status",e);}
    c.sendBroadcast(new Intent(Repository.EVENT).setPackage(c.getPackageName()).putExtra("text",label(state)).putExtra("read_sync_book",id).putExtra("read_sync_state",state));
  }
  public static JSONObject report(Context c)throws JSONException{
    Store s=Store.get(c);SharedPreferences p=c.getSharedPreferences("read_sync_status",0);JobScheduler scheduler=c.getSystemService(JobScheduler.class);
    boolean active; synchronized(gate){active=workerActive;}
    JSONObject report=new JSONObject().put("schema",2).put("count",s.queuedBooks().size()).put("states",new JSONObject(p.getString("rows","{}")))
      .put("syncPaused",isPaused(c)).put("job42Pending",scheduler!=null&&scheduler.getPendingJob(42)!=null).put("workerActive",active)
      .put("lastScheduleAt",p.getLong("lastScheduleAt",0)).put("lastScheduleOutcome",p.getString("lastScheduleOutcome","never"))
      .put("lastScheduleReason",p.getString("lastScheduleReason",""))
      .put("lastProcessStartAt",p.getLong("lastProcessStartAt",0)).put("lastProcessEndAt",p.getLong("lastProcessEndAt",0))
      .put("lastProcessOutcome",p.getString("lastProcessOutcome","never")).put("lastJobStopReason",p.getInt("lastJobStopReason",0));
    JSONArray pending=new JSONArray();int n=0;for(Store.ReadRequest q:s.readRequests().values()){if(n++>=24)break;pending.put(new JSONObject().put("bookId",q.id).put("tip",q.tip).put("generation",q.generation));}return report.put("pending",pending);
  }
  private static void attempt(Context c,Store.ReadRequest q){
    try{synchronized(ReadSync.class){SharedPreferences p=c.getSharedPreferences("read_sync_status",0);JSONObject rows=new JSONObject(p.getString("rows","{}"));JSONObject row=rows.optJSONObject(q.id);if(row!=null&&q.generation.equals(row.optString("generation"))){row.put("attempts",row.optInt("attempts")+1);p.edit().putString("rows",rows.toString()).apply();}}}catch(JSONException e){Diagnostics.failure("read.sync.attempt",e);}
  }
  private static void publishPending(Context c,String state,String reason){for(String id:Store.get(c).queuedBooks())publish(c,id,state,reason);}
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
  private static boolean processPass(Context c){
    OperationTrace.Span trace=OperationTrace.begin("read.sync");boolean previous=RequestTurn.background.get();RequestTurn.background.set(true);String outcome="no_request";
    c.getSharedPreferences("read_sync_status",0).edit().putLong("lastProcessStartAt",System.currentTimeMillis()).apply();
    try{
      Repository.init(c);Store s=Store.get(c);
      for(Store.ReadRequest q:s.readRequests().values())reconcile(c,q);
      if(s.queuedBooks().isEmpty()){outcome="no_eligible_after_reconcile";metric(c,"lastScheduleOutcome","skipped_empty");return false;}
      if(isPaused(c)){publishPending(c,"PAUSED","sync_paused");outcome="paused";metric(c,"lastScheduleOutcome","skipped_paused");return false;}
      if(isStopped()){outcome="stopped_by_os";return true;}
      if(GeckoClient.authenticating){publishPending(c,"WAIT_BUSY","interactive_login");outcome="wait_busy";return true;}
      if(!web.connected(c)){publishPending(c,"WAIT_NETWORK","network_unavailable");outcome="wait_network";return true;}
      synchronized(gate){if(!Repository.busy.compareAndSet(false,true)){publishPending(c,"WAIT_BUSY","repository_owner");outcome="wait_busy";metric(c,"lastScheduleOutcome","deferred_busy");return true;}}
      boolean finished=false;
      try{Repository.cancel.set(false);boolean retry=processQueued(c);finished=!retry;outcome=isStopped()?"stopped_by_os":retry?"retry_io":"sent_all";return retry;}
      finally{GeckoClient.release();Repository.syncState=finished?"COMPLETED":"WAITING";Repository.executionStage="IDLE";Repository.busy.set(false);synchronized(Repository.busy){Repository.busy.notifyAll();}Repository.resumePending(c);}
    }finally{
      c.getSharedPreferences("read_sync_status",0).edit().putLong("lastProcessEndAt",System.currentTimeMillis()).putString("lastProcessOutcome",outcome).apply();
      RequestTurn.background.set(previous);OperationTrace.end(trace,outcome,0);
    }
  }
  private static String reconcile(Context c,Store.ReadRequest q){String result=Store.get(c).reconcileReadRequest(q);if("NEW_UNREAD".equals(result)||"LOCAL_ONLY".equals(result))publish(c,q.id,result,"local_reconcile");return result;}
  /** Called by the single-flight owner; independent of shelf import. */
  public static boolean processQueued(Context c){
    Store s=Store.get(c);boolean retry=false;
    for(Store.ReadRequest q:s.readRequests().values()){
      if(isStopped()||isPaused(c)||Repository.cancel.get()){retry=true;break;}
      String reconciled=reconcile(c,q);if("GONE".equals(reconciled)||"NEW_UNREAD".equals(reconciled)||"LOCAL_ONLY".equals(reconciled))continue;
      q=s.readRequest(q.id);if(q==null)continue;Store.Book b=s.book(q.id);
      if(!web.connected(c)){publish(c,q.id,"WAIT_NETWORK","network_unavailable");retry=true;continue;}
      try{
        Repository.allowed(c);publish(c,q.id,"WAIT_VERIFY","fresh_catalog");attempt(c,q);web.catalog(c,b);
        if(isPaused(c)||Repository.cancel.get()||isStopped()){retry=true;break;}if(!s.sameReadRequest(q))continue;
        if(!s.canSubmitServer(q.id)){
          if(s.unread(q.id)>0&&s.retireReadRequest(q,false))publish(c,q.id,"NEW_UNREAD","fresh_catalog_has_new_text");
          else {publish(c,q.id,"WAIT_RETRY","server_tip_unverified");retry=true;}continue;
        }
        if(isStopped()||isPaused(c)||Repository.cancel.get()){retry=true;break;}
        publish(c,q.id,"SENDING","verified_tip");
        String action=web.action(c,q.id);
        if("NO_READ_BUTTON".equals(action)){if(s.retireReadRequest(q,true))publish(c,q.id,"NO_BUTTON","verified_action_row");continue;}
        if(!"OK".equals(action)&&!"OK_BUTTON_GONE".equals(action))throw new java.io.IOException("Unexpected read-all response");
        boolean retired=s.retireReadRequest(q,"OK_BUTTON_GONE".equals(action));
        if(retired)publish(c,q.id,"SENT","OK_BUTTON_GONE".equals(action)?"button_removed_after_click":"button_unread_zero");
        if(!isStopped()&&!isPaused(c)&&!Repository.cancel.get())web.catalog(c,b);
        if(retired&&s.unread(q.id)>0)publish(c,q.id,"NEW_UNREAD","post_action_new_text");
      }catch(Exception e){retry|=s.sameReadRequest(q);if(s.sameReadRequest(q))publish(c,q.id,"WAIT_RETRY",e.getClass().getSimpleName());Diagnostics.failure("read.sync",e);}
    }
    return retry||!s.queuedBooks().isEmpty();
  }
  public boolean onStartJob(JobParameters p){
    java.util.concurrent.atomic.AtomicBoolean token=new java.util.concurrent.atomic.AtomicBoolean();
    synchronized(gate){if(workerActive){demand=true;return false;}workerActive=true;}
    jobsRunning.put(p,token);
    new Thread(()->{boolean retry=true;try{retry=drain(this,token,"job42");}finally{jobsRunning.remove(p);if(!token.get())jobFinished(p,retry);}},"Hako read job42").start();return true;
  }
  public boolean onStopJob(JobParameters p){java.util.concurrent.atomic.AtomicBoolean token=jobsRunning.get(p);if(token!=null)token.set(true);getSharedPreferences("read_sync_status",0).edit().putInt("lastJobStopReason",Build.VERSION.SDK_INT>=31?p.getStopReason():-1).apply();return !isPaused(this);}
}
