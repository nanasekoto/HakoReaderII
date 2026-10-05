package vn.nanase.hako;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  public static class EinkDialog {
    public static AlertDialog show(Context context, String title, String message,
                                    String posLabel, Runnable onPos,
                                    String negLabel, Runnable onNeg) {
      AlertDialog.Builder b = new AlertDialog.Builder(context);
      if (title != null && !title.isEmpty()) b.setTitle(title);
      if (message != null && !message.isEmpty()) b.setMessage(message);
      if (posLabel != null) b.setPositiveButton(posLabel + " (Vol -)", (d, w) -> { if (onPos != null) onPos.run(); });
      if (negLabel != null) b.setNegativeButton(negLabel + " (Vol +)", (d, w) -> { if (onNeg != null) onNeg.run(); });
      AlertDialog dialog = b.create();
      dialog.setOnKeyListener((d, keyCode, event) -> {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            dialog.dismiss();
            if (onPos != null) onPos.run();
            return true;
          } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            dialog.dismiss();
            if (onNeg != null) onNeg.run();
            return true;
          }
        }
        return false;
      });
      dialog.show();
      return dialog;
    }
  }

  private boolean touchLocked = false;
  private BroadcastReceiver powerReceiver;

  private Store store;
  private android.content.SharedPreferences prefs;
  private LinearLayout root, bar, topBar, boundaryPrompt; private int boundaryDir = 0; private boolean pendingOpenAtEnd = false;
  public static final java.util.TimeZone VN_TZ = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
  public static String formatVnDate(long timeMs, String pattern) {
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT);
    sdf.setTimeZone(VN_TZ);
    return sdf.format(new java.util.Date(timeMs));
  }
  private static boolean isSyncingShelf = false;
  private TextView status, title, syncBadge;
  private WebView web;
  private NativeReader nativeReader;
  private ListView currentList;
  private ScrollView currentScroll;
  private final java.util.ArrayDeque<Runnable> navStack = new java.util.ArrayDeque<>();
  private String visitSession="";
  private boolean online = false, readerReady = false;
  private boolean volumeUpPressed = false;
  private long volumeUpPressTime = 0;

  private void toggleTouchLock() {
    touchLocked=!touchLocked;
    prefs.edit().putBoolean("touch_lock",touchLocked).apply();
    if(nativeReader!=null)nativeReader.setTouchLocked(touchLocked);
    hideToolbar(); hideBoundaryPrompt();
    if(lockIndicator!=null)lockIndicator.setVisibility(touchLocked?View.VISIBLE:View.GONE);
  }
  private TextView lockIndicator;
  private volatile String bookId = "", chapterId = "";
  private int generation = 0;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private BroadcastReceiver updates;
  private volatile int lastPos = 0;
  private volatile float lastFraction = 0;
  private final int INK = Color.rgb(25, 25, 25), MUTED = Color.rgb(80, 80, 80);

  public void onCreate(Bundle state) {
    super.onCreate(state); java.util.TimeZone.setDefault(VN_TZ);
    store = Store.get(this);
    prefs = getSharedPreferences("settings", 0);
    touchLocked=prefs.getBoolean("touch_lock",false);
    if(!prefs.getBoolean("migrated_font_v3",false)){prefs.edit().putInt("font2",2).putBoolean("migrated_font_v3",true).apply();}
    CookieManager.getInstance().setAcceptCookie(true);
    updates =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            String text = i.getStringExtra("text");
            if (status != null) status.setText(text);
            if (syncBadge != null) {
              syncBadge.setText(Repository.isSyncing ? ("⤓ " + Repository.syncPct + "%") : (Repository.syncPct >= 100 ? "✓ 100%" : (Repository.syncPct + "%")));
            }
            if (nativeReader != null) {
              nativeReader.setSyncStatus(Repository.isSyncing, Repository.syncPct);
            }
          }
        };
    registerReceiver(updates, new IntentFilter(Repository.EVENT));
    ChargeJob.schedule(this, prefs.getBoolean("charging", true));
    HakoParser.ORIGIN=prefs.getString("origin","https://docln.sbs");
    Repository.init(this);
    io.execute(()->store.cleanStartup());
    library();
  }

  private int dp(int x) {
    return (int) (getResources().getDisplayMetrics().density * x + .5f);
  }

  private int dp(float x) {
    return (int) (getResources().getDisplayMetrics().density * x + .5f);
  }

  private TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextColor(INK);
    t.setTextSize(size);
    t.setPadding(dp(12), dp(8), dp(12), dp(8));
    return t;
  }

  private Button button(String label, Runnable r) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(Color.BLACK);
    b.setTextSize(12f);
    b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setAllCaps(false);
    b.setMinHeight(dp(30));
    b.setMinimumWidth(0);
    b.setPadding(dp(8), 0, dp(8), 0);
    android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
    btnBg.setColor(Color.WHITE);
    btnBg.setStroke(dp(1.2f), Color.BLACK);
    btnBg.setCornerRadius(dp(6));
    b.setBackground(btnBg);
    b.setStateListAnimator(null);
    b.setOnClickListener(v -> r.run());
    return b;
  }

  private void row(String[] labels, Runnable[] actions) {
    LinearLayout l = new LinearLayout(this);
    for (int n = 0; n < labels.length; n++)
      l.addView(button(labels[n], actions[n]), new LinearLayout.LayoutParams(0, dp(46), 1));
    root.addView(l);
  }

  private void reset() {
    generation++;
    caughtUpPrompt=false; caughtUpBook=null;
    cancelLockKey();
    currentList=null; currentScroll=null; status=null;
    nativeReader=null;
    readerReady = false;
    if (web != null) {
      web.stopLoading();
      web.removeJavascriptInterface("Bridge");
      web.destroy();
      web = null;
    }
    online = false;
    topBar = null; bar = null; boundaryPrompt = null; boundaryDir = 0; syncBadge = null;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);
    FrameLayout shell=new FrameLayout(this);
    shell.addView(root,new FrameLayout.LayoutParams(-1,-1));
    lockIndicator=new TextView(this);
    lockIndicator.setText("KHÓA · Giữ Vol+ để mở");lockIndicator.setTextSize(12);
    lockIndicator.setTextColor(Color.WHITE);lockIndicator.setBackgroundColor(Color.BLACK);
    lockIndicator.setPadding(dp(6),dp(2),dp(6),dp(2));
    FrameLayout.LayoutParams lockLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
    shell.addView(lockIndicator,lockLp);
    lockIndicator.setVisibility(touchLocked?View.VISIBLE:View.GONE);
    setContentView(shell);
  }

  private void message(String s) {
    if (isFinishing()) return;
    new AlertDialog.Builder(this)
        .setTitle("Hako Pocket")
        .setMessage(s)
        .setPositiveButton("Đóng", null)
        .show();
  }

  private void status() {
    LinearLayout bottomBar = new LinearLayout(this);
    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setGravity(Gravity.CENTER_VERTICAL);
    bottomBar.setPadding(dp(12), dp(4), dp(12), dp(4));

    status = text(Repository.status, 11);
    status.setTextColor(MUTED);
    status.setMaxLines(1);
    status.setPadding(0, 0, 0, 0);
    bottomBar.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));

    syncBadge = new TextView(this);
    syncBadge.setText(Repository.isSyncing ? ("⤓ " + Repository.syncPct + "%") : (Repository.syncPct >= 100 ? "✓ 100%" : (Repository.syncPct + "%")));
    syncBadge.setTextSize(11);
    syncBadge.setTypeface(Typeface.DEFAULT_BOLD);
    syncBadge.setTextColor(Color.BLACK);
    android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
    pillBg.setColor(Color.WHITE);
    pillBg.setStroke(dp(1), Color.BLACK);
    pillBg.setCornerRadius(dp(4));
    syncBadge.setBackground(pillBg);
    syncBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
    bottomBar.addView(syncBadge, new LinearLayout.LayoutParams(-2, -2));

    root.addView(bottomBar);
  }

  private void task(String label, Callable<?> work, Runnable done) {
    int token = generation;
    if (status != null) status.setText(label);
    io.execute(
        () -> {
          try {
            work.call();
            runOnUiThread(
                () -> {
                  if (isFinishing() || token != generation) return;
                  if (done != null) done.run();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isFinishing() || token != generation) return;
                  if (status != null) status.setText("Chưa hoàn tất");
                  message(e.getMessage() == null ? e.toString() : e.getMessage());
                });
          }
        });
  }

  private void leaveReader(){visitSession="";String old=Repository.activeBook;Repository.activeBook="";if(!old.isEmpty())store.clearTemporary(old);}
  private void exitApp(){isSyncingShelf=false;saveThen(()->{leaveReader();Repository.cancel.set(true);stopService(new Intent(this,DownloadService.class));finishAndRemoveTask();});}
  private void library() {
    navStack.clear();
    leaveReader();reset();bookId="";chapterId="";
    root.addView(text("HAKO POCKET",20));
    root.addView(text("Đọc nhẹ • Xteink S4 • v0.5.2",11));
    LinearLayout grid=new LinearLayout(this);
    grid.setOrientation(1);
    String[] names={
      "Đọc tiếp","Vừa đọc","Tủ sách",
      "Mới cập nhật","Yêu thích","Tìm kiếm",
      "Cài đặt","Tài khoản","Đổi tên miền",
      "Đọc mẫu","Cập nhật","Thoát"
    };
    int[] iconKinds={12,7,2,8,9,10,13,11,14,3,8,6};
    Runnable[] actions={
      ()->{Store.Book b=store.book(prefs.getString("lastBook",""));if(b!=null){navStack.push(this::library);openBook(b);}else bookList(false);},
      ()->{navStack.push(this::library);bookList(false);},
      ()->{navStack.push(this::library);bookList(true);},
      ()->{navStack.push(this::library);onlineList("Mới cập nhật",HakoParser.ORIGIN+"/danh-sach?truyendich=1&sapxep=capnhat");},
      ()->{navStack.push(this::library);frequentList();},
      this::searchDialog,
      this::settings,
      ()->browse(HakoParser.ORIGIN+"/login"),
      this::domain,
      this::demo,
      ()->{Repository.cancel.set(false);startDownloads("",Repository.MODE_SYNC_LIBRARY);},
      this::exitApp
    };
    int cols=3;
    for(int n=0;n<names.length;n+=cols){
      LinearLayout line=new LinearLayout(this);
      line.setOrientation(0);
      for(int j=n;j<n+cols&&j<names.length;j++){
        final int x=j;
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(1);cell.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable cellBg = new android.graphics.drawable.GradientDrawable();
        cellBg.setColor(Color.WHITE);
        cellBg.setStroke(dp(1.5f), Color.BLACK);
        cellBg.setCornerRadius(dp(8));
        cell.setBackground(cellBg);
        cell.setPadding(dp(2), dp(6), dp(2), dp(6));

        IconButton icon=new IconButton(this,iconKinds[j],names[j],false,actions[j]);
        cell.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView label=new TextView(this);
        label.setText(names[j]);
        label.setTextSize(13.5f);
        label.setTextColor(Color.BLACK);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0,dp(2),0,0);
        cell.addView(label);
        cell.setOnClickListener(v->actions[x].run());
        LinearLayout.LayoutParams lpCell = new LinearLayout.LayoutParams(0,-1,1f);
        lpCell.setMargins(dp(3),dp(3),dp(3),dp(3));
        line.addView(cell,lpCell);
      }
      grid.addView(line,new LinearLayout.LayoutParams(-1,0,1f));
    }
    root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
    status();
  }
    private void onlineList(String title, String url) {
    leaveReader();reset();bookId="";chapterId="";
    String[] categories={"Truyện Dịch","Convert","Sáng Tác","Mới Đăng","Hoàn Thành","Nổi Bật"};
    String[] queries={"truyendich=1&sapxep=capnhat","convert=1&sapxep=capnhat",
        "sangtac=1&sapxep=capnhat","truyendich=1&sapxep=truyenmoi",
        "hoanthanh=1&sapxep=capnhat","sapxep=top"};
    LinearLayout header=new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(new IconButton(this,4,"Quay lại",this::goBack),new LinearLayout.LayoutParams(dp(42),dp(42)));
    TextView heading=new TextView(this);
    heading.setText(title); heading.setTextSize(17); heading.setTypeface(Typeface.DEFAULT_BOLD);
    heading.setTextColor(INK); heading.setSingleLine(true);
    heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.setOnLongClickListener(v->{message(title);return true;});
    header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
    header.addView(new IconButton(this,10,"Tìm kiếm",this::searchDialog),new LinearLayout.LayoutParams(dp(42),dp(42)));
    header.addView(new IconButton(this,13,"Bộ lọc",()->new AlertDialog.Builder(this)
        .setTitle("Chọn danh sách").setItems(categories,(d,index)->onlineList(categories[index],
          HakoParser.ORIGIN+"/danh-sach?"+queries[index])).setNegativeButton("Đóng",null).show()),
        new LinearLayout.LayoutParams(dp(42),dp(42)));
    header.addView(new IconButton(this,14,"Mở web",()->browse(url)),new LinearLayout.LayoutParams(dp(42),dp(42)));
    root.addView(header);
    final PagedBookList list = new PagedBookList(this);
    currentList = list;
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    status();
    final List<HakoParser.Link> stories = new ArrayList<>();
    task("Đang tải danh sách…", () -> {
      String html = Repository.page(url, false);
      stories.addAll(HakoParser.storyList(html, url));
      return null;
    }, () -> {
      if (stories.isEmpty()) {
        status.setText("Không tìm thấy truyện. Chọn Mở web để kiểm tra.");
        return;
      }


      list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
      list.setDrawSelectorOnTop(false);
      list.setDivider(null);
      list.setDividerHeight(dp(5));
      list.setPadding(dp(6),dp(3),dp(6),dp(3));
      list.setClipToPadding(true);
      list.setAdapter(new ArrayAdapter<HakoParser.Link>(this, 0, stories) {
        public View getView(int p, View convert, ViewGroup parent) {
          HakoParser.Link item = getItem(p);
          LinearLayout card = new LinearLayout(MainActivity.this);
          card.setOrientation(LinearLayout.VERTICAL);
          card.setGravity(Gravity.CENTER_VERTICAL);
          card.setLayoutParams(new AbsListView.LayoutParams(-1, list.rowHeight(p)));
          card.setMinimumHeight(dp(52));

          android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
          cardBg.setColor(Color.WHITE);
          cardBg.setStroke(dp(1.8f), Color.BLACK);
          cardBg.setCornerRadius(dp(6));
          card.setBackground(cardBg);
          card.setPadding(dp(10), dp(2), dp(10), dp(2));

          TextView titleView = new TextView(MainActivity.this);
          titleView.setText(item.title);
          titleView.setTextSize(17f);
          titleView.setTypeface(Typeface.DEFAULT_BOLD);
          titleView.setTextColor(Color.BLACK);
          titleView.setMaxLines(2);
          titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
          card.addView(titleView);

          if (!item.info.isEmpty()) {
            TextView infoView = new TextView(MainActivity.this);
            infoView.setText(item.info);
            infoView.setTextSize(12f);
            infoView.setTextColor(MUTED);
            infoView.setMaxLines(1);
            infoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            infoView.setPadding(0, dp(2), 0, 0);
            card.addView(infoView);
          }
          return card;
        }
      });
      status.setText(stories.size()+" truyện");
      list.setOnItemLongClickListener((a,v,p,id)->{
        HakoParser.Link item=stories.get(p);
        message(item.title+(item.info.isEmpty()?"":"\n\n"+item.info));return true;
      });
      list.setOnItemClickListener((a, v, p, id) -> {
        navStack.push(() -> onlineList(title, url));
        addUrl(stories.get(p).url);
      });

    });
  }

  private void searchDialog() {
    EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Nhập tên truyện hoặc tác giả…");
    new AlertDialog.Builder(this).setTitle("Tìm kiếm HAKO").setView(field).setPositiveButton("Tìm",(d,w)->{
      String q=field.getText().toString().trim();
      if(!q.isEmpty()){try{String u=HakoParser.ORIGIN+"/tim-kiem?keywords="+java.net.URLEncoder.encode(q,"UTF-8");navStack.push(this::library);onlineList("Tìm: "+q,u);}catch(Exception ignored){}}
    }).setNegativeButton("Hủy",null).show();
  }

  private void bookList(boolean shelf){
    leaveReader();reset();bookId="";chapterId="";
    LinearLayout topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.VERTICAL);
    topBar.setPadding(dp(10), dp(3), dp(10), dp(2));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView headerTitle = new TextView(this);
    headerTitle.setText(shelf ? "Tủ sách" : "Vừa đọc");
    headerTitle.setTextSize(19f);
    headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
    headerTitle.setTextColor(INK);
    headerTitle.setSingleLine(true);
    header.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

    header.addView(new IconButton(this,0,"Trang chính",this::library), new LinearLayout.LayoutParams(dp(40), dp(38)));

    final TextView syncStatus = new TextView(this);
    IconButton btnSync = new IconButton(this,shelf?8:7,shelf ? "Cập nhật" : "Lịch sử", () -> {
      if (shelf) {
        if (isSyncingShelf) return;
        isSyncingShelf = true;
        syncStatus.setText("Đang cập nhật tủ sách từ HAKO…");
        io.execute(() -> {
          try {
            Repository.importShelf(this);
            prefs.edit().putLong("lastShelfSync", System.currentTimeMillis()).apply();
            isSyncingShelf = false;
            runOnUiThread(() -> {
              if (isFinishing()) return;
              bookList(true);
            });
          } catch (Exception e) {
            isSyncingShelf = false;
            runOnUiThread(() -> {
              if (isFinishing()) return;
              syncStatus.setText("Lỗi cập nhật: " + (e.getMessage() != null ? e.getMessage() : "Mất mạng"));
            });
          }
        });
      } else {
        browse(HakoParser.ORIGIN + "/lich-su-doc");
      }
    });
    LinearLayout.LayoutParams lpSync = new LinearLayout.LayoutParams(dp(40), dp(38));
    lpSync.setMargins(dp(4), 0, 0, 0);
    header.addView(btnSync, lpSync);

    Button btnMore = button("⋯", () -> {
      new AlertDialog.Builder(this).setItems(new String[]{
        "Dừng tải", "HAKO / Tủ sách",
        prefs.getBoolean("unreadOnly", false) ? "Hiện tất cả truyện" : "Chỉ bộ còn chương mới",
        "Thêm link truyện (dán URL)"
      }, (d, w) -> {
        if (w == 0) Repository.cancel.set(true);
        if (w == 1) browse(HakoParser.ORIGIN + "/ke-sach");
        if (w == 2) {
          prefs.edit().putBoolean("unreadOnly", !prefs.getBoolean("unreadOnly", false)).apply();
          bookList(true);
        }
        if (w == 3) addDialog();
      }).show();
    });
    LinearLayout.LayoutParams lpMore = new LinearLayout.LayoutParams(-2, dp(30));
    lpMore.setMargins(dp(4), 0, 0, 0);
    header.addView(btnMore, lpMore);
    topBar.addView(header);

    String statusStr;
    if (shelf) {
      if (isSyncingShelf) {
        statusStr = "Đang cập nhật tủ sách từ HAKO…";
      } else {
        long lastSync = prefs.getLong("lastShelfSync", 0);
        int totalFollowed = 0;
        for (Store.Book b : store.books()) if (b.followed) totalFollowed++;
        if (lastSync > 0) {
          statusStr = "Đã cập nhật lúc " + formatVnDate(lastSync, "HH:mm dd/MM/yyyy") + (totalFollowed > 0 ? " · " + totalFollowed + " bộ" : "");
        } else {
          statusStr = "Chưa cập nhật tủ sách" + (totalFollowed > 0 ? " · " + totalFollowed + " bộ" : "");
        }
      }
    } else {
      int readCount = 0;
      for (Store.Book b : store.books()) if (b.stamp > 0) readCount++;
      statusStr = "Lịch sử đọc trên máy · " + readCount + " bộ";
    }
    syncStatus.setText(statusStr);
    syncStatus.setTextSize(11f);
    syncStatus.setTextColor(MUTED);
    syncStatus.setSingleLine(true);
    syncStatus.setPadding(0, dp(1), 0, 0);
    topBar.addView(syncStatus);

    root.addView(topBar);

    List<Store.Book> books = new ArrayList<>();
    for (Store.Book b : store.books()) if (shelf ? (b.followed && (!prefs.getBoolean("unreadOnly", false) || hasNew(b))) : b.stamp > 0) books.add(b);
    if (shelf) Collections.sort(books, (a, b) -> Integer.compare(a.shelfRank, b.shelfRank));

    PagedBookList list = new PagedBookList(this);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(null);
    list.setDividerHeight(dp(3));
    list.setPadding(dp(6), dp(2), dp(6), dp(2));
    list.setClipToPadding(true);



    list.setAdapter(new ArrayAdapter<Store.Book>(this, 0, books) {
      public View getView(int p, View convert, ViewGroup parent) {
        Store.Book b = getItem(p);
        LinearLayout card = new LinearLayout(MainActivity.this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutParams(new AbsListView.LayoutParams(-1, list.rowHeight(p)));

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1.5f), Color.BLACK);
        cardBg.setCornerRadius(dp(4));
        card.setBackground(cardBg);
        card.setPadding(dp(8), dp(2.5f), dp(8), dp(2.5f));

        LinearLayout top = new LinearLayout(MainActivity.this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setText(b.title);
        titleView.setTextSize(17f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.BLACK);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        int newCount = newChapterCount(b);
        if (newCount > 0) {
          TextView badge = new TextView(MainActivity.this);
          badge.setText("+" + newCount + " mới");
          badge.setTextSize(10f);
          badge.setTextColor(Color.WHITE);
          badge.setBackgroundColor(Color.BLACK);
          badge.setTypeface(Typeface.DEFAULT_BOLD);
          badge.setPadding(dp(4), dp(1), dp(4), dp(1));
          LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
          lpBadge.setMargins(dp(5), 0, 0, 0);
          top.addView(badge, lpBadge);
        }
        if (!shelf) {
          top.addView(new IconButton(MainActivity.this,18,"Tải toàn bộ: "+b.title,
              ()->startDownloads(b.id,Repository.MODE_BOOK_ALL)),
              new LinearLayout.LayoutParams(dp(42),dp(42)));
        }
        card.addView(top);

        List<Store.Chapter> chs = store.chapters(b.id);
        Store.Chapter ch = store.chapter(b.id, b.current);
        int unread = ch == null ? -1 : Math.max(0, chs.size() - ch.ord - 1);

        StringBuilder sub = new StringBuilder();
        if (b.stamp > 0 && ch != null) {
          sub.append("Đang đọc: ").append(ch.title);
          if (unread > 0) sub.append(" · còn ").append(unread).append(" ch");
          sub.append(" · ").append(formatVnDate(b.stamp, "dd/MM"));
        } else if (!chs.isEmpty()) {
          sub.append(chs.size()).append(" chương · Chưa đọc");
        } else if (!b.shelfInfo.isEmpty()) {
          sub.append(b.shelfInfo);
        } else {
          sub.append("Chưa mở đọc · Chạm để xem mục lục");
        }
        if (b.dropped) {
          sub.append(" · Tạm ngưng");
        }

        TextView subView = new TextView(MainActivity.this);
        subView.setText(sub.toString());
        subView.setTextSize(12f);
        subView.setTextColor(MUTED);
        subView.setMaxLines(1);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView.setPadding(0, dp(1), 0, 0);
        card.addView(subView);

        return card;
      }
    });
    list.setOnItemClickListener((a, v, p, id) -> { navStack.push(() -> bookList(shelf)); openBook(books.get(p)); });
    list.setOnItemLongClickListener((a, v, p, id) -> { bookMenu(books.get(p)); return true; });
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    if (books.isEmpty()) root.addView(text(shelf ? "Bấm Cập nhật sau khi đăng nhập HAKO." : "Chưa có lịch sử đọc trong APK.", 14));
  }
  private boolean hasNew(Store.Book b){try{return Integer.parseInt(b.shelfInfo.split(" ")[0])>0;}catch(Exception e){return false;}}
  private void frequentList(){
    leaveReader();reset();bookId="";chapterId="";
    LinearLayout topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.VERTICAL);
    topBar.setPadding(dp(10), dp(3), dp(10), dp(2));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView headerTitle = new TextView(this);
    headerTitle.setText("Yêu thích");
    headerTitle.setTextSize(19f);
    headerTitle.setTypeface(Typeface.DEFAULT_BOLD);
    headerTitle.setTextColor(INK);
    headerTitle.setSingleLine(true);
    header.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

    header.addView(new IconButton(this,0,"Trang chính",this::library), new LinearLayout.LayoutParams(dp(40), dp(38)));
    topBar.addView(header);

    List<Store.Book> books = new ArrayList<>();
    for (Store.Book b : store.books()) if (b.visits > 0 || b.pinned || store.isKeepFull(b.id)) books.add(b);
    Collections.sort(books, (a, b) -> {
      if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
      boolean af=store.isKeepFull(a.id),bf=store.isKeepFull(b.id);
      if(af!=bf)return af?-1:1;
      if(af){int fullOrder=Long.compare(prefs.getLong("full_priority_"+b.id,0),prefs.getLong("full_priority_"+a.id,0));if(fullOrder!=0)return fullOrder;}
      int n = Integer.compare(b.visits, a.visits);
      return n != 0 ? n : Long.compare(b.stamp, a.stamp);
    });

    TextView infoView = new TextView(this);
    infoView.setText("Tải full · Theo dõi đặc biệt · Đọc nhiều · " + books.size() + " bộ");
    infoView.setTextSize(11f);
    infoView.setTextColor(MUTED);
    infoView.setSingleLine(true);
            infoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    infoView.setPadding(0, dp(1), 0, 0);
    topBar.addView(infoView);

    root.addView(topBar);

    PagedBookList list = new PagedBookList(this);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(null);
    list.setDividerHeight(dp(3));
    list.setPadding(dp(6), dp(2), dp(6), dp(2));
    list.setClipToPadding(true);



    list.setAdapter(new ArrayAdapter<Store.Book>(this, 0, books) {
      public View getView(int p, View convert, ViewGroup parent) {
        Store.Book b = getItem(p);
        LinearLayout card = new LinearLayout(MainActivity.this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutParams(new AbsListView.LayoutParams(-1, list.rowHeight(p)));

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1.5f), Color.BLACK);
        cardBg.setCornerRadius(dp(4));
        card.setBackground(cardBg);
        card.setPadding(dp(8), dp(2.5f), dp(8), dp(2.5f));

        LinearLayout top = new LinearLayout(MainActivity.this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setText((b.pinned ? "★ " : store.isKeepFull(b.id) ? "↓ " : "") + b.title);
        titleView.setTextSize(17f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.BLACK);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        List<Store.Chapter> chs = store.chapters(b.id);
        int totalChs = chs.size();
        int currentOrd = 0;
        if (!b.current.isEmpty()) {
          for (int i = 0; i < chs.size(); i++) {
            if (chs.get(i).id.equals(b.current)) { currentOrd = chs.get(i).ord; break; }
          }
        } else {
          for (int i = 0; i < chs.size(); i++) {
            if (store.wasRead(chs.get(i).id)) currentOrd = i;
          }
        }

        boolean fullMode=store.isKeepFull(b.id);
        if(fullMode)currentOrd=0;
        int oldChs = Math.max(0, currentOrd);
        int newChsTotal = Math.max(0, totalChs - currentOrd);
        int newChsDownloaded = 0;
        for (int i = currentOrd; i < totalChs; i++) {
          if (store.readable(chs.get(i).id)) newChsDownloaded++;
        }

        int downloadPct = 100;
        if (totalChs > 0) {
          downloadPct = Math.min(100, Math.round((oldChs + newChsDownloaded) * 100f / totalChs));
        }

        TextView badge = new TextView(MainActivity.this);
        if (totalChs == 0) {
          badge.setText("CHƯA TẢI");
          badge.setBackgroundColor(Color.GRAY);
        } else if (newChsTotal == 0 || newChsDownloaded >= newChsTotal) {
          badge.setText(fullMode?"FULL 100%":"✓ 100%");
          badge.setBackgroundColor(Color.BLACK);
        } else {
          badge.setText((fullMode?"FULL ":"TẢI ") + downloadPct + "%");
          badge.setBackgroundColor(Color.BLACK);
        }
        badge.setTextSize(10f);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(4), dp(1), dp(4), dp(1));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
        lpBadge.setMargins(dp(5), 0, 0, 0);
        top.addView(badge, lpBadge);
        card.addView(top);

        StringBuilder sub = new StringBuilder();
        if (totalChs == 0) {
          sub.append("Chưa có mục lục · Cần bật Wi-Fi để cập nhật");
        } else if (newChsTotal == 0 || newChsDownloaded >= newChsTotal) {
          sub.append(fullMode?"Đã lưu đủ ":"Bắt kịp ").append(totalChs).append(" chương · Offline");
        } else {
          int unread = newChsTotal - newChsDownloaded;
          sub.append("Đã lưu ").append(newChsDownloaded).append("/").append(newChsTotal).append(fullMode?" chương":" ch tiếp theo").append(" · còn ").append(unread);
        }
        if (b.stamp > 0) {
          sub.append(" · ").append(formatVnDate(b.stamp, "dd/MM"));
        }

        TextView subView = new TextView(MainActivity.this);
        subView.setText(sub.toString());
        subView.setTextSize(12f);
        subView.setTextColor(MUTED);
        subView.setMaxLines(1);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView.setPadding(0, dp(1), 0, 0);
        card.addView(subView);

        return card;
      }
    });
    list.setOnItemClickListener((a, v, i, id) -> { navStack.push(this::frequentList); openBookContents(books.get(i)); });
    list.setOnItemLongClickListener((a, v, i, id) -> { bookMenu(books.get(i)); return true; });
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
  }
  private void saveBook(){Store.Book b=store.book(bookId);if(b==null)return;if(b.followed){message("Bộ này đã trong tủ sách HAKO.");return;}Repository.cancel.set(false);task("Đang lưu lên HAKO…",()->{RenderedPage.action(this,b.url,"follow");store.followed(b.id,true,b.shelfRank);return null;},()->Toast.makeText(this,"Đã lưu vào tủ sách HAKO",Toast.LENGTH_SHORT).show());}
  private boolean caughtUpPrompt=false;
  private volatile boolean caughtUpUpdating=false;
  private Store.Book caughtUpBook;

  private void markCaughtUp(Store.Book b){
    if(b==null || caughtUpUpdating || caughtUpPrompt)return;
    if(nativeReader==null){
      EinkDialog.show(this,"Đã bắt kịp: "+b.title,
          "Cập nhật đã đọc hết lên HAKO?","Cập nhật",()->submitCaughtUp(b),"Để sau",null);
      return;
    }
    showBoundaryPrompt(1);
    caughtUpPrompt=true; caughtUpBook=b;
    // Reuse the reader overlay so hardware lock remains available even during confirmation.
    View card=boundaryPrompt.getChildAt(0);
    if(card instanceof ViewGroup){
      ViewGroup group=(ViewGroup)card;
      ((TextView)group.getChildAt(0)).setText("Đã đọc đến chương mới nhất");
      ((TextView)group.getChildAt(1)).setText("Cập nhật đã đọc hết lên HAKO?\nVol−: cập nhật · Vol+: để sau");
      ViewGroup buttons=(ViewGroup)group.getChildAt(2);
      ((Button)buttons.getChildAt(0)).setText("Để sau (Vol+)");
      Button confirm=(Button)buttons.getChildAt(1);
      confirm.setText("Cập nhật (Vol−)");
      confirm.setOnClickListener(v->{hideBoundaryPrompt();submitCaughtUp(b);});
    }
  }

  private void submitCaughtUp(Store.Book b){
    if(caughtUpUpdating)return;
    caughtUpUpdating=true; Repository.cancel.set(false);
    task("Đánh dấu trên HAKO…",()->{
      try{
        RenderedPage.action(this,HakoParser.ORIGIN+"/ke-sach",
            "read:"+b.id.substring(b.id.lastIndexOf('-')+1));
        store.shelfInfo(b.id,"Không có chương mới tại lần đánh dấu vừa rồi");
      }finally{caughtUpUpdating=false;}
      return null;
    },()->{
      if(status!=null)status.setText("Đã cập nhật đã đọc lên HAKO");
      if(nativeReader!=null)nativeReader.setNotice("Đã cập nhật HAKO");
    });
  }

  private void readerMenu(){new AlertDialog.Builder(this).setItems(new String[]{"Chương trước","Chương tiếp","Đánh dấu đoạn","Ẩn thanh công cụ","Tải trước / tiếp tục","Dừng tải","Mở HAKO","Chạm lật trang: "+(prefs.getBoolean("taps",true)?"Bật":"Tắt"),"Đầu chương","Cuối chương","Đã bắt kịp trên HAKO","Tải lại chương này"},(d,w)->{
    if(w==0)saveThen(()->adjacent(-1));if(w==1)saveThen(()->adjacent(1));if(w==2)bookmarks();if(w==3){bar.setVisibility(View.GONE);Toast.makeText(this,"Vuốt dọc rồi chạm để hiện công cụ",Toast.LENGTH_SHORT).show();}if(w==4)startDownloads(bookId,Repository.MODE_BOOK_NEXT);if(w==5)Repository.cancel.set(true);if(w==6){Store.Chapter ch=store.chapter(bookId,chapterId);saveThen(()->browse(ch.url));}if(w==7){boolean t=!prefs.getBoolean("taps",true);prefs.edit().putBoolean("taps",t).apply();if(nativeReader!=null)nativeReader.taps(t);}if(w==8&&nativeReader!=null)nativeReader.jump(false);if(w==9&&nativeReader!=null)nativeReader.jump(true);if(w==10)markCaughtUp(store.book(bookId));if(w==11){final String bid=bookId,cid=chapterId;store.state(cid,false,"");task("Tải lại chương…",()->{Repository.cancel.set(false);Repository.download(this,store.chapter(bid,cid));return null;},()->showReader(bid,cid));}}).show();}
  private void confirmBulk() {
    EinkDialog.show(
        this,
        "Đồng bộ thư viện",
        "Tải phạm vi offline của các truyện đã có trong thư viện, kể cả khi không sạc. Bạn có thể dừng bất cứ lúc nào.",
        "Tải ngay",
        () -> startDownloads("", Repository.MODE_SYNC_LIBRARY),
        "Hủy",
        null);
  }

  private void startDownloads(String id, int mode) {
    if(mode==Repository.MODE_BOOK_ALL && !id.isEmpty()) {
      store.setKeepFull(id,true);
      prefs.edit().putLong("full_priority_"+id,System.currentTimeMillis()).apply();
    }
    Intent i = new Intent(this, DownloadService.class).putExtra("book", id).putExtra("mode", mode);
    startForegroundService(i);
  }

  private void bookMenu(Store.Book b) {
    boolean autoDl = prefs.getBoolean("auto_dl_" + b.id, false);
    new AlertDialog.Builder(this)
        .setTitle(b.title)
        .setItems(
            new String[] {
              "Mục lục / chọn chương",
              "⬇ Tải tất cả chương (bộ này)",
              autoDl ? "⚡ Tự động tải khi sạc: ĐANG BẬT" : "⚡ Tự động tải khi sạc: ĐANG TẮT",
              "Tải lại mục lục",
              "Mở trên HAKO",
              b.dropped ? "Tiếp tục theo dõi" : "Tạm ngưng / drop",
              b.pinned ? "Bỏ theo dõi đặc biệt" : "Theo dõi đặc biệt / yêu thích",
              "Đã bắt kịp trên HAKO"
            },
            (d, w) -> {
              if (w == 0) contents(b);
              if (w == 1) {
                startDownloads(b.id, Repository.MODE_BOOK_ALL);
                Toast.makeText(this, "Bắt đầu tải tất cả chương: " + b.title, Toast.LENGTH_SHORT).show();
              }
              if (w == 2) {
                boolean next = !autoDl;
                prefs.edit().putBoolean("auto_dl_" + b.id, next).apply();
                Toast.makeText(this, next ? "Đã BẬT tự động tải khi sạc cho bộ này" : "Đã TẮT tự động tải khi sạc cho bộ này", Toast.LENGTH_SHORT).show();
              }
              if (w == 3)
                task(
                    "Cập nhật mục lục…",
                    () -> {
                      prefs.edit().remove("catalog-"+b.id).apply();
                      Repository.catalog(this, b);
                      return null;
                    },
                    () -> contents(b));
              if (w == 4) browse(b.url);
              if (w == 5) { store.dropped(b.id, !b.dropped); bookList(b.followed); }
              if (w == 6) { store.pin(b.id, !b.pinned); frequentList(); }
              if (w == 7) markCaughtUp(b);
            })
        .show();
  }

  private void addDialog() {
    EditText field = new EditText(this);
    field.setSingleLine(true);
    field.setHint("https://docln.sbs/truyen/…");
    field.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    new AlertDialog.Builder(this)
        .setTitle("Thêm truyện hoặc chương")
        .setView(field)
        .setPositiveButton("Thêm", (d, w) -> addUrl(field.getText().toString().trim()))
        .setNegativeButton("Hủy", null)
        .show();
  }

  private void addUrl(String u) {
    task(
        "Đang lấy mục lục…",
        () -> {
          Repository.add(this, u);
          return null;
        },
        () -> {
          Store.Book b = store.book(HakoParser.storyId(HakoParser.storyUrl(u)));
          if (b != null) openBook(b);
        });
  }

  private int newChapterCount(Store.Book b) {
    if (b == null || b.shelfInfo == null) return 0;
    try {
      return Integer.parseInt(b.shelfInfo.trim().split("\\s+")[0]);
    } catch (Exception e) {
      return 0;
    }
  }

  private int resolveTargetIndex(Store.Book b, List<Store.Chapter> chs) {
    if (chs.isEmpty()) return 0;
    if (!b.current.isEmpty()) {
      for (int i = 0; i < chs.size(); i++) {
        if (chs.get(i).id.equals(b.current)) return i;
      }
    }
    int newCount = newChapterCount(b);
    if (newCount > 0 && newCount <= chs.size()) {
      return chs.size() - newCount;
    }
    int lastRead = -1;
    for (int i = 0; i < chs.size(); i++) {
      if (store.wasRead(chs.get(i).id)) lastRead = i;
    }
    if (lastRead >= 0) {
      return Math.min(lastRead + 1, chs.size() - 1);
    }
    return 0;
  }

  private void openBookContents(Store.Book b){
    if(b==null)return;
    if(store.chapters(b.id).isEmpty())task("Đang lấy mục lục…",()->{Repository.catalog(this,b);return null;},()->contents(store.book(b.id)));
    else contents(b);
  }

  private void chapterHeader(Store.Book b,Store.Chapter target,String section,String count){
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(new IconButton(this,4,"Quay lại",this::goBack),new LinearLayout.LayoutParams(dp(40),dp(40)));
    TextView name=new TextView(this);name.setText(b.title);name.setTextColor(INK);
    name.setTextSize(18);name.setTypeface(Typeface.DEFAULT_BOLD);name.setMaxLines(2);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    header.addView(new IconButton(this,12,"Đọc tiếp",()->openChapter(b.id,target.id)),new LinearLayout.LayoutParams(dp(40),dp(40)));
    header.addView(new IconButton(this,18,"Tải toàn bộ",()->startDownloads(b.id,Repository.MODE_BOOK_ALL)),new LinearLayout.LayoutParams(dp(40),dp(40)));
    header.addView(new IconButton(this,15,"Menu truyện",()->bookMenu(b)),new LinearLayout.LayoutParams(dp(40),dp(40)));
    root.addView(header);
    TextView info=new TextView(this);info.setText(section+" · "+count);info.setTextSize(12);info.setTextColor(MUTED);
    info.setSingleLine(true);info.setEllipsize(android.text.TextUtils.TruncateAt.END);info.setPadding(dp(8),dp(2),dp(8),dp(4));root.addView(info);
  }

  private void openBook(Store.Book b) {
    if (store.chapters(b.id).isEmpty()) {
      task(
          "Đang lấy mục lục…",
          () -> {
            Repository.catalog(this, b);
            return null;
          },
          () -> {
            Store.Book loaded = store.book(b.id);
            openBookDirect(loaded);
          });
    } else {
      openBookDirect(b);
    }
  }

  private void openBookDirect(Store.Book b) {
    List<Store.Chapter> chs = store.chapters(b.id);
    if (chs.isEmpty()) {
      contents(b);
      return;
    }
    int targetIdx = resolveTargetIndex(b, chs);
    Store.Chapter target = chs.get(targetIdx);
    if (store.readable(target.id)) {
      navStack.push(() -> contents(b));
      openChapter(b.id, target.id);
    } else {
      // If chapter not yet downloaded, enter TOC immediately and prioritize downloading target chapter
      contents(b);
      task("Đang tải " + target.title + "…", () -> {
        Repository.download(this, target);
        return null;
      }, () -> {
        contents(store.book(b.id));
      });
    }
  }

  private static class VolumeGroup {
    String name;
    List<Store.Chapter> chapters = new ArrayList<>();
    List<String> cleanTitles = new ArrayList<>();
  }

  private List<VolumeGroup> groupChapters(List<Store.Chapter> chs) {
    List<VolumeGroup> list = new ArrayList<>();
    if (chs == null || chs.isEmpty()) return list;

    boolean hasExplicit = false;
    for (Store.Chapter c : chs) {
      if (c.title != null && (c.title.contains(" · ") || c.title.matches("^(?i)(Tập\\s*\\d+|Vol\\s*\\d+|Quyển\\s*\\d+|Arc\\s*\\d+|Phần\\s*\\d+).*"))) {
        hasExplicit = true;
        break;
      }
    }

    if (hasExplicit) {
      Map<String, VolumeGroup> map = new LinkedHashMap<>();
      for (Store.Chapter c : chs) {
        String volName = "Tập khác";
        String cleanTitle = c.title;
        if (c.title.contains(" · ")) {
          String[] parts = c.title.split(" · ", 2);
          volName = parts[0].trim();
          cleanTitle = parts[1].trim();
        } else {
          java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?i)(Tập\\s*\\d+[^:-]*|Vol\\s*\\d+[^:-]*|Quyển\\s*\\d+[^:-]*|Arc\\s*\\d+[^:-]*|Phần\\s*\\d+[^:-]*)[\\s:-]*(.*)$").matcher(c.title);
          if (m.find()) {
            volName = m.group(1).trim();
            cleanTitle = (m.group(2) != null && !m.group(2).trim().isEmpty()) ? m.group(2).trim() : c.title;
          }
        }
        VolumeGroup vg = map.get(volName);
        if (vg == null) {
          vg = new VolumeGroup();
          vg.name = volName;
          map.put(volName, vg);
          list.add(vg);
        }
        vg.chapters.add(c);
        vg.cleanTitles.add(cleanTitle);
      }
    } else if (chs.size() > 20) {
      int blockSize = 20;
      for (int i = 0; i < chs.size(); i++) {
        int volIdx = i / blockSize + 1;
        int startCh = (volIdx - 1) * blockSize + 1;
        int endCh = Math.min(chs.size(), volIdx * blockSize);
        String volName = "Tập " + volIdx + " (Chương " + startCh + " – " + endCh + ")";
        VolumeGroup vg = null;
        for (VolumeGroup existing : list) {
          if (existing.name.equals(volName)) { vg = existing; break; }
        }
        if (vg == null) {
          vg = new VolumeGroup();
          vg.name = volName;
          list.add(vg);
        }
        vg.chapters.add(chs.get(i));
        vg.cleanTitles.add(chs.get(i).title);
      }
    } else {
      VolumeGroup vg = new VolumeGroup();
      vg.name = "Toàn bộ chương";
      vg.chapters.addAll(chs);
      for (Store.Chapter c : chs) vg.cleanTitles.add(c.title);
      list.add(vg);
    }
    return list;
  }

  private void contents(Store.Book b) {
    if(b==null)return;
    List<Store.Chapter> chs = store.chapters(b.id);
    if (chs.isEmpty()) {
      message("Chưa có mục lục. Kết nối mạng và chọn Tải lại mục lục.");
      return;
    }
    List<VolumeGroup> groups = groupChapters(chs);
    if (groups.size() == 1) {
      volumeChapters(b, groups.get(0));
      return;
    }

    leaveReader();reset();bookId=b.id;chapterId="";
    int targetIdx = resolveTargetIndex(b, chs);
    Store.Chapter targetCh = chs.get(targetIdx);

    chapterHeader(b, targetCh, "Chọn tập", groups.size()+" tập · "+chs.size()+" chương");

    PagedBookList list = new PagedBookList(this);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(new android.graphics.drawable.ColorDrawable(Color.BLACK));
    list.setDividerHeight(dp(1));

    list.setAdapter(new ArrayAdapter<VolumeGroup>(this, 0, groups) {
      public View getView(int p, View convert, ViewGroup parent) {
        VolumeGroup vg = getItem(p);
        boolean containsTarget = false;
        for (Store.Chapter c : vg.chapters) {
          if (c.id.equals(targetCh.id)) { containsTarget = true; break; }
        }

        LinearLayout card = new LinearLayout(MainActivity.this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(new AbsListView.LayoutParams(-1,list.rowHeight(p)));
        card.setPadding(dp(8), dp(4), dp(8), dp(4));
        card.setBackgroundColor(containsTarget ? Color.rgb(240, 240, 240) : Color.WHITE);

        LinearLayout top = new LinearLayout(MainActivity.this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setText(vg.name);
        titleView.setTextSize(17);
        titleView.setMaxLines(2);titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Color.BLACK);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (containsTarget) {
          TextView badge = new TextView(MainActivity.this);
          badge.setText("ĐANG ĐỌC");
          badge.setTextSize(11);
          badge.setTextColor(Color.WHITE);
          badge.setBackgroundColor(Color.BLACK);
          badge.setTypeface(Typeface.DEFAULT_BOLD);
          badge.setPadding(dp(5), dp(1), dp(5), dp(1));
          LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
          lpBadge.setMargins(dp(6), 0, 0, 0);
          top.addView(badge, lpBadge);
        }
        card.addView(top);

        int downloaded = 0;
        for (Store.Chapter c : vg.chapters) if (store.readable(c.id)) downloaded++;
        String info = vg.chapters.size() + " chương · Đã tải " + downloaded + "/" + vg.chapters.size() + " ch";
        TextView subView = new TextView(MainActivity.this);
        subView.setText(info);
        subView.setTextSize(12);
        subView.setSingleLine(true);subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView.setTextColor(MUTED);
        subView.setPadding(0, dp(4), 0, 0);
        card.addView(subView);

        return card;
      }
    });

    list.setOnItemClickListener((a, v, p, id) -> {
      navStack.push(() -> contents(b));
      volumeChapters(b, groups.get(p));
    });

    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    status();
  }

  private void volumeChapters(Store.Book b, VolumeGroup vg) {
    leaveReader();reset();bookId=b.id;chapterId="";
    List<Store.Chapter> allChs = store.chapters(b.id);
    int targetIdx = resolveTargetIndex(b, allChs);
    Store.Chapter targetCh = allChs.get(targetIdx);

    chapterHeader(b,targetCh,vg.name,vg.chapters.size()+" chương");

    List<String> labels = new ArrayList<>();
    for (int n = 0; n < vg.chapters.size(); n++) {
      Store.Chapter c = vg.chapters.get(n);
      boolean isTarget = c.id.equals(targetCh.id);
      boolean wasRead = store.wasRead(c.id);
      String prefix = isTarget ? "▶ [Đang đọc] " : wasRead ? "✓ [Đã đọc] " : "• ";
      String statusIcon = c.ready ? "✓ đủ" : store.html(c.id).isFile() ? "◐" : "↓";
      String chTitle = (n < vg.cleanTitles.size()) ? vg.cleanTitles.get(n) : c.title;
      labels.add(prefix + chTitle + " (" + statusIcon + ")");
    }

    PagedBookList list = new PagedBookList(this);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(new android.graphics.drawable.ColorDrawable(Color.BLACK));
    list.setDividerHeight(dp(1));

    list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
      public View getView(int p, View v, ViewGroup parent) {
        TextView t = (TextView) super.getView(p, v, parent);
        Store.Chapter c = vg.chapters.get(p);
        boolean isTarget = c.id.equals(targetCh.id);
        t.setTextColor(Color.BLACK);
        t.setTypeface(isTarget ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextSize(17);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setLayoutParams(new AbsListView.LayoutParams(-1,list.rowHeight(p)));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        if (isTarget) {
          t.setBackgroundColor(Color.rgb(230, 230, 230));
        } else {
          t.setBackgroundColor(Color.WHITE);
        }
        return t;
      }
    });

    list.setOnItemLongClickListener((a,v,p,id)->{message(vg.chapters.get(p).title);return true;});
    list.setOnItemClickListener((a, v, p, id) -> {
      navStack.push(() -> volumeChapters(b, vg));
      openChapter(b.id, vg.chapters.get(p).id);
    });

    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    // Note: Starts from TOP (Chapter 1) by default! No auto-scrolling to hide Chapter 1!
    status();
  }
  private void openChapter(String bid, String cid) {
    Store.Chapter c = store.chapter(bid, cid);
    if (c == null) {
      message("Không tìm thấy chương trong mục lục.");
      return;
    }
    if(!Repository.activeBook.equals(bid)){leaveReader();Repository.activeBook=bid;}
    Repository.cancel.set(false);
    Repository.pendingBook=bid;
    if (store.readable(cid)) {
      showReader(bid, cid);
    } else {
      if (boundaryPrompt != null) {
        boundaryPrompt.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(2), Color.BLACK);
        cardBg.setCornerRadius(dp(10));
        card.setBackground(cardBg);
        TextView tv = text("Đang tải " + c.title + "…\nVui lòng đợi trong giây lát.", 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        card.addView(tv);
        boundaryPrompt.addView(card, new LinearLayout.LayoutParams(-1, -2));
        boundaryPrompt.setVisibility(View.VISIBLE);
      }
      task(
          "Đang tải " + c.title + "…",
          () -> {
            Repository.download(this, c);
            return null;
          },
          () -> {
            hideBoundaryPrompt();
            showReader(bid, cid);
          });
    }
  }

  private void showReader(String bid,String cid){
    Store.Book b=store.book(bid);Store.Chapter c=store.chapter(bid,cid);if(b==null||c==null)return;
    if(!store.readable(cid)){message("Nội dung chưa lưu thành công. Hãy thử tải lại hoặc mở HAKO.");return;}
    int pos=b.current.equals(cid)?b.pos:0;float fraction=b.current.equals(cid)?b.fraction:0;
    reset();bookId=bid;chapterId=cid;lastPos=pos;lastFraction=fraction;prefs.edit().putString("lastBook",bid).apply();
    if(!visitSession.equals(bid)){store.visited(bid);visitSession=bid;}

    topBar=new LinearLayout(this);
    topBar.setOrientation(1);
    topBar.setBackgroundColor(Color.WHITE);
    title=text(b.title+" · "+c.title,11);
    title.setSingleLine(true);
    title.setPadding(dp(8),dp(3),dp(8),dp(3));
    topBar.addView(title);

    LinearLayout topIcons=new LinearLayout(this);
    topIcons.setOrientation(0);
    topIcons.addView(new IconButton(this,2,b.followed?"Đã lưu tủ sách":"Thêm tủ sách",()->{
      saveBook();
      Toast.makeText(this,store.book(bid).followed?"Đã lưu vào tủ sách":"Chưa lưu",Toast.LENGTH_SHORT).show();
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,17,"Đã đọc hết / Cập nhật HAKO",()->{
      markCaughtUp(store.book(bid));
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,8,"Tải lại chương này",()->{
      store.state(cid,false,"");
      task("Tải lại chương…",()->{
        Repository.cancel.set(false);
        Repository.download(this,store.chapter(bid,cid));
        return null;
      },()->showReader(bid,cid));
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,16,"Khóa/Mở cảm ứng",this::toggleTouchLock),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,14,"Mở trên web HAKO",()->{
      saveThen(()->browse(c.url));
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topBar.addView(topIcons);

    FrameLayout readerFrame = new FrameLayout(this);

    nativeReader=new NativeReader(this,store.dir(cid),new NativeReader.Listener(){
      public void position(int p,float f,int page,int count){
        if(isFinishing()||isDestroyed()||!bid.equals(bookId)||!cid.equals(chapterId))return;
        lastPos=p;lastFraction=f;store.position(bid,cid,p,f);store.readChapter(cid);
        if(title!=null)title.setText(c.title+" · "+(page+1)+"/"+count);
        if(!readerReady){readerReady=true;io.execute(()->store.prune(store.book(bid)));if(!bid.equals("demo"))startDownloads(bid,Repository.MODE_BOOK_NEXT);}
      }
      public void boundary(int dir){
        if(!readerReady)return;
        if(nativeReader!=null&&nativeReader.getPageCount()<=1&&!store.readable(cid)){
          android.widget.Toast.makeText(MainActivity.this,"Chương chưa tải xong hoặc không có nội dung.",android.widget.Toast.LENGTH_SHORT).show();
          return;
        }
        List<Store.Chapter> allChs=store.chapters(bid);
        boolean isLastChapter=(!allChs.isEmpty()&&allChs.get(allChs.size()-1).id.equals(cid));
        if(dir>0&&isLastChapter){
          markCaughtUp(store.book(bid));
          return;
        }
        showBoundaryPrompt(dir);
      }
      public void toolbar(){
        int vis=isToolbarVisible()?View.GONE:View.VISIBLE;
        if(bar!=null)bar.setVisibility(vis);
        if(topBar!=null)topBar.setVisibility(vis);
      }
      public void dismissToolbar(){
        hideToolbar();
      }
    });

    if (pendingOpenAtEnd) {
      nativeReader.setOpenAtEnd(true);
      pendingOpenAtEnd = false;
    }

    nativeReader.setTitles(b.title, c.title);
    nativeReader.setSyncStatus(Repository.isSyncing, Repository.syncPct);
    nativeReader.setTouchLocked(touchLocked);
    // nativeReader fills 100% of readerFrame
    readerFrame.addView(nativeReader, new FrameLayout.LayoutParams(-1, -1));

    // boundaryPrompt centered card
    boundaryPrompt = new LinearLayout(this);
    boundaryPrompt.setOrientation(LinearLayout.VERTICAL);
    boundaryPrompt.setGravity(Gravity.CENTER);
    boundaryPrompt.setVisibility(View.GONE);
    FrameLayout.LayoutParams lpPrompt = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
    lpPrompt.setMargins(dp(16), 0, dp(16), 0);
    readerFrame.addView(boundaryPrompt, lpPrompt);

    // topBar floats at the top
    topBar.setVisibility(View.GONE);
    readerFrame.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

    // bar (bottom bar) floats at the bottom
    bar=new LinearLayout(this);bar.setOrientation(0);bar.setBackgroundColor(Color.WHITE);
    String[] labels={"Trang chủ","Trước","Mục lục","Tiếp","Chữ","Thoát"};
    Runnable[] actions={
      ()->saveThen(()->{leaveReader();library();}),
      ()->saveThen(()->adjacent(-1)),
      ()->{navStack.push(()->showReader(bid,cid));contents(store.book(bid));},
      ()->saveThen(()->adjacent(1)),
      this::settings,
      this::exitApp
    };
    int[] iconKinds = {0, 4, 1, 5, 3, 6};
    for(int n=0;n<labels.length;n++)bar.addView(new IconButton(this,iconKinds[n],labels[n],actions[n]),new LinearLayout.LayoutParams(0,dp(40),1));
    bar.setVisibility(View.GONE);
    readerFrame.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    root.addView(readerFrame, new LinearLayout.LayoutParams(-1, -1));
    try{nativeReader.content(Store.read(store.html(cid)),pos,fraction);applyReaderStyle();}catch(Exception e){message(e.getMessage());}
  }

  private void saveThen(Runnable next){if(nativeReader!=null&&readerReady)store.position(bookId,chapterId,lastPos,lastFraction);next.run();}

  private void adjacent(int delta) {
    List<Store.Chapter> chs = store.chapters(bookId);
    for (int n = 0; n < chs.size(); n++)
      if (chs.get(n).id.equals(chapterId)) {
        int target = n + delta;
        if (target >= 0 && target < chs.size()) {
          if (delta < 0) pendingOpenAtEnd = true;
          openChapter(bookId, chs.get(target).id);
        }
        else
          Toast.makeText(
                  this, delta > 0 ? "Đã đến cuối mục lục" : "Đây là chương đầu", Toast.LENGTH_SHORT)
              .show();
        return;
      }
  }

  private void bookmarks() {
    saveThen(
        () -> {
          String key = "marks-" + bookId;
          JSONArray arr;
          try {
            arr = new JSONArray(prefs.getString(key, "[]"));
          } catch (Exception e) {
            arr = new JSONArray();
          }
          final JSONArray marks = arr;
          String[] labels = new String[marks.length() + 1];
          labels[0] = "+ Lưu đoạn đang đọc";
          for (int i = 0; i < marks.length(); i++)
            labels[i + 1] =
                marks.optJSONObject(i).optString("title")
                    + " · đoạn "
                    + (marks.optJSONObject(i).optInt("pos") + 1);
          new AlertDialog.Builder(this)
              .setTitle("Đánh dấu trên máy")
              .setItems(
                  labels,
                  (d, w) -> {
                    try {
                      if (w == 0) {
                        JSONObject m = new JSONObject();
                        m.put("chapter", chapterId);
                        m.put("pos", lastPos);
                        m.put("fraction", lastFraction);
                        m.put("title", store.chapter(bookId, chapterId).title);
                        marks.put(m);
                        prefs.edit().putString(key, marks.toString()).apply();
                        Toast.makeText(this, "Đã lưu đánh dấu", Toast.LENGTH_SHORT).show();
                      } else {
                        JSONObject m = marks.getJSONObject(w - 1);
                        String bid = bookId;
                        store.position(
                            bid,
                            m.getString("chapter"),
                            m.optInt("pos"),
                            (float) m.optDouble("fraction"));
                        openChapter(bid, m.getString("chapter"));
                      }
                    } catch (Exception e) {
                      message("Không đọc được đánh dấu.");
                    }
                  })
              .setNeutralButton("Xóa đánh dấu…", (dialog, which) -> removeBookmark(key, marks))
              .setNegativeButton("Đóng", null)
              .show();
        });
  }

  private void removeBookmark(String key, JSONArray marks) {
    if (marks.length() == 0) return;
    String[] names = new String[marks.length()];
    for (int i = 0; i < names.length; i++)
      names[i] =
          marks.optJSONObject(i).optString("title")
              + " · đoạn "
              + (marks.optJSONObject(i).optInt("pos") + 1);
    new AlertDialog.Builder(this)
        .setTitle("Chọn đánh dấu muốn xóa")
        .setItems(
            names,
            (d, w) -> {
              marks.remove(w);
              prefs.edit().putString(key, marks.toString()).apply();
              Toast.makeText(this, "Đã xóa đánh dấu trên máy", Toast.LENGTH_SHORT).show();
            })
        .setNegativeButton("Hủy", null)
        .show();
  }

  private void importWebHistory(Runnable after){
    if(web==null){after.run();return;}web.evaluateJavascript("localStorage.getItem('reading_series')",value->{try{Object raw=new JSONTokener(value).nextValue();if(raw instanceof String){JSONArray list=new JSONArray((String)raw);for(int i=0;i<list.length();i++){JSONObject h=list.getJSONObject(i);String u=HakoParser.normalize(h.optString("series_url")),id=HakoParser.storyId(u),cu=HakoParser.normalize(h.optString("chapter_url")),cid=HakoParser.chapterId(cu);if(id.isEmpty()||cid.isEmpty())continue;store.putBook(new HakoParser.Link(id,h.optString("series_title","Truyện"),u));store.importHistory(id,cid,h.optLong("read_time")*1000);}}}catch(Exception ignored){}after.run();});
  }
  private void browse(String url) {
    Repository.cancel.set(true);leaveReader();reset();
    online = true;
    root.addView(text("HAKO • Đăng nhập trực tiếp trên website", 15));
    row(
        new String[] {"Trang chính", "Tủ sách", "Đọc offline"},
        new Runnable[] {
          () -> {
            CookieManager.getInstance().flush();
            importWebHistory(this::library);
          },
          () -> web.loadUrl(HakoParser.ORIGIN + "/ke-sach"),
          () -> {
            String u = web.getUrl();
            if (HakoParser.storyId(HakoParser.storyUrl(u == null ? "" : u)).isEmpty()) {
              message("Mở trang truyện hoặc chương rồi bấm Lưu truyện này.");
              return;
            }
            library();
            addUrl(u);
          }
        });
    status();
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setSupportMultipleWindows(false);
    s.setJavaScriptCanOpenWindowsAutomatically(false);
    web.setWebChromeClient(new WebChromeClient());
    web.setWebViewClient(
        new WebViewClient() {
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            String h = r.getUrl().getHost();
            if ("https".equals(r.getUrl().getScheme()) && HakoParser.isOrigin(r.getUrl().toString())) return false;
            message(
                "Liên kết ngoài HAKO không mở trong ứng dụng này. Đăng nhập bằng tên tài khoản/mật"
                    + " khẩu trên HAKO; đăng nhập Google trong WebView chưa được hỗ trợ.");
            return true;
          }

          public void onPageFinished(WebView v, String u) {
            CookieManager.getInstance().flush();
            if (status != null) status.setText("Mở truyện/chương → Đọc offline. Theo dõi bằng nút trên HAKO.");
          }

          public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (req.isForMainFrame() && status != null)
              status.setText("Không tải được trang. Kiểm tra mạng và thử lại.");
          }
        });
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    web.loadUrl(url);
  }

  private String readerFont(){int n=prefs.getInt("font2",0);return n==1?"DejaVu,serif":n==2?"sans-serif":n==3?"ReaderCustom,serif":"Tinos,serif";}
  private void domain(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(HakoParser.ORIGIN);new AlertDialog.Builder(this).setTitle("Tên miền HAKO HTTPS").setView(input).setPositiveButton("Kiểm tra",(d,w)->{String value=input.getText().toString().trim();if(!value.startsWith("https://"))value="https://"+value;final String origin=value.replaceAll("/+$","");try{java.net.URI u=java.net.URI.create(origin);if(u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!u.getPath().isEmpty())throw new Exception();}catch(Exception e){message("Chỉ nhập tên miền HTTPS, không có đường dẫn.");return;}task("Kiểm tra tên miền…",()->{String h=Repository.page(origin,false);if(!h.contains("Light Novel")&&!h.contains("HAKO"))throw new Exception("Không nhận diện trang HAKO");return null;},()->{Repository.cancel.set(true);HakoParser.ORIGIN=origin;prefs.edit().putString("origin",origin).apply();store.rebase(origin);message("Đã đổi tên miền. Đăng nhập lại nếu cần.");});}).setNegativeButton("Hủy",null).show();}
  private void settings(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(10),0,dp(10),0);
    box.addView(text("Phông chữ đọc sách (chạm để chọn):",14));
    Spinner fonts=new Spinner(this);fonts.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tinos (chân thanh mảnh)","DejaVu Serif (chân rõ nét)","Liberation Serif (chữ dày êm mắt)","Liberation Sans (không chân siêu nét)","Không chân hệ thống","Font tự nhập (TTF/OTF)"}));fonts.setSelection(prefs.getInt("font2",2));box.addView(fonts);
    TextView preview=text("Tiếng Việt: Nguyễn, quyển, tưởng, khuỷu — ă â ê ô ơ ư đ",18);box.addView(preview);
    TextView sizeLabel=text("Cỡ chữ",14);box.addView(sizeLabel);SeekBar size=new SeekBar(this);size.setMax(44);size.setProgress(Math.round((prefs.getFloat("size2",22f)-12)*2));box.addView(size);
    TextView lineLabel=text("Giãn dòng trong đoạn",14);box.addView(lineLabel);
    SeekBar line=new SeekBar(this);line.setMax(16);line.setProgress(Math.round((prefs.getFloat("line",1.30f)-0.70f)/0.05f));box.addView(line);
    TextView paraLabel=text("Khoảng cách giữa các đoạn",14);box.addView(paraLabel);
    SeekBar para=new SeekBar(this);para.setMax(10);para.setProgress(prefs.getInt("paraSpaceDp",8)/2);box.addView(para);
    TextView indentLabel=text("Thụt đầu dòng mỗi đoạn",14);box.addView(indentLabel);
    SeekBar indent=new SeekBar(this);indent.setMax(10);indent.setProgress(prefs.getInt("indentDp",9)/3);box.addView(indent);
    TextView marginLabel=text("Lề mép màn hình",14);box.addView(marginLabel);SeekBar margin=new SeekBar(this);margin.setMax(28);margin.setProgress(prefs.getInt("margin",5));box.addView(margin);
    CheckBox bold=check("Nét đậm (thử trên màn E Ink)",prefs.getInt("weight",400)>400);box.addView(bold);
    Runnable update=()->{
      float fs=12+size.getProgress()/2f;
      float ls=Math.round((0.70f+line.getProgress()*0.05f)*100f)/100f;
      int pDp=para.getProgress()*2;
      int iDp=indent.getProgress()*3;
      sizeLabel.setText("Cỡ chữ: "+fs+" sp");
      lineLabel.setText("Giãn dòng trong đoạn: "+String.format(java.util.Locale.US,"%.2fx",ls));
      paraLabel.setText("Khoảng cách đoạn: "+(pDp==0?"0 dp (sát nhau)":pDp+" dp"));
      indentLabel.setText("Thụt đầu dòng: "+(iDp==0?"0 dp (không thụt)":iDp+" dp"));
      marginLabel.setText("Lề mép màn hình: "+margin.getProgress()+" dp");
      preview.setTextSize(fs);
      try{int n=fonts.getSelectedItemPosition();android.graphics.Typeface tf=n==0?android.graphics.Typeface.createFromAsset(getAssets(),"Tinos.ttf"):n==1?android.graphics.Typeface.createFromAsset(getAssets(),"DejaVu.ttf"):n==2?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSerif.ttf"):n==3?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSans.ttf"):n==4?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf"));preview.setTypeface(tf,bold.isChecked()?1:0);}catch(Exception e){preview.setTypeface(android.graphics.Typeface.SERIF);}
    };
    SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};
    size.setOnSeekBarChangeListener(listener);line.setOnSeekBarChangeListener(listener);para.setOnSeekBarChangeListener(listener);indent.setOnSeekBarChangeListener(listener);margin.setOnSeekBarChangeListener(listener);
    fonts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int p,long id){update.run();}public void onNothingSelected(AdapterView<?> a){}});
    bold.setOnCheckedChangeListener((a,b)->update.run());update.run();
    CheckBox taps=check("Chạm trên/dưới để lật trang",prefs.getBoolean("taps",true));box.addView(taps);CheckBox charging=check("Đồng bộ tủ sách khi sạc",prefs.getBoolean("charging",true));box.addView(charging);CheckBox touchLockBox=check("Khóa cảm ứng khi đọc (chống chạm túi, chỉ mở bằng phím cứng)",prefs.getBoolean("touch_lock",false));box.addView(touchLockBox);
    box.addView(button("Nhập font TTF / OTF",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),88)));
    box.addView(button("Tên miền HAKO",this::domain));box.addView(button("Đọc mẫu offline",this::demo));
    box.addView(text("Tải trước 1–2: chờ 4s; 3–5: 12s; 6–10: 30s; 11–15: 60s. Màn hình tắt: ngừng tải trước khi dùng pin. Đọc bản lưu không cần chạy trang HAKO.",12));
    ScrollView scroll=new ScrollView(this);scroll.addView(box);
    new AlertDialog.Builder(this).setTitle("Chữ & tải nội dung").setView(scroll).setPositiveButton("Áp dụng",(d,w)->{
      if(fonts.getSelectedItemPosition()==5&&!new File(getFilesDir(),"reader-font.ttf").isFile()){message("Chưa nhập font. Đang giữ lựa chọn cũ.");return;}
      float ls=Math.round((0.70f+line.getProgress()*0.05f)*100f)/100f;
      int pDp=para.getProgress()*2;
      int iDp=indent.getProgress()*3;
      prefs.edit().putInt("font2",fonts.getSelectedItemPosition()).putFloat("size2",12+size.getProgress()/2f).putFloat("line",ls).putInt("paraSpaceDp",pDp).putInt("indentDp",iDp).putInt("margin",margin.getProgress()).putInt("weight",bold.isChecked()?700:400).putBoolean("taps",taps.isChecked()).putBoolean("charging",charging.isChecked()).putBoolean("touch_lock",touchLockBox.isChecked()).apply();touchLocked=touchLockBox.isChecked();if(nativeReader!=null)nativeReader.setTouchLocked(touchLocked);if(lockIndicator!=null)lockIndicator.setVisibility(touchLocked?View.VISIBLE:View.GONE);
      ChargeJob.schedule(this,charging.isChecked());
      if(nativeReader!=null)applyReaderStyle();
    }).setNegativeButton("Đóng",null).show();
  }

  private CheckBox check(String label, boolean checked) {
    CheckBox c = new CheckBox(this);
    c.setText(label);
    c.setTextColor(INK);
    c.setChecked(checked);
    return c;
  }

  private void applyReaderStyle(){if(nativeReader==null)return;android.graphics.Typeface face;int choice=prefs.getInt("font2",2);try{face=choice==0?android.graphics.Typeface.createFromAsset(getAssets(),"Tinos.ttf"):choice==1?android.graphics.Typeface.createFromAsset(getAssets(),"DejaVu.ttf"):choice==2?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSerif.ttf"):choice==3?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSans.ttf"):choice==4?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf"));}catch(Exception e){face=android.graphics.Typeface.SERIF;}
    face=android.graphics.Typeface.create(face,prefs.getInt("weight",400)>=700?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
    nativeReader.style(prefs.getFloat("size2",22f),prefs.getInt("margin",5),prefs.getFloat("line",1.30f),prefs.getInt("paraSpaceDp",8),prefs.getInt("indentDp",9),face,prefs.getBoolean("taps",true));
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle("Hako Pocket 0.5.2 • Bản thử nghiệm")
        .setMessage(
            "Đăng nhập HAKO → Nhập kệ sách → chọn truyện → chọn chương.\n\n"
                + "✓ là chương có đủ nội dung/ảnh. ◐ là đã có chữ nhưng thiếu ảnh. Nhấn giữ truyện"
                + " để cập nhật mục lục hoặc tải riêng.\n\n"
                + "Vị trí đoạn lưu trên APK. Menu Đã bắt kịp cập nhật bộ đếm HAKO khi bạn xác nhận.\n\n"
                + "Chú thích hiển thị bằng ký hiệu ✎; bấm để xem. Chưa kiểm chứng mọi định dạng"
                + " HAKO.\n\n"
                + "Dùng nút Dừng tải để tạm dừng; lượt tải sau bỏ qua chương đã hoàn tất. Dữ liệu"
                + " nằm trong ứng dụng, sẽ mất nếu gỡ app/xóa dữ liệu.")
        .setPositiveButton("Đóng", null)
        .setNeutralButton("Đọc mẫu", (d, w) -> demo())
        .show();
  }

  private void demo() {
    store.putBook(new HakoParser.Link("demo", "Hướng dẫn Hako Pocket (offline)", ""));
    List<HakoParser.Link> ls = new ArrayList<>();
    for (int n = 1; n <= 12; n++) ls.add(new HakoParser.Link("demo-" + n, "Chương mẫu " + n, ""));
    store.catalog("demo", ls);
    try {
      for (int n = 1; n <= 12; n++) {
        StringBuilder b = new StringBuilder();
        b.append(
            "<h2>Chương mẫu "
                + n
                + "</h2><p>Đây là nội dung mẫu tự viết để thử đọc offline. Không lấy từ truyện"
                + " HAKO.</p><details><summary>✎ Chú thích mẫu</summary><p>Bấm icon để mở chú thích"
                + " ngay trong chương.</p></details>");
        for (int p = 1; p <= 24; p++)
          b.append(
              "<p id='"
                  + p
                  + "'>Đoạn "
                  + p
                  + ". Bạn có thể chạm nửa dưới màn hình để tiến, nửa trên để lùi. Vị trí này được"
                  + " lưu trên thiết bị. Đổi cỡ chữ, về thư viện rồi mở lại để thử khả năng tiếp"
                  + " tục đọc.</p>");
        Store.write(store.html("demo-" + n), b.toString());
        store.state("demo-" + n, true, "");
      }
      openBook(store.book("demo"));
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == 88 && result == RESULT_OK && data != null) {
      Uri u = data.getData();
      task(
          "Đang nhập font…",
          () -> {
            File dest = new File(getFilesDir(), "reader-font.tmp");
            try (InputStream in = getContentResolver().openInputStream(u);
                FileOutputStream out = new FileOutputStream(dest)) {
              byte[] b = new byte[8192];
              int n, total = 0;
              while ((n = in.read(b)) != -1) {
                total += n;
                if (total > 8 * 1024 * 1024) throw new IOException("Font tối đa 8 MB");
                out.write(b, 0, n);
              }
            }
            android.graphics.Typeface.createFromFile(dest);
            if (!dest.renameTo(new File(getFilesDir(), "reader-font.ttf")))
              throw new IOException("Không lưu được font");
            prefs.edit().putBoolean("customFont", true).putInt("font2",5).apply();
            return null;
          },
          () -> {
            applyReaderStyle();message("Đã nhập font.");
          });
    }
  }

  protected void onPause() {
    cancelLockKey();
    if(nativeReader!=null&&readerReady)store.position(bookId,chapterId,lastPos,lastFraction);
    if (web != null) {
      CookieManager.getInstance().flush();
    }
    super.onPause();
  }

  protected void onDestroy() {
    if (updates != null) unregisterReceiver(updates);
    if (web != null) {
      web.removeJavascriptInterface("Bridge");
      web.destroy();
    }
    io.shutdown();
    super.onDestroy();
  }

  private void hideToolbar() {
    if (bar != null) bar.setVisibility(View.GONE);
    if (topBar != null) topBar.setVisibility(View.GONE);
  }

  private void hideBoundaryPrompt() {
    caughtUpPrompt=false; caughtUpBook=null;
    if (boundaryPrompt != null) {
      boundaryPrompt.setVisibility(View.GONE);
      boundaryDir = 0;
    }
  }

  private boolean isBoundaryPromptVisible() {
    return boundaryPrompt != null && boundaryPrompt.getVisibility() == View.VISIBLE;
  }

  private void showBoundaryPrompt(int dir) {
    if (boundaryPrompt == null) return;
    hideToolbar();
    boundaryDir = dir;
    boundaryPrompt.removeAllViews();

    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setGravity(Gravity.CENTER_HORIZONTAL);
    card.setPadding(dp(18), dp(16), dp(18), dp(16));

    android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
    cardBg.setColor(Color.WHITE);
    cardBg.setStroke(dp(2), Color.BLACK);
    cardBg.setCornerRadius(dp(10));
    card.setBackground(cardBg);

    TextView titleView = text(dir > 0 ? "ĐÃ ĐỌC HẾT CHƯƠNG" : "ĐANG Ở ĐẦU CHƯƠNG", 16);
    titleView.setTypeface(Typeface.DEFAULT_BOLD);
    titleView.setTextColor(Color.BLACK);
    titleView.setGravity(Gravity.CENTER);
    titleView.setPadding(0, 0, 0, dp(6));
    card.addView(titleView);

    String msg = dir > 0
        ? "Chuyển sang chương tiếp theo?"
        : "Quay lại chương trước?\n(Sẽ mở lại trang cuối vừa đọc)";
    TextView msgView = text(msg, 14);
    msgView.setTextColor(Color.BLACK);
    msgView.setGravity(Gravity.CENTER);
    msgView.setPadding(0, 0, 0, dp(14));
    card.addView(msgView);

    LinearLayout btnRow = new LinearLayout(this);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.CENTER);

    Button btnStay = new Button(this);
    btnStay.setText("Ở lại (Vol +)");
    btnStay.setTextSize(13);
    btnStay.setTypeface(Typeface.DEFAULT_BOLD);
    btnStay.setTextColor(Color.BLACK);
    android.graphics.drawable.GradientDrawable btnStayBg = new android.graphics.drawable.GradientDrawable();
    btnStayBg.setColor(Color.WHITE);
    btnStayBg.setStroke(dp(1), Color.BLACK);
    btnStayBg.setCornerRadius(dp(6));
    btnStay.setBackground(btnStayBg);
    btnStay.setPadding(dp(10), dp(6), dp(10), dp(6));
    btnStay.setStateListAnimator(null); btnStay.setOnClickListener(v -> hideBoundaryPrompt());
    LinearLayout.LayoutParams lpStay = new LinearLayout.LayoutParams(0, dp(42), 1f);
    lpStay.setMargins(0, 0, dp(8), 0);
    btnRow.addView(btnStay, lpStay);

    Button btnConfirm = new Button(this);
    btnConfirm.setText(dir > 0 ? "Tiếp tục (Vol -)" : "Quay lại (Vol -)");
    btnConfirm.setTextSize(13);
    btnConfirm.setTypeface(Typeface.DEFAULT_BOLD);
    btnConfirm.setTextColor(Color.WHITE);
    android.graphics.drawable.GradientDrawable btnConfirmBg = new android.graphics.drawable.GradientDrawable();
    btnConfirmBg.setColor(Color.BLACK);
    btnConfirmBg.setCornerRadius(dp(6));
    btnConfirm.setBackground(btnConfirmBg);
    btnConfirm.setPadding(dp(10), dp(6), dp(10), dp(6));
    btnConfirm.setStateListAnimator(null); btnConfirm.setOnClickListener(v -> {
      int d = boundaryDir;
      hideBoundaryPrompt();
      adjacent(d);
    });
    LinearLayout.LayoutParams lpConfirm = new LinearLayout.LayoutParams(0, dp(42), 1f);
    btnRow.addView(btnConfirm, lpConfirm);

    card.addView(btnRow);

    boundaryPrompt.addView(card, new LinearLayout.LayoutParams(-1, -2));
    boundaryPrompt.setVisibility(View.VISIBLE);
  }

  private boolean isToolbarVisible() {
    return (bar != null && bar.getVisibility() == View.VISIBLE)
        || (topBar != null && topBar.getVisibility() == View.VISIBLE);
  }

  private void goBack() {
    if (isBoundaryPromptVisible()) {
      hideBoundaryPrompt();
      return;
    }
    if (nativeReader != null && isToolbarVisible()) {
      hideToolbar();
      return;
    }
    if (nativeReader != null) {
      saveThen(() -> {
        leaveReader();
        if (!navStack.isEmpty()) navStack.pop().run();
        else library();
      });
      return;
    }
    if (online && web != null && web.canGoBack()) {
      web.goBack();
      return;
    }
    if (web != null) {
      leaveReader();
      if (!navStack.isEmpty()) navStack.pop().run();
      else library();
      return;
    }
    if (!navStack.isEmpty()) {
      navStack.pop().run();
      return;
    }
    if (!bookId.isEmpty()) {
      bookId = "";
      bookList(true);
      return;
    }
    if (currentList != null) {
      library();
      return;
    }
    confirmExit();
  }

  private void confirmExit() {
    EinkDialog.show(
        this,
        "Thoát Hako Pocket",
        "Bạn có muốn thoát ứng dụng?",
        "Thoát",
        this::exitApp,
        "Ở lại",
        null);
  }

  @Override
  public void onBackPressed() {
    goBack();
  }

  private boolean isPageDownKey(int code){
    return code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_PAGE_DOWN||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_RIGHT;
  }
  private boolean isPageUpKey(int code){
    return code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_PAGE_UP||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_LEFT;
  }

  private final ListPaging listPaging = new ListPaging();
  private final Handler lockHandler=new Handler(Looper.getMainLooper());
  private final HoldKey lockKey=new HoldKey();
  private long lockDownTime;
  private final Runnable lockHold=()->{android.util.Log.d("HakoKeys","hold timer, locked="+touchLocked);if(lockKey.hold())toggleTouchLock();};

  private void cancelLockKey(){android.util.Log.d("HakoKeys","cancel hold");lockHandler.removeCallbacks(lockHold);lockKey.reset();}

  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    if (touchLocked) return true;
    return super.dispatchTouchEvent(event);
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if(!focused)cancelLockKey();
  }

  private void handleSingleKeyAction(int dir) {
    if (isBoundaryPromptVisible()) {
      if(caughtUpPrompt){
        Store.Book b=caughtUpBook; hideBoundaryPrompt();
        if(dir>0 && b!=null)submitCaughtUp(b);
        return;
      }
      if(dir>0){int direction=boundaryDir;hideBoundaryPrompt();adjacent(direction);}
      else hideBoundaryPrompt();
      return;
    }
    if (nativeReader != null) {
      hideToolbar();
      nativeReader.turn(dir);
    }
  }

  private void performPageAction(int dir){
    if(nativeReader!=null)handleSingleKeyAction(dir);
    else if(web!=null){if(dir>0)web.pageDown(false);else web.pageUp(false);}
    else if(currentList!=null&&currentList.isShown()){
      if(currentList instanceof PagedBookList)((PagedBookList)currentList).turn(dir);
      else listPaging.turn(currentList,dir);
    }else if(currentScroll!=null&&currentScroll.isShown())currentScroll.pageScroll(dir>0?View.FOCUS_DOWN:View.FOCUS_UP);
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event){
    int code = event.getKeyCode();
    int action = event.getAction();

    // 1. Hardware Menu / Front Center / Enter button to open toolbar
    if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
      if (action == KeyEvent.ACTION_DOWN && nativeReader != null) {
        if (isToolbarVisible()) hideToolbar();
        else {
          if (bar != null) bar.setVisibility(View.VISIBLE);
          if (topBar != null) topBar.setVisibility(View.VISIBLE);
        }
        return true;
      }
    }

    // Hold Volume Up for 700 ms to lock/unlock; consume repeats and the final release.
    if(isPageUpKey(code) && web==null){
      android.util.Log.d("HakoKeys","code="+code+" action="+action+" repeat="+event.getRepeatCount()+" locked="+touchLocked);
      if(action==KeyEvent.ACTION_DOWN){
        if(lockKey.down()){lockDownTime=SystemClock.uptimeMillis();lockHandler.postDelayed(lockHold,700);}
      }else if(action==KeyEvent.ACTION_UP){
        lockHandler.removeCallbacks(lockHold);
        if(!event.isCanceled() && SystemClock.uptimeMillis()-lockDownTime>=700 && lockKey.hold())toggleTouchLock();
        if(lockKey.up(event.isCanceled()))performPageAction(-1);
      }
      return true;
    }
    if(touchLocked && code==KeyEvent.KEYCODE_BACK)return true;

    // 3. Other page turn hardware keys (Page Up/Down, D-pad, etc.)
    boolean down = isPageDownKey(code);
    boolean up = isPageUpKey(code);
    if (down || up) {
      if (action == KeyEvent.ACTION_DOWN) {
        performPageAction(down?1:-1);
      }
      return true;
    }
    return super.dispatchKeyEvent(event);
  }
  public boolean onKeyUp(int key,KeyEvent event){if(nativeReader!=null&&(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN))return true;return super.onKeyUp(key,event);}
  public boolean onKeyDown(int key, KeyEvent event) {
    if(nativeReader!=null){
      if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN) return true;
      hideToolbar();
      if(key==KeyEvent.KEYCODE_PAGE_DOWN||key==KeyEvent.KEYCODE_DPAD_RIGHT){nativeReader.turn(1);return true;}
      if(key==KeyEvent.KEYCODE_PAGE_UP||key==KeyEvent.KEYCODE_DPAD_LEFT){nativeReader.turn(-1);return true;}
    }
    return super.onKeyDown(key, event);
  }
}

