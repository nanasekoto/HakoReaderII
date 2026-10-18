package vn.nanase.hako;

import android.content.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

public final class Repository {
  private static Context app;
  public static void init(Context c){String pending=c.getSharedPreferences("settings",0).getString("pending_origin","");if(!pending.isEmpty()&&android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())Store.get(c).rebase(pending);app=c.getApplicationContext();syncPaused=c.getSharedPreferences("settings",0).getBoolean("sync_paused",false);GeckoClient.init(c);HakoParser.ORIGIN=c.getSharedPreferences("settings",0).getString("origin","https://docln.sbs");}
  public static volatile String activeBook="";
  private static final AtomicInteger requestedShelfScan=new AtomicInteger();
  public static void requestShelfScan(int mode){requestedShelfScan.set(mode);}
  private static long lastNotice;private static String lastNoticeText="";
  public static volatile boolean pocketPaused=false, syncPaused=false, shelfUpdating=false;
  public static void awaitIdle() throws InterruptedException {
    synchronized(busy){while(busy.get())busy.wait();}
  }
  private static final ThreadLocal<Boolean> explicitFull=ThreadLocal.withInitial(()->false);
  private static volatile String currentDownloadBook="";
  static boolean pausedRequestAllowed(int mode,boolean charging){return mode==MODE_BOOK_ALL&&!charging;}
  private static boolean pauseAutomatic(boolean charging){
    return downloadCancelled() || (syncPaused&&!explicitFull.get()) || (pocketPaused && !charging && !manualOverride);
  }
  public static void setSyncPaused(Context c,boolean paused){
    syncPaused=paused;c.getSharedPreferences("settings",0).edit().putBoolean("sync_paused",paused).commit();
    if(paused){savePausedRequests(c);cancel.set(true);pendingAll=false;pendingPersonal=false;clearBook();c.getSystemService(android.app.job.JobScheduler.class).cancel(42);}
    ChargeJob.schedule(c,!paused&&c.getSharedPreferences("settings",0).getBoolean("charging",true));
    ReadSync.kick(c,paused?"pause":"resume");
    notify(c,paused?"Đã tạm dừng đồng bộ · Dữ liệu tải được giữ nguyên":"Đã tiếp tục đồng bộ");
  }
  private static void savePausedRequests(Context c){android.content.SharedPreferences.Editor saved=c.getSharedPreferences("paused_requests",0).edit();
    if(isSyncing){if(currentMode==MODE_SYNC_LIBRARY)saved.putBoolean("shelf",true);else if(currentMode==MODE_SYNC_PERSONAL)saved.putBoolean("personal",true);else if(!currentDownloadBook.isEmpty())saved.putInt("book:"+currentDownloadBook,currentMode);}
    if(pendingAll)saved.putBoolean("shelf",true);if(pendingPersonal)saved.putBoolean("personal",true);BookRequest pending=peekBook();if(!pending.book.isEmpty())saved.putInt("book:"+pending.book,pending.mode);saved.commit();
  }
  public static void restorePausedRequests(Context c){if(syncPaused||c.getSharedPreferences("settings",0).getBoolean("sync_paused",false))return;cancel.set(false);android.content.SharedPreferences saved=c.getSharedPreferences("paused_requests",0);Map<String,?> requests=saved.getAll();
    if(Boolean.TRUE.equals(requests.get("shelf")))pendingAll=true;if(Boolean.TRUE.equals(requests.get("personal")))pendingPersonal=true;
    // The existing runtime queue holds one prioritized book. Keep other saved books until consumed.
    for(Map.Entry<String,?> entry:requests.entrySet())if(entry.getKey().startsWith("book:")&&entry.getValue() instanceof Number&&pendingBook.isEmpty()){queueBook(entry.getKey().substring(5),((Number)entry.getValue()).intValue());saved.edit().remove(entry.getKey()).commit();}
    saved.edit().remove("shelf").remove("personal").commit();resumePending(c);if(requests.isEmpty())notify(c,"Đã tiếp tục · Không có tác vụ đang dở");
  }
  private static final Object stopLock=new Object();private static final AtomicInteger stopRevision=new AtomicInteger();private static final ThreadLocal<Integer> runStopRevision=new ThreadLocal<>();
  public static int stopEpoch(){return stopRevision.get();}
  static boolean downloadCancelled(){Integer started=runStopRevision.get();return cancel.get()||(started!=null&&started!=stopRevision.get());}
  public static void stopDownloads(Context c){synchronized(stopLock){stopRevision.incrementAndGet();cancel.set(true);pendingAll=false;pendingPersonal=false;clearBook();c.getSharedPreferences("download_resume",0).edit().clear().apply();c.getSharedPreferences("paused_requests",0).edit().clear().apply();}c.getSystemService(android.app.job.JobScheduler.class).cancel(41);notify(c,"Đã dừng tải theo yêu cầu");}
  private static long lastNetwork=0;
  public static void cooldown(Context c,long ms){c.getSharedPreferences("settings",0).edit().remove("cooldownUntil").apply();}
  public static void allowed(Context c)throws IOException {c.getSharedPreferences("settings",0).edit().remove("cooldownUntil").apply();}
  public static final String EVENT = "vn.nanase.hako.STATUS";
  public static final AtomicBoolean busy = new AtomicBoolean(false),
      cancel = new AtomicBoolean(false);
  public static volatile boolean pendingAll = false, pendingPersonal=false, manualOverride = false;
  public static volatile String status = "Sẵn sàng", pendingBook = "";
  public static final int MODE_BOOK_NEXT = 1, MODE_BOOK_ALL = 2, MODE_SYNC_LIBRARY = 3, MODE_READER_NEXT = 4, MODE_SYNC_PERSONAL = 5;
  private static int pendingBookMode=MODE_BOOK_NEXT;
  static final class BookRequest {
    final String book;final int mode;
    BookRequest(String book,int mode){this.book=book;this.mode=mode;}
  }
  private static final Object bookQueueLock=new Object();
  static void queueBook(String book,int mode){synchronized(bookQueueLock){
    // Never share the network/download monitor with main-thread queue handoff.
    if(book.equals(pendingBook)&&pendingBookMode!=MODE_READER_NEXT&&mode==MODE_READER_NEXT)return;
    pendingBookMode=mode;pendingBook=book;
  }}
  static BookRequest takeBook(){synchronized(bookQueueLock){BookRequest request=new BookRequest(pendingBook,pendingBookMode);pendingBook="";pendingBookMode=MODE_BOOK_NEXT;return request;}}
  static BookRequest peekBook(){synchronized(bookQueueLock){return new BookRequest(pendingBook,pendingBookMode);}}
  static int queuedBookMode(){synchronized(bookQueueLock){return pendingBookMode;}}
  private static void clearBook(){synchronized(bookQueueLock){pendingBook="";pendingBookMode=MODE_BOOK_NEXT;}}
  static void discardQueued(String book){synchronized(bookQueueLock){if(pendingBook.equals(book)){pendingBook="";pendingBookMode=MODE_BOOK_NEXT;}}}
  public static volatile int currentMode = 0;public static volatile String scopeReason="NONE",executionStage="IDLE";
  public static volatile int syncTotal = 0, syncDone = 0, syncPct = 100;
  public static volatile boolean isSyncing = false;
  public static volatile String syncState = "IDLE";
  public static volatile String priorityChapterId = "";

