package vn.nanase.hako;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
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
  private android.net.ConnectivityManager.NetworkCallback networkCallback;


  private static volatile UiLibrarySnapshot readyLibrary;
  private volatile int chapterOpenRequest;
  private final ExecutorService libraryIo=Executors.newSingleThreadExecutor();
  private final Handler libraryHandler=new Handler(Looper.getMainLooper());
  private boolean libraryLoading=false,libraryReloadAgain=false;
  private Runnable libraryLoaded;
  private long libraryLoadStarted;
  private TextView listLoadStatus, libraryMetadata;
  private List<Store.Book> activeLibraryBooks;
  private ArrayAdapter<Store.Book> activeLibraryAdapter;
  private final Runnable libraryClock=new Runnable(){public void run(){
    if(isFinishing()||isDestroyed())return;
    if(listLoadStatus!=null&&libraryLoading&&!Repository.busy.get()&&!Repository.pendingAll)listLoadStatus.setText("Đọc dữ liệu cục bộ · "+((SystemClock.elapsedRealtime()-libraryLoadStarted)/1000)+"s · Giao diện vẫn dùng bản đã lưu");
    if(status!=null&&(Repository.busy.get()||Repository.pendingAll))status.setText(Repository.status+" · "+Math.max(0,(SystemClock.elapsedRealtime()-Repository.stageStarted)/1000)+"s");
    if(libraryLoading||Repository.busy.get()||Repository.pendingAll)libraryHandler.postDelayed(this,1000);
  }};
  private void reloadLibrary(boolean force,Runnable after){
    if(!force&&readyLibrary!=null){if(after!=null)after.run();return;}
    if(after!=null)libraryLoaded=after;
    if(libraryLoading){if(force)libraryReloadAgain=true;return;}
    libraryLoading=true;libraryLoadStarted=SystemClock.elapsedRealtime();
    libraryHandler.removeCallbacks(libraryClock);libraryHandler.postDelayed(libraryClock,1000);
    libraryIo.execute(()->{
      UiLibrarySnapshot result=null;String error=null;
      try{result=LibraryState.refresh(this);}catch(Exception e){error=e.getMessage();}
      final UiLibrarySnapshot loaded=result;final String failure=error;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        libraryLoading=false;
        if(loaded!=null){readyLibrary=loaded;refreshLibraryView();}
        Runnable done=libraryLoaded;libraryLoaded=null;
        if(done!=null&&loaded!=null)done.run();
        if(listLoadStatus!=null&&!Repository.busy.get()&&!Repository.pendingAll)listLoadStatus.setText(loaded==null?"Không đọc được thư viện: "+failure:"Dữ liệu cục bộ sẵn · "+loaded.books.size()+" bộ · chuẩn bị "+loaded.loadMillis+"ms");
        if(libraryReloadAgain){libraryReloadAgain=false;reloadLibrary(false,()->refreshLibraryView());}
      });
    });
  }
  private Store.Book displayBook(String id){if(readyLibrary!=null)for(Store.Book b:readyLibrary.books)if(b.id.equals(id))return b;return null;}
  private List<Store.Book> libraryBooks(int mode){
    List<Store.Book> result=new ArrayList<>();
    if(readyLibrary==null)return result;
    List<Store.Book> indexed=readyLibrary.index(mode==1&&prefs.getBoolean("unreadOnly",false)?6:mode);if(indexed!=null)return new ArrayList<>(indexed);
    for(Store.Book b:readyLibrary.books){
      if(mode==1?b.followed&&(!prefs.getBoolean("unreadOnly",false)||hasNew(b)):
         mode==4?b.stamp>0:!b.id.equals("demo")&&(mode==3?readyLibrary.summary(b).completedOffline:store.favorite(b)))result.add(b);
    }
    if(mode==1)result.sort(readyLibrary::compareShelf);
    if(mode==4)result.sort((a,b)->{int n=Long.compare(b.stamp,a.stamp);return n!=0?n:a.id.compareTo(b.id);});
    if(mode==2||mode==3)result.sort((a,b)->{int n=Integer.compare(b.visits,a.visits);if(n!=0)return n;n=Long.compare(b.stamp,a.stamp);return n!=0?n:a.title.compareToIgnoreCase(b.title);});
    return result;
  }
  private String libraryMetadataText(int mode,int count){
    if(mode==1){
      if(Repository.shelfUpdating)return "Đang cập nhật tủ sách từ HAKO…";
      long stamp=readyLibrary==null?0:readyLibrary.lastShelfSync;
      return (stamp>0?"Đã cập nhật lúc "+formatVnDate(stamp,"HH:mm dd/MM/yyyy"):"Chưa cập nhật tủ sách")+" · "+count+" bộ";
    }
    if(mode==4)return "Lịch sử đọc trên máy · "+count+" bộ";
    return (mode==3?"Bạn đánh dấu hoàn thành · Đã tải đủ · ":"Đọc nhiều trước · Truyện ghim để dễ tìm · ")+count+" bộ";
  }
  private void refreshLibraryView(){
    if(readyLibrary==null||activeLibraryAdapter==null||libraryListMode==0)return;
    // Keep the same root, header, viewport and scroll anchor during background updates.
    List<Store.Book> updated=libraryBooks(libraryListMode);
    boolean sameOrder=activeLibraryBooks!=null&&activeLibraryBooks.size()==updated.size();
    if(sameOrder)for(int i=0;i<updated.size();i++)if(!activeLibraryBooks.get(i).id.equals(updated.get(i).id)){sameOrder=false;break;}
    boolean changed=false;
    if(sameOrder){for(int i=0;i<updated.size();i++)if(activeLibraryBooks.get(i)!=updated.get(i)){activeLibraryBooks.set(i,updated.get(i));changed=true;}}
    else{activeLibraryAdapter.setNotifyOnChange(false);activeLibraryAdapter.clear();activeLibraryAdapter.addAll(updated);changed=true;Diagnostics.count("ui.list_rebuild");}
    if(changed){activeLibraryAdapter.notifyDataSetChanged();Diagnostics.count("ui.list_notify");}
    if(libraryMetadata!=null)libraryMetadata.setText(libraryMetadataText(libraryListMode,updated.size()));
  }
  private void openLibraryList(int mode){
    if(mode==1||mode==4)renderBookList(mode==1);
    else renderFrequentList(mode==3);
    // Home already preloads the snapshot. Navigation uses it immediately.
    reloadLibrary(false,null);
    libraryHandler.removeCallbacks(libraryClock);libraryHandler.postDelayed(libraryClock,1000);
  }
  private int libraryLineHeight(TextView view){
    android.graphics.Paint.FontMetricsInt fm=view.getPaint().getFontMetricsInt();
    return fm.bottom-fm.top+dp(4);
  }
  private void addLibraryLoadStatus(LinearLayout header){
    listLoadStatus=new TextView(this);
    listLoadStatus.setText(Repository.busy.get()||Repository.pendingAll?Repository.status:
      readyLibrary==null?"Đang chuẩn bị dữ liệu cục bộ":"Thư viện sẵn · chuẩn bị "+readyLibrary.loadMillis+"ms");
    listLoadStatus.setTextSize(11f);listLoadStatus.setTextColor(MUTED);
    listLoadStatus.setTypeface(UI_REGULAR);listLoadStatus.setIncludeFontPadding(false);
    listLoadStatus.setSingleLine(true);listLoadStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
    listLoadStatus.setPadding(0,dp(2),0,dp(2));
    header.addView(listLoadStatus,new LinearLayout.LayoutParams(-1,libraryLineHeight(listLoadStatus)));
    status=listLoadStatus;

  }

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
  private int libraryListMode=0;
  private boolean reportExportPending=false;
  private TextView status, title, syncBadge;
  private AlertDialog settingsDialog;
  private NativeReader nativeReader;
  private ListView currentList;
  private ScrollView currentScroll;
  private final java.util.ArrayDeque<Runnable> navStack = new java.util.ArrayDeque<>();
  private String visitSession="";
  private boolean readerReady = false;
  private boolean volumeUpPressed = false;
  private long volumeUpPressTime = 0;

  private void toggleTouchLock() {
    touchLocked=!touchLocked;
    prefs.edit().putBoolean("touch_lock",touchLocked).apply();
    if(nativeReader!=null)nativeReader.setTouchLocked(touchLocked);
    hideToolbar(); hideBoundaryPrompt();
    Repository.pocketPaused=touchLocked;Diagnostics.foreground=true;
  }
  private volatile String bookId = "", chapterId = "";
  private int generation = 0;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private BroadcastReceiver updates;
  private volatile int lastPos = 0;
  private volatile float lastFraction = 0;
  private static final Typeface UI_REGULAR=Typeface.create("sans-serif",Typeface.NORMAL);
  private static final Typeface UI_MEDIUM=Typeface.create(UI_REGULAR,500,false);
  private final int INK = Color.rgb(25, 25, 25), MUTED = Color.rgb(80, 80, 80);

  public void onCreate(Bundle state) {
    super.onCreate(state); java.util.TimeZone.setDefault(VN_TZ);
    final long boot=Diagnostics.now();Diagnostics.start(this);io.execute(()->ReportJob.installBundled(this));
    readyLibrary=LibraryState.peek();
    store = Store.get(this);
    prefs = getSharedPreferences("settings", 0);
    if(!prefs.getBoolean("auto_wifi_v6",false))prefs.edit().putBoolean("charging",true).putBoolean("auto_wifi_v6",true).commit();
    touchLocked=prefs.getBoolean("touch_lock",false);
    if(!prefs.getBoolean("migrated_font_v3",false)){prefs.edit().putInt("font2",2).putBoolean("migrated_font_v3",true).apply();}
    updates =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            if(i.hasExtra("diagnostic_status")){if(Diagnostics.foreground)Toast.makeText(MainActivity.this,i.getStringExtra("diagnostic_status"),Toast.LENGTH_LONG).show();return;}
            String text = i.getStringExtra("text");
            if(nativeReader!=null&&text!=null&&(text.startsWith("Đã đồng bộ đã đọc")||text.startsWith("Đang chờ gửi")||text.startsWith("Chưa gửi Đọc hết")))nativeReader.setNotice(text);
            if(i.getBooleanExtra("snapshot_changed",false)){readyLibrary=LibraryState.peek();refreshLibraryView();if(nativeReader!=null&&readyLibrary!=null){Store.Book b=displayBook(bookId);if(b!=null&&chapterId.equals(b.current)){int remaining=readyLibrary.summary(b).remaining;if(remaining>=0)nativeReader.setRemainingChapters(remaining);}}}
            else if(i.getBooleanExtra("shelf_changed",false)||i.getBooleanExtra("books_changed",false))reloadLibrary(true,()->refreshLibraryView());
            if (status != null&&!String.valueOf(status.getText()).equals(text)) status.setText(text);
            if(i.getBooleanExtra("books_changed",false)&&!bookId.isEmpty()&&chapterId.isEmpty()&&currentList!=null&&currentList.getAdapter() instanceof BaseAdapter)((BaseAdapter)currentList.getAdapter()).notifyDataSetChanged();
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
    io.execute(()->ReadSync.schedule(this));
    powerReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){ChargeJob.kick(c);}};
    registerReceiver(powerReceiver,new IntentFilter(Intent.ACTION_POWER_CONNECTED));
    networkCallback=new android.net.ConnectivityManager.NetworkCallback(){public void onAvailable(android.net.Network n){ReadSync.schedule(MainActivity.this);if(ChargeJob.isCharging(MainActivity.this))ChargeJob.kick(MainActivity.this);}};
    getSystemService(android.net.ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback);
    HakoParser.ORIGIN=prefs.getString("origin","https://docln.sbs");
    Repository.init(this);GeckoClient.init(this);
    // Do not scan the library on the main thread before the home screen appears.
    // Use the persisted display state; validate only the selected content on its worker.
    Repository.syncPct=prefs.getInt("last_offline_percent",0);
    library();
    if(readyLibrary!=null){final UiLibrarySnapshot saved=readyLibrary;libraryIo.execute(saved::prepareIndices);}
    root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){root.getViewTreeObserver().removeOnPreDrawListener(this);Diagnostics.elapsed("startup.first_layout",boot);return true;}});
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
    t.setTypeface(UI_REGULAR);
    t.setPadding(dp(12), dp(8), dp(12), dp(8));
    return t;
  }

  private Button button(String label, Runnable r) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(Color.BLACK);
    b.setTextSize(12f);
    b.setTypeface(UI_MEDIUM);
    b.setAllCaps(false);
    b.setMinHeight(dp(30));
    b.setMinimumWidth(0);
    b.setPadding(dp(8), 0, dp(8), 0);
    android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
    btnBg.setColor(Color.WHITE);
    btnBg.setStroke(dp(1), Color.BLACK);
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

  private boolean storyInformation;
  private void reset() {storyInformation=false;
    generation++;
    caughtUpPrompt=false; caughtUpBook=null;
    cancelLockKey();
    currentList=null; currentScroll=null; status=null;listLoadStatus=null;libraryMetadata=null;activeLibraryAdapter=null;activeLibraryBooks=null;libraryListMode=0;
    nativeReader=null;
    readerReady = false;
    topBar = null; bar = null; boundaryPrompt = null; boundaryDir = 0; syncBadge = null;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setFocusableInTouchMode(true);
    root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    root.setBackgroundColor(Color.WHITE);
    FrameLayout shell=new FrameLayout(this);
    shell.addView(root,new FrameLayout.LayoutParams(-1,-1));
    setContentView(shell);
    root.requestFocus();
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
    syncBadge.setTypeface(UI_MEDIUM);
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

  private final java.util.concurrent.ExecutorService webIo=java.util.concurrent.Executors.newSingleThreadExecutor(),readerIo=java.util.concurrent.Executors.newSingleThreadExecutor(),transferIo=java.util.concurrent.Executors.newSingleThreadExecutor();
  private void task(String label,Callable<?> work,Runnable done){taskOp(OperationTrace.classify(label),label,work,done);}
  private void taskOp(String code,String label,Callable<?> work,Runnable done){
    int token=generation;long queued=Diagnostics.now();OperationTrace.Span parent=OperationTrace.current();if(status!=null)status.setText(label);
    java.util.concurrent.ExecutorService executor=code.startsWith("data.")?transferIo:code.equals("read.open")?readerIo:OperationTrace.web(code)?webIo:io;
    executor.execute(()->{OperationTrace.Span span=OperationTrace.begin(code,queued,parent);try{
      if(token!=generation&&(code.equals("read.open")||code.startsWith("toc.")||code.equals("web.list")||code.equals("book.info"))){OperationTrace.end(span,"obsolete",0);return;}
      Diagnostics.elapsed("worker.wait",queued);long begun=Diagnostics.now();work.call();Diagnostics.elapsed("worker.run",begun);OperationTrace.workDone(span);
      runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=generation){OperationTrace.end(span,"obsolete",0);return;}long render=Diagnostics.now();OperationTrace.Span previous=OperationTrace.bind(span);String outcome="ok";try{if(done!=null)done.run();}catch(RuntimeException error){outcome="ui_error";throw error;}finally{Diagnostics.elapsed("ui.callback",render);OperationTrace.end(span,outcome,Diagnostics.now()-render);OperationTrace.restore(previous);}});
    }catch(Exception error){OperationTrace.end(span,error.getClass().getSimpleName(),0);Diagnostics.failure("worker",error);runOnUiThread(()->{if(isFinishing()||isDestroyed()||token!=generation)return;if(status!=null)status.setText("Chưa hoàn tất");message(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());});}});
  }

  private void leaveReader(){visitSession="";String old=Repository.activeBook;Repository.activeBook="";if(!old.isEmpty())io.execute(()->store.clearTemporary(old));}
  private void exitApp(){isSyncingShelf=false;saveThen(()->{leaveReader();Repository.cancel.set(true);stopService(new Intent(this,DownloadService.class));finishAndRemoveTask();});}
  private void library() {
    navStack.clear();
    leaveReader();reset();bookId="";chapterId="";
    reloadLibrary(false,null);
    LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.addView(text("HAKO POCKET",20),new LinearLayout.LayoutParams(0,-2,1));
    Button report=button("Báo cáo",this::reportDialog);report.setTextSize(11);report.setPadding(dp(4),0,dp(4),0);report.setContentDescription("Gửi báo cáo");heading.addView(report,new LinearLayout.LayoutParams(dp(82),dp(36)));root.addView(heading);
    root.addView(text("Đọc nhẹ • Xteink S4 • v"+appVersion(),11));
    LinearLayout grid=new LinearLayout(this);
    grid.setOrientation(1);
    boolean paused=prefs.getBoolean("sync_paused",false);
    String[] names={
      "Đọc tiếp","Vừa đọc","Yêu thích",
      "Tủ sách","Mới cập nhật","Đã tải xong",
      "Cập nhật",paused?"Tiếp tục":"Tạm dừng","Tìm kiếm",
      "Tài khoản","Cài đặt","Thoát"
    };
    int[] iconKinds={12,7,9,1,8,17,8,paused?12:20,10,11,13,6};
    Runnable[] actions={
      ()->{Store.Book b=displayBook(prefs.getString("lastBook",""));if(b!=null){navStack.push(this::library);openBook(b);}else bookList(false);},
      ()->{navStack.push(this::library);bookList(false);},
      ()->{navStack.push(this::library);frequentList();},
      ()->{navStack.push(this::library);bookList(true);},
      ()->{navStack.push(this::library);onlineList("Mới cập nhật",HakoParser.ORIGIN+"/danh-sach?truyendich=1&sapxep=capnhat");},
      ()->{navStack.push(this::library);frequentList(true);},
      ()->startDownloads("",Repository.MODE_SYNC_PERSONAL),
      ()->{boolean resume=prefs.getBoolean("sync_paused",false);Repository.setSyncPaused(this,!resume);library();if(resume)startDownloads("",Repository.MODE_SYNC_LIBRARY);},
      this::searchDialog,
      ()->browse(HakoParser.ORIGIN+"/login"),
      this::settings,
      this::exitApp
    };
    int[] homeOrder={0,1,2,3,4,5,6,7,8,9,10,11};
    int cols=3;
    for(int n=0;n<homeOrder.length;n+=cols){
      LinearLayout line=new LinearLayout(this);
      line.setOrientation(0);
      for(int k=n;k<n+cols&&k<homeOrder.length;k++){
        final int j=homeOrder[k]; final int x=j;
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(1);cell.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable cellBg = new android.graphics.drawable.GradientDrawable();
        cellBg.setColor(Color.WHITE);
        cellBg.setStroke(dp(1), Color.BLACK);
        cellBg.setCornerRadius(dp(8));
        cell.setBackground(cellBg);
        cell.setPadding(dp(2), dp(6), dp(2), dp(6));

        IconButton icon=new IconButton(this,iconKinds[j],names[j],false,actions[j]);
        cell.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView label=new TextView(this);
        label.setText(names[j]);
        label.setTextSize(16f);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextColor(Color.BLACK);
        label.setTypeface(UI_MEDIUM);
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
    root.addView(grid,new LinearLayout.LayoutParams(-1,0,1f));
    Store.Book last=displayBook(prefs.getString("lastBook",""));
    TextView summary=text(last==null?"Chọn truyện để đọc · Đồng bộ trước khi tắt Wi-Fi":"Đọc tiếp: "+last.title,15);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(summary,new LinearLayout.LayoutParams(-1,-2));
    status();
  }
    private void onlineList(String title, String url) {
    leaveReader();reset();bookId="";chapterId="";
    String[] categories={"Truyện Dịch","Convert","Sáng Tác","Mới Đăng","Hoàn Thành"};
    String[] queries={"truyendich=1&sapxep=capnhat","convert=1&sapxep=capnhat",
        "sangtac=1&sapxep=capnhat","truyendich=1&sapxep=truyenmoi",
        "hoanthanh=1&sapxep=capnhat"};
    LinearLayout header=new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(new IconButton(this,4,"Quay lại",this::goBack),new LinearLayout.LayoutParams(dp(42),dp(42)));
    TextView heading=new TextView(this);
    heading.setText(title); heading.setTextSize(16); heading.setTypeface(UI_MEDIUM);
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
          cardBg.setStroke(dp(1), Color.BLACK);
          cardBg.setCornerRadius(dp(6));
          card.setBackground(cardBg);
          card.setPadding(dp(10), dp(2), dp(10), dp(2));

          TextView titleView = new TextView(MainActivity.this);
          titleView.setText(item.title);
          titleView.setTextSize(16f);
          titleView.setTypeface(UI_MEDIUM);
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
        openStoryInfo(stories.get(p),false);
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

  private void bookList(boolean shelf){openLibraryList(shelf?1:4);}
  private void renderBookList(boolean shelf){
    leaveReader();reset();bookId="";chapterId="";libraryListMode=shelf?1:4;
    LinearLayout topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.VERTICAL);
    topBar.setPadding(dp(10), dp(3), dp(10), dp(2));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView headerTitle = new TextView(this);
    headerTitle.setText(shelf ? "Tủ sách" : "Vừa đọc");
    headerTitle.setTextSize(18f);
    headerTitle.setTypeface(UI_MEDIUM);
    headerTitle.setTextColor(INK);
    headerTitle.setSingleLine(true);
    header.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

    header.addView(new IconButton(this,0,"Trang chính",this::library), new LinearLayout.LayoutParams(dp(40), dp(38)));

    final TextView syncStatus = new TextView(this);
    IconButton btnSync = new IconButton(this,shelf?8:7,shelf?"Cập nhật":"Lịch sử",()->{
      if(shelf){Repository.cancel.set(false);syncStatus.setText("Đã nhận yêu cầu · kiểm tra theo neo đã biết");startDownloads("",Repository.MODE_SYNC_LIBRARY);}
      else browse(HakoParser.ORIGIN+"/lich-su-doc");
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

    syncStatus.setText(libraryMetadataText(libraryListMode,libraryBooks(libraryListMode).size()));
    libraryMetadata=syncStatus;
    syncStatus.setTextSize(11f);
    syncStatus.setTextColor(MUTED);
    syncStatus.setSingleLine(true);
    syncStatus.setPadding(0, dp(1), 0, 0);
    topBar.addView(syncStatus,new LinearLayout.LayoutParams(-1,libraryLineHeight(syncStatus)));
    // Both metadata lines reserve their height before any data arrives.

    addLibraryLoadStatus(topBar);
    root.addView(topBar);

    List<Store.Book> books = libraryBooks(libraryListMode);
    activeLibraryBooks=books;

    PagedBookList list = new PagedBookList(this);
    list.cardsWithControls(!shelf);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(null);
    list.setDividerHeight(dp(3));
    list.setPadding(dp(6), dp(6), dp(6), dp(4));
    list.setClipToPadding(true);



    activeLibraryAdapter=new ArrayAdapter<Store.Book>(this, 0, books) {
      public View getView(int p, View convert, ViewGroup parent) {
        Store.Book b = getItem(p);
        LinearLayout card = new LinearLayout(MainActivity.this);
        card.setOrientation(LinearLayout.VERTICAL);
        // A focusable download child suppresses ListView's item click. Own the
        // card action explicitly; the download child keeps its separate click.
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        card.setOnClickListener(v -> {
          navStack.push(() -> bookList(shelf));
          openBook(b);
        });
        card.setOnLongClickListener(v -> {
          bookMenu(b);
          return true;
        });
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutParams(new AbsListView.LayoutParams(-1, list.rowHeight(p)));

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1), Color.BLACK);
        cardBg.setCornerRadius(dp(4));
        card.setBackground(cardBg);
        card.setPadding(dp(8), dp(2.5f), dp(8), dp(2.5f));

        LinearLayout top = new LinearLayout(MainActivity.this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setText(b.title);
        titleView.setTextSize(16f);
        titleView.setTypeface(UI_MEDIUM);
        titleView.setTextColor(Color.BLACK);
        titleView.setIncludeFontPadding(false);
        titleView.setMaxLines(shelf?2:Math.max(1,Math.min(2,(list.rowHeight(p)-dp(43))/Math.max(1,titleView.getLineHeight()))));
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        UiLibrarySnapshot.Summary summary=readyLibrary.summary(b);
        int newCount = summary.newCount;
        if (newCount > 0) {
          TextView badge = new TextView(MainActivity.this);
          badge.setText("+" + newCount + " mới");
          badge.setTextSize(10f);
          badge.setTextColor(Color.WHITE);
          badge.setBackgroundColor(Color.BLACK);
          badge.setTypeface(UI_MEDIUM);
          badge.setPadding(dp(4), dp(1), dp(4), dp(1));
          LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
          lpBadge.setMargins(dp(5), 0, 0, 0);
          top.addView(badge, lpBadge);
        }
        card.addView(top);

        int unread=summary.remaining;

        StringBuilder sub = new StringBuilder();
        if (b.stamp > 0 && !summary.currentTitle.isEmpty()) {
          sub.append(summary.currentRead?"Đã đọc tới: ":"Đang đọc: ").append(summary.currentTitle);
          if (unread > 0) sub.append(" · còn ").append(unread).append(" ch");
          sub.append(" · ").append(formatVnDate(b.stamp, "dd/MM"));
        } else if (summary.total>0) {
          sub.append(summary.total).append(" chương · Chưa đọc");
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
        subView.setText((summary.missingImages>0?"Thiếu "+summary.missingImages+" ảnh · ":"")+subView.getText()+" · "+summary.readLabel());
        if(shelf)card.addView(subView);
        else {
          LinearLayout controls=new LinearLayout(MainActivity.this);controls.setGravity(Gravity.CENTER_VERTICAL);
          controls.addView(subView,new LinearLayout.LayoutParams(0,-2,1));
        if (!shelf) {
          controls.addView(new IconButton(MainActivity.this,18,"Tải toàn bộ: "+b.title,
              ()->startDownloads(b.id,Repository.MODE_BOOK_ALL)),
              new LinearLayout.LayoutParams(dp(42),dp(38)));
          CheckBox completed=new CheckBox(MainActivity.this);completed.setText("Hoàn thành");completed.setTextSize(12f);completed.setSingleLine(true);completed.setTextColor(INK);completed.setButtonTintList(android.content.res.ColorStateList.valueOf(INK));completed.setPadding(0,0,0,0);completed.setMinHeight(0);completed.setMinimumHeight(0);
          completed.setContentDescription("Truyện đã hoàn thành: "+b.title);completed.setChecked(store.isCompleted(b.id));
          completed.setOnCheckedChangeListener((v,checked)->{store.setCompleted(b.id,checked);if(checked)startDownloads(b.id,Repository.MODE_BOOK_ALL);});
          controls.addView(completed,new LinearLayout.LayoutParams(dp(108),dp(38)));
        }
          card.addView(controls);
        }


        return card;
      }
    };
    list.setAdapter(activeLibraryAdapter);
    list.setOnItemClickListener((a, v, p, id) -> { navStack.push(() -> bookList(shelf)); openBook(books.get(p)); });
    list.setOnItemLongClickListener((a, v, p, id) -> { bookMenu(books.get(p)); return true; });
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

  }
  private boolean hasNew(Store.Book b){try{return Integer.parseInt(b.shelfInfo.split(" ")[0])>0;}catch(Exception e){return false;}}
  private void frequentList(){frequentList(false);}
  private void frequentList(boolean completedOnly){openLibraryList(completedOnly?3:2);}
  private void renderFrequentList(boolean completedOnly){
    leaveReader();reset();bookId="";chapterId="";libraryListMode=completedOnly?3:2;
    LinearLayout topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.VERTICAL);
    topBar.setPadding(dp(10), dp(3), dp(10), dp(2));

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView headerTitle = new TextView(this);
    headerTitle.setText(completedOnly?"Đã tải xong":"Yêu thích");
    headerTitle.setTextSize(18f);
    headerTitle.setTypeface(UI_MEDIUM);
    headerTitle.setTextColor(INK);
    headerTitle.setSingleLine(true);
    header.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

    header.addView(new IconButton(this,0,"Trang chính",this::library), new LinearLayout.LayoutParams(dp(40), dp(38)));
    topBar.addView(header);

    List<Store.Book> books = libraryBooks(libraryListMode);
    activeLibraryBooks=books;

    TextView infoView = new TextView(this);
    infoView.setText((completedOnly?"Bạn đánh dấu hoàn thành · Đã tải đủ · ":"Đọc nhiều trước · Truyện ghim để dễ tìm · ") + books.size() + " bộ");
    infoView.setTextSize(11f);
    infoView.setTextColor(MUTED);
    infoView.setSingleLine(true);
            infoView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    infoView.setPadding(0, dp(1), 0, 0);
    libraryMetadata=infoView;
    topBar.addView(infoView,new LinearLayout.LayoutParams(-1,libraryLineHeight(infoView)));

    addLibraryLoadStatus(topBar);
    root.addView(topBar);

    PagedBookList list = new PagedBookList(this);
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(null);
    list.setDividerHeight(dp(3));
    list.setPadding(dp(6), dp(6), dp(6), dp(4));
    list.setClipToPadding(true);



    activeLibraryAdapter=new ArrayAdapter<Store.Book>(this, 0, books) {
      public View getView(int p, View convert, ViewGroup parent) {
        Store.Book b = getItem(p);
        LinearLayout card = new LinearLayout(MainActivity.this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutParams(new AbsListView.LayoutParams(-1, list.rowHeight(p)));

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setStroke(dp(1), Color.BLACK);
        cardBg.setCornerRadius(dp(4));
        card.setBackground(cardBg);
        card.setPadding(dp(8), dp(2.5f), dp(8), dp(2.5f));

        LinearLayout top = new LinearLayout(MainActivity.this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(MainActivity.this);
        titleView.setText((b.pinned ? "★ " : store.isKeepFull(b.id) ? "↓ " : "") + b.title);
        titleView.setTextSize(16f);
        titleView.setTypeface(UI_MEDIUM);
        titleView.setTextColor(Color.BLACK);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        UiLibrarySnapshot.Summary summary=readyLibrary.summary(b);
        int totalChs=summary.total;
        boolean fullMode=summary.full;
        int newChsTotal=totalChs,newChsDownloaded=summary.stored;
        int downloadPct=totalChs==0?0:Math.min(summary.stored==totalChs?100:99,Math.round(summary.stored*100f/totalChs));

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
        badge.setTypeface(UI_MEDIUM);
        badge.setPadding(dp(4), dp(1), dp(4), dp(1));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
        lpBadge.setMargins(dp(5), 0, 0, 0);

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
        int storedAll=summary.stored;
        subView.setText((summary.missingImages>0?"Thiếu "+summary.missingImages+" ảnh · ":"")+"Chữ "+storedAll+"/"+totalChs+" · "+summary.readLabel()+(summary.newCount>0?" · "+summary.newCount+" mới":""));
        int wholePct=totalChs==0?0:Math.min(storedAll==totalChs?100:99,Math.round(storedAll*100f/totalChs));badge.setText("CHỮ "+wholePct+"%");
        LinearLayout meta=new LinearLayout(MainActivity.this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.addView(subView,new LinearLayout.LayoutParams(0,-2,1));meta.addView(badge);card.addView(meta);

        return card;
      }
    };
    list.setAdapter(activeLibraryAdapter);
    list.setOnItemClickListener((a, v, i, id) -> { navStack.push(()->frequentList(completedOnly)); openBookContents(books.get(i)); });
    list.setOnItemLongClickListener((a, v, i, id) -> { bookMenu(books.get(i)); return true; });
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
  }
  private void saveBook(){final String bid=bookId;Repository.cancel.set(false);
    task("Đang lưu lên HAKO…",()->{Store.Book b=store.book(bid);if(b==null)throw new IOException("Không tìm thấy truyện");if(!b.followed){RenderedPage.action(this,b.url,"follow");store.followed(b.id,true,b.shelfRank);}return null;},()->Toast.makeText(this,"Đã lưu vào tủ sách HAKO",Toast.LENGTH_SHORT).show());
  }
  private boolean caughtUpPrompt=false;
  private volatile boolean caughtUpUpdating=false;
  private Store.Book caughtUpBook;

  private void markCaughtUp(Store.Book b){
    if(b!=null&&!caughtUpUpdating)submitCaughtUp(b);
  }

  private void submitCaughtUp(Store.Book b){if(!caughtUpUpdating)submitCaughtUpConfirmed(b,false);}
  private void submitCaughtUpConfirmed(Store.Book b,boolean acceptIllustrations){
    if(b==null||caughtUpUpdating)return;caughtUpUpdating=true;
    final String[] failure={""};
    task("Đang lưu đã đọc…",()->{try{if(store.unread(b.id)>0)throw new IOException("Chưa gửi Đọc hết lên HAKO · "+store.readCounts(store.chapters(b.id)).label());store.queueCaughtUp(b.id,acceptIllustrations);ReadSync.schedule(this);}catch(Exception e){failure[0]=e.getMessage();}finally{caughtUpUpdating=false;}return null;},()->{if(!failure[0].isEmpty()){message(failure[0]);return;}if(nativeReader!=null)nativeReader.setNotice("Đã đọc · Chờ đồng bộ HAKO");if(status!=null)status.setText("Đã lưu hàng chờ HAKO");});
  }

  private void readerMenu(){new AlertDialog.Builder(this).setItems(new String[]{"Chương trước","Chương tiếp","Đánh dấu đoạn","Ẩn thanh công cụ","Tải trước / tiếp tục","Dừng tải","Mở HAKO","Chạm lật trang: "+(prefs.getBoolean("taps",true)?"Bật":"Tắt"),"Đầu chương","Cuối chương","Đã bắt kịp trên HAKO","Tải lại chương này","Chỉ tải lại ảnh thiếu"},(d,w)->{
    if(w==0)saveThen(()->adjacent(-1));if(w==1)saveThen(()->adjacent(1));if(w==2)bookmarks();if(w==3){bar.setVisibility(View.GONE);Toast.makeText(this,"Vuốt dọc rồi chạm để hiện công cụ",Toast.LENGTH_SHORT).show();}if(w==4)startDownloads(bookId,Repository.MODE_BOOK_NEXT);if(w==5)Repository.cancel.set(true);if(w==6){final String bid=bookId,cid=chapterId;final String[] url={""};task("Đang mở HAKO…",()->{Store.Chapter ch=store.chapter(bid,cid);if(ch!=null)url[0]=ch.url;return null;},()->{if(!url[0].isEmpty())browse(url[0]);});}if(w==7){boolean t=!prefs.getBoolean("taps",true);prefs.edit().putBoolean("taps",t).apply();if(nativeReader!=null)nativeReader.taps(t);}if(w==8&&nativeReader!=null)nativeReader.jump(false);if(w==9&&nativeReader!=null)nativeReader.jump(true);if(w==10)markCaughtUp(displayBook(bookId));if(w==12){final String bid=bookId,cid=chapterId;task("Đang tải ảnh còn thiếu…",()->{Repository.cancel.set(false);Repository.downloadImages(this,store.chapter(bid,cid));return null;},()->showReader(bid,cid));}if(w==11){final String bid=bookId,cid=chapterId;task("Tải lại chương…",()->{Repository.cancel.set(false);Repository.download(this,store.chapter(bid,cid),true);return null;},()->showReader(bid,cid));}}).show();}
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

  private void startDownloads(String id, int mode) {OperationTrace.Span trace=OperationTrace.begin(mode==Repository.MODE_READER_NEXT?"download.prefetch":"download.enqueue");try{if(id!=null&&!id.isEmpty())store.allowDownloads(id);
    if(prefs.getBoolean("sync_paused",false)){if(mode==Repository.MODE_BOOK_NEXT||mode==Repository.MODE_READER_NEXT)return;Repository.setSyncPaused(this,false);}
    if(mode==Repository.MODE_BOOK_ALL && !id.isEmpty()) {
      store.setKeepFull(id,true);

    }
    if(mode==Repository.MODE_SYNC_LIBRARY){Repository.notify(this,Repository.busy.get()?"Đã nhận Cập nhật · chờ tác vụ hiện tại kết thúc":"Đã nhận Cập nhật · chuẩn bị kết nối HAKO");libraryHandler.removeCallbacks(libraryClock);libraryHandler.postDelayed(libraryClock,1000);}
    Intent i = new Intent(this, DownloadService.class).putExtra("book", id).putExtra("mode", mode);
    startForegroundService(i);
  }finally{OperationTrace.end(trace,"complete",0);}}

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
              b.pinned ? "Bỏ ghim trong Yêu thích" : "Ghim trong Yêu thích (không tăng ưu tiên tải)",
              "Đã bắt kịp trên HAKO",
              "Thông tin truyện",
              "Xóa chương đã tải (giữ thông tin)"
            },
            (d, w) -> {
              if (w == 0) contents(b);
              if (w == 1) {
                startDownloads(b.id, Repository.MODE_BOOK_ALL);
                Toast.makeText(this, "Bắt đầu tải tất cả chương: " + b.title, Toast.LENGTH_SHORT).show();
              }
              if (w == 2) {
                boolean next = !autoDl;
                prefs.edit().putBoolean("auto_dl_" + b.id, next).apply();if(next)store.allowDownloads(b.id);
                Toast.makeText(this, next ? "Đã BẬT tự động tải khi sạc cho bộ này" : "Đã TẮT tự động tải khi sạc cho bộ này", Toast.LENGTH_SHORT).show();
              }
              if (w == 3)
                task(
                    "Cập nhật mục lục…",
                    () -> {
                      Repository.catalog(this, b,true);
                      return null;
                    },
                    () -> contents(b));
              if (w == 4) browse(b.url);
              if (w == 5) task("Đang lưu theo dõi…",()->{store.dropped(b.id,!b.dropped);return null;},()->bookList(b.followed));
              if (w == 6) task("Đang lưu ghim…",()->{store.pin(b.id,!b.pinned);return null;},this::frequentList);
              if (w == 7) markCaughtUp(b);
              if(w==8)openStoryInfo(new HakoParser.Link(b.id,b.title,b.url),false);
              if(w==9)clearDownloadedDialog(b);
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

  private void openStoryInfo(HakoParser.Link link,boolean force){
    leaveReader();reset();bookId=link.id;chapterId="";root.addView(text(link.title,19));root.addView(button("Quay lại",this::goBack));status();
    final StoryInfo[] cached={null};final Store.Book[] book={null};final TocState.Entry[] detail={null};final List<VolumeGroup>[] groups=new List[]{null};
    taskOp("book.info_cache","Đang mở thông tin truyện…",()->{try{cached[0]=StoryInfo.load(this,link.id);}catch(Exception ignored){}book[0]=store.book(link.id);if(book[0]!=null){detail[0]=TocState.load(this,link.id);if(detail[0]==null)detail[0]=TocState.refresh(this,link.id);groups[0]=preparedGroups(detail[0]);}return null;},()->{
      if(cached[0]!=null){tocEntry=detail[0];tocGroups=groups[0]==null?new ArrayList<>():groups[0];renderStoryInfo(cached[0],book[0]);}
      if(force||cached[0]==null||(!link.latestKey.isEmpty()&&!link.latestKey.equals(cached[0].latestKey)))fetchStoryInfo(link);
    });
  }
  private void fetchStoryInfo(HakoParser.Link link){final StoryInfo[] info={null};final Store.Book[] book={null};final TocState.Entry[] detail={null};final List<VolumeGroup>[] groups=new List[]{null};
    taskOp("book.info","Đang lấy thông tin truyện…",()->{Repository.init(this);Repository.allowed(this);String html=Repository.page(link.url,true);info[0]=StoryInfo.parse(html,link.url);if(info[0].id.isEmpty())throw new IOException("Không nhận dạng được truyện");store.putBook(new HakoParser.Link(info[0].id,info[0].title,info[0].url));java.util.List<HakoParser.Link> chapters=HakoParser.chapters(html,link.url);if(!chapters.isEmpty())store.catalog(info[0].id,chapters);info[0].save(this);book[0]=store.book(info[0].id);detail[0]=TocState.refresh(this,info[0].id);groups[0]=preparedGroups(detail[0]);return null;},()->{tocEntry=detail[0];tocGroups=groups[0];renderStoryInfo(info[0],book[0]);});
  }
  private void renderStoryInfo(StoryInfo info,Store.Book b){
    if(info==null)return;reset();storyInformation=true;bookId=info.id;chapterId="";
    LinearLayout header=new LinearLayout(this);header.addView(new IconButton(this,4,"Quay lại",this::goBack),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView heading=text(info.title,17);heading.setMaxLines(2);heading.setTypeface(UI_MEDIUM);header.addView(heading,new LinearLayout.LayoutParams(0,dp(52),1));if(b!=null)header.addView(new IconButton(this,15,"Menu truyện",()->bookMenu(b)),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
    root.addView(text("Tác giả: "+(info.author.isEmpty()?"Chưa rõ":info.author)+" · "+info.status,12));if(!info.artist.isEmpty())root.addView(text("Họa sĩ: "+info.artist,12));
    String updated=info.updatedText;if(info.updatedAt>0&&info.updatedAt<=System.currentTimeMillis()){long age=System.currentTimeMillis()-info.updatedAt;updated=age<3600000?(age/60000)+" phút trước":age<86400000?(age/3600000)+" giờ trước":(age/86400000)+" ngày trước";}
    root.addView(text("Cập nhật: "+updated+" · "+info.chapters+" chương",12));
    ScrollView summary=new ScrollView(this);TextView body=text(info.summary.isEmpty()?"Chưa có giới thiệu đã lưu.":info.summary,14);body.setPadding(dp(6),dp(4),dp(6),dp(4));summary.addView(body);root.addView(summary,new LinearLayout.LayoutParams(-1,dp(128)));currentScroll=summary;summary.setContentDescription("Giới thiệu truyện");
    LinearLayout tools=new LinearLayout(this);Button order=button(prefs.getBoolean("chapter_desc_"+info.id,false)?"Mới trước ⇅":"Cũ trước ⇅",()->{prefs.edit().putBoolean("chapter_desc_"+info.id,!prefs.getBoolean("chapter_desc_"+info.id,false)).apply();renderStoryInfo(info,b);});tools.addView(order,new LinearLayout.LayoutParams(0,dp(40),1));tools.addView(button("Làm mới",()->openStoryInfo(new HakoParser.Link(info.id,info.title,info.url),true)),new LinearLayout.LayoutParams(dp(100),dp(40)));root.addView(tools);
    if(b!=null&&tocEntry!=null&&info.id.equals(tocEntry.book.id)){List<VolumeGroup> groups=new ArrayList<>(tocGroups);if(prefs.getBoolean("chapter_desc_"+info.id,false))Collections.reverse(groups);PagedBookList list=new PagedBookList(this);currentList=list;list.setAdapter(new BaseAdapter(){public int getCount(){return groups.size();}public Object getItem(int n){return groups.get(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup parent){VolumeGroup group=groups.get(n);TextView row=text(group.name+"\n"+group.chapters.size()+" chương · "+group.readSummary,14);row.setPadding(dp(8),dp(10),dp(8),dp(10));return row;}});list.setOnItemClickListener((parent,view,n,id)->{tocSection=groups.get(n).name;volumeChapters(b,groups.get(n));});root.addView(list,new LinearLayout.LayoutParams(-1,0,1));}
    else root.addView(text("Chưa có mục lục đã lưu.",13));
    summary.setOnTouchListener((view,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)summary.requestFocus();return false;});summary.setFocusableInTouchMode(true);status();
  }
  private void clearDownloadedDialog(Store.Book b){new AlertDialog.Builder(this).setTitle("Xóa chương đã tải?").setMessage("Chỉ xóa chữ và ảnh đã tải của bộ này. Giữ thông tin, mục lục, yêu thích và tiến trình đọc. Tải nền của bộ này dừng cho đến khi bạn chọn tải lại.").setPositiveButton("Xóa chương",(d,w)->taskOp("book.clear","Đang xóa chương đã tải…",()->{store.clearDownloaded(b.id);return null;},()->{preparedCache=null;preparedCacheKey="";message("Đã xóa nội dung tải về; thông tin và tiến trình truyện được giữ.");})).setNegativeButton("Giữ lại",null).show();}

  private int resolveTargetIndex(Store.Book b, List<Store.Chapter> chs) {
    // List rows and Back callbacks may still carry the position from before reading.
    List<String> ids=new ArrayList<>();Set<String> images=new HashSet<>(),read=new HashSet<>();
    String current=b.current,tip=prefs.getString("read_tip_"+b.id,"");
    for(Store.Chapter chapter:chs){ids.add(chapter.id);if(store.ignoredForReading(chapter))images.add(chapter.id);}
    int end=ids.indexOf(tip);for(int i=0;i<=end;i++)read.add(ids.get(i));
    if(images.contains(current))current=prefs.getString("reading_text_"+b.id,"");
    int index=ReadingNavigation.resume(ids,current,read,images);
    return index<0?0:index; // An all-image book remains manually accessible from its TOC.
  }

  private void openBookContents(Store.Book b){if(b==null)return;contents(b);}
  private void chapterHeader(Store.Book b,Store.Chapter target,String section,String count){
    TextView name=text(b.title,17);name.setTypeface(UI_MEDIUM);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(8),dp(3),dp(8),dp(3));root.addView(name);
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(new IconButton(this,4,"Quay lại",this::goBack),new LinearLayout.LayoutParams(dp(48),dp(44)));
    Button resume=button(b.current.isEmpty()?"Bắt đầu":"Đọc tiếp",()->openChapter(b.id,""));resume.setTextSize(12);resume.setPadding(dp(3),0,dp(3),0);
    header.addView(resume,new LinearLayout.LayoutParams(0,dp(44),1));
    header.addView(new IconButton(this,b.pinned?19:9,b.pinned?"Bỏ ghim":"Ghim để dễ tìm",()->task("Đang lưu ghim…",()->{Store.Book latest=store.book(b.id);if(latest!=null)store.pin(b.id,!latest.pinned);return null;},()->contents(b))),new LinearLayout.LayoutParams(dp(48),dp(44)));
    header.addView(new IconButton(this,18,"Tải toàn bộ",()->startDownloads(b.id,Repository.MODE_BOOK_ALL)),new LinearLayout.LayoutParams(dp(48),dp(44)));
    Button sort=button(prefs.getBoolean("chapter_desc_"+b.id,false)?"Mới trước ⇅":"Cũ trước ⇅",()->{
      prefs.edit().putBoolean("chapter_desc_"+b.id,!prefs.getBoolean("chapter_desc_"+b.id,false)).apply();
      if(tocEntry!=null&&b.id.equals(tocEntry.book.id)){if(tocSection.isEmpty())renderContents(tocEntry.book,tocEntry.chapters,tocGroups);else for(VolumeGroup group:tocGroups)if(group.name.equals(tocSection)){renderVolumeChapters(tocEntry.book,group,tocEntry.chapters,tocEntry.read,group.readSummary);break;}}
    });sort.setTextSize(11);header.addView(sort,new LinearLayout.LayoutParams(dp(92),dp(44)));
    header.addView(new IconButton(this,15,"Menu truyện",()->bookMenu(b)),new LinearLayout.LayoutParams(dp(48),dp(44)));
    root.addView(header);
    TextView info=new TextView(this);info.setText(section+" · "+count);info.setTextSize(12);info.setTextColor(MUTED);
    info.setSingleLine(true);info.setEllipsize(android.text.TextUtils.TruncateAt.END);info.setPadding(dp(8),dp(2),dp(8),dp(4));root.addView(info);
  }

  private void openBook(Store.Book b){if(b!=null){navStack.push(()->contents(b));openChapter(b.id,"");}}
  private void openBookDirect(Store.Book b){openBook(b);}

  private static class VolumeGroup {
    String name;
    String readSummary="";
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
        if(HakoParser.readingShortcutTitle(c.title))continue;
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
    list.removeIf(group->group.chapters.isEmpty());
    return list;
  }

  private TocState.Entry tocEntry;private List<VolumeGroup> tocGroups;private String tocSection="";
  private List<VolumeGroup> preparedGroups(TocState.Entry entry){List<VolumeGroup> groups=groupChapters(entry.chapters);for(VolumeGroup group:groups)group.readSummary=groupLabel(entry,group);return groups;}
  private String groupLabel(TocState.Entry entry,VolumeGroup group){int unread=0;for(Store.Chapter ch:group.chapters)if(!store.ignoredForReading(ch)&&!entry.read.contains(ch.id))unread++;return unread+" chương chưa đọc";}
  private void contents(Store.Book b) {
    if(b==null)return;TocState.Entry warm=TocState.peek(b.id);
    if(warm!=null&&warm==tocEntry&&tocGroups!=null){Diagnostics.count("toc.ui_hit");tocSection="";renderContents(warm.book,warm.chapters,tocGroups);return;}
    final TocState.Entry[] entry={null};final List<VolumeGroup> groups=new ArrayList<>();final boolean[] restored={false};
    task("Đang mở mục lục…",()->{entry[0]=TocState.load(this,b.id);restored[0]=entry[0]!=null&&warm==null;if(entry[0]==null)entry[0]=TocState.refresh(this,b.id);if(entry[0]!=null&&entry[0].chapters.isEmpty()){Repository.catalog(this,b);entry[0]=TocState.refresh(this,b.id);}if(entry[0]!=null)groups.addAll(preparedGroups(entry[0]));return null;},()->{
      if(entry[0]==null)return;tocEntry=entry[0];tocGroups=groups;tocSection="";renderContents(entry[0].book,entry[0].chapters,groups);
      if(restored[0])io.execute(()->{try{TocState.Entry fresh=TocState.refresh(this,b.id);List<VolumeGroup> updated=preparedGroups(fresh);runOnUiThread(()->{if(isDestroyed()||!b.id.equals(bookId)||!chapterId.isEmpty()||tocEntry!=entry[0])return;int position=currentList==null?0:currentList.getFirstVisiblePosition();String section=tocSection;tocEntry=fresh;tocGroups=updated;if(section.isEmpty())renderContents(fresh.book,fresh.chapters,updated);else for(VolumeGroup group:updated)if(group.name.equals(section)){renderVolumeChapters(fresh.book,group,fresh.chapters,fresh.read,group.readSummary);break;}if(currentList!=null)currentList.setSelection(position);});}catch(Exception error){Diagnostics.failure("toc.reconcile",error);}});
    });
  }

  private void renderContents(Store.Book b,List<Store.Chapter> chs,List<VolumeGroup> groups) {
    if (chs.isEmpty()) {
      message("Chưa có mục lục. Kết nối mạng và chọn Tải lại mục lục.");
      return;
    }
    if (groups.size() == 1) {
      volumeChapters(b, groups.get(0));
      return;
    }

    leaveReader();reset();bookId=b.id;chapterId="";
    int targetIdx = resolveTargetIndex(b, chs);
    Store.Chapter targetCh = chs.get(targetIdx);

    chapterHeader(b, targetCh, "Chọn tập", groups.size()+" tập · "+chs.size()+" chương");

    PagedBookList list = new PagedBookList(this);list.compact();
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
        titleView.setTextSize(16);
        titleView.setMaxLines(2);titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setTypeface(UI_MEDIUM);
        titleView.setTextColor(Color.BLACK);
        top.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (containsTarget) {
          TextView badge = new TextView(MainActivity.this);
          badge.setText("ĐANG ĐỌC");
          badge.setTextSize(11);
          badge.setTextColor(Color.WHITE);
          badge.setBackgroundColor(Color.BLACK);
          badge.setTypeface(UI_MEDIUM);
          badge.setPadding(dp(5), dp(1), dp(5), dp(1));
          LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
          lpBadge.setMargins(dp(6), 0, 0, 0);
          top.addView(badge, lpBadge);
        }
        card.addView(top);

        int downloaded = 0;
        for (Store.Chapter c : vg.chapters) if (c.ready) downloaded++;
        String info = vg.chapters.size() + " chương · Đã tải " + downloaded + "/" + vg.chapters.size() + " ch";
        TextView subView = new TextView(MainActivity.this);
        subView.setText(info);
        subView.setTextSize(12);
        subView.setSingleLine(true);subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subView.setTextColor(MUTED);
        subView.setPadding(0, dp(4), 0, 0);
        subView.setText(subView.getText()+" · "+vg.readSummary);
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
    TocState.Entry warm=TocState.peek(b.id);if(warm!=null&&warm==tocEntry){Diagnostics.count("toc.volume_hit");tocSection=vg.name;renderVolumeChapters(warm.book,vg,warm.chapters,warm.read,groupLabel(warm,vg));return;}
    final TocState.Entry[] entry={null};final List<VolumeGroup> groups=new ArrayList<>();
    task("Đang mở tập…",()->{entry[0]=TocState.refresh(this,b.id);if(entry[0]!=null)groups.addAll(preparedGroups(entry[0]));return null;},()->{if(entry[0]==null)return;tocEntry=entry[0];tocGroups=groups;for(VolumeGroup group:groups)if(group.name.equals(vg.name)){tocSection=group.name;renderVolumeChapters(entry[0].book,group,entry[0].chapters,entry[0].read,group.readSummary);return;}contents(entry[0].book);});
  }
  private void renderVolumeChapters(Store.Book b,VolumeGroup vg,List<Store.Chapter> allChs,Set<String> read,String label){
    if(allChs.isEmpty())return;
    leaveReader();reset();bookId=b.id;chapterId="";
    int targetIdx=resolveTargetIndex(b,allChs);Store.Chapter targetCh=allChs.get(targetIdx);
    chapterHeader(b,targetCh,vg.name,vg.chapters.size()+" chương · "+label);

    final List<Store.Chapter> display=new ArrayList<>(vg.chapters);
    final Map<String,String> clean=new HashMap<>();for(int i=0;i<vg.chapters.size();i++)clean.put(vg.chapters.get(i).id,i<vg.cleanTitles.size()?vg.cleanTitles.get(i):vg.chapters.get(i).title);
    if(prefs.getBoolean("chapter_desc_"+b.id,false))Collections.reverse(display);

    List<String> labels = new ArrayList<>();
    for (int n = 0; n < display.size(); n++) {
      Store.Chapter c = display.get(n);
      boolean isTarget = c.id.equals(targetCh.id);
      boolean wasRead = read.contains(c.id);
      String prefix = store.ignoredForReading(c)?"[Minh họa] ":
          isTarget?(wasRead?"✓ [Đã đọc tới] ":"▶ [Đang đọc] "):wasRead?"✓ ":"• ";
      String statusIcon = c.ready ? "↓✓" : "↓";
      String chTitle = clean.get(c.id);
      labels.add(prefix + chTitle + " (" + statusIcon + ")");
    }

    PagedBookList list = new PagedBookList(this);list.compact();
    currentList = list;
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDrawSelectorOnTop(false);
    list.setDivider(new android.graphics.drawable.ColorDrawable(Color.BLACK));
    list.setDividerHeight(dp(1));

    list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
      public View getView(int p, View v, ViewGroup parent) {
        TextView t = (TextView) super.getView(p, v, parent);
        Store.Chapter c = display.get(p);
        boolean isTarget = c.id.equals(targetCh.id);
        t.setText(labels.get(p));
        t.setTextColor(Color.BLACK);
        t.setTypeface(isTarget ? UI_MEDIUM : UI_REGULAR);
        t.setTextSize(16);
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

    list.setOnItemLongClickListener((a,v,p,id)->{message(display.get(p).title);return true;});
    list.setOnItemClickListener((a, v, p, id) -> {
      navStack.push(() -> volumeChapters(b, vg));
      openChapter(b.id, display.get(p).id);
    });

    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    // Note: Starts from TOP (Chapter 1) by default! No auto-scrolling to hide Chapter 1!
    status();
  }
  private static final class OpenResult {Store.Book book;Store.Chapter chapter;NativeReader.Prepared content;int remaining;}
  private NativeReader.Prepared preparedCache;private String preparedCacheKey="";
  private void openChapter(String bid,String requested){
    final int request=++chapterOpenRequest;final long begun=Diagnostics.now();
    if(!Repository.activeBook.equals(bid)){leaveReader();Repository.activeBook=bid;}
    Repository.cancel.set(false);final boolean visit=!visitSession.equals(bid);final OpenResult result=new OpenResult();
    final float density=getResources().getDisplayMetrics().density;final int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
    final int para=prefs.getInt("paraSpaceDp",8),indent=prefs.getInt("indentDp",9);
    if(nativeReader!=null)nativeReader.setNotice("Đang load chương…");
    task("Đang mở chương…",()->{
      if(request!=chapterOpenRequest)return null;
      Store.Selection selected=store.selectForReading(bid,requested);
      if(selected==null&&requested.isEmpty()){Store.Book book=store.book(bid);if(book!=null)Repository.catalog(this,book);selected=store.selectForReading(bid,requested);}
      if(selected==null)throw new IOException("Không tìm thấy chương trong mục lục.");
      Store.Chapter chapter=selected.chapter;
      if(!store.readable(chapter.id)){runOnUiThread(()->{if(request==chapterOpenRequest&&status!=null)status.setText("Đang tải chương đã chọn…");});Repository.download(this,chapter);if(!store.readable(chapter.id))throw new IOException("Nội dung chưa lưu thành công.");}
      if(request!=chapterOpenRequest)return null;
      File file=store.html(chapter.id);String key=chapter.id+":"+store.displayFingerprint(chapter.id)+":"+para+":"+indent+":"+density+":"+width+":"+height;
      if(key.equals(preparedCacheKey)&&preparedCache!=null){result.content=preparedCache;Diagnostics.count("reader.prepared_hit");}
      else{String html=Store.read(file);result.content=NativeReader.prepare(html,store.dir(chapter.id),density,width,height,para,indent);if(html.length()<=256000){preparedCache=result.content;preparedCacheKey=key;}else{preparedCache=null;preparedCacheKey="";}}
      result.book=selected.book;result.chapter=chapter;result.remaining=store.unread(bid);
      if(visit&&request==chapterOpenRequest)store.visited(bid);
      return null;
    },()->{if(request!=chapterOpenRequest||result.chapter==null)return;hideBoundaryPrompt();showPreparedReader(bid,result.chapter.id,result.book,result.chapter,result.content);if(nativeReader!=null)nativeReader.setRemainingChapters(result.remaining);Diagnostics.elapsed("reader.open_to_ui",begun);});
  }
  private void showReader(String bid,String cid){openChapter(bid,cid);}

  private void showPreparedReader(String bid,String cid,Store.Book b,Store.Chapter c,NativeReader.Prepared prepared){
    int pos=b.current.equals(cid)?b.pos:0;float fraction=b.current.equals(cid)?b.fraction:0;
    reset();bookId=bid;chapterId=cid;lastPos=pos;lastFraction=fraction;prefs.edit().putString("lastBook",bid).apply();
    visitSession=bid;

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
      
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,17,"Đã đọc hết / Cập nhật HAKO",()->{
      markCaughtUp(b);
    }),new LinearLayout.LayoutParams(0,dp(36),1));
    topIcons.addView(new IconButton(this,8,"Tải lại chương này",()->{
      task("Tải lại chương…",()->{
        store.state(cid,false,"");
        Repository.cancel.set(false);
        Repository.download(this,store.chapter(bid,cid),true);
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
      private int persisted=Integer.MIN_VALUE;private float persistedFraction;private boolean completed;
      public void position(int p,float f,int page,int count){
        if(isFinishing()||isDestroyed()||!bid.equals(bookId)||!cid.equals(chapterId))return;
        lastPos=p;lastFraction=f;
        final boolean finalPage=page==count-1;
        boolean changed=persisted!=p||Float.compare(persistedFraction,f)!=0||(!completed&&finalPage);
        persisted=p;persistedFraction=f;boolean finish=!completed&&finalPage;completed|=finalPage;
        if(changed)io.execute(()->{
          if(store.ignoredForReading(c))return;
          store.position(bid,cid,p,f);
          if(finish){store.completeThrough(bid,cid);ReadSync.schedule(MainActivity.this);
            boolean waiting=store.queued(bid)&&store.unread(bid)==0;
            if(waiting)runOnUiThread(()->{if(bid.equals(bookId)&&cid.equals(chapterId)&&nativeReader!=null)nativeReader.setNotice("Đã đọc hết · Đang chờ gửi HAKO");});
          }
        });
        if(title!=null)title.setText(c.title+" · "+(page+1)+"/"+count);
        if(!readerReady){readerReady=true;io.execute(()->{
          store.prune(store.book(bid));
          if(!bid.equals("demo")&&store.needsReadingDownload(bid))runOnUiThread(()->{
            if(bid.equals(bookId)&&cid.equals(chapterId)&&nativeReader!=null)startDownloads(bid,Repository.MODE_READER_NEXT);
          });
        });}
      }
      public void boundary(int dir){
        if(!readerReady)return;
        final List<Store.Chapter>[] available=new List[]{null};
        task("Đang chuyển chương…",()->{available[0]=store.chapters(bid);return null;},()->{
          if(!bid.equals(bookId)||!cid.equals(chapterId))return;
          boolean found=false;for(Store.Chapter ch:available[0])if(ch.id.equals(cid)){found=true;break;}
          if(dir>0&&found&&store.adjacentIndex(available[0],cid,1)<0)loadNextChapter();else showBoundaryPrompt(dir);
        });

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
      ()->{navStack.push(()->showReader(bid,cid));contents(b);},
      ()->saveThen(()->adjacent(1)),
      this::settings,
      this::exitApp
    };
    int[] iconKinds = {0, 4, 1, 5, 3, 6};
    for(int n=0;n<labels.length;n++)bar.addView(new IconButton(this,iconKinds[n],labels[n],actions[n]),new LinearLayout.LayoutParams(0,dp(40),1));
    bar.setVisibility(View.GONE);
    readerFrame.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    root.addView(readerFrame, new LinearLayout.LayoutParams(-1, -1));
    try{applyReaderStyle();nativeReader.prepared(prepared,pos,fraction);}catch(Exception e){message(e.getMessage());}
  }

  private void saveThen(Runnable next){if(nativeReader!=null&&readerReady){final String bid=bookId,cid=chapterId;final int p=lastPos;final float f=lastFraction;io.execute(()->store.position(bid,cid,p,f));}next.run();}

  private void adjacent(int delta) {
    final String bid=bookId,cid=chapterId;final String[] next={""};
    task("Đang chuyển chương…",()->{List<Store.Chapter> chapters=store.chapters(bid);int target=store.adjacentIndex(chapters,cid,delta);if(target>=0)next[0]=chapters.get(target).id;return null;},()->{
      if(!bid.equals(bookId)||!cid.equals(chapterId))return;
      if(!next[0].isEmpty()){if(delta<0)pendingOpenAtEnd=true;saveThen(()->openChapter(bid,next[0]));}
      else if(delta>0)loadNextChapter();else Toast.makeText(this,"Đây là chương đầu",Toast.LENGTH_SHORT).show();
    });
  }

  private volatile boolean checkingNextChapter;
  private void loadNextChapter(){
    if(checkingNextChapter)return;
    if(!ReadSync.connected(this)){if(nativeReader!=null)nativeReader.setNotice("Đang chờ mạng · Giữ tại chương hiện tại");return;}
    final String bid=bookId,cid=chapterId;checkingNextChapter=true;
    if(nativeReader!=null)nativeReader.setNotice("Đang load chương mới…");
    final String[] next={""};final boolean[] queued={false};
    task("Đang kiểm tra chương mới…",()->{try{Repository.refreshForReading(this,store.book(bid));List<Store.Chapter> chapters=store.chapters(bid);int index=store.adjacentIndex(chapters,cid,1);if(index>=0)next[0]=chapters.get(index).id;queued[0]=store.queued(bid);return null;}catch(Exception error){runOnUiThread(()->{if(bid.equals(bookId)&&cid.equals(chapterId)&&nativeReader!=null)nativeReader.setNotice("Chưa tải được chương mới · Giữ chương hiện tại");});throw error;}finally{checkingNextChapter=false;}},()->{
      if(!bid.equals(bookId)||!cid.equals(chapterId))return;
      if(!next[0].isEmpty())openChapter(bid,next[0]);
      else if(nativeReader!=null)nativeReader.setNotice(queued[0]?"Đã đọc hết · Đang chờ gửi HAKO":"Đã đến chương mới nhất");
    });
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

  private void browse(String url) {
    if(settingsDialog!=null){settingsDialog.dismiss();settingsDialog=null;}
    Repository.cancel.set(true);GeckoClient.authenticating=true;leaveReader();
    new Thread(()->{GeckoClient.release();runOnUiThread(()->{if(!isFinishing())startActivityForResult(new Intent(this,GeckoBrowserActivity.class).putExtra("url",url).putExtra("origin",HakoParser.ORIGIN),91);else GeckoClient.authenticating=false;});},"Hako login handoff").start();
  }

  private String readerFont(){int n=prefs.getInt("font2",0);return n==1?"DejaVu,serif":n==2?"sans-serif":n==3?"ReaderCustom,serif":"Tinos,serif";}
  private void domain(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(HakoParser.ORIGIN);new AlertDialog.Builder(this).setTitle("Tên miền HAKO HTTPS").setView(input).setPositiveButton("Kiểm tra",(d,w)->{String value=input.getText().toString().trim();if(!value.startsWith("https://"))value="https://"+value;final String origin=value.replaceAll("/+$","");try{java.net.URI u=java.net.URI.create(origin);if(u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!u.getPath().isEmpty())throw new Exception();}catch(Exception e){message("Chỉ nhập tên miền HTTPS, không có đường dẫn.");return;}task("Kiểm tra tên miền…",()->{String h=Repository.page(origin,false);if(!h.contains("Light Novel")&&!h.contains("HAKO"))throw new Exception("Không nhận diện trang HAKO");return null;},()->{Repository.cancel.set(true);HakoParser.ORIGIN=origin;prefs.edit().putString("origin",origin).apply();store.rebase(origin);message("Đã đổi tên miền. Đăng nhập lại nếu cần.");});}).setNegativeButton("Hủy",null).show();}
  private void webViewInfo(){
    android.content.pm.PackageInfo provider=WebView.getCurrentWebViewPackage();
    String info="Engine của app: GeckoView · "+appVersion()+"\nWebView hệ thống (app không dùng đăng nhập):\n"+
      "Gói: "+(provider==null?"Không xác định":provider.packageName)+"\n"+
      "Phiên bản: "+(provider==null?"Không xác định":provider.versionName)+"\n\n"+
      "Thiết bị: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+
      "Android: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n"+
      "CPU hỗ trợ: "+android.text.TextUtils.join(", ",Build.SUPPORTED_ABIS)+"\n"+
      "CPU 32-bit: "+android.text.TextUtils.join(", ",Build.SUPPORTED_32_BIT_ABIS)+"\n"+
      "CPU 64-bit: "+android.text.TextUtils.join(", ",Build.SUPPORTED_64_BIT_ABIS);
    new AlertDialog.Builder(this).setTitle("Thông tin WebView & thiết bị").setMessage(info)
      .setPositiveButton("Đóng",null).setNeutralButton("Sao chép",(d,w)->{
        android.content.ClipboardManager clip=getSystemService(android.content.ClipboardManager.class);
        clip.setPrimaryClip(ClipData.newPlainText("WebView & thiết bị",info));
      }).show();
  }
  private final java.util.concurrent.ExecutorService reportIo=java.util.concurrent.Executors.newSingleThreadExecutor();
  private boolean reportWorking;
  private void saveReport(boolean send){
    if(reportWorking){message("Báo cáo đang được xử lý. Bạn vẫn có thể đọc truyện.");return;}
    reportWorking=true;Toast.makeText(this,"Đang lưu báo cáo trên máy…",Toast.LENGTH_SHORT).show();
    reportIo.execute(()->{OperationTrace.Span operation=OperationTrace.begin(send?"report.send":"report.save");String result;boolean saved=false;try{
      ReportJob.installBundled(this);File file=Diagnostics.queue(this);saved=true;
      getSharedPreferences("diagnostics",0).edit().putString("manualReport",file.getName()).apply();
      result=send?ReportJob.deliver(this):"Đã lưu báo cáo trên máy · Không gửi email";
    }catch(Exception error){Diagnostics.failure("report.manual",error);result=(saved?"Đã lưu · Chưa gửi được":"Không lưu được báo cáo")+" ("+error.getClass().getSimpleName()+")";}
    OperationTrace.end(operation,result.startsWith("Đã gửi")?"sent":saved?"saved_only":"failed",0);final String notice=result;getSharedPreferences("diagnostics",0).edit().putString("status",notice).apply();runOnUiThread(()->{reportWorking=false;if(!isFinishing()&&!isDestroyed())message(notice);});});
  }
  private void reportDialog(){
    String status=getSharedPreferences("diagnostics",0).getString("status",ReportJob.configured(this)?"Sẵn sàng gửi qua Wi-Fi":"Chưa thiết lập dịch vụ email");
    new AlertDialog.Builder(this).setTitle("Báo cáo hoạt động").setMessage(status).setPositiveButton("Gửi ngay",(d,w)->saveReport(true))
      .setNeutralButton("Lưu trên máy",(d,w)->saveReport(false)).setNegativeButton("Tùy chọn",(d,w)->new AlertDialog.Builder(this).setItems(new String[]{"Xem số đo","Thiết lập gửi email","Đóng"},(a,n)->{
      if(n==0)reportIo.execute(()->{try{String summary=Diagnostics.summary(this);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())message(summary);});}catch(Exception error){runOnUiThread(()->message("Không mở được số đo"));}});
      if(n==1)reportSettings();
    }).show()).show();
  }
  private void optionalPicker(Intent intent,int request){try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException unavailable){Diagnostics.failure("picker.unavailable",unavailable);message("S4 không có trình chọn tệp. Báo cáo có thể lưu bằng nút Lưu trên máy; bản riêng đã có sẵn cấu hình email.");}}
  private void reportSettings(){
    android.content.SharedPreferences p=getSharedPreferences("diagnostics",0);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(12),dp(8),dp(12),0);
    box.addView(text("Nhập địa chỉ và mã kết nối từ dịch vụ Apps Script đã triển khai.",12));
    EditText url=new EditText(this);url.setHint("Địa chỉ Apps Script /exec");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);url.setText(p.getString("endpoint",""));box.addView(url);
    EditText token=new EditText(this);token.setHint("Mã kết nối");token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setText(p.getString("token",""));box.addView(token);
    box.addView(text("Chỉ gửi khi bạn bấm Gửi ngay. App luôn lưu log trên máy.",12));
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Thiết lập báo cáo").setView(box).setPositiveButton("Lưu",null).setNeutralButton("Nhập tệp",(d,w)->optionalPicker(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),94)).setNegativeButton("Đóng",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String endpoint=url.getText().toString().trim(),key=token.getText().toString().trim();if(!endpoint.isEmpty()&&(!ReportJob.validEndpoint(endpoint)||key.length()<16)){url.setError("Cần địa chỉ Apps Script /exec hợp lệ và mã kết nối ít nhất 16 ký tự");return;}p.edit().putString("endpoint",endpoint).putString("token",key).putBoolean("auto",false).apply();if(endpoint.isEmpty())getSystemService(android.app.job.JobScheduler.class).cancel(43);dialog.dismiss();Toast.makeText(this,"Đã lưu thiết lập báo cáo",Toast.LENGTH_SHORT).show();}));dialog.show();
  }

  private void updateDetails(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(10),dp(8),dp(10),dp(8));
    TextView report=text(UpdateReport.text(this),12);report.setTextIsSelectable(true);box.addView(report);
    ScrollView scroll=new ScrollView(this);scroll.addView(box);
    new AlertDialog.Builder(this).setTitle("Chi tiết cập nhật").setView(scroll).setPositiveButton("Đóng",null).setNeutralButton("Xuất báo cáo",(d,w)->{reportExportPending=true;optionalPicker(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"Hako-bao-cao-cap-nhat.txt"),92);}).setNegativeButton("Làm mới",(d,w)->updateDetails()).show();
  }
  private void requestFullShelf(int mode){Repository.requestShelfScan(mode);startDownloads("",Repository.MODE_SYNC_LIBRARY);}
  private ScrollView settingsScroll;
  private boolean settingsVolume(int key,KeyEvent event){if(key!=KeyEvent.KEYCODE_VOLUME_UP&&key!=KeyEvent.KEYCODE_VOLUME_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&settingsScroll!=null)settingsScroll.pageScroll(key==KeyEvent.KEYCODE_VOLUME_UP?View.FOCUS_UP:View.FOCUS_DOWN);return true;}
  private void stepChange(SeekBar model,int amount){OperationTrace.Span trace=OperationTrace.begin(String.valueOf(model.getContentDescription()));try{model.setProgress(model.getProgress()+amount);}finally{OperationTrace.end(trace,"ok",0);}}
  private SeekBar stepControl(LinearLayout box,int max,int value){SeekBar model=new SeekBar(this);model.setMax(max);model.setProgress(value);LinearLayout row=new LinearLayout(this);Button minus=button("−",()->stepChange(model,-1));Button plus=button("+",()->stepChange(model,1));TextView hint=text("",14);model.setTag(hint);hint.setGravity(Gravity.CENTER);row.addView(minus,new LinearLayout.LayoutParams(dp(64),dp(44)));row.addView(hint,new LinearLayout.LayoutParams(0,dp(44),1));row.addView(plus,new LinearLayout.LayoutParams(dp(64),dp(44)));box.addView(row);return model;}
  private void settings(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(10),0,dp(10),0);
    box.addView(text("Phím âm lượng: lên / xuống. Nút − / +: chỉnh thông số.",12));
    box.addView(text("Phông chữ (bấm đổi trực tiếp):",14));
    String[] fontNames={"Tinos","DejaVu Serif","Liberation Serif","Liberation Sans","Không chân hệ thống","Font tự nhập"};final int[] fontChoice={Math.max(0,Math.min(5,prefs.getInt("font2",2)))};Button fontSelect=button("Phông: "+fontNames[fontChoice[0]]+" ⇅",()->{});box.addView(fontSelect);
    TextView preview=text("Tiếng Việt: Nguyễn, quyển, tưởng, khuỷu — ă â ê ô ơ ư đ",18);box.addView(preview);
    TextView sizeLabel=text("Cỡ chữ",14);box.addView(sizeLabel);SeekBar size=stepControl(box,44,Math.round((prefs.getFloat("size2",22f)-12)*2));
    TextView lineLabel=text("Giãn dòng trong đoạn",14);box.addView(lineLabel);
    SeekBar line=stepControl(box,16,Math.round((prefs.getFloat("line",1.30f)-0.70f)/0.05f));
    TextView paraLabel=text("Khoảng cách giữa các đoạn",14);box.addView(paraLabel);
    SeekBar para=stepControl(box,10,prefs.getInt("paraSpaceDp",8)/2);
    TextView indentLabel=text("Thụt đầu dòng mỗi đoạn",14);box.addView(indentLabel);
    SeekBar indent=stepControl(box,10,prefs.getInt("indentDp",9)/3);
    TextView marginLabel=text("Lề mép màn hình",14);box.addView(marginLabel);SeekBar margin=stepControl(box,28,prefs.getInt("margin",5));
    CheckBox bold=check("Nét đậm (thử trên màn E Ink)",prefs.getInt("weight",400)>400);box.addView(bold);
    final android.graphics.Typeface[] fontCache=new android.graphics.Typeface[6];
    Runnable update=()->{
      float fs=12+size.getProgress()/2f;
      float ls=Math.round((0.70f+line.getProgress()*0.05f)*100f)/100f;
      int pDp=para.getProgress()*2;
      int iDp=indent.getProgress()*3;
      sizeLabel.setText("Cỡ chữ");
      lineLabel.setText("Giãn dòng");
      paraLabel.setText("Khoảng cách đoạn");
      indentLabel.setText("Thụt đầu dòng");
      marginLabel.setText("Lề mép màn hình");
      ((TextView)size.getTag()).setText(fs+" sp");((TextView)line.getTag()).setText(String.format(java.util.Locale.US,"%.2fx",ls));((TextView)para.getTag()).setText(pDp+" dp");((TextView)indent.getTag()).setText(iDp+" dp");((TextView)margin.getTag()).setText(margin.getProgress()+" dp");
      preview.setTextSize(fs);
      try{int n=fontChoice[0];android.graphics.Typeface tf=fontCache[n];if(tf==null)tf=n==0?android.graphics.Typeface.createFromAsset(getAssets(),"Tinos.ttf"):n==1?android.graphics.Typeface.createFromAsset(getAssets(),"DejaVu.ttf"):n==2?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSerif.ttf"):n==3?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSans.ttf"):n==4?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf"));fontCache[n]=tf;preview.setTypeface(tf,bold.isChecked()?1:0);}catch(Exception e){preview.setTypeface(android.graphics.Typeface.SERIF);}
    };
    SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};
    size.setOnSeekBarChangeListener(listener);line.setOnSeekBarChangeListener(listener);para.setOnSeekBarChangeListener(listener);indent.setOnSeekBarChangeListener(listener);margin.setOnSeekBarChangeListener(listener);
    fontSelect.setOnClickListener(v->{fontChoice[0]=(fontChoice[0]+1)%fontNames.length;fontSelect.setText("Phông: "+fontNames[fontChoice[0]]+" ⇅");update.run();});
    size.setContentDescription("settings.size");line.setContentDescription("settings.line");para.setContentDescription("settings.paragraph");indent.setContentDescription("settings.indent");margin.setContentDescription("settings.margin");
    bold.setOnCheckedChangeListener((a,b)->update.run());update.run();
    CheckBox taps=check("Chạm trên/dưới để lật trang",prefs.getBoolean("taps",true));box.addView(taps);CheckBox charging=check("Đồng bộ tủ sách khi sạc",prefs.getBoolean("charging",true));box.addView(charging);CheckBox touchLockBox=check("Khóa cảm ứng khi đọc (chống chạm túi, chỉ mở bằng phím cứng)",prefs.getBoolean("touch_lock",false));box.addView(touchLockBox);
    box.addView(button("Nhập font TTF / OTF",()->optionalPicker(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),88)));
    box.addView(button("Tài khoản HAKO",()->browse(HakoParser.ORIGIN+"/login")));
    box.addView(button("Chi tiết cập nhật / Xuất báo cáo lỗi",this::updateDetails));
    box.addView(button("Kiểm tra toàn bộ tủ sách",()->requestFullShelf(1)));
    box.addView(button("Đối chiếu quét nhanh với toàn bộ",()->requestFullShelf(2)));
    box.addView(button("Thông tin WebView & thiết bị",this::webViewInfo));
    box.addView(button("Sao lưu / chuyển thiết bị",()->{settingsDialog.dismiss();transferDialog();}));
    box.addView(button("Tên miền HAKO",this::domain));box.addView(button("Đọc mẫu offline",this::demo));
    box.addView(text("Tải tự động: 15 chương chữ mỗi đợt, gồm chương đang đọc. Đến chương chữ thứ 14 mới chuẩn bị đợt tiếp. Giữ 3 chương trước; Tải full thủ công giữ toàn bộ. Khóa hoặc rời app: dừng tải tự động sau chương đang tải khi dùng pin. Tải full thủ công vẫn tiếp tục. Đọc bản lưu không cần chạy trang HAKO.",12));
    ScrollView scroll=new ScrollView(this);scroll.addView(box);settingsScroll=scroll;
    settingsDialog = new AlertDialog.Builder(this).setTitle("Chữ & tải nội dung").setView(scroll).setPositiveButton("Áp dụng",(d,w)->{OperationTrace.Span trace=OperationTrace.begin("settings.apply");try{
      if(fontChoice[0]==5&&!new File(getFilesDir(),"reader-font.ttf").isFile()){message("Chưa nhập font. Đang giữ lựa chọn cũ.");return;}
      float ls=Math.round((0.70f+line.getProgress()*0.05f)*100f)/100f;
      int pDp=para.getProgress()*2;
      int iDp=indent.getProgress()*3;
      prefs.edit().putInt("font2",fontChoice[0]).putFloat("size2",12+size.getProgress()/2f).putFloat("line",ls).putInt("paraSpaceDp",pDp).putInt("indentDp",iDp).putInt("margin",margin.getProgress()).putInt("weight",bold.isChecked()?700:400).putBoolean("taps",taps.isChecked()).putBoolean("charging",charging.isChecked()).putBoolean("touch_lock",touchLockBox.isChecked()).apply();touchLocked=touchLockBox.isChecked();if(nativeReader!=null)nativeReader.setTouchLocked(touchLocked);Repository.pocketPaused=touchLocked;if(touchLocked){hideToolbar();hideBoundaryPrompt();}
      ChargeJob.schedule(this,charging.isChecked());
      if(nativeReader!=null)applyReaderStyle();
    }finally{OperationTrace.end(trace,"complete",0);}}).setNegativeButton("Đóng",null).show();settingsDialog.setOnKeyListener((dialog,key,event)->settingsVolume(key,event));scroll.requestFocus();
  }

  private volatile boolean transferWorking;
  private void transferDialog(){String path;try{path=DataTransfer.archive(this).getAbsolutePath();}catch(Exception error){message(error.getMessage());return;}new AlertDialog.Builder(this).setTitle("Sao lưu / chuyển thiết bị").setMessage("Database đã nằm trong bộ nhớ trong. Gói chuyển dữ liệu ở:\n"+path+"\n\nCopy Hako-transfer.zip bằng USB sang cùng thư mục trên thiết bị mới. Phiên đăng nhập HAKO không nằm trong gói; thiết bị mới cần đăng nhập lại.").setPositiveButton("Sao lưu đầy đủ",(d,w)->exportTransfer(true)).setNeutralButton("Chỉ thông tin",(d,w)->exportTransfer(false)).setNegativeButton("Nhận gói",(d,w)->new AlertDialog.Builder(this).setTitle("Nhận trên thiết bị mới").setMessage("Chỉ dùng khi app chưa có truyện để tránh ghi đè. Bạn đã copy Hako-transfer.zip vào thư mục được hiển thị chưa?").setPositiveButton("Nhận gói",(a,b)->importTransfer()).setNegativeButton("Đóng",null).show()).show();}
  private void exportTransfer(boolean content){if(transferWorking){message("Đang xử lý gói dữ liệu");return;}transferWorking=true;final File[] file={null};taskOp("data.export","Đang sao lưu dữ liệu…",()->{try{file[0]=DataTransfer.export(this,content);return null;}finally{transferWorking=false;}},()->message("Đã tạo gói "+(content?"đầy đủ":"thông tin")+" tại:\n"+file[0].getAbsolutePath()+"\nGói có thông tin riêng; chỉ chuyển sang thiết bị của bạn."));}
  private void importTransfer(){if(transferWorking){message("Đang xử lý gói dữ liệu");return;}transferWorking=true;taskOp("data.import","Đang nhận dữ liệu…",()->{try{DataTransfer.restore(this);return null;}finally{transferWorking=false;}},()->new AlertDialog.Builder(this).setTitle("Đã nhận dữ liệu").setMessage("Đóng app rồi mở lại để dùng dữ liệu trên thiết bị mới.").setCancelable(false).setPositiveButton("Đóng app",(d,w)->{finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());}).show());}

  private CheckBox check(String label, boolean checked) {
    CheckBox c = new CheckBox(this);
    c.setText(label);
    c.setTextColor(INK);
    c.setChecked(checked);
    return c;
  }

  private android.graphics.Typeface faceCache;private String faceCacheKey="";
  private void applyReaderStyle(){if(nativeReader==null)return;android.graphics.Typeface face;int choice=prefs.getInt("font2",2);String key=choice+":"+(choice==5?new File(getFilesDir(),"reader-font.ttf").lastModified():0);try{face=key.equals(faceCacheKey)&&faceCache!=null?faceCache:choice==0?android.graphics.Typeface.createFromAsset(getAssets(),"Tinos.ttf"):choice==1?android.graphics.Typeface.createFromAsset(getAssets(),"DejaVu.ttf"):choice==2?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSerif.ttf"):choice==3?android.graphics.Typeface.createFromAsset(getAssets(),"LiberationSans.ttf"):choice==4?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf"));}catch(Exception e){face=android.graphics.Typeface.SERIF;}faceCache=face;faceCacheKey=key;
    face=android.graphics.Typeface.create(face,prefs.getInt("weight",400)>=700?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
    nativeReader.style(prefs.getFloat("size2",22f),prefs.getInt("margin",5),prefs.getFloat("line",1.30f),prefs.getInt("paraSpaceDp",8),prefs.getInt("indentDp",9),face,prefs.getBoolean("taps",true));
  }

  private String appVersion(){
    try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
    catch(Exception e){return "không rõ";}
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle("Hako Pocket "+appVersion())
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
    if(req==94){if(result==RESULT_OK&&data!=null&&data.getData()!=null){final Uri source=data.getData();task("Đang nhập cấu hình báo cáo…",()->{StringBuilder text=new StringBuilder();try(InputStream in=getContentResolver().openInputStream(source)){if(in==null)throw new IOException("Không mở được tệp");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>8192)throw new IOException("Tệp cấu hình quá lớn");bytes.write(buffer,0,n);}text.append(bytes.toString("UTF-8"));}JSONObject config=new JSONObject(text.toString());String endpoint=config.optString("endpoint","").trim(),token=config.optString("token","").trim();if(!ReportJob.validEndpoint(endpoint)||token.length()<16)throw new IOException("Cấu hình dịch vụ không hợp lệ");getSharedPreferences("diagnostics",0).edit().putString("endpoint",endpoint).putString("token",token).putBoolean("auto",false).apply();return null;},()->message("Đã nhập cấu hình. Bấm Báo cáo → Gửi ngay để thử gửi."));}return;}
    if(req==93){if(result==RESULT_OK&&data!=null&&data.getData()!=null){final Uri destination=data.getData();task("Đang lưu báo cáo…",()->{File file=Diagnostics.queue(this);try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("Không mở được nơi lưu");byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}return null;},()->message("Đã lưu báo cáo hoạt động."));}return;}
    if(req==92){reportExportPending=false;if(result==RESULT_OK&&data!=null&&data.getData()!=null){final Uri destination=data.getData();task("Đang lưu báo cáo…",()->{try(OutputStream out=getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("Không mở được nơi lưu");out.write(UpdateReport.text(this).getBytes("UTF-8"));}return null;},()->message("Đã lưu báo cáo cập nhật."));}return;}
    if(req==91){GeckoClient.authenticating=false;GeckoClient.release();Repository.cancel.set(false);if(result==RESULT_OK&&data!=null){
      if(HakoParser.isOrigin(data.getStringExtra("url")))prefs.edit().remove("cooldownUntil").apply();
      final Intent returned=data;
      task("Đang nhập lịch sử đọc…",()->{try{JSONArray list=new JSONArray(returned.getStringExtra("history"));for(int n=0;n<list.length();n++){JSONObject h=list.getJSONObject(n);String u=HakoParser.normalize(h.optString("series_url")),id=HakoParser.storyId(u),cid=HakoParser.chapterId(h.optString("chapter_url"));if(!id.isEmpty()&&!cid.isEmpty()){store.putBook(new HakoParser.Link(id,h.optString("series_title","Truyện"),u));store.importHistory(id,cid,h.optLong("read_time")*1000);}}}catch(Exception error){Diagnostics.failure("history.import",error);}return null;},()->{if(returned.getBooleanExtra("read",false)){String u=returned.getStringExtra("url");if(u!=null&&!HakoParser.storyId(HakoParser.storyUrl(u)).isEmpty())addUrl(u);else message("Mở truyện hoặc chương trước khi chọn Đọc offline.");}});
    }return;}

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
    if(nativeReader!=null&&readerReady){final String bid=bookId,cid=chapterId;final int p=lastPos;final float f=lastFraction;io.execute(()->store.position(bid,cid,p,f));}
    Repository.pocketPaused=true;Diagnostics.background(this);
    super.onPause();
  }

  @Override protected void onResume(){
    super.onResume();
    Repository.pocketPaused=touchLocked;Diagnostics.foreground=true;
  }

  protected void onDestroy() {
    if (updates != null) unregisterReceiver(updates);
    if(powerReceiver!=null)unregisterReceiver(powerReceiver);
    if(networkCallback!=null)getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
    libraryHandler.removeCallbacksAndMessages(null);
    libraryIo.shutdown();
    io.shutdown();webIo.shutdown();readerIo.shutdown();transferIo.shutdown();reportIo.shutdown();
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
    cardBg.setStroke(dp(1), Color.BLACK);
    cardBg.setCornerRadius(dp(10));
    card.setBackground(cardBg);

    TextView titleView = text(dir > 0 ? "ĐÃ ĐỌC HẾT CHƯƠNG" : "ĐANG Ở ĐẦU CHƯƠNG", 16);
    titleView.setTypeface(UI_MEDIUM);
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
    btnStay.setTypeface(UI_MEDIUM);
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
    btnConfirm.setTypeface(UI_MEDIUM);
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
    else if(currentList!=null&&currentList.isShown()){
      if(currentList instanceof PagedBookList)((PagedBookList)currentList).turn(dir);
      else listPaging.turn(currentList,dir);
    }else if(currentScroll!=null&&currentScroll.isShown())currentScroll.pageScroll(dir>0?View.FOCUS_DOWN:View.FOCUS_UP);
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event){
    int code = event.getKeyCode();
    int action = event.getAction();
    if(settingsDialog!=null&&settingsDialog.isShowing()&&settingsVolume(code,event))return true;
    if(nativeReader==null&&(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN)){if(action==KeyEvent.ACTION_DOWN){if(storyInformation&&currentScroll!=null&&(code==KeyEvent.KEYCODE_VOLUME_DOWN?currentScroll.canScrollVertically(1):currentList==null||currentList.getFirstVisiblePosition()==0)){currentScroll.pageScroll(code==KeyEvent.KEYCODE_VOLUME_UP?View.FOCUS_UP:View.FOCUS_DOWN);}else if(currentScroll!=null&&currentScroll.hasFocus()&&!storyInformation)currentScroll.pageScroll(code==KeyEvent.KEYCODE_VOLUME_UP?View.FOCUS_UP:View.FOCUS_DOWN);else performPageAction(code==KeyEvent.KEYCODE_VOLUME_UP?-1:1);}return true;}

    if(touchLocked && (code==KeyEvent.KEYCODE_MENU || code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER))return true;

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
    if(isPageUpKey(code)){
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

