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
  private Store store;
  private android.content.SharedPreferences prefs;
  private LinearLayout root, bar;
  private TextView status, title;
  private WebView web;
  private NativeReader nativeReader;
  private ListView currentList;
  private ScrollView currentScroll;
  private String visitSession="";
  private boolean online = false, readerReady = false;
  private volatile String bookId = "", chapterId = "";
  private int generation = 0;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private BroadcastReceiver updates;
  private volatile int lastPos = 0;
  private volatile float lastFraction = 0;
  private final int INK = Color.rgb(25, 25, 25), MUTED = Color.rgb(80, 80, 80);

  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = Store.get(this);
    prefs = getSharedPreferences("settings", 0);
    CookieManager.getInstance().setAcceptCookie(true);
    updates =
        new BroadcastReceiver() {
          public void onReceive(Context c, Intent i) {
            if (status != null) status.setText(i.getStringExtra("text"));
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
    b.setTextColor(INK);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setMinHeight(dp(42));
    b.setMinimumWidth(0);
    b.setPadding(dp(4), 0, dp(4), 0);
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
    nativeReader=null;
    readerReady = false;
    if (web != null) {
      web.stopLoading();
      web.removeJavascriptInterface("Bridge");
      web.destroy();
      web = null;
    }
    online = false;
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.WHITE);
    setContentView(root);
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
    status = text(Repository.status, 12);
    status.setTextColor(MUTED);
    status.setMaxLines(2);
    root.addView(status);
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
  private void exitApp(){saveThen(()->{leaveReader();Repository.cancel.set(true);stopService(new Intent(this,DownloadService.class));finishAndRemoveTask();});}
  private void library() {
    leaveReader();reset();bookId="";chapterId="";
    root.addView(text("HAKO POCKET",22));root.addView(text("Đọc nhẹ • lưu vị trí trên máy",12));
    ScrollView scroll=new ScrollView(this);currentScroll=scroll;LinearLayout grid=new LinearLayout(this);grid.setOrientation(1);scroll.addView(grid);
    String[] names={"Đọc tiếp","Vừa đọc","Tủ sách","Thường đọc","Mới cập nhật","Truyện mới","Tìm kiếm","Tài khoản","Cài đặt","Mở liên kết","Thoát"};
    Runnable[] actions={()->{Store.Book b=store.book(prefs.getString("lastBook",""));if(b!=null)openBook(b);else bookList(false);},()->bookList(false),()->bookList(true),this::frequentList,()->onlineList("Mới cập nhật",HakoParser.ORIGIN+"/danh-sach?truyendich=1&sapxep=capnhat"),()->onlineList("Truyện mới",HakoParser.ORIGIN+"/danh-sach?truyendich=1&sapxep=truyenmoi"),this::searchDialog,()->browse(HakoParser.ORIGIN+"/login"),this::settings,this::addDialog,this::exitApp};
    for(int n=0;n<names.length;n+=2){LinearLayout line=new LinearLayout(this);for(int j=n;j<n+2&&j<names.length;j++){final int x=j;LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);cell.setGravity(Gravity.CENTER);IconButton icon=new IconButton(this,new int[]{12,7,1,9,8,9,10,11,13,14,6}[j],names[j],actions[j]);cell.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView label=text(names[j],14);label.setGravity(Gravity.CENTER);cell.addView(label);cell.setOnClickListener(v->actions[x].run());line.addView(cell,new LinearLayout.LayoutParams(0,dp(94),1));}grid.addView(line);}root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    status();
  }
    private void onlineList(String title, String url) {
    leaveReader();reset();bookId="";chapterId="";root.addView(text(title,20));
    row(new String[]{"Trang chính","Tìm kiếm","Mở web"},new Runnable[]{this::library,this::searchDialog,()->browse(url)});
    final List<HakoParser.Link> stories = new ArrayList<>();
    task("Đang tải danh sách…", () -> {
      String html = Repository.page(url, false);
      stories.addAll(HakoParser.storyList(html, url));
      return null;
    }, () -> {
      if (stories.isEmpty()) {
        root.addView(text("Không tìm thấy truyện hoặc trang yêu cầu xác minh. Hãy chọn Mở web.", 14));
        return;
      }
      List<String> names = new ArrayList<>();
      for (HakoParser.Link l : stories) names.add(l.title + (l.info.isEmpty() ? "" : "\n" + l.info));
      ListView list = new ListView(this);
      currentList = list;
      list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
        public View getView(int p, View v, ViewGroup parent) {
          TextView t = (TextView) super.getView(p, v, parent);
          t.setTextColor(INK);
          t.setTextSize(15);
          t.setPadding(dp(10), dp(10), dp(10), dp(10));
          return t;
        }
      });
      list.setOnItemClickListener((a, v, p, id) -> addUrl(stories.get(p).url));
      root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
      status();
    });
  }

  private void searchDialog() {
    EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Nhập tên truyện hoặc tác giả…");
    new AlertDialog.Builder(this).setTitle("Tìm kiếm HAKO").setView(field).setPositiveButton("Tìm",(d,w)->{
      String q=field.getText().toString().trim();
      if(!q.isEmpty()){try{String u=HakoParser.ORIGIN+"/tim-kiem?q="+java.net.URLEncoder.encode(q,"UTF-8");onlineList("Tìm: "+q,u);}catch(Exception ignored){}}
    }).setNegativeButton("Hủy",null).show();
  }

  private void bookList(boolean shelf){
    leaveReader();reset();bookId="";chapterId="";root.addView(text(shelf?"Tủ sách":"Vừa đọc",20));
    row(new String[]{"Trang chính",shelf?"Cập nhật":"Lịch sử HAKO","⋯"},new Runnable[]{this::library,()->{if(shelf)task("Nhập tủ sách…",()->{Repository.importShelf(this);return null;},()->bookList(true));else browse(HakoParser.ORIGIN+"/lich-su-doc");},()->{new AlertDialog.Builder(this).setItems(new String[]{"Đồng bộ ngay","Dừng tải","HAKO / Tủ sách",prefs.getBoolean("unreadOnly",false)?"Hiện tất cả truyện":"Chỉ bộ còn chương mới"},(d,w)->{if(w==0)confirmBulk();if(w==1)Repository.cancel.set(true);if(w==2)browse(HakoParser.ORIGIN+"/ke-sach");if(w==3){prefs.edit().putBoolean("unreadOnly",!prefs.getBoolean("unreadOnly",false)).apply();bookList(true);}}).show();}});
    status();List<Store.Book> books=new ArrayList<>();for(Store.Book b:store.books())if(shelf?(b.followed&&(!prefs.getBoolean("unreadOnly",false)||hasNew(b))):b.stamp>0)books.add(b);
    if(shelf)java.util.Collections.sort(books,(a,b)->Integer.compare(a.shelfRank,b.shelfRank));
    List<String> labels=new ArrayList<>();for(Store.Book b:books){List<Store.Chapter> chs=store.chapters(b.id);Store.Chapter ch=store.chapter(b.id,b.current);int unread=ch==null?-1:Math.max(0,chs.size()-ch.ord-1);labels.add(b.title+"\n"+(b.stamp>0?"Đọc: "+android.text.format.DateFormat.format("dd/MM HH:mm",b.stamp)+" · ":"")+(b.dropped?"Tạm ngưng · ":"")+(ch==null?"Chưa đọc trong APK":ch.title)+(unread<0?"":" · "+unread+" chương phía sau")+"\n"+(b.followed?"HAKO: "+b.shelfInfo:"Đọc tạm"));}
    ListView list=new ListView(this);currentList=list;list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(INK);t.setTextSize(15);t.setPadding(dp(10),dp(10),dp(10),dp(10));return t;}});list.setOnItemClickListener((a,v,p,id)->openBook(books.get(p)));list.setOnItemLongClickListener((a,v,p,id)->{bookMenu(books.get(p));return true;});root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    if(books.isEmpty())root.addView(text(shelf?"Bấm Cập nhật sau khi đăng nhập HAKO.":"Chưa có lịch sử đọc trong APK.",14));
  }
  private boolean hasNew(Store.Book b){try{return Integer.parseInt(b.shelfInfo.split(" ")[0])>0;}catch(Exception e){return false;}}
  private void frequentList(){
    leaveReader();reset();root.addView(text("Thường xuyên đọc",20));row(new String[]{"Trang chính"},new Runnable[]{this::library});
    List<Store.Book> books=new ArrayList<>();for(Store.Book b:store.books())if(b.visits>0||b.pinned)books.add(b);
    Collections.sort(books,(a,b)->{if(a.pinned!=b.pinned)return a.pinned?-1:1;int n=Integer.compare(b.visits,a.visits);return n!=0?n:Long.compare(b.stamp,a.stamp);});
    List<String> names=new ArrayList<>();for(Store.Book b:books)names.add((b.pinned?"★ ":"")+b.title+"\n"+b.visits+" lần mở đọc · "+(b.stamp==0?"Chưa đọc":android.text.format.DateFormat.format("dd/MM HH:mm",b.stamp)));
    ListView list=new ListView(this);currentList=list;list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names));list.setOnItemClickListener((a,v,i,id)->openBook(books.get(i)));list.setOnItemLongClickListener((a,v,i,id)->{bookMenu(books.get(i));return true;});root.addView(list,new LinearLayout.LayoutParams(-1,0,1));status();
  }
  private void saveBook(){Store.Book b=store.book(bookId);if(b==null)return;if(b.followed){message("Bộ này đã trong tủ sách HAKO.");return;}Repository.cancel.set(false);task("Đang lưu lên HAKO…",()->{RenderedPage.action(this,b.url,"follow");store.followed(b.id,true,b.shelfRank);return null;},()->Toast.makeText(this,"Đã lưu vào tủ sách HAKO",Toast.LENGTH_SHORT).show());}
  private void markCaughtUp(Store.Book b){new AlertDialog.Builder(this).setTitle("Đã bắt kịp: "+b.title).setMessage("Đánh dấu bộ này đã đọc trên HAKO. Bộ đếm chương mới sẽ tính từ mốc hiện tại; không đổi màu liên kết trên điện thoại.").setPositiveButton("Đã đọc hết",(d,w)->{Repository.cancel.set(false);task("Đánh dấu trên HAKO…",()->{RenderedPage.action(this,HakoParser.ORIGIN+"/ke-sach","read:"+b.id.substring(b.id.lastIndexOf('-')+1));store.shelfInfo(b.id,"Không có chương mới tại lần đánh dấu vừa rồi");return null;},()->message("HAKO đã xác nhận đánh dấu đã đọc."));}).setNegativeButton("Hủy",null).show();}
  private void readerMenu(){new AlertDialog.Builder(this).setItems(new String[]{"Chương trước","Chương tiếp","Đánh dấu đoạn","Ẩn thanh công cụ","Tải trước / tiếp tục","Dừng tải","Mở HAKO","Chạm lật trang: "+(prefs.getBoolean("taps",true)?"Bật":"Tắt"),"Đầu chương","Cuối chương","Đã bắt kịp trên HAKO","Tải lại chương này"},(d,w)->{
    if(w==0)saveThen(()->adjacent(-1));if(w==1)saveThen(()->adjacent(1));if(w==2)bookmarks();if(w==3){bar.setVisibility(View.GONE);Toast.makeText(this,"Vuốt dọc rồi chạm để hiện công cụ",Toast.LENGTH_SHORT).show();}if(w==4)startDownloads(bookId,false);if(w==5)Repository.cancel.set(true);if(w==6){Store.Chapter ch=store.chapter(bookId,chapterId);saveThen(()->browse(ch.url));}if(w==7){boolean t=!prefs.getBoolean("taps",true);prefs.edit().putBoolean("taps",t).apply();if(nativeReader!=null)nativeReader.taps(t);}if(w==8&&nativeReader!=null)nativeReader.jump(false);if(w==9&&nativeReader!=null)nativeReader.jump(true);if(w==10)markCaughtUp(store.book(bookId));if(w==11){final String bid=bookId,cid=chapterId;store.state(cid,false,"");task("Tải lại chương…",()->{Repository.cancel.set(false);Repository.download(this,store.chapter(bid,cid));return null;},()->showReader(bid,cid));}}).show();}
  private void confirmBulk() {
    new AlertDialog.Builder(this)
        .setTitle("Đồng bộ thư viện")
        .setMessage(
            "Tải phạm vi offline của các truyện đã có trong thư viện, kể cả khi không sạc. Truyện"
                + " chưa chọn vị trí sẽ tải từ chương đầu. Bạn có thể dừng bất cứ lúc nào.")
        .setPositiveButton("Tải ngay", (d, w) -> startDownloads("", true))
        .setNegativeButton("Hủy", null)
        .show();
  }

  private void startDownloads(String id, boolean all) {
    Intent i = new Intent(this, DownloadService.class).putExtra("book", id).putExtra("all", all);
    startForegroundService(i);
  }

  private void bookMenu(Store.Book b) {
    new AlertDialog.Builder(this)
        .setTitle(b.title)
        .setItems(
            new String[] {
              "Mục lục / chọn chương", "Tải lại mục lục", "Tải truyện này", "Mở trên HAKO", b.dropped?"Tiếp tục theo dõi":"Tạm ngưng / drop",b.pinned?"Bỏ ghim":"Ghim thường đọc","Đã bắt kịp trên HAKO"
            },
            (d, w) -> {
              if (w == 0) contents(b);
              if (w == 1)
                task(
                    "Cập nhật mục lục…",
                    () -> {
                      prefs.edit().remove("catalog-"+b.id).apply();
                      Repository.catalog(this, b);
                      return null;
                    },
                    () -> contents(b));
              if (w == 2) startDownloads(b.id, false);
              if (w == 3) browse(b.url);
              if(w==5){store.pin(b.id,!b.pinned);frequentList();}if(w==6)markCaughtUp(b);
              if(w==4){store.dropped(b.id,!b.dropped);bookList(b.followed);}
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

  private void openBook(Store.Book b) {
    if (store.chapters(b.id).isEmpty())
      task(
          "Đang lấy mục lục…",
          () -> {
            Repository.catalog(this, b);
            return null;
          },
          () -> {Store.Book loaded=store.book(b.id);if(!loaded.current.isEmpty()&&store.chapter(loaded.id,loaded.current)!=null)openChapter(loaded.id,loaded.current);else contents(loaded);});
    else if (!b.current.isEmpty()&&store.chapter(b.id,b.current)!=null) openChapter(b.id, b.current);
    else contents(b);
  }

  private void contents(Store.Book b) {
    List<Store.Chapter> chs = store.chapters(b.id);
    if (chs.isEmpty()) {
      message("Chưa có mục lục. Kết nối mạng và chọn Tải lại mục lục.");
      return;
    }
    leaveReader();reset();bookId=b.id;chapterId="";
    int targetIdx = 0;
    String curCid = b.current;
    if (!curCid.isEmpty()) {
      for (int i = 0; i < chs.size(); i++) {
        if (chs.get(i).id.equals(curCid)) { targetIdx = i; break; }
      }
    } else {
      int lastRead = -1;
      for (int i = 0; i < chs.size(); i++) {
        if (store.wasRead(chs.get(i).id)) lastRead = i;
      }
      if (lastRead >= 0) targetIdx = Math.min(lastRead + 1, chs.size() - 1);
    }
    boolean desc = prefs.getBoolean("tocDesc", false);
    List<Store.Chapter> displayChs = new ArrayList<>(chs);
    if (desc) java.util.Collections.reverse(displayChs);
    int selectedPos = displayChs.indexOf(chs.get(targetIdx));
    if (selectedPos < 0) selectedPos = 0;
    root.addView(text(b.title, 18));
    String sub = chs.size() + " chương · " + (desc ? "Mới nhất trước ▼" : "Cũ nhất trước ▲");
    root.addView(text(sub, 12));
    List<String> rowBtns = new ArrayList<>();
    List<Runnable> rowActs = new ArrayList<>();
    rowBtns.add("Trang chính");rowActs.add(this::library);
    final Store.Chapter curCh = store.chapter(b.id, b.current);
    if (curCh != null) {
      rowBtns.add("Đọc tiếp");
      rowActs.add(() -> openChapter(b.id, curCh.id));
    }
    rowBtns.add(desc ? "Cũ nhất ▲" : "Mới nhất ▼");
    rowActs.add(() -> { prefs.edit().putBoolean("tocDesc", !desc).apply(); contents(b); });
    rowBtns.add("⋯ Menu");rowActs.add(() -> bookMenu(b));
    row(rowBtns.toArray(new String[0]), rowActs.toArray(new Runnable[0]));
    List<String> labels = new ArrayList<>();
    for (int n = 0; n < displayChs.size(); n++) {
      Store.Chapter c = displayChs.get(n);
      boolean isCur = c.id.equals(b.current);
      boolean wasRead = store.wasRead(c.id);
      String prefix = isCur ? "▶ [Đang đọc] " : wasRead ? "✓ [Đã đọc] " : "• ";
      String statusIcon = c.ready ? "✓ đủ" : store.html(c.id).isFile() ? "◐" : "↓";
      labels.add(prefix + c.title + " (" + statusIcon + ")");
    }
    ListView list = new ListView(this);
    currentList = list;
    final int finalSelectedPos = selectedPos;
    list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
      public View getView(int p, View v, ViewGroup parent) {
        TextView t = (TextView) super.getView(p, v, parent);
        boolean isCur = displayChs.get(p).id.equals(b.current);
        t.setTextColor(isCur ? Color.BLACK : INK);
        t.setTypeface(isCur ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextSize(15);
        t.setPadding(dp(10), dp(10), dp(10), dp(10));
        return t;
      }
    });
    list.setOnItemClickListener((a, v, p, id) -> openChapter(b.id, displayChs.get(p).id));
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    list.post(() -> list.setSelection(Math.max(0, finalSelectedPos - 1)));
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
      
    } else
      task(
          "Đang tải " + c.title + "…",
          () -> {
            Repository.download(this, c);
            return null;
          },
          () -> {
            showReader(bid, cid);
          });
  }

  private void showReader(String bid,String cid){
    Store.Book b=store.book(bid);Store.Chapter c=store.chapter(bid,cid);if(b==null||c==null)return;
    if(!store.readable(cid)){message("Nội dung chưa lưu thành công. Hãy thử tải lại hoặc mở HAKO.");return;}
    int pos=b.current.equals(cid)?b.pos:0;float fraction=b.current.equals(cid)?b.fraction:0;
    reset();bookId=bid;chapterId=cid;lastPos=pos;lastFraction=fraction;prefs.edit().putString("lastBook",bid).apply();
    if(!visitSession.equals(bid)){store.visited(bid);visitSession=bid;}
    nativeReader=new NativeReader(this,store.dir(cid),new NativeReader.Listener(){
      public void position(int p,float f,int page,int count){if(isFinishing()||isDestroyed()||!bid.equals(bookId)||!cid.equals(chapterId))return;lastPos=p;lastFraction=f;store.position(bid,cid,p,f);store.readChapter(cid);if(title!=null)title.setText(c.title+" · "+(page+1)+"/"+count);
        if(!readerReady){readerReady=true;io.execute(()->store.prune(store.book(bid)));if(!bid.equals("demo"))startDownloads(bid,false);}}
      public void boundary(int dir){if(!readerReady)return;if(nativeReader!=null&&nativeReader.getPageCount()<=1&&!store.readable(cid)){android.widget.Toast.makeText(MainActivity.this,"Chương chưa tải xong hoặc không có nội dung.",android.widget.Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(MainActivity.this).setMessage(dir>0?"Hết chương. Sang chương tiếp?":"Đầu chương. Mở chương trước?").setPositiveButton("Chuyển",(d,w)->adjacent(dir)).setNegativeButton("Ở lại",null).show();}
      public void toolbar(){if(bar!=null)bar.setVisibility(bar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}
    });root.addView(nativeReader,new LinearLayout.LayoutParams(-1,0,1));
    bar=new LinearLayout(this);bar.setOrientation(1);LinearLayout icons=new LinearLayout(this);
    String[] labels={"Trang chính","Trước","Mục lục","Tiếp","Chữ","Menu","Thoát"};
    Runnable[] actions={
      ()->saveThen(this::library),
      ()->saveThen(()->adjacent(-1)),
      ()->contents(store.book(bid)),
      ()->saveThen(()->adjacent(1)),
      this::settings,
      this::readerMenu,
      this::exitApp
    };
    int[] iconKinds = {0, 4, 1, 5, 3, 15, 6};
    for(int n=0;n<labels.length;n++)icons.addView(new IconButton(this,iconKinds[n],labels[n],actions[n]),new LinearLayout.LayoutParams(0,dp(40),1));
    title=text(c.title,11);title.setSingleLine(true);title.setPadding(dp(6),0,dp(6),0);bar.addView(title);bar.addView(icons);root.addView(bar);
    try{applyReaderStyle();nativeReader.content(Store.read(store.html(cid)),pos,fraction);}catch(Exception e){message(e.getMessage());}
  }

  private void saveThen(Runnable next){if(nativeReader!=null&&readerReady)store.position(bookId,chapterId,lastPos,lastFraction);next.run();}

  private void adjacent(int delta) {
    List<Store.Chapter> chs = store.chapters(bookId);
    for (int n = 0; n < chs.size(); n++)
      if (chs.get(n).id.equals(chapterId)) {
        int target = n + delta;
        if (target >= 0 && target < chs.size()) openChapter(bookId, chs.get(target).id);
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
    Spinner fonts=new Spinner(this);fonts.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tinos (có chân)","DejaVu Serif","Không chân hệ thống","Font đã nhập"}));fonts.setSelection(prefs.getInt("font2",0));box.addView(fonts);
    TextView preview=text("Tiếng Việt: Nguyễn, quyển, tưởng, khuỷu — ă â ê ô ơ ư đ",18);box.addView(preview);
    TextView sizeLabel=text("Cỡ chữ",14);box.addView(sizeLabel);SeekBar size=new SeekBar(this);size.setMax(44);size.setProgress(Math.round((prefs.getFloat("size2",18)-12)*2));box.addView(size);
    TextView lineLabel=text("Giãn dòng",14);box.addView(lineLabel);SeekBar line=new SeekBar(this);line.setMax(8);line.setProgress(Math.round((prefs.getFloat("line",1.35f)-1.1f)*10));box.addView(line);
    TextView marginLabel=text("Lề",14);box.addView(marginLabel);SeekBar margin=new SeekBar(this);margin.setMax(28);margin.setProgress(prefs.getInt("margin",10));box.addView(margin);
    CheckBox bold=check("Nét đậm (thử trên màn E Ink)",prefs.getInt("weight",400)>400);box.addView(bold);
    Runnable update=()->{float fs=12+size.getProgress()/2f;sizeLabel.setText("Cỡ chữ: "+fs+" sp");lineLabel.setText("Giãn dòng: "+(Math.round((1.1f+line.getProgress()/10f)*100f)/100f)+"x");marginLabel.setText("Lề: "+margin.getProgress()+" dp");preview.setTextSize(fs);try{int n=fonts.getSelectedItemPosition();android.graphics.Typeface tf=n==0?android.graphics.Typeface.createFromAsset(getAssets(),"Tinos.ttf"):n==1?android.graphics.Typeface.createFromAsset(getAssets(),"DejaVu.ttf"):n==2?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf"));preview.setTypeface(tf,bold.isChecked()?1:0);}catch(Exception e){preview.setTypeface(android.graphics.Typeface.SERIF);}};
    SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};size.setOnSeekBarChangeListener(listener);line.setOnSeekBarChangeListener(listener);margin.setOnSeekBarChangeListener(listener);fonts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int p,long id){update.run();}public void onNothingSelected(AdapterView<?> a){}});bold.setOnCheckedChangeListener((a,b)->update.run());update.run();
    CheckBox taps=check("Chạm trên/dưới để lật trang",prefs.getBoolean("taps",true));box.addView(taps);CheckBox charging=check("Đồng bộ tủ sách khi sạc",prefs.getBoolean("charging",true));box.addView(charging);
    box.addView(button("Nhập font TTF / OTF",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),88)));
    box.addView(button("Tên miền HAKO",this::domain));box.addView(button("Đọc mẫu offline",this::demo));
    box.addView(text("Tải trước 1–2: chờ 4s; 3–5: 12s; 6–10: 30s; 11–15: 60s. Màn hình tắt: ngừng tải trước khi dùng pin. Đọc bản lưu không cần chạy trang HAKO.",12));
    ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle("Chữ & tải nội dung").setView(scroll).setPositiveButton("Áp dụng",(d,w)->{if(fonts.getSelectedItemPosition()==3&&!new File(getFilesDir(),"reader-font.ttf").isFile()){message("Chưa nhập font. Đang giữ lựa chọn cũ.");return;}prefs.edit().putInt("font2",fonts.getSelectedItemPosition()).putFloat("size2",12+size.getProgress()/2f).putFloat("line",Math.round((1.1f+line.getProgress()/10f)*100f)/100f).putInt("margin",margin.getProgress()).putInt("weight",bold.isChecked()?700:400).putBoolean("taps",taps.isChecked()).putBoolean("charging",charging.isChecked()).apply();ChargeJob.schedule(this,charging.isChecked());if(nativeReader!=null)applyReaderStyle();}).setNegativeButton("Đóng",null).show();
  }

  private CheckBox check(String label, boolean checked) {
    CheckBox c = new CheckBox(this);
    c.setText(label);
    c.setTextColor(INK);
    c.setChecked(checked);
    return c;
  }

  private void applyReaderStyle(){if(nativeReader==null)return;android.graphics.Typeface face;int choice=prefs.getInt("font2",0);try{face=choice==3?android.graphics.Typeface.createFromFile(new File(getFilesDir(),"reader-font.ttf")):choice==2?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromAsset(getAssets(),choice==1?"DejaVu.ttf":"Tinos.ttf");}catch(Exception e){face=android.graphics.Typeface.SERIF;}
    face=android.graphics.Typeface.create(face,prefs.getInt("weight",400)>=700?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
    nativeReader.style(prefs.getFloat("size2",18f),prefs.getInt("margin",10),prefs.getFloat("line",1.35f),face,prefs.getBoolean("taps",true));
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle("Hako Pocket 0.3 • Bản thử nghiệm")
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
            prefs.edit().putBoolean("customFont", true).putInt("font2",3).apply();
            return null;
          },
          () -> {
            applyReaderStyle();message("Đã nhập font.");
          });
    }
  }

  protected void onPause() {
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

  public void onBackPressed() {
    if(nativeReader!=null){saveThen(this::library);return;}
    if (online && web != null && web.canGoBack()) {
      web.goBack();
      return;
    }
    if (web != null) {
      if(online){importWebHistory(this::library);return;}
      saveThen(this::library);
      return;
    }
    super.onBackPressed();
  }

  @Override public boolean dispatchKeyEvent(KeyEvent event){
    int code=event.getKeyCode();
    if(code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_VOLUME_UP){
      if(event.getAction()==KeyEvent.ACTION_DOWN){
        int dir=(code==KeyEvent.KEYCODE_VOLUME_DOWN)?1:-1;
        if(nativeReader!=null){
          nativeReader.turn(dir);
        }else if(web!=null){
          if(dir>0)web.pageDown(false);else web.pageUp(false);
        }else if(currentList!=null&&currentList.isShown()){
          currentList.scrollListBy(dir*dp(200));
        }else if(currentScroll!=null&&currentScroll.isShown()){
          currentScroll.scrollBy(0,dir*dp(200));
        }
      }
      return true;
    }
    return super.dispatchKeyEvent(event);
  }
  public boolean onKeyUp(int key,KeyEvent event){if(nativeReader!=null&&(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN))return true;return super.onKeyUp(key,event);}
  public boolean onKeyDown(int key, KeyEvent event) {
    if(nativeReader!=null){if(key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN||key==KeyEvent.KEYCODE_DPAD_RIGHT){nativeReader.turn(1);return true;}if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_PAGE_UP||key==KeyEvent.KEYCODE_DPAD_LEFT){nativeReader.turn(-1);return true;}}
    return super.onKeyDown(key, event);
  }
}
