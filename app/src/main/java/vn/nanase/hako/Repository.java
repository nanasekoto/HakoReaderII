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
  public static void init(Context c){app=c.getApplicationContext();syncPaused=c.getSharedPreferences("settings",0).getBoolean("sync_paused",false);GeckoClient.init(c);HakoParser.ORIGIN=c.getSharedPreferences("settings",0).getString("origin","https://docln.sbs");}
  public static volatile String activeBook="";
  private static final AtomicInteger requestedShelfScan=new AtomicInteger();
  public static void requestShelfScan(int mode){requestedShelfScan.set(mode);}
  private static long lastNotice;private static String lastNoticeText="";
  public static volatile boolean pocketPaused=false, syncPaused=false, shelfUpdating=false;
  public static void awaitIdle() throws InterruptedException {
    synchronized(busy){while(busy.get())busy.wait();}
  }
  private static boolean pauseAutomatic(boolean charging){
    return syncPaused || (pocketPaused && !charging && !manualOverride);
  }
  public static void setSyncPaused(Context c,boolean paused){
    syncPaused=paused;c.getSharedPreferences("settings",0).edit().putBoolean("sync_paused",paused).commit();
    if(paused){cancel.set(true);pendingAll=false;pendingPersonal=false;pendingBook="";c.getSystemService(android.app.job.JobScheduler.class).cancel(42);}
    ChargeJob.schedule(c,!paused&&c.getSharedPreferences("settings",0).getBoolean("charging",true));
    if(!paused)ReadSync.schedule(c);
    notify(c,paused?"Đã tạm dừng đồng bộ · Dữ liệu tải được giữ nguyên":"Đã tiếp tục đồng bộ");
  }
  private static long lastNetwork=0;
  public static void cooldown(Context c,long ms){long capped=Math.min(ms,30*60*1000L);c.getSharedPreferences("settings",0).edit().putLong("cooldownUntil",System.currentTimeMillis()+capped).apply();}
  public static void allowed(Context c)throws IOException {if(System.currentTimeMillis()<c.getSharedPreferences("settings",0).getLong("cooldownUntil",0))throw new IOException("Đang nghỉ sau giới hạn truy cập. Thử lại sau 30 phút.");}
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
  static synchronized void queueBook(String book,int mode){
    // A reader handoff must not downgrade an explicit full/continue request for this book.
    if(book.equals(pendingBook)&&pendingBookMode!=MODE_READER_NEXT&&mode==MODE_READER_NEXT)return;
    pendingBookMode=mode;pendingBook=book;
  }
  static synchronized BookRequest takeBook(){
    BookRequest request=new BookRequest(pendingBook,pendingBookMode);
    pendingBook="";pendingBookMode=MODE_BOOK_NEXT;return request;
  }
  static synchronized int queuedBookMode(){return pendingBookMode;}
  public static volatile int currentMode = 0;
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

  public static String page(String u, boolean auth) throws Exception {
    if(app==null)throw new IOException("Chưa khởi tạo app");
    String script="(function(){var t=document.title||'';if(/^(just a moment|attention required|access denied|503 service unavailable|too many requests)[.!… ]*$/i.test(t.trim())||document.querySelector('#challenge-form,.cf-browser-verification'))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc đang giới hạn truy cập. Mở HAKO để kiểm tra.'});if(!document.body||!document.body.innerHTML.trim())return JSON.stringify({});return JSON.stringify({html:document.documentElement.outerHTML});})()";
    return new String(GeckoClient.request(u,script,8*1024*1024),"UTF-8");
  }

  public static void importShelf(Context ctx) throws Exception {
    init(ctx);allowed(ctx);shelfUpdating=true;syncState="SHELF";
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
    boolean canUseAnchor=anchorKnown;
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
      if(cancel.get()||syncPaused)throw new IOException("Đã tạm dừng cập nhật tủ sách");
      notify(ctx, "Đang nhập kệ sách • trang " + (done.size() + 1));
      UpdateReport.page(ctx);UpdateReport.log(ctx,"Kiểm tra trang "+(done.size()+1)+" tủ sách");
      allowed(ctx);String html = page(next, true);
      done.add(next);
      if (Jsoup.parse(html).selectFirst("input[type=password]") != null)
        throw new IOException("Hãy đăng nhập HAKO trước khi nhập kệ sách.");
      List<HakoParser.Link> links = HakoParser.shelf(html, next);
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
      notify(ctx,"Đã nhận trang "+done.size()+" · "+count+" truyện · đang kiểm tra phần còn lại");
      ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("shelf_changed",true).putExtra("text",status));
      List<String> following=HakoParser.shelfPages(html,next);
      boolean orderSafe=true;
      for(Element option:Jsoup.parse(html).select("select option[selected]")){String v=(option.text()+" "+option.attr("value")).toLowerCase(Locale.ROOT);if(v.matches(".*(a-z|z-a|title|tên truyện|cũ nhất|_asc).*"))orderSafe=false;}
      boolean stop=!following.isEmpty()&&ShelfAnchor.canStopAtUnchanged(anchorRows,previousKeys,anchorKnown&&orderSafe);
      if(stop&&quickObserved==null){quickObserved=new HashSet<>(shelfKeys.keySet());UpdateReport.log(ctx,"Gặp chương mới nhất không đổi: "+anchorTitle+" · trang "+done.size());}
      if(stop&&!full){partial=true;UpdateReport.log(ctx,"Dừng quét nhanh ở trang "+done.size()+"; giữ các trang phía sau trong bộ nhớ");break;}
      urls.addAll(following);
      boolean more=false;for(String u:urls)if(!done.contains(u)){more=true;break;}
      if(more){notify(ctx,"Đã nhận trang "+done.size()+" · mở trang tiếp theo");Thread.sleep(500);}
    }
    if(cancel.get()||syncPaused)throw new IOException("Đã tạm dừng cập nhật tủ sách");
    if(audit){
      prefs.edit().putLong("shelf_anchor_audited_at",System.currentTimeMillis()).commit();
      int missed=0;
      if(quickObserved==null)UpdateReport.log(ctx,"Không gặp mốc dừng: quét nhanh cũng cần kiểm tra toàn bộ");
      else for(HakoParser.Link row:collected)if(!quickObserved.contains(row.id)&&(!ShelfPolicy.unchanged(row.latestKey,previousKeys.get(row.id)))){missed++;UpdateReport.log(ctx,"Quét nhanh có thể bỏ sót: "+row.title+" (chương mới hoặc thông tin chưa rõ)");}
      if(missed>0){prefs.edit().putBoolean("shelf_fast_disabled",true).putBoolean("shelf_anchor_verified",false).commit();UpdateReport.log(ctx,"Đã tắt dừng sớm do đối chiếu không khớp. Những lần sau quét đầy đủ.");}
      else if(quickObserved!=null){prefs.edit().putBoolean("shelf_anchor_verified",true).putBoolean("shelf_fast_disabled",false).commit();UpdateReport.log(ctx,"Đối chiếu điểm dừng: không phát hiện cập nhật bị bỏ sót");}
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
    shelfUpdating=false;
    UpdateReport.checkpoint(ctx,"Hoàn tất cập nhật tủ sách");
    notify(ctx, "Đã cập nhật tủ sách: " + count + " mục · Chuẩn bị tải theo ưu tiên");
    ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("shelf_changed",true).putExtra("text",status));
    }finally{shelfUpdating=false;if("SHELF".equals(syncState))syncState="CHECKING";}
  }

  public static Store.Book add(Context c, String url) throws Exception {
    String u = HakoParser.storyUrl(url);
    String id = HakoParser.storyId(u);
    if (id.isEmpty()) throw new IOException("Dán URL truyện hoặc chương từ docln.sbs");
    allowed(c);String html = page(u, true);
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

  public static void refreshForReading(Context c,Store.Book b)throws Exception{
    while(true){awaitIdle();if(busy.compareAndSet(false,true))break;}
    try{init(c);allowed(c);catalog(c,b,true);}
    finally{GeckoClient.release();busy.set(false);synchronized(busy){busy.notifyAll();}resumePending(c);}
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
    String html = page(b.url, true);
    List<HakoParser.Link> ls = HakoParser.chapters(html, b.url);
    if (ls.isEmpty()) throw new IOException("Mục lục trống: " + b.title);
    Store.get(c).catalog(b.id, ls);
    Store.get(c).establishReadBaseline(b.id);
    c.getSharedPreferences("settings",0).edit().putLong("catalog-"+b.id,System.currentTimeMillis()).putString("catalog_shelf_key_"+b.id,shelfKey).remove("shelf_changed_"+b.id).remove("catalog_failed_"+b.id).commit();
  }

  public static synchronized void download(Context ctx, Store.Chapter c) throws Exception {
    download(ctx,c,false);
  }

  public static synchronized void download(Context ctx,Store.Chapter c,boolean force) throws Exception {download(ctx,c,force,false);}
  public static synchronized void downloadImages(Context ctx,Store.Chapter c)throws Exception{download(ctx,c,true,true);}
  private static synchronized void download(Context ctx,Store.Chapter c,boolean force,boolean imagesOnly) throws Exception {
    Store s = Store.get(ctx);
    boolean cachedContent=s.readable(c.id);
    if(!force&&s.cachedIllustration(c.id)){UpdateReport.log(ctx,c.title+": giữ minh họa đã lưu · không tải lại trang");return;}
    if(!CachePolicy.shouldFetch(cachedContent,force)){if(!c.ready)s.state(c.id,true,c.error);return;}
    allowed(ctx);
    long pause=Math.max(0,1500-(android.os.SystemClock.elapsedRealtime()-lastNetwork));
    if(pause>0)Thread.sleep(pause);
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
            writeSmallImage(data,out);
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

  public static void run(Context ctx, String book, int mode, boolean charging) throws Exception {
    if(pocketPaused && !charging && (mode==MODE_BOOK_NEXT||mode==MODE_READER_NEXT))return;
    init(ctx);
    if(syncPaused)return;
    if (!busy.compareAndSet(false, true)) {
      if (mode == MODE_SYNC_LIBRARY) pendingAll = true;
      else if(mode==MODE_SYNC_PERSONAL)pendingPersonal=true;
      else if (book != null && !book.isEmpty()) {if(mode==MODE_BOOK_ALL)Store.get(ctx).setKeepFull(book,true);queueBook(book,mode);}
      return;
    }
    cancel.set(false);
    currentMode = mode;
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
      if(mode==MODE_SYNC_LIBRARY){importShelf(ctx);ReadSync.processQueued(ctx);}
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
      while (!cancel.get() && !pauseAutomatic(charging) && (!todo.isEmpty() || !pendingBook.isEmpty() || pendingAll || pendingPersonal)) {
        if (pendingAll||pendingPersonal) {
          int requested=pendingAll?MODE_SYNC_LIBRARY:MODE_SYNC_PERSONAL;
          if(pendingAll)pendingAll=false;else pendingPersonal=false;librarySync=true;currentMode=requested;
          // A refresh request preempts the bulk queue after the current chapter.
          if(requested==MODE_SYNC_LIBRARY){importShelf(ctx);ReadSync.processQueued(ctx);}refreshedCatalogs.clear();todo.clear();requestModes.clear();ledger.clear();syncTotal=0;syncDone=0;
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
        currentMode=bookMode;
        ctx.getSharedPreferences("download_resume",0).edit().putInt(b.id,bookMode).commit();
        if(librarySync&&b!=null&&!inScope(s,b,bookMode)){UpdateReport.log(ctx,b.title+": ngoài phạm vi cập nhật");continue;}
        if (b == null || b.id == null || b.id.isEmpty() || !FetchPolicy.allowDownload(b.dropped,(mode==MODE_BOOK_ALL && b.id.equals(book))||(manualOverride&&s.isKeepFull(b.id)))) continue;

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

        boolean toEnd = FetchPolicy.toEnd(b.followed,s.favorite(b),b.visits>0,isFullMode);
        int scopeEnd = toEnd ? Integer.MAX_VALUE : current + FetchPolicy.AHEAD;

        // 2. Identify EXACT chapters needed
        List<Store.Chapter> needDownload = new ArrayList<>();
        // Priority: current reading chapter to end of book
        for (Store.Chapter ch : chapters) {
          if (ch.ord >= current && ch.ord <= scopeEnd && !s.isHidden(b.id,ch.id) && !s.readable(ch.id) && s.autoIllustration(ch)) needDownload.add(ch);
        }
        // If full mode: also earlier chapters
        for (Store.Chapter ch : chapters) {
          if (isFullMode && !readerOnly && ch.ord < current && !s.isHidden(b.id,ch.id) && !s.readable(ch.id) && s.autoIllustration(ch)) needDownload.add(ch);
        }

        needDownload.sort((a,z)->Boolean.compare(s.illustrationHint(a),s.illustrationHint(z)));
        UpdateReport.log(ctx,b.title+": phạm vi — "+(readerOnly?"mở đọc: chỉ phần hiện tại và phía sau, không tải bù chương cũ":isFullMode?"toàn bộ truyện":toEnd?"từ chương đang đọc đến cuối, giữ 3 chương trước":"chương đang đọc + tối đa 15 chương tiếp, giữ 3 chương trước"));
        int stored=0;for(Store.Chapter ch:chapters)if(ch.ready)stored++;
        UpdateReport.log(ctx,b.title+": đã lưu "+stored+"/"+chapters.size()+" chương toàn bộ · Cần tải "+needDownload.size()+" chương trong phạm vi đã chọn");
        for(Store.Chapter needed:needDownload){if(!needed.book.equals(b.id))throw new IOException("Hàng đợi sai truyện; đã dừng để giữ dữ liệu");if(!s.illustrationHint(needed))ledger.add(needed.id);}
        syncTotal=ledger.total();syncDone=ledger.done();
        syncState = "DOWNLOADING";
        if (syncTotal == 0) {
          notify(ctx, b.title + ": Đã tải đủ ✓");
        } else {
          syncPct = Math.min(99, Math.round(syncDone * 100f / syncTotal));
          notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + ")");
        }

        // 3. Download loop per chapter with preemption
        int distance=0;
        for (Store.Chapter ch : needDownload) {
          if (cancel.get() || pauseAutomatic(charging) || pendingAll || pendingPersonal || IllustrationPolicy.shouldYield(b.id,pendingBook)) break;
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

          long delay = FetchPolicy.delayMillis(++distance);
          if (!waitFor(ctx, delay, b.id, charging)) break;

          try {
            download(ctx, ch);
            if (s.readable(ch.id)) {
              ledger.complete(ch.id);syncDone=ledger.done();
              syncPct = syncTotal > 0 ? Math.min(99, Math.round(syncDone * 100f / syncTotal)) : 100;
              notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + "): " + ch.title);
            }
          } catch (Exception e) {
            if(!s.illustrationHint(ch))hadFailures=true;
            s.state(ch.id, false, e.getMessage());
            if(s.illustrationHint(ch))ctx.getSharedPreferences("settings",0).edit().putBoolean("illustration_attempted_"+ch.id,true).commit();
            UpdateReport.log(ctx,b.title+" · "+ch.title+": lỗi tải — "+e.getMessage());
            notify(ctx, "Lỗi tải " + ch.title + ": " + e.getMessage());
            Thread.sleep(1500);
          }
        }

        if(!pendingAll&&(IllustrationPolicy.shouldYield(b.id,pendingBook)||(!priorityChapterId.isEmpty()&&!needDownload.isEmpty()))){final String interrupted=b.id;todo.removeIf(x->x.id.equals(interrupted));todo.add(b);UpdateReport.checkpoint(ctx,"Nhường lượt tải: "+b.title+" · giữ phần còn lại trong hàng chờ");}
        else UpdateReport.checkpoint(ctx,"Hoàn tất lượt xử lý: "+b.title);
        boolean unfinished=false;for(Store.Chapter pending:needDownload)if(!s.ignoredForReading(pending)&&!s.readable(pending.id)){unfinished=true;break;}
        if(!unfinished&&!cancel.get()&&!pauseAutomatic(charging))ctx.getSharedPreferences("download_resume",0).edit().remove(b.id).commit();
        Store.Book latest = s.book(b.id);
        if (latest != null && !isFullMode) s.prune(latest);
        ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("books_changed",true).putExtra("text",status));
      }

      if(librarySync&&!hadFailures&&!cancel.get()&&!pauseAutomatic(charging)&&todo.isEmpty())ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",true).commit();

      int remaining = 0;
      if (syncTotal > 0 && syncDone < syncTotal) remaining = syncTotal - syncDone;
      if(hadFailures||cancel.get()||pauseAutomatic(charging)){syncState="INCOMPLETE";notify(ctx,"Chưa hoàn tất hoặc đã tạm dừng · Xem Chi tiết cập nhật");}
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
      try{UiLibrarySnapshot display=LibraryState.refresh(ctx);int total=0,stored=0;for(Store.Book item:display.books){if(!Store.get(ctx).syncTarget(item))continue;UiLibrarySnapshot.Summary info=display.summary(item);total+=info.scopeTotal;stored+=info.scopeStored;}syncPct=total==0?0:Math.round(stored*100f/total);ctx.getSharedPreferences("settings",0).edit().putInt("last_offline_percent",syncPct).apply();}catch(Exception snapshotError){UpdateReport.log(ctx,"Giữ bản thông tin cũ: "+snapshotError.getMessage());}
      GeckoClient.release();busy.set(false);
      ReadSync.schedule(ctx);
      synchronized(busy){busy.notifyAll();}
      if(cancel.get()||syncPaused){pendingBook="";pendingAll=false;pendingPersonal=false;}
      manualOverride = false;
      isSyncing = false;
      notify(ctx,syncPaused?"Đã tạm dừng đồng bộ · Chữ offline "+syncPct+"%":!"COMPLETED".equals(syncState)?"Lượt cập nhật chưa hoàn tất · Chữ offline "+syncPct+"% · Xem Chi tiết cập nhật":syncPct==100?"Chữ offline 100% · Ảnh thiếu xem riêng":"Lượt tải đã xong · Chữ offline toàn thư viện "+syncPct+"% · Phần còn thiếu xem Chi tiết cập nhật");
      UpdateReport.finish(ctx);
      resumePending(ctx);
    }
  }

  static boolean inScope(Store s,Store.Book b,int mode){
    return mode==MODE_SYNC_LIBRARY?b.followed:mode==MODE_SYNC_PERSONAL?(b.stamp>0||s.favorite(b)):true;
  }
  public static void resumeInterrupted(Context c)throws Exception{
    Map<String,?> saved=c.getSharedPreferences("download_resume",0).getAll();
    for(Map.Entry<String,?> entry:saved.entrySet()){
      if(syncPaused||!ChargeJob.isCharging(c)||!ReadSync.wifi(c))return;
      Store.Book b=Store.get(c).book(entry.getKey());if(b==null||b.dropped){c.getSharedPreferences("download_resume",0).edit().remove(entry.getKey()).commit();continue;}
      int mode=((Number)entry.getValue()).intValue();
      run(c,b.id,mode==MODE_BOOK_ALL?MODE_BOOK_ALL:MODE_READER_NEXT,true);
    }
  }

  public static void resumePending(Context ctx){
    if(busy.get()||syncPaused||cancel.get()||(!pendingAll&&!pendingPersonal&&pendingBook.isEmpty()))return;
    Intent i=new Intent(ctx,DownloadService.class);
    if(pendingAll)i.putExtra("mode",MODE_SYNC_LIBRARY);
    else if(pendingPersonal)i.putExtra("mode",MODE_SYNC_PERSONAL);
    else i.putExtra("mode",queuedBookMode()).putExtra("book",pendingBook);
    try{ctx.startForegroundService(i);}catch(Exception e){notify(ctx,"Yêu cầu cập nhật còn chờ · Không khởi chạy được: "+e.getClass().getSimpleName());}
  }

  private static boolean waitFor(Context c, long delay, String id, boolean charging) throws Exception {
    long end = android.os.SystemClock.elapsedRealtime() + delay;
    while (android.os.SystemClock.elapsedRealtime() < end) {
      if (cancel.get() || pauseAutomatic(charging) || pendingAll || pendingPersonal || IllustrationPolicy.shouldYield(id,pendingBook) || !priorityChapterId.isEmpty()) return false;
      if (charging && !ChargeJob.isCharging(c)) return false;
      Thread.sleep(Math.min(500, Math.max(1, end - android.os.SystemClock.elapsedRealtime())));
    }
    allowed(c);
    return true;
  }
}
