package vn.nanase.hako;

import android.content.*;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

public final class Repository {
  private static Context app;
  private static String userAgent="HakoPocket/0.3";
  public static void init(Context c){app=c.getApplicationContext();userAgent=android.webkit.WebSettings.getDefaultUserAgent(c);HakoParser.ORIGIN=c.getSharedPreferences("settings",0).getString("origin","https://docln.sbs");}
  public static volatile String activeBook="";
  private static long lastNetwork=0;
  public static void cooldown(Context c,long ms){long capped=Math.min(ms,3*60*1000L);c.getSharedPreferences("settings",0).edit().putLong("cooldownUntil",System.currentTimeMillis()+capped).apply();}
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
    HttpURLConnection con = (HttpURLConnection) u.openConnection();
    con.setConnectTimeout(15000);
    con.setReadTimeout(20000);
    con.setInstanceFollowRedirects(false);
    con.setRequestProperty("User-Agent",userAgent);
    con.setRequestProperty("Referer",HakoParser.ORIGIN+"/");
    if (authenticated) {
      String cookies = CookieManager.getInstance().getCookie(HakoParser.ORIGIN);
      if (cookies != null) con.setRequestProperty("Cookie", cookies);
    }
    try {
      int code = con.getResponseCode();
      if (code >= 300 && code < 400)
        throw new IOException(
            authenticated
                ? "Phiên hết hạn hoặc trang chuyển hướng. Mở HAKO để đăng nhập lại."
                : "Trang chuyển hướng. Hãy mở HAKO để kiểm tra đường dẫn.");
      if (code == 403 || code == 429) {
        if(app!=null)cooldown(app,30*60*1000L);throw new IOException(
            "HAKO giới hạn truy cập (" + code + "). Đã dừng tải; hãy thử lại sau.");}
      if (code != 200) throw new IOException("Máy chủ trả lỗi " + code);
      try (InputStream in = con.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] b = new byte[8192];
        int n, total = 0;
        while ((n = in.read(b)) != -1) {
          total += n;
          if (total > max) throw new IOException("Tài nguyên quá lớn");
          out.write(b, 0, n);
        }
        return out.toByteArray();
      }
    } finally {
      con.disconnect();
    }
  }

  public static String page(String u, boolean auth) throws Exception {
    return new String(fetch(u, auth, 8 * 1024 * 1024), "UTF-8");
  }

  public static void importShelf(Context ctx) throws Exception {
    init(ctx);allowed(ctx);Store s = Store.get(ctx);
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    urls.add(HakoParser.ORIGIN + "/ke-sach");
    Set<String> done = new HashSet<>();
    int count = 0;List<HakoParser.Link> collected=new ArrayList<>();
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
      notify(ctx, "Đang nhập kệ sách • trang " + (done.size() + 1));
      allowed(ctx);String html = page(next, true);
      done.add(next);
      if (Jsoup.parse(html).selectFirst("input[type=password]") != null)
        throw new IOException("Hãy đăng nhập HAKO trước khi nhập kệ sách.");
      List<HakoParser.Link> links = HakoParser.shelf(html, next);
      for (HakoParser.Link l : links) {
        collected.add(l);
        count++;
      }
      urls.addAll(HakoParser.shelfPages(html, next));
      Thread.sleep(2000);
    }
    s.replaceShelf(collected);
    notify(ctx, "Đã nhập " + count + " mục từ kệ sách. Chưa tải hàng loạt nội dung.");
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

  public static void catalog(Context c, Store.Book b) throws Exception {
    long stamp=c.getSharedPreferences("settings",0).getLong("catalog-"+b.id,0);
    if(!Store.get(c).chapters(b.id).isEmpty()&&System.currentTimeMillis()-stamp<15*60*1000)return;
    allowed(c);
    String html = page(b.url, true);
    List<HakoParser.Link> ls = HakoParser.chapters(html, b.url);
    if (ls.isEmpty()) throw new IOException("Mục lục trống: " + b.title);
    Store.get(c).catalog(b.id, ls);
    c.getSharedPreferences("settings",0).edit().putLong("catalog-"+b.id,System.currentTimeMillis()).apply();
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
    if(owner!=null&&!owner.followed&&!c.book.equals(activeBook))return;
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
    init(ctx);
    if (!busy.compareAndSet(false, true)) {
      if (mode == MODE_SYNC_LIBRARY) pendingAll = true;
      else if (book != null && !book.isEmpty()) pendingBook = book;
      return;
    }
    cancel.set(false);
    currentMode = mode;
    manualOverride = !charging && (mode == MODE_BOOK_ALL || mode == MODE_SYNC_LIBRARY);
    isSyncing = true;
    syncState = "CHECKING";
    syncTotal = 0;
    syncDone = 0;
    syncPct = 0;
    notify(ctx, "Đang kiểm tra mục lục…");

    try {
      allowed(ctx);
      Store s = Store.get(ctx);
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
          if (autoDl || b.id.equals(activeBook)) {
            todo.add(b);
          }
        }
      }

      // If activeBook is in list, move to front
      if (!activeBook.isEmpty()) {
        for (int i = 0; i < todo.size(); i++) {
          if (todo.get(i).id.equals(activeBook)) {
            Store.Book ab = todo.remove(i);
            todo.add(0, ab);
            break;
          }
        }
      }

      while (!cancel.get() && (!todo.isEmpty() || !pendingBook.isEmpty() || pendingAll)) {
        if (pendingAll) {
          pendingAll = false;
          for (Store.Book b : s.books()) {
            if (b.id.equals("demo") || b.dropped) continue;
            boolean autoDl = ctx.getSharedPreferences("settings", 0).getBoolean("auto_dl_" + b.id, false);
            if (autoDl) {
              boolean exists = false;
              for (Store.Book t : todo) if (t.id.equals(b.id)) exists = true;
              if (!exists) todo.add(b);
            }
          }
        }
        if (!pendingBook.isEmpty()) {
          Store.Book p = s.book(pendingBook);
          pendingBook = "";
          if (p != null) {
            todo.removeIf(x -> x.id.equals(p.id));
            todo.add(0, p);
          }
        }
        if (todo.isEmpty() || (charging && !ChargeJob.isCharging(ctx))) break;

        Store.Book b = todo.remove(0);
        if (b == null || b.id == null || b.id.isEmpty() || b.dropped) continue;

        // 1. Refresh catalog FIRST to know real chapter count
        catalog(ctx, b);
        Store.Book refreshed = s.book(b.id);
        if (refreshed != null) b = refreshed;
        List<Store.Chapter> chapters = s.chapters(b.id);
        int current = 0;
        for (Store.Chapter ch : chapters) if (ch.id.equals(b.current)) current = ch.ord;

        boolean isFullMode = (mode == MODE_BOOK_ALL) || s.isKeepFull(b.id);

        // 2. Identify EXACT chapters needed
        List<Store.Chapter> needDownload = new ArrayList<>();
        // Priority: current reading chapter to end of book
        for (Store.Chapter ch : chapters) {
          if (ch.ord >= current && !s.readable(ch.id)) needDownload.add(ch);
        }
        // If full mode: also earlier chapters
        if (isFullMode) {
          for (Store.Chapter ch : chapters) {
            if (ch.ord < current && !s.readable(ch.id)) needDownload.add(ch);
          }
        }

        syncTotal += needDownload.size();
        syncState = "DOWNLOADING";
        if (syncTotal == 0) {
          notify(ctx, b.title + ": Đã tải đủ ✓");
        } else {
          syncPct = Math.min(100, Math.round(syncDone * 100f / syncTotal));
          notify(ctx, "Đang tải " + syncPct + "% (" + syncDone + "/" + syncTotal + ")");
        }

        // 3. Download loop per chapter with preemption
        for (Store.Chapter ch : needDownload) {
          if (cancel.get()) break;
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

          if (s.readable(ch.id)) continue;

          long delay = charging ? 2000 : 3000;
          if (!waitFor(ctx, delay, b.id, charging)) break;

          try {
            download(ctx, ch);
            if (s.readable(ch.id)) {
              syncDone++;
              syncPct = syncTotal > 0 ? Math.min(100, Math.round(syncDone * 100f / syncTotal)) : 100;
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
      }

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
      busy.set(false);
      pendingBook = "";
      pendingAll = false;
      manualOverride = false;
      isSyncing = false;
      if (syncTotal == 0 || syncDone >= syncTotal) {
        syncPct = 100;
        syncState = "COMPLETED";
      }
    }
  }

  private static boolean waitFor(Context c, long delay, String id, boolean charging) throws Exception {
    long end = android.os.SystemClock.elapsedRealtime() + delay;
    while (android.os.SystemClock.elapsedRealtime() < end) {
      if (cancel.get() || !priorityChapterId.isEmpty()) return false;
      if (charging && !ChargeJob.isCharging(c)) return false;
      Thread.sleep(Math.min(500, Math.max(1, end - android.os.SystemClock.elapsedRealtime())));
    }
    allowed(c);
    return true;
  }
}