  public static volatile long stageStarted=android.os.SystemClock.elapsedRealtime();
  public static void notify(Context c, String s) {
    if(!s.equals(status))stageStarted=android.os.SystemClock.elapsedRealtime();
    status = s;
    long now=android.os.SystemClock.elapsedRealtime();
    if(s.equals(lastNoticeText)|| (s.startsWith("Đang tải ")&&now-lastNotice<1200))return;
    lastNotice=now;lastNoticeText=s;
    c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()).putExtra("text", s));
  }

  public static byte[] fetch(String url, boolean authenticated, int max) throws Exception {
    URL u = new URL(url);
    if (!"https".equals(u.getProtocol())) throw new IOException("Chỉ hỗ trợ HTTPS");
    if (authenticated && !HakoParser.isOrigin(url))
      throw new IOException("Sai máy chủ đăng nhập");
    if(app==null)throw new IOException("Chưa khởi tạo app");

    return GeckoClient.request(url,null,max);
  }

  public static String page(String u, boolean auth) throws Exception {return pageAt(u,HakoParser.ORIGIN);}
  public static String pageAt(String u,String origin) throws Exception {
    if(app==null)throw new IOException("Chưa khởi tạo app");
    if(ServerSource.available(app)){try{return ServerSource.html(app,u);}catch(Exception e){ServerSource.failed(app,e);}}
    ServerSource.directAllowed(app);
    String script="(function(){var t=document.title||'';if(/^(just a moment|attention required|access denied|503 service unavailable|too many requests)[.!… ]*$/i.test(t.trim())||document.querySelector('#challenge-form,.cf-browser-verification'))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc đang giới hạn truy cập. Mở HAKO để kiểm tra.'});if(!document.body||!document.body.innerHTML.trim())return JSON.stringify({});return JSON.stringify({html:document.documentElement.outerHTML});})()";
    return new String(GeckoClient.request(u,script,8*1024*1024,origin),"UTF-8");
  }

  public static void importShelf(Context ctx) throws Exception {
    init(ctx);allowed(ctx);
    if(ServerSource.available(ctx)){try{ServerSource.shelf(ctx,true);notify(ctx,"Đã nhận tủ sách từ server · cập nhật nền");return;}catch(Exception e){ServerSource.failed(ctx,e);}}
    ServerSource.directAllowed(ctx);shelfUpdating=true;syncState="SHELF";
    notify(ctx,"Đang cập nhật tủ sách trước khi tải chương…");
    try{
    Store s = Store.get(ctx);
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    urls.add(HakoParser.ORIGIN + "/ke-sach");
    Set<String> done = new HashSet<>();
    int count = 0;List<HakoParser.Link> collected=new ArrayList<>();
    android.content.SharedPreferences prefs=ctx.getSharedPreferences("settings",0);
    long newest=0;boolean partial=false;
    int scanMode=requestedShelfScan.getAndSet(0);boolean audit=scanMode==2,full=scanMode!=0;
    boolean anchorKnown=prefs.getBoolean("shelf_anchor_ready",false)&&HakoParser.ORIGIN.equals(prefs.getString("shelf_anchor_origin",""));
    boolean canUseAnchor=anchorKnown&&!prefs.getBoolean("shelf_snapshot_fast_disabled",false);
    String anchor=prefs.getString("shelf_anchor_id","");
    UiLibrarySnapshot saved=LibraryState.refresh(ctx);
    Map<String,Store.Book> previousBooks=new HashMap<>();Map<String,String> previousKeys=new HashMap<>();
    for(Store.Book old:saved.books)if(old.followed){previousBooks.put(old.id,old);previousKeys.put(old.id,saved.summary(old).latestKey);}
    Set<String> quickObserved=null;String anchorTitle="";
    UpdateReport.log(ctx,full?(audit?"Đối chiếu quét nhanh với toàn bộ tủ sách":"Kiểm tra toàn bộ tủ sách"):"Quét nhanh tủ sách; không có mốc hợp lệ sẽ quét đầy đủ");
    java.util.Map<String,String> shelfKeys=new java.util.HashMap<>();java.util.Map<String,Long> shelfTimes=new java.util.HashMap<>();Set<String> changedShelfBooks=new HashSet<>();
    while (true) {
      String next = null;
      for (String u : urls)
        if (!done.contains(u)) {
          next = u;
          break;
        }
      if (next == null) break;
      if (done.size() >= 100)
        throw new IOException("Kệ sách vượt 100 trang; đã giữ phần nhập được.");
      if(downloadCancelled()||syncPaused)throw new IOException("Đã tạm dừng cập nhật tủ sách");
      notify(ctx, "Đang nhập kệ sách • trang " + (done.size() + 1));
      UpdateReport.page(ctx);UpdateReport.log(ctx,"Kiểm tra trang "+(done.size()+1)+" tủ sách");
      allowed(ctx);Map<String,Store.ReadRequest> readObservation=Store.get(ctx).readRequests();
      String html = page(next, true);
      done.add(next);
      if (Jsoup.parse(html).selectFirst("input[type=password]") != null)
        throw new IOException("Hãy đăng nhập HAKO trước khi nhập kệ sách.");
      List<HakoParser.Link> links = HakoParser.shelf(html, next);ReadSync.observeShelf(ctx,html,readObservation);Diagnostics.count("shelf.page_parsed");for(HakoParser.Link ignored:links)Diagnostics.count("shelf.row_seen");
      if(links.isEmpty()&&!previousKeys.isEmpty())throw new IOException("Không đọc được danh sách tủ sách; đã giữ dữ liệu cũ");
      List<ShelfAnchor.Row> anchorRows=new ArrayList<>();
      for (HakoParser.Link l : links) {
        anchorRows.add(new ShelfAnchor.Row(l.id,l.latestKey,shelfCount(l.info)));
        if(l.id.equals(anchor))anchorTitle=l.title;
        Store.Book previous=previousBooks.get(l.id);
        int before=previous==null?-1:shelfCount(previous.shelfInfo),after=shelfCount(l.info);
        if(l.latestKey.isEmpty()&&after>=0&&((before>=0&&after>before)||(previous==null&&after>0)))changedShelfBooks.add(l.id);
        collected.add(l);
        count++;
        shelfKeys.put(l.id,l.latestKey);shelfTimes.put(l.id,l.updatedAt);newest=Math.max(newest,l.updatedAt);
      }
      // Publish each successfully parsed page without waiting for the remaining network requests.
      // Never remove membership or advance the successful-sync timestamp for a partial scan.
      s.mergeShelf(links,collected.size()-links.size());
      android.content.SharedPreferences.Editor pageEdit=prefs.edit();
      for(HakoParser.Link row:links){pageEdit.putString("shelf_key_"+row.id,row.latestKey);pageEdit.putLong("shelf_updated_at_"+row.id,row.updatedAt);}
      pageEdit.putLong("lastShelfPartial",System.currentTimeMillis()).commit();
      for(HakoParser.Link row:links){Store.Book old=previousBooks.get(row.id);if(old==null||!Objects.equals(row.latestKey,previousKeys.get(row.id))||row.updatedAt!=saved.summary(old).updatedAt)LibraryState.changed(ctx,row.id,false);}
      notify(ctx,"Đã nhận trang "+done.size()+" · "+count+" truyện · đang kiểm tra phần còn lại");
      ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("shelf_changed",true).putExtra("text",status));
      List<String> following=HakoParser.shelfPages(html,next);
      boolean orderSafe=HakoParser.shelfUpdateOrder(html);
      boolean stop=!following.isEmpty()&&ShelfAnchor.canStopAtUnchanged(anchorRows,previousKeys,anchorKnown&&orderSafe&&(audit||canUseAnchor));
      if(stop&&quickObserved==null){quickObserved=new HashSet<>(shelfKeys.keySet());UpdateReport.log(ctx,"Gặp chương mới nhất không đổi: "+anchorTitle+" · trang "+done.size());}
      if(stop&&!full){Diagnostics.count("shelf.anchor_stop");partial=true;UpdateReport.log(ctx,"Dừng quét nhanh ở trang "+done.size()+"; giữ các trang phía sau trong bộ nhớ");break;}
      urls.addAll(following);
      boolean more=false;for(String u:urls)if(!done.contains(u)){more=true;break;}
      if(more){notify(ctx,"Đã nhận trang "+done.size()+" · mở trang tiếp theo");Thread.sleep(500);}
    }
    if(downloadCancelled()||syncPaused)throw new IOException("Đã tạm dừng cập nhật tủ sách");
    if(audit){
      prefs.edit().putLong("shelf_anchor_audited_at",System.currentTimeMillis()).commit();
      int missed=0;
      if(quickObserved==null)UpdateReport.log(ctx,"Không gặp mốc dừng: quét nhanh cũng cần kiểm tra toàn bộ");
      else for(HakoParser.Link row:collected)if(!quickObserved.contains(row.id)&&(!ShelfPolicy.unchanged(row.latestKey,previousKeys.get(row.id)))){missed++;UpdateReport.log(ctx,"Quét nhanh có thể bỏ sót: "+row.title+" (chương mới hoặc thông tin chưa rõ)");}
      if(missed>0){prefs.edit().putBoolean("shelf_snapshot_fast_disabled",true).putBoolean("shelf_anchor_verified",false).commit();UpdateReport.log(ctx,"Đã tắt dừng sớm do đối chiếu không khớp. Những lần sau quét đầy đủ.");}
      else if(quickObserved!=null){prefs.edit().putBoolean("shelf_anchor_verified",true).putBoolean("shelf_snapshot_fast_disabled",false).commit();UpdateReport.log(ctx,"Đối chiếu điểm dừng: không phát hiện cập nhật bị bỏ sót");}
      else prefs.edit().putBoolean("shelf_anchor_verified",false).commit();
    }
    if(!partial)s.replaceShelf(collected);
    android.content.SharedPreferences.Editor edit=prefs.edit();
    // Replace current keys only for rows actually observed; older cached rows remain intact.
    for(java.util.Map.Entry<String,String> e:shelfKeys.entrySet())edit.putString("shelf_key_"+e.getKey(),e.getValue());
    for(java.util.Map.Entry<String,Long> e:shelfTimes.entrySet())edit.putLong("shelf_updated_at_"+e.getKey(),e.getValue());
    for(Map.Entry<String,String> e:shelfKeys.entrySet())if(ShelfPolicy.unchanged(e.getValue(),prefs.getString("catalog_shelf_key_"+e.getKey(),"")))edit.remove("shelf_changed_"+e.getKey());
    for(String changed:changedShelfBooks)edit.putBoolean("shelf_changed_"+changed,true);
    if(!partial){String nextAnchor="";for(HakoParser.Link row:collected)if(!row.latestKey.isEmpty())nextAnchor=row.id;
      if(!nextAnchor.equals(anchor))edit.putBoolean("shelf_anchor_verified",false);
      edit.putString("shelf_anchor_id",nextAnchor).putString("shelf_anchor_origin",HakoParser.ORIGIN).putBoolean("shelf_anchor_ready",true);
    }
    edit.commit();
    ctx.getSharedPreferences("settings",0).edit().putLong("lastShelfSync",System.currentTimeMillis()).commit();
    for(HakoParser.Link row:collected)if(changedShelfBooks.contains(row.id)||!Objects.equals(row.latestKey,previousKeys.get(row.id)))LibraryState.changed(ctx,row.id,false);
    shelfUpdating=false;
    UpdateReport.checkpoint(ctx,"Hoàn tất cập nhật tủ sách");
    notify(ctx, "Đã cập nhật tủ sách: " + count + " mục · Chuẩn bị tải theo ưu tiên");
    ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("shelf_changed",true).putExtra("text",status));
    }finally{shelfUpdating=false;if("SHELF".equals(syncState))syncState=busy.get()?"CHECKING":"COMPLETED";}
  }

  public static Store.Book add(Context c, String url) throws Exception {
    String u = HakoParser.storyUrl(url);
    String id = HakoParser.storyId(u);
    if (id.isEmpty()) throw new IOException("Dán URL truyện hoặc chương từ docln.sbs");
    allowed(c);
    if(ServerSource.available(c)){try{Store.Book book=ServerSource.catalog(c,u,false);String cid=HakoParser.chapterId(url);if(!cid.isEmpty()&&Store.get(c).chapter(book.id,cid)!=null)Store.get(c).selectChapter(book.id,cid);return Store.get(c).book(book.id);}catch(Exception e){ServerSource.failed(c,e);}}
    String html = page(u, true);
    Store s = Store.get(c);
    s.putBook(new HakoParser.Link(id, HakoParser.title(html, u), u));
    List<HakoParser.Link> list = HakoParser.chapters(html, u);
    if (list.isEmpty())
      throw new IOException("Chưa lấy được mục lục. HAKO có thể đã đổi cấu trúc.");
    s.catalog(id, list);
    String cid = HakoParser.chapterId(url);
    if (!cid.isEmpty() && s.chapter(id, cid) != null) s.selectChapter(id, cid);
    return s.book(id);
  }

  /** Shelf failure must not suppress per-title read verification; preserve the shelf exception. */
  private static void importShelfWithReadQueue(Context c)throws Exception{
    try{importShelf(c);}finally{
      if(!syncPaused&&!cancel.get())try{ReadSync.processQueued(c);}catch(Exception e){Diagnostics.failure("read.sync.after_shelf",e);}
    }
  }
  public static void refreshForReading(Context c,Store.Book b)throws Exception{
    while(true){awaitIdle();if(busy.compareAndSet(false,true))break;}
    try{init(c);allowed(c);catalog(c,b,true);}
    finally{GeckoClient.release();busy.set(false);synchronized(busy){busy.notifyAll();}resumePending(c);ReadSync.ownerReleased(c,"after_refresh_for_reading");}
  }

  private static int shelfCount(String value){try{return Integer.parseInt(value.trim().split("\\s+")[0]);}catch(Exception e){return -1;}}

  public static boolean unchangedShelf(Context c,Store.Book b){
    android.content.SharedPreferences p=c.getSharedPreferences("settings",0);
    return !p.getBoolean("catalog_failed_"+b.id,false)&&!Store.get(c).chapters(b.id).isEmpty()&&ShelfPolicy.unchanged(p.getString("shelf_key_"+b.id,""),p.getString("catalog_shelf_key_"+b.id,""));
  }

  public static void catalog(Context c, Store.Book b) throws Exception {
    catalog(c,b,false);
  }
  public static void catalog(Context c,Store.Book b,boolean force) throws Exception {
    android.content.SharedPreferences prefs=c.getSharedPreferences("settings",0);
    if(!force&&unchangedShelf(c,b)){UpdateReport.skipped(c);UpdateReport.log(c,b.title+": bỏ qua mục lục — chương mới nhất không đổi");return;}
    UpdateReport.checked(c);UpdateReport.log(c,b.title+": kiểm tra mục lục — "+(force?"làm mới bắt buộc (thủ công hoặc xác minh đọc hết)":Store.get(c).chapters(b.id).isEmpty()?"chưa có mục lục":"chương mới hoặc thông tin chưa rõ"));
    prefs.edit().putBoolean("catalog_failed_"+b.id,true).commit();
    allowed(c);
    String shelfKey=c.getSharedPreferences("settings",0).getString("shelf_key_"+b.id,"");
    if(ServerSource.available(c)){try{ServerSource.catalog(c,b.url,force);catalogVerified(c,b.id,shelfKey);LibraryState.changed(c,b.id,true);return;}catch(Exception e){ServerSource.failed(c,e);}}
    String html = page(b.url, true);
    List<HakoParser.Link> ls = HakoParser.chapters(html, b.url);
    if (ls.isEmpty()) throw new IOException("Mục lục trống: " + b.title);
    Store.get(c).catalog(b.id, ls);
    Store.get(c).establishReadBaseline(b.id);
    catalogVerified(c,b.id,shelfKey);
    LibraryState.changed(c,b.id,true);
  }

  /** Explicit success: absent flag means unverified, never successful by omission. */
  static void catalogVerified(Context c,String book,String shelfKey){
    c.getSharedPreferences("settings",0).edit().putLong("catalog-"+book,System.currentTimeMillis()).putString("catalog_shelf_key_"+book,shelfKey).remove("shelf_changed_"+book).putBoolean("catalog_failed_"+book,false).commit();
  }

  public static void download(Context ctx, Store.Chapter c) throws Exception {
    download(ctx,c,false);
  }

  public static void download(Context ctx,Store.Chapter c,boolean force) throws Exception {download(ctx,c,force,false);}
  public static void downloadImages(Context ctx,Store.Chapter c)throws Exception{download(ctx,c,true,true);}
  public static final java.util.concurrent.locks.ReentrantReadWriteLock contentLock=new java.util.concurrent.locks.ReentrantReadWriteLock();
  private static final class ChapterOwner {final Object lock=new Object();int users;}
  private static final Map<String,ChapterOwner> chapterOwners=new HashMap<>();
  private static void download(Context ctx,Store.Chapter c,boolean force,boolean imagesOnly) throws Exception {
    if(c==null)throw new IOException("Không tìm thấy chương");ChapterOwner owner;synchronized(chapterOwners){owner=chapterOwners.get(c.id);if(owner==null){owner=new ChapterOwner();chapterOwners.put(c.id,owner);}owner.users++;}
    contentLock.readLock().lock();try{synchronized(owner.lock){downloadChapter(ctx,c,force,imagesOnly);}}finally{contentLock.readLock().unlock();synchronized(chapterOwners){if(--owner.users==0)chapterOwners.remove(c.id);}}
  }
  private static void downloadChapter(Context ctx,Store.Chapter c,boolean force,boolean imagesOnly) throws Exception {
    Store s = Store.get(ctx);
    if(imagesOnly&&!DeviceProfile.images(ctx))return;
    boolean cachedContent=s.readable(c.id);
    if(!force&&s.cachedIllustration(c.id)){UpdateReport.log(ctx,c.title+": giữ minh họa đã lưu · không tải lại trang");return;}
    if(!CachePolicy.shouldFetch(cachedContent,force)){if(!c.ready)s.state(c.id,true,c.error);ServerSource.repairCachedImages(ctx,c);return;}
    allowed(ctx);
    if(ServerSource.available(ctx)){try{ServerSource.download(ctx,c,force,imagesOnly);return;}catch(ServerSource.PendingImages e){throw e;}catch(InterruptedIOException e){throw e;}catch(Exception e){ServerSource.failed(ctx,e);}}
    long pause=Math.max(0,1500-(android.os.SystemClock.elapsedRealtime()-lastNetwork));
    if(pause>0&&!c.book.equals(activeBook))Thread.sleep(pause);
    lastNetwork=android.os.SystemClock.elapsedRealtime();
    File dir = s.dir(c.id);
    dir.mkdirs();
    if (dir.getUsableSpace() < 64L * 1024 * 1024)
      throw new IOException("Bộ nhớ còn dưới 64 MB. Hãy giải phóng dung lượng trước khi tải tiếp.");
    String content = "";
    // Retained image markup allows retrying missing image files without fetching the chapter again.
    if((!force||imagesOnly)&&s.html(c.id).isFile()){
      String cached=Store.read(s.html(c.id));
      HakoParser.OfflineContent offline=HakoParser.offlineContent(cached);
      if((imagesOnly||!offline.text)&&!offline.images.isEmpty()){
        boolean retry=true;
        for(Element img:Jsoup.parseBodyFragment(cached).select("img[src]")){
          String name=HakoParser.offlineImageName(c.id,img.attr("src"));
          if(!name.isEmpty()&&!Store.imageFileReadable(new File(dir,name))&&!img.hasAttr("data-hako-src"))retry=false;
        }
        if(retry)content=cached;
      }
    }
    if(content.isEmpty())content=HakoParser.content(RenderedPage.chapter(ctx,c.url), c.url);
    s.classify(c.id,content);
    if(s.illustration(c.id))ctx.getSharedPreferences("settings",0).edit().putBoolean("illustration_attempted_"+c.id,true).commit();
    Document d = Jsoup.parseBodyFragment(content);
    int errors = 0, index = 0;
    String error = "";
    if(!DeviceProfile.images(ctx))d.select("img").remove();
    for (Element img : d.select("img[src]")) {
      String src = HakoParser.imageFetchUrl(img.hasAttr("data-hako-src")?img.attr("data-hako-src"):img.attr("src"));
      img.attr("data-hako-src",src);
      String localName=HakoParser.offlineImageName(c.id,img.attr("src"));
      if(!localName.isEmpty()&&Store.imageFileReadable(new File(dir,localName)))continue;
      String name = "image-" + imageKey(src) + ".bin";
      File file = new File(dir, name);
      if (!HakoParser.imageAllowed(src)) {
        errors++;
        error = "Máy chủ ảnh chưa được hỗ trợ";
        img.attr("src",src);
        continue;
      }
      try {
        if (!Store.imageFileReadable(file)) {
          byte[] data = fetch(src, false, 16 * 1024 * 1024);
          File tmp = new File(dir, name + ".tmp");
          try (FileOutputStream out = new FileOutputStream(tmp)) {
            writeDeviceImage(ctx,data,out);
            out.getFD().sync();
          }
          if(!Store.imageFileReadable(tmp)){tmp.delete();throw new IOException("Máy chủ không trả ảnh đọc được");}
          if (!tmp.renameTo(file)) throw new IOException("Không lưu được ảnh");
        }
        img.attr("src", "https://offline.hako.invalid/" + c.id + "/" + name);
      } catch (Exception e) {
        errors++;
        error = e.getMessage();
        img.attr("src",src);
      }
    }
    if(!HakoParser.validContent(d.body().html()))throw new IOException("Nội dung rỗng");
    synchronized(s){Store.Book owner=s.book(c.book);
    if(owner!=null&&!FetchPolicy.allowCache(owner.followed||s.syncTarget(owner),c.book.equals(activeBook),s.isKeepFull(owner.id)))return;
    if(!CachePolicy.shouldFetch(s.readable(c.id),force))return;
    Store.write(s.html(c.id), d.body().html());
    boolean readable=s.readable(c.id);
    String warning=errors == 0 ? "" : "Thiếu " + errors + " ảnh. " + error;
    s.state(c.id,readable,warning);
    if(!readable)throw new IOException("Chương chỉ có ảnh chưa tải đủ: "+c.title+" · "+warning);}
  }

  private static void promoteActive(List<Store.Book> todo){for(int i=0;i<todo.size();i++)if(todo.get(i).id.equals(activeBook)){Store.Book b=todo.remove(i);todo.add(0,b);break;}}
  private static void writeDeviceImage(Context context,byte[] data,OutputStream out)throws IOException{
    if(DeviceProfile.eink(context)){writeSmallImage(data,out);return;}
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
    android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,options);
    if(options.outWidth<=0||options.outHeight<=0)throw new IOException("Ảnh không hợp lệ");
    options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2400||options.outHeight/options.inSampleSize>4800)options.inSampleSize*=2;
    options.inJustDecodeBounds=false;options.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;
    android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,options);
    if(image==null)throw new IOException("Không giải mã được ảnh");
    try{if(!image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IOException("Không lưu được ảnh");}finally{image.recycle();}
  }
  public static void writeSmallImage(byte[] data,OutputStream out)throws IOException{
    android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;
    android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,o);
    if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Máy chủ không trả ảnh hợp lệ");
    o.inSampleSize=1;while(o.outWidth/o.inSampleSize>960||o.outHeight/o.inSampleSize>2400)o.inSampleSize*=2;
    o.inJustDecodeBounds=false;o.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
    android.graphics.Bitmap raw=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,o);
    if(raw==null)throw new IOException("Không giải mã được ảnh");
    android.graphics.Bitmap small=null;
    try{
      float scale=Math.min(1f,Math.min(480f/raw.getWidth(),1600f/raw.getHeight()));
      int w=Math.max(1,Math.round(raw.getWidth()*scale)),h=Math.max(1,Math.round(raw.getHeight()*scale));
      small=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.RGB_565);
      android.graphics.Canvas canvas=new android.graphics.Canvas(small);canvas.drawColor(android.graphics.Color.WHITE);
      android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
      canvas.drawBitmap(raw,null,new android.graphics.Rect(0,0,w,h),paint);
      if(!small.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,out))throw new IOException("Không nén được ảnh");
    }finally{if(small!=null)small.recycle();raw.recycle();}
  }

  private static String imageKey(String src) throws Exception {
    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(src.getBytes("UTF-8"));
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 12; i++)
      b.append(String.format(java.util.Locale.ROOT, "%02x", hash[i] & 255));
    return b.toString();
  }

  public static void run(Context ctx,String book,int mode,boolean charging)throws Exception{run(ctx,book,mode,charging,stopEpoch());}
  static void run(Context ctx, String book, int mode, boolean charging,int acceptedStop) throws Exception {OperationTrace.Span trace=OperationTrace.begin("download.run");boolean previousBackground=RequestTurn.background.get();RequestTurn.background.set(true);try{
    if(pocketPaused && !charging && (mode==MODE_BOOK_NEXT||mode==MODE_READER_NEXT))return;
    init(ctx);
    if(syncPaused&&!pausedRequestAllowed(mode,charging))return;
    synchronized(stopLock){if(acceptedStop!=stopRevision.get())return;
    if (!busy.compareAndSet(false, true)) {
      if (mode == MODE_SYNC_LIBRARY) pendingAll = true;
      else if(mode==MODE_SYNC_PERSONAL)pendingPersonal=true;
      else if (book != null && !book.isEmpty()) {if(mode==MODE_BOOK_ALL)Store.get(ctx).setKeepFull(book,true);queueBook(book,mode);}
      return;
    }
    runStopRevision.set(acceptedStop);explicitFull.set(pausedRequestAllowed(mode,charging));
    cancel.set(false);}
    currentDownloadBook=book==null?"":book;currentMode = mode;
    boolean librarySync=mode==MODE_SYNC_LIBRARY||mode==MODE_SYNC_PERSONAL;
    manualOverride = !charging && (mode == MODE_BOOK_ALL || mode == MODE_SYNC_LIBRARY || mode==MODE_SYNC_PERSONAL);
    isSyncing = true;
    syncState = "CHECKING";
    syncTotal = 0;
    syncDone = 0;
    syncPct = 0;
    UpdateReport.begin(ctx);
    if(mode==MODE_SYNC_LIBRARY)ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",false).commit();
    notify(ctx, "Đang kiểm tra mục lục…");

    try {
      allowed(ctx);
      Store s = Store.get(ctx);
      if(mode==MODE_SYNC_LIBRARY){importShelfWithReadQueue(ctx);}
      ArrayList<Store.Book> todo = new ArrayList<>();
      Map<String,Integer> requestModes=new HashMap<>();

      if (mode == MODE_BOOK_NEXT || mode == MODE_BOOK_ALL || mode==MODE_READER_NEXT) {
        Store.Book b = s.book(book);
        if (b != null && !b.id.equals("demo")) {
          if (mode == MODE_BOOK_ALL) s.setKeepFull(b.id, true);
          todo.add(b);
          requestModes.put(b.id,mode);
        }
      } else { // MODE_SYNC_LIBRARY
        for (Store.Book b : LibraryState.refresh(ctx).books) {
          if (b.id.equals("demo") || b.dropped) continue;
          if (inScope(s,b,mode)&&(mode==MODE_SYNC_PERSONAL||s.syncTarget(b) || b.id.equals(activeBook))) {
            todo.add(b);requestModes.put(b.id,mode);
          }else UpdateReport.log(ctx,b.title+": không tải — chỉ nằm trong tủ sách/ghim, chưa đọc trong APK và chưa chọn tải");
        }
      }

      if(librarySync)s.sortDownloads(todo);
      promoteActive(todo);
      Set<String> refreshedCatalogs=new HashSet<>();
      DownloadLedger ledger=new DownloadLedger();
      boolean hadFailures=false;
      while (!downloadCancelled() && !pauseAutomatic(charging) && (!todo.isEmpty() || !pendingBook.isEmpty() || pendingAll || pendingPersonal)) {
        if (pendingAll||pendingPersonal) {
          int requested=pendingAll?MODE_SYNC_LIBRARY:MODE_SYNC_PERSONAL;
          if(pendingAll)pendingAll=false;else pendingPersonal=false;librarySync=true;currentMode=requested;
          // A refresh request preempts the bulk queue after the current chapter.
          if(requested==MODE_SYNC_LIBRARY){importShelfWithReadQueue(ctx);}refreshedCatalogs.clear();todo.clear();requestModes.clear();ledger.clear();syncTotal=0;syncDone=0;
          for(Store.Book candidate:LibraryState.refresh(ctx).books){
            if(candidate.id.equals("demo")||candidate.dropped)continue;
            if(inScope(s,candidate,requested)&&(requested==MODE_SYNC_PERSONAL||s.syncTarget(candidate)||candidate.id.equals(activeBook))){todo.add(candidate);requestModes.put(candidate.id,requested);}
          }
          s.sortDownloads(todo);promoteActive(todo);
        }
        if (!pendingBook.isEmpty()) {
          BookRequest request=takeBook();
          Store.Book p = s.book(request.book);
          if (p != null) {
            // A shelf/full task already authorized for this book keeps its broader scope.
            boolean alreadyQueued=false;for(Store.Book queued:todo)if(queued.id.equals(p.id)){alreadyQueued=true;break;}
            if(!alreadyQueued&&!requestModes.containsKey(p.id))requestModes.put(p.id,request.mode);
            else if(request.mode!=MODE_READER_NEXT)requestModes.put(p.id,request.mode);
            todo.removeIf(x -> x.id.equals(p.id));
            todo.add(0,p);
            notify(ctx,"Ưu tiên truyện đang đọc: "+p.title);
          }
        }
        if (todo.isEmpty() || (charging && !ChargeJob.isCharging(ctx))) break;

        Store.Book b = todo.remove(0);
        if(b==null)continue;
        int bookMode=requestModes.getOrDefault(b.id,librarySync?MODE_SYNC_LIBRARY:mode);
        boolean readerOnly=bookMode==MODE_READER_NEXT;
        currentDownloadBook=b.id;currentMode=bookMode;scopeReason=bookMode==MODE_BOOK_ALL?(charging?"BACKGROUND_FULL_RESUME":"USER_FULL_DOWNLOAD"):bookMode==MODE_READER_NEXT||bookMode==MODE_BOOK_NEXT?"BATCH_PREFETCH_15":charging?"BACKGROUND_CHARGE":"USER_LIBRARY_REFRESH";
        synchronized(stopLock){if(downloadCancelled())break;ctx.getSharedPreferences("download_resume",0).edit().putInt(b.id,bookMode).commit();}
        if(librarySync&&b!=null&&!inScope(s,b,bookMode)){UpdateReport.log(ctx,b.title+": ngoài phạm vi cập nhật");continue;}
        if (b == null || b.id == null || b.id.isEmpty() || s.downloadsSuppressed(b.id) || !FetchPolicy.allowDownload(b.dropped,(mode==MODE_BOOK_ALL && b.id.equals(book))||(manualOverride&&s.isKeepFull(b.id)))) continue;

        // 1. Refresh catalog FIRST to know real chapter count
        if(!refreshedCatalogs.contains(b.id)){
          UpdateReport.selected(ctx);UpdateReport.log(ctx,b.title+": đủ điều kiện tải — "+(s.isKeepFull(b.id)?"bạn chọn giữ toàn bộ":b.visits>0?"đã đọc trong APK":"bạn bật tự động tải hoặc đang mở đọc"));
          try{
            if(readerOnly&&!s.chapters(b.id).isEmpty())UpdateReport.log(ctx,b.title+": lượt mở đọc dùng mục lục đã lưu; cập nhật web theo yêu cầu hoặc lịch sạc + Wi-Fi");
            else catalog(ctx,b,bookMode==MODE_SYNC_PERSONAL);
          }catch(Exception error){hadFailures=true;UpdateReport.log(ctx,b.title+": lỗi kiểm tra mục lục — "+error.getMessage()+"; giữ dữ liệu offline và tiếp tục bộ khác");notify(ctx,"Không cập nhật được "+b.title+" · Xem Chi tiết cập nhật");continue;}
          refreshedCatalogs.add(b.id);
        }
        if(librarySync&&!s.syncTarget(b)&&!b.id.equals(activeBook)){ctx.getSharedPreferences("download_resume",0).edit().remove(b.id).commit();UpdateReport.log(ctx,b.title+": đã kiểm tra chương mới · không có yêu cầu tải");continue;}
        Store.Book refreshed = s.book(b.id);
        if (refreshed != null) b = refreshed;
        if(!priorityChapterId.isEmpty()){Store.Chapter priority=s.chapter(b.id,priorityChapterId);if(priority!=null&&priority.book.equals(b.id)&&s.readable(priority.id))priorityChapterId="";}
        List<Store.Chapter> chapters = s.chapters(b.id);
        int current = s.readingStart(b,chapters);

        boolean isFullMode = bookMode==MODE_BOOK_ALL || s.isKeepFull(b.id) || s.isCompleted(b.id);

        boolean fullRequest=bookMode==MODE_BOOK_ALL;
        Store.BatchPlan batch=fullRequest?null:s.readingBatch(b,chapters);
        boolean toEnd=fullRequest;int scopeEnd=fullRequest?Integer.MAX_VALUE:current+14;
        List<Store.Chapter> scope=fullRequest?chapters:batch.chapters;
        List<Store.Chapter> needDownload=new ArrayList<>();
        if(fullRequest||!s.batchDone(b.id,batch))for(Store.Chapter ch:scope)if(!s.isHidden(b.id,ch.id)&&s.autoIllustration(ch)&&!s.readable(ch.id))needDownload.add(ch);
        needDownload.sort((a,z)->Boolean.compare(s.illustrationHint(a),s.illustrationHint(z)));
        UpdateReport.log(ctx,b.title+": phạm vi — "+(readerOnly?"mở đọc: chỉ phần hiện tại và phía sau, không tải bù chương cũ":isFullMode?"toàn bộ truyện":toEnd?"từ chương đang đọc đến cuối, giữ 3 chương trước":"đợt tối đa 15 chương chữ, nghỉ đến chương chữ thứ 14"));
        int stored=0;for(Store.Chapter ch:chapters)if(ch.ready)stored++;
        UpdateReport.log(ctx,b.title+": đã lưu "+stored+"/"+chapters.size()+" chương toàn bộ · Cần tải "+needDownload.size()+" chương trong phạm vi đã chọn");
        for(Store.Chapter needed:needDownload){if(!needed.book.equals(b.id))throw new IOException("Hàng đợi sai truyện; đã dừng để giữ dữ liệu");if(!s.illustrationHint(needed))ledger.add(needed.id);}
        syncTotal=ledger.total();syncDone=ledger.done();
        syncState = "DOWNLOADING";
        if (syncTotal == 0) {
          notify(ctx, b.title + ": Đã tải đủ ✓");
        } else {
          syncPct = DownloadLedger.percent(syncDone,syncTotal);
          notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + ")");
        }

        if(DeviceProfile.eink(ctx)&&ServerSource.available(ctx)&&!needDownload.isEmpty()){
          try{notify(ctx,"Server đang chuẩn bị gói nội dung · "+Math.min(15,needDownload.size())+" chương/lượt");ServerSource.pack(ctx,needDownload);}
          catch(InterruptedIOException stopped){throw stopped;}catch(Exception error){ServerSource.failed(ctx,error);}
        }
        // 3. Download loop per chapter with preemption
        int distance=0;
        for (Store.Chapter ch : needDownload) {
          if(s.downloadsSuppressed(b.id))break;
          if (downloadCancelled() || pauseAutomatic(charging) || pendingAll || pendingPersonal || IllustrationPolicy.shouldYield(b.id,pendingBook)) break;
          if (charging && !ChargeJob.isCharging(ctx)) break;

          // Priority check: did user open another chapter?
          if (!priorityChapterId.isEmpty()) {
            String pCid = priorityChapterId;
            Store.Chapter pch = s.chapter(b.id, pCid);
            if (pch != null && pch.book.equals(b.id)) priorityChapterId = "";
            if (pch != null && pch.book.equals(b.id) && !s.readable(pch.id)) {
              notify(ctx, "Ưu tiên: " + pch.title);
              try {
                download(ctx, pch);
                if (s.readable(pch.id)){ledger.complete(pch.id);syncDone=ledger.done();}
              } catch (Exception ignored) {}
            }
          }

          if(s.illustrationHint(ch)&&!s.autoIllustration(ch)){UpdateReport.log(ctx,ch.title+": bỏ qua minh họa đã thử tải; phần thiếu chờ yêu cầu riêng");continue;}
          // Recheck disk: this snapshot may predate a priority download.
          if (s.readable(ch.id)){ledger.complete(ch.id);syncDone=ledger.done();UpdateReport.log(ctx,ch.title+": dùng cache hợp lệ, không gọi mạng");continue;}

          long delay = fullRequest?FetchPolicy.delayMillis(++distance):4000;
          if (!waitFor(ctx, delay, b.id, charging)) break;

          try {
            download(ctx, ch);Diagnostics.count("download.attempt_completed");
            if (s.readable(ch.id)) {
              ledger.complete(ch.id);syncDone=ledger.done();
              syncPct = syncTotal > 0 ? DownloadLedger.percent(syncDone,syncTotal) : 100;
              notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + "): " + ch.title);
            }
          } catch (Exception e) {
            Diagnostics.count("download.failed");if(!s.illustrationHint(ch))hadFailures=true;
            s.state(ch.id, false, e.getMessage());
            if(s.illustrationHint(ch))ctx.getSharedPreferences("settings",0).edit().putBoolean("illustration_attempted_"+ch.id,true).commit();
            UpdateReport.log(ctx,b.title+" · "+ch.title+": lỗi tải — "+e.getMessage());
            notify(ctx, "Lỗi tải " + ch.title + ": " + e.getMessage());
            String reason=e.getMessage()==null?"":e.getMessage();if(reason.contains("403")||reason.contains("429")||reason.contains("xác minh")||reason.contains("đăng nhập")||reason.contains("quá thời gian")||reason.contains("45 giây")){todo.clear();break;}
            Thread.sleep(1500);
          }
        }

        if(!pendingAll&&(IllustrationPolicy.shouldYield(b.id,pendingBook)||(!priorityChapterId.isEmpty()&&!needDownload.isEmpty()))){final String interrupted=b.id;todo.removeIf(x->x.id.equals(interrupted));todo.add(b);UpdateReport.checkpoint(ctx,"Nhường lượt tải: "+b.title+" · giữ phần còn lại trong hàng chờ");}
        else UpdateReport.checkpoint(ctx,"Hoàn tất lượt xử lý: "+b.title);
        boolean unfinished=false;for(Store.Chapter pending:needDownload)if(!s.ignoredForReading(pending)&&!s.readable(pending.id)){unfinished=true;break;}
        if(!unfinished&&!downloadCancelled()&&!pauseAutomatic(charging)){if(!fullRequest)s.finishBatch(b.id,batch);}
        if(!unfinished&&!downloadCancelled()&&!pauseAutomatic(charging))ctx.getSharedPreferences("download_resume",0).edit().remove(b.id).commit();
        Store.Book latest = s.book(b.id);
        if (latest != null && !isFullMode) s.prune(latest);
        ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("books_changed",true).putExtra("text",status));
      }

      if(librarySync&&!hadFailures&&!downloadCancelled()&&!pauseAutomatic(charging)&&todo.isEmpty())ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",true).commit();

      int remaining = 0;
      if (syncTotal > 0 && syncDone < syncTotal) remaining = syncTotal - syncDone;
      if(hadFailures||downloadCancelled()||pauseAutomatic(charging)){syncState="INCOMPLETE";notify(ctx,"Chưa hoàn tất hoặc đã tạm dừng · Xem Chi tiết cập nhật");}
      else if (syncTotal > 0 && remaining == 0) {
        syncState = "COMPLETED";
        syncPct = 100;
        isSyncing = false;
        notify(ctx, "✓ Đã tải xong chương chữ mới · Ảnh lỗi xem riêng");
      } else if (syncTotal == 0) {
        syncState = "COMPLETED";
        syncPct = 100;
        isSyncing = false;
        notify(ctx, "✓ Phạm vi chữ đã đủ offline · Ảnh lỗi xem riêng");
      } else {
        syncState = "INCOMPLETE";
        isSyncing = false;
        notify(ctx, "Chưa hoàn tất: còn " + remaining + " chương chưa tải");
      }
    } catch(Exception error){UpdateReport.checkpoint(ctx,"Đã dừng tác vụ: "+error.getMessage());throw error;} finally {
      if("CHECKING".equals(syncState)||"SHELF".equals(syncState)||"DOWNLOADING".equals(syncState))syncState="INCOMPLETE";
      if("COMPLETED".equals(syncState))syncPct=100;else syncPct=Math.min(99,DownloadLedger.percent(syncDone,syncTotal));
      ctx.getSharedPreferences("settings",0).edit().putInt("last_download_percent_v2",syncPct).apply();
      executionStage="IDLE";Diagnostics.activityEvent();GeckoClient.release();
      if(cancel.get()){clearBook();pendingAll=false;pendingPersonal=false;}else if(syncPaused&&!explicitFull.get()){if(queuedBookMode()!=MODE_BOOK_ALL)clearBook();pendingAll=false;pendingPersonal=false;}
      runStopRevision.remove();explicitFull.remove();currentDownloadBook="";manualOverride = false;
      isSyncing = false;
      notify(ctx,acceptedStop!=stopRevision.get()?"Đã dừng tải theo yêu cầu":syncPaused?(mode==MODE_BOOK_ALL&&"COMPLETED".equals(syncState)?"Đã tải full · Tác vụ nền vẫn tạm dừng":"Đã tạm dừng lượt cập nhật"):!"COMPLETED".equals(syncState)?"Lượt cập nhật chưa hoàn tất · Xem Chi tiết cập nhật":"Đã hoàn tất lượt cập nhật · Ảnh thiếu xem riêng");
      UpdateReport.finish(ctx);
      busy.set(false);synchronized(busy){busy.notifyAll();}
      ReadSync.ownerReleased(ctx,mode==MODE_SYNC_LIBRARY?"after_shelf_update":"after_repository_run");
      if(!syncPaused&&!downloadCancelled()){if(!ctx.getSharedPreferences("paused_requests",0).getAll().isEmpty())restorePausedRequests(ctx);else resumePending(ctx);}
    }
  }finally{RequestTurn.background.set(previousBackground);OperationTrace.end(trace,"closed",0);}}

  static boolean inScope(Store s,Store.Book b,int mode){
    return mode==MODE_SYNC_LIBRARY?b.followed:mode==MODE_SYNC_PERSONAL?(b.stamp>0||s.favorite(b)):true;
  }
  public static void resumeInterrupted(Context c)throws Exception{
    Map<String,?> saved=c.getSharedPreferences("download_resume",0).getAll();
    for(Map.Entry<String,?> entry:saved.entrySet()){
      if(syncPaused||!ChargeJob.isCharging(c)||!ReadSync.wifi(c))return;
      Store.Book b=Store.get(c).book(entry.getKey());if(b==null||b.dropped){c.getSharedPreferences("download_resume",0).edit().remove(entry.getKey()).commit();continue;}
      int mode=((Number)entry.getValue()).intValue();
      if((mode==MODE_SYNC_LIBRARY||mode==MODE_SYNC_PERSONAL)&&!Store.get(c).syncTarget(b)&&!b.id.equals(activeBook)){c.getSharedPreferences("download_resume",0).edit().remove(b.id).commit();continue;}
      int resumeMode=mode==MODE_BOOK_ALL||mode==MODE_READER_NEXT||mode==MODE_BOOK_NEXT?mode:MODE_BOOK_NEXT;
      run(c,b.id,resumeMode,true);
    }
  }

  public static void resumePending(Context ctx){
    if(busy.get()||syncPaused||cancel.get()||(!pendingAll&&!pendingPersonal&&pendingBook.isEmpty()))return;
    Intent i=new Intent(ctx,DownloadService.class);
    if(pendingAll)i.putExtra("mode",MODE_SYNC_LIBRARY);
    else if(pendingPersonal)i.putExtra("mode",MODE_SYNC_PERSONAL);
    else{BookRequest request=peekBook();if(request.book.isEmpty())return;i.putExtra("mode",request.mode).putExtra("book",request.book);}
    try{ctx.startForegroundService(i);}catch(Exception e){notify(ctx,"Yêu cầu cập nhật còn chờ · Không khởi chạy được: "+e.getClass().getSimpleName());}
  }

  private static boolean waitFor(Context c, long delay, String id, boolean charging) throws Exception {
    executionStage="WAIT_DELAY";Diagnostics.activityEvent();long end = android.os.SystemClock.elapsedRealtime() + delay;
    while (android.os.SystemClock.elapsedRealtime() < end) {
      if (cancel.get() || pauseAutomatic(charging) || pendingAll || pendingPersonal || IllustrationPolicy.shouldYield(id,pendingBook) || !priorityChapterId.isEmpty()) return false;
      if (charging && !ChargeJob.isCharging(c)) return false;
      Thread.sleep(Math.min(500, Math.max(1, end - android.os.SystemClock.elapsedRealtime())));
    }
    allowed(c);executionStage="PREPARING";Diagnostics.activityEvent();
    return true;
  }
}
