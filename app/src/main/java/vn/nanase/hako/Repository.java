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
  public static volatile boolean pocketPaused=false, syncPaused=false, shelfUpdating=false;
  public static void awaitIdle() throws InterruptedException {
    synchronized(busy){while(busy.get())busy.wait();}
  }
  private static boolean pauseAutomatic(boolean charging){
    return syncPaused || (pocketPaused && !charging && !manualOverride);
  }
  public static void setSyncPaused(Context c,boolean paused){
    syncPaused=paused;c.getSharedPreferences("settings",0).edit().putBoolean("sync_paused",paused).commit();
    if(paused){cancel.set(true);pendingAll=false;pendingBook="";c.getSystemService(android.app.job.JobScheduler.class).cancel(42);}
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
  public static volatile boolean pendingAll = false, manualOverride = false;
  public static volatile String status = "Sẵn sàng", pendingBook = "";
  public static final int MODE_BOOK_NEXT = 1, MODE_BOOK_ALL = 2, MODE_SYNC_LIBRARY = 3;
  public static volatile int currentMode = 0;
  public static volatile int syncTotal = 0, syncDone = 0, syncPct = 100;
  public static volatile boolean isSyncing = false;
  public static volatile String syncState = "IDLE";
  public static volatile String priorityChapterId = "";

  public static void notify(Context c, String s) {
    status = s;
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
    String script="(function(){var t=(document.body?document.body.innerText:'').slice(0,3000);if(/verify you are human|checking your browser|just a moment|too many requests|access denied/i.test(t))return JSON.stringify({error:'Trang yêu cầu xác minh hoặc đang giới hạn truy cập. Mở HAKO để kiểm tra.'});if(!document.body||!document.body.innerHTML.trim())return JSON.stringify({});return JSON.stringify({html:document.documentElement.outerHTML});})()";
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
    long watermark=prefs.getLong("shelf_watermark",0), newest=0;boolean partial=false;
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
      allowed(ctx);String html = page(next, true);
      done.add(next);
      if (Jsoup.parse(html).selectFirst("input[type=password]") != null)
        throw new IOException("Hãy đăng nhập HAKO trước khi nhập kệ sách.");
      List<HakoParser.Link> links = HakoParser.shelf(html, next);
      for (HakoParser.Link l : links) {
        Store.Book previous=s.book(l.id);
        int before=previous==null?-1:shelfCount(previous.shelfInfo),after=shelfCount(l.info);
        if(after>=0&&((before>=0&&after>before)||(previous==null&&after>0)))changedShelfBooks.add(l.id);
        collected.add(l);
        count++;
        shelfKeys.put(l.id,l.latestKey);shelfTimes.put(l.id,l.updatedAt);newest=Math.max(newest,l.updatedAt);
      }
      // A small number of updates alone is not enough to skip unseen shelf pages.
      List<String> following=HakoParser.shelfPages(html,next);
      if(!following.isEmpty()&&ShelfPolicy.oldPage(links,watermark,HakoParser.shelfUpdateOrder(html))){partial=true;break;}
      urls.addAll(HakoParser.shelfPages(html, next));
      Thread.sleep(2000);
    }
    if(cancel.get()||syncPaused)throw new IOException("Đã tạm dừng cập nhật tủ sách");
    if(partial)s.mergeShelf(collected);else s.replaceShelf(collected);
    android.content.SharedPreferences.Editor edit=prefs.edit();
    // Replace current keys only for rows actually observed; older cached rows remain intact.
    for(java.util.Map.Entry<String,String> e:shelfKeys.entrySet())edit.putString("shelf_key_"+e.getKey(),e.getValue());
    for(java.util.Map.Entry<String,Long> e:shelfTimes.entrySet())edit.putLong("shelf_updated_at_"+e.getKey(),e.getValue());
    for(String changed:changedShelfBooks)edit.putBoolean("shelf_changed_"+changed,true);
    if(!partial&&newest>0)edit.putLong("shelf_watermark",newest);
    edit.commit();
    ctx.getSharedPreferences("settings",0).edit().putLong("lastShelfSync",System.currentTimeMillis()).commit();
    shelfUpdating=false;
    notify(ctx, "Đã cập nhật tủ sách: " + count + " mục · Chuẩn bị tải theo ưu tiên");
    ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("shelf_changed",true).putExtra("text",status));
    }finally{shelfUpdating=false;}
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

  private static int shelfCount(String value){try{return Integer.parseInt(value.trim().split("\\s+")[0]);}catch(Exception e){return -1;}}

  public static boolean unchangedShelf(Context c,Store.Book b){
    android.content.SharedPreferences p=c.getSharedPreferences("settings",0);
    return b.followed&&!Store.get(c).chapters(b.id).isEmpty()&&ShelfPolicy.unchanged(p.getString("shelf_key_"+b.id,""),p.getString("catalog_shelf_key_"+b.id,""));
  }

  public static void catalog(Context c, Store.Book b) throws Exception {
    long stamp=c.getSharedPreferences("settings",0).getLong("catalog-"+b.id,0);
    if(!Store.get(c).chapters(b.id).isEmpty()&&System.currentTimeMillis()-stamp<15*60*1000)return;
    allowed(c);
    String shelfKey=c.getSharedPreferences("settings",0).getString("shelf_key_"+b.id,"");
    String html = page(b.url, true);
    List<HakoParser.Link> ls = HakoParser.chapters(html, b.url);
    if (ls.isEmpty()) throw new IOException("Mục lục trống: " + b.title);
    Store.get(c).catalog(b.id, ls);
    Store.get(c).establishReadBaseline(b.id);
    c.getSharedPreferences("settings",0).edit().putLong("catalog-"+b.id,System.currentTimeMillis()).putString("catalog_shelf_key_"+b.id,shelfKey).remove("shelf_changed_"+b.id).apply();
  }

  public static synchronized void download(Context ctx, Store.Chapter c) throws Exception {
    Store s = Store.get(ctx);
    if (s.readable(c.id) && c.ready) return;
    allowed(ctx);
    long pause=Math.max(0,1500-(android.os.SystemClock.elapsedRealtime()-lastNetwork));
    if(pause>0)Thread.sleep(pause);
    lastNetwork=android.os.SystemClock.elapsedRealtime();
    File dir = s.dir(c.id);
    dir.mkdirs();
    if (dir.getUsableSpace() < 64L * 1024 * 1024)
      throw new IOException("Bộ nhớ còn dưới 64 MB. Hãy giải phóng dung lượng trước khi tải tiếp.");
    String content = HakoParser.content(RenderedPage.chapter(ctx,c.url), c.url);
    Document d = Jsoup.parseBodyFragment(content);
    int errors = 0, index = 0;
    String error = "";
    for (Element img : d.select("img[src]")) {
      String src = img.attr("src");
      String name = "image-" + imageKey(src) + ".bin";
      File file = new File(dir, name);
      if (!HakoParser.imageAllowed(src)) {
        errors++;
        img.replaceWith(new Element("p").text("[Ảnh ngoài máy chủ HAKO — xem trên web]"));
        continue;
      }
      try {
        if (!file.isFile()) {
          byte[] data = fetch(src, false, 16 * 1024 * 1024);
          File tmp = new File(dir, name + ".tmp");
          try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
          }
          if (!tmp.renameTo(file)) throw new IOException("Không lưu được ảnh");
        }
        img.attr("src", "https://offline.hako.invalid/" + c.id + "/" + name);
      } catch (Exception e) {
        errors++;
        error = e.getMessage();
        img.replaceWith(new Element("p").text("[Ảnh chưa tải được — thử tải lại khi có mạng]"));
      }
    }
    if(!HakoParser.validContent(d.body().html()))throw new IOException("Nội dung rỗng");
    synchronized(s){Store.Book owner=s.book(c.book);
    if(owner!=null&&!FetchPolicy.allowCache(owner.followed||s.syncTarget(owner),c.book.equals(activeBook),s.isKeepFull(owner.id)))return;
    Store.write(s.html(c.id), d.body().html());
    s.state(c.id, errors == 0, errors == 0 ? "" : "Thiếu " + errors + " ảnh. " + error);}
  }

  private static String imageKey(String src) throws Exception {
    byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(src.getBytes("UTF-8"));
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < 12; i++)
      b.append(String.format(java.util.Locale.ROOT, "%02x", hash[i] & 255));
    return b.toString();
  }

  public static void run(Context ctx, String book, int mode, boolean charging) throws Exception {
    if(pocketPaused && !charging && mode==MODE_BOOK_NEXT)return;
    init(ctx);
    if(syncPaused)return;
    if (!busy.compareAndSet(false, true)) {
      if (mode == MODE_SYNC_LIBRARY) pendingAll = true;
      else if (book != null && !book.isEmpty()) pendingBook = book;
      return;
    }
    cancel.set(false);
    currentMode = mode;
    boolean librarySync=mode==MODE_SYNC_LIBRARY;
    manualOverride = !charging && (mode == MODE_BOOK_ALL || mode == MODE_SYNC_LIBRARY);
    isSyncing = true;
    syncState = "CHECKING";
    syncTotal = 0;
    syncDone = 0;
    syncPct = 0;
    if(mode==MODE_SYNC_LIBRARY)ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",false).commit();
    notify(ctx, "Đang kiểm tra mục lục…");

    try {
      allowed(ctx);
      Store s = Store.get(ctx);
      if(mode==MODE_SYNC_LIBRARY)importShelf(ctx);
      ArrayList<Store.Book> todo = new ArrayList<>();

      if (mode == MODE_BOOK_NEXT || mode == MODE_BOOK_ALL) {
        Store.Book b = s.book(book);
        if (b != null && !b.id.equals("demo")) {
          if (mode == MODE_BOOK_ALL) s.setKeepFull(b.id, true);
          todo.add(b);
        }
      } else { // MODE_SYNC_LIBRARY
        for (Store.Book b : s.books()) {
          if (b.id.equals("demo") || b.dropped) continue;
          boolean autoDl = ctx.getSharedPreferences("settings", 0).getBoolean("auto_dl_" + b.id, false);
          if (s.syncTarget(b) || s.hasShelfUpdate(b) || autoDl || b.id.equals(activeBook)) {
            todo.add(b);
          }
        }
      }

      if(librarySync)todo.sort(s::compareDownloads);
      Set<String> refreshedCatalogs=new HashSet<>();
      while (!cancel.get() && !pauseAutomatic(charging) && (!todo.isEmpty() || !pendingBook.isEmpty() || pendingAll)) {
        if (pendingAll) {
          pendingAll = false;librarySync=true;currentMode=MODE_SYNC_LIBRARY;
          // A refresh request preempts the bulk queue after the current chapter.
          importShelf(ctx);refreshedCatalogs.clear();todo.clear();syncTotal=0;syncDone=0;
          for(Store.Book candidate:s.books()){
            if(candidate.id.equals("demo")||candidate.dropped)continue;
            boolean autoDl=ctx.getSharedPreferences("settings",0).getBoolean("auto_dl_"+candidate.id,false);
            if(s.syncTarget(candidate)||s.hasShelfUpdate(candidate)||autoDl||candidate.id.equals(activeBook))todo.add(candidate);
          }
          todo.sort(s::compareDownloads);
        }
        if (!pendingBook.isEmpty()) {
          Store.Book p = s.book(pendingBook);
          pendingBook = "";
          if (p != null) {
            todo.removeIf(x -> x.id.equals(p.id));
            todo.add(p);
            if(librarySync)todo.sort(s::compareDownloads);else {todo.remove(p);todo.add(0,p);}
          }
        }
        if (todo.isEmpty() || (charging && !ChargeJob.isCharging(ctx))) break;

        Store.Book b = todo.remove(0);
        if (b == null || b.id == null || b.id.isEmpty() || !FetchPolicy.allowDownload(b.dropped,(mode==MODE_BOOK_ALL && b.id.equals(book))||(manualOverride&&s.isKeepFull(b.id)))) continue;

        // 1. Refresh catalog FIRST to know real chapter count
        if(!refreshedCatalogs.contains(b.id)){
          if(librarySync)ctx.getSharedPreferences("settings",0).edit().remove("catalog-"+b.id).commit();
          boolean finishedUnchanged=librarySync&&s.isCompleted(b.id)&&!s.chapters(b.id).isEmpty()&&!s.hasShelfUpdate(b);
          if(!finishedUnchanged&&(!librarySync||!unchangedShelf(ctx,b)))catalog(ctx,b);
          refreshedCatalogs.add(b.id);
        }
        Store.Book refreshed = s.book(b.id);
        if (refreshed != null) b = refreshed;
        List<Store.Chapter> chapters = s.chapters(b.id);
        int current = 0;
        for (Store.Chapter ch : chapters) if (ch.id.equals(b.current)) current = ch.ord;

        boolean isFullMode = (mode == MODE_BOOK_ALL && b.id.equals(book)) || s.isKeepFull(b.id) || s.isCompleted(b.id);

        // 2. Identify EXACT chapters needed
        List<Store.Chapter> needDownload = new ArrayList<>();
        // Priority: current reading chapter to end of book
        for (Store.Chapter ch : chapters) {
          if (ch.ord >= current && !ch.ready) needDownload.add(ch);
        }
        // If full mode: also earlier chapters
        if (isFullMode) {
          for (Store.Chapter ch : chapters) {
            if (ch.ord < current && !ch.ready) needDownload.add(ch);
          }
        }

        syncTotal += needDownload.size();
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
          if (cancel.get() || pauseAutomatic(charging) || pendingAll) break;
          if (charging && !ChargeJob.isCharging(ctx)) break;

          // Priority check: did user open another chapter?
          if (!priorityChapterId.isEmpty()) {
            String pCid = priorityChapterId;
            priorityChapterId = "";
            Store.Chapter pch = s.chapter(b.id, pCid);
            if (pch != null && !s.readable(pch.id)) {
              notify(ctx, "Ưu tiên: " + pch.title);
              try {
                download(ctx, pch);
                if (s.readable(pch.id)) syncDone++;
              } catch (Exception ignored) {}
            }
          }

          if (ch.ready) continue;

          long delay = FetchPolicy.delayMillis(++distance);
          if (!waitFor(ctx, delay, b.id, charging)) break;

          try {
            download(ctx, ch);
            if (s.readable(ch.id)) {
              syncDone++;
              syncPct = syncTotal > 0 ? Math.min(99, Math.round(syncDone * 100f / syncTotal)) : 100;
              notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + "): " + ch.title);
            }
          } catch (Exception e) {
            s.state(ch.id, false, e.getMessage());
            notify(ctx, "Lỗi tải " + ch.title + ": " + e.getMessage());
            Thread.sleep(1500);
          }
        }

        Store.Book latest = s.book(b.id);
        if (latest != null && !isFullMode) s.prune(latest);
        ctx.sendBroadcast(new Intent(EVENT).setPackage(ctx.getPackageName()).putExtra("books_changed",true).putExtra("text",status));
      }

      if(librarySync&&!cancel.get()&&!pauseAutomatic(charging)&&todo.isEmpty())ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",true).commit();

      int remaining = 0;
      if (syncTotal > 0 && syncDone < syncTotal) remaining = syncTotal - syncDone;
      if (syncTotal > 0 && remaining == 0) {
        syncState = "COMPLETED";
        syncPct = 100;
        isSyncing = false;
        notify(ctx, "✓ Đã tải xong tất cả chương mới");
      } else if (syncTotal == 0) {
        syncState = "COMPLETED";
        syncPct = 100;
        isSyncing = false;
        notify(ctx, "✓ Tất cả chương đã có đủ offline");
      } else {
        syncState = "INCOMPLETE";
        isSyncing = false;
        notify(ctx, "Chưa hoàn tất: còn " + remaining + " chương chưa tải");
      }
    } finally {
      syncPct=Store.get(ctx).totalOfflinePercent();
      busy.set(false);GeckoClient.release();
      ReadSync.schedule(ctx);
      synchronized(busy){busy.notifyAll();}
      pendingBook = "";
      pendingAll = false;
      manualOverride = false;
      isSyncing = false;
      notify(ctx,syncPaused?"Đã tạm dừng đồng bộ · Dữ liệu tải được giữ nguyên":syncPct==100?"Offline đã đủ · Có thể tắt Wi-Fi":"Offline "+syncPct+"% · Chưa tải đủ");
    }
  }

  private static boolean waitFor(Context c, long delay, String id, boolean charging) throws Exception {
    long end = android.os.SystemClock.elapsedRealtime() + delay;
    while (android.os.SystemClock.elapsedRealtime() < end) {
      if (cancel.get() || pauseAutomatic(charging) || pendingAll || !priorityChapterId.isEmpty()) return false;
      if (charging && !ChargeJob.isCharging(c)) return false;
      Thread.sleep(Math.min(500, Math.max(1, end - android.os.SystemClock.elapsedRealtime())));
    }
    allowed(c);
    return true;
  }
}
