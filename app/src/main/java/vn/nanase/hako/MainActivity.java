package vn.nanase.hako; import android.app.*; import android.content.*; import android.graphics.Color; import android.net.Uri; import android.os.*; import android.view.*; import android.webkit.*; import android.widget.*; import java.io.*; import java.util.*; import java.util.concurrent.*; import org.json.*; public class MainActivity extends Activity { private Store store; private android.content.SharedPreferences prefs; private LinearLayout root, bar; private TextView status, title; private WebView web; private NativeReader nativeReader; private String visitSession=\"\"; private boolean online = false, readerReady = false; private volatile String bookId = \"\", chapterId = \"\"; private int generation = 0; private final ExecutorService io = Executors.newSingleThreadExecutor(); private BroadcastReceiver updates; private volatile int lastPos = 0; private volatile float lastFraction = 0; private final int INK = Color.rgb(25, 25, 25), MUTED = Color.rgb(80, 80, 80); public void onCreate(Bundle state) { super.onCreate(state); store = Store.get(this); prefs = getSharedPreferences(\"settings\", 0); CookieManager.getInstance().setAcceptCookie(true); updates = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { if (status != null) status.setText(i.getStringExtra(\"text\")); } }; registerReceiver(updates, new IntentFilter(Repository.EVENT)); ChargeJob.schedule(this, prefs.getBoolean(\"charging\", true)); HakoParser.ORIGIN=prefs.getString(\"origin\",\"https://docln.sbs\"); Repository.init(this); io.execute(()->store.cleanStartup()); library(); } private int dp(int x) { return (int) (getResources().getDisplayMetrics().density * x + .5f); } private TextView text(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextColor(INK); t.setTextSize(size); t.setPadding(dp(12), dp(8), dp(12), dp(8)); return t; } private Button button(String label, Runnable r) { Button b = new Button(this); b.setText(label); b.setTextColor(INK); b.setTextSize(13); b.setAllCaps(false); b.setMinHeight(dp(42)); b.setMinimumWidth(0); b.setPadding(dp(4), 0, dp(4), 0); b.setOnClickListener(v -> r.run()); return b; } private void row(String[] labels, Runnable[] actions) { LinearLayout l = new LinearLayout(this); for (int n = 0; n < labels.length; n++) l.addView(button(labels[n], actions[n]), new LinearLayout.LayoutParams(0, dp(46), 1)); root.addView(l); } private void reset() { generation++; nativeReader=null; readerReady = false; if (web != null) { web.stopLoading(); web.removeJavascriptInterface(\"Bridge\"); web.destroy(); web = null; } online = false; root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE); setContentView(root); } private void message(String s) { if (isFinishing()) return; new AlertDialog.Builder(this) .setTitle(\"Hako Pocket\") .setMessage(s) .setPositiveButton(\"Đóng\", null) .show(); } private void status() { status = text(Repository.status, 12); status.setTextColor(MUTED); status.setMaxLines(2); root.addView(status); } private void task(String label, Callable work, Runnable done) { int token = generation; if (status != null) status.setText(label); io.execute( () -> { try { work.call(); runOnUiThread( () -> { if (isFinishing() || token != generation) return; if (done != null) done.run(); }); } catch (Exception e) { runOnUiThread( () -> { if (isFinishing() || token != generation) return; if (status != null) status.setText(\"Chưa hoàn tất\"); message(e.getMessage() == null ? e.toString() : e.getMessage()); }); } }); } private void leaveReader(){visitSession=\"\";String old=Repository.activeBook;Repository.activeBook=\"\";if(!old.isEmpty())store.clearTemporary(old);} private void exitApp(){saveThen(()->{leaveReader();Repository.cancel.set(true);stopService(new Intent(this,DownloadService.class));finishAndRemoveTask();});} private void library() { leaveReader();reset();bookId=\"\";chapterId=\"\"; root.addView(text(\"HAKO POCKET\",22));root.addView(text(\"Đọc nhẹ • lưu vị trí trên máy\",12)); ScrollView scroll=new ScrollView(this);LinearLayout grid=new LinearLayout(this);grid.setOrientation(1);scroll.addView(grid); String[] names={\"Đọc tiếp\",\"Vừa đọc\",\"Tủ sách\",\"Thường đọc\",\"Mới cập nhật\",\"Truyện mới\",\"Tìm kiếm\",\"Tài khoản\",\"Cài đặt\",\"Mở liên kết\",\"Thoát\"}; Runnable[] actions={()->{Store.Book b=store.book(prefs.getString(\"lastBook\",\"\"));if(b!=null)openBook(b);else bookList(false);},()->bookList(false),()->bookList(true),this::frequentList,()->browse(HakoParser.ORIGIN+\"/danh-sach?truyendich=1&sapxep=capnhat\"),()->browse(HakoParser.ORIGIN+\"/danh-sach?truyendich=1&sapxep=truyenmoi\"),()->browse(HakoParser.ORIGIN+\"/\"),()->browse(HakoParser.ORIGIN+\"/login\"),this::settings,this::addDialog,this::exitApp}; for(int n=0;nactions[x].run());line.addView(cell,new LinearLayout.LayoutParams(0,dp(94),1));}grid.addView(line);}root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); status(); } private void bookList(boolean shelf){ leaveReader();reset();bookId=\"\";chapterId=\"\";root.addView(text(shelf?\"Tủ sách\":\"Vừa đọc\",20)); row(new String[]{\"Trang chính\",shelf?\"Cập nhật\":\"Lịch sử HAKO\",\"⋯\"},new Runnable[]{this::library,()->{if(shelf)task(\"Nhập tủ sách…\",()->{Repository.importShelf(this);return null;},()->bookList(true));else browse(HakoParser.ORIGIN+\"/lich-su-doc\");},()->{new AlertDialog.Builder(this).setItems(new String[]{\"Đồng bộ ngay\",\"Dừng tải\",\"HAKO / Tủ sách\",prefs.getBoolean(\"unreadOnly\",false)?\"Hiện tất cả truyện\":\"Chỉ bộ còn chương mới\"},(d,w)->{if(w==0)confirmBulk();if(w==1)Repository.cancel.set(true);if(w==2)browse(HakoParser.ORIGIN+\"/ke-sach\");if(w==3){prefs.edit().putBoolean(\"unreadOnly\",!prefs.getBoolean(\"unreadOnly\",false)).apply();bookList(true);}}).show();}}); status();List books=new ArrayList<>();for(Store.Book b:store.books())if(shelf?(b.followed&&(!prefs.getBoolean(\"unreadOnly\",false)||hasNew(b))):b.stamp>0)books.add(b); if(shelf)java.util.Collections.sort(books,(a,b)->Integer.compare(a.shelfRank,b.shelfRank)); List labels=new ArrayList<>();for(Store.Book b:books){List chs=store.chapters(b.id);Store.Chapter ch=store.chapter(b.id,b.current);int unread=ch==null?-1:Math.max(0,chs.size()-ch.ord-1);labels.add(b.title+\"\\n\"+(b.stamp>0?\"Đọc: \"+android.text.format.DateFormat.format(\"dd/MM HH:mm\",b.stamp)+\" · \":\"\")+(b.dropped?\"Tạm ngưng · \":\"\")+(ch==null?\"Chưa đọc trong APK\":ch.title)+(unread<0?\"\":\" · \"+unread+\" chương phía sau\")+\"\\n\"+(b.followed?\"HAKO: \"+b.shelfInfo:\"Đọc tạm\"));} ListView list=new ListView(this);list.setAdapter(new ArrayAdapter(this,android.R.layout.simple_list_item_1,labels){public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(INK);t.setTextSize(15);t.setPadding(dp(10),dp(10),dp(10),dp(10));return t;}});list.setOnItemClickListener((a,v,p,id)->openBook(books.get(p)));list.setOnItemLongClickListener((a,v,p,id)->{bookMenu(books.get(p));return true;});root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); if(books.isEmpty())root.addView(text(shelf?\"Bấm Cập nhật sau khi đăng nhập HAKO.\":\"Chưa có lịch sử đọc trong APK.\",14)); } private boolean hasNew(Store.Book b){try{return Integer.parseInt(b.shelfInfo.split(\" \")[0])>0;}catch(Exception e){return false;}} private void frequentList(){ leaveReader();reset();root.addView(text(\"Thường xuyên đọc\",20));row(new String[]{\"Trang chính\"},new Runnable[]{this::library}); List books=new ArrayList<>();for(Store.Book b:store.books())if(b.visits>0||b.pinned)books.add(b); Collections.sort(books,(a,b)->{if(a.pinned!=b.pinned)return a.pinned?-1:1;int n=Integer.compare(b.visits,a.visits);return n!=0?n:Long.compare(b.stamp,a.stamp);}); List names=new ArrayList<>();for(Store.Book b:books)names.add((b.pinned?\"★ \":\"\")+b.title+\"\\n\"+b.visits+\" lần mở đọc · \"+(b.stamp==0?\"Chưa đọc\":android.text.format.DateFormat.format(\"dd/MM HH:mm\",b.stamp))); ListView list=new ListView(this);list.setAdapter(new ArrayAdapter(this,android.R.layout.simple_list_item_1,names));list.setOnItemClickListener((a,v,i,id)->openBook(books.get(i)));list.setOnItemLongClickListener((a,v,i,id)->{bookMenu(books.get(i));return true;});root.addView(list,new LinearLayout.LayoutParams(-1,0,1));status(); } private void saveBook(){Store.Book b=store.book(bookId);if(b==null)return;if(b.followed){message(\"Bộ này đã trong tủ sách HAKO.\");return;}Repository.cancel.set(false);task(\"Đang lưu lên HAKO…\",()->{RenderedPage.action(this,b.url,\"follow\");store.followed(b.id,true,b.shelfRank);return null;},()->Toast.makeText(this,\"Đã lưu vào tủ sách HAKO\",Toast.LENGTH_SHORT).show());} private void markCaughtUp(Store.Book b){new AlertDialog.Builder(this).setTitle(\"Đã bắt kịp: \"+b.title).setMessage(\"Đánh dấu bộ này đã đọc trên HAKO. Bộ đếm chương mới sẽ tính từ mốc hiện tại; không đổi màu liên kết trên điện thoại.\").setPositiveButton(\"Đã đọc hết\",(d,w)->{Repository.cancel.set(false);task(\"Đánh dấu trên HAKO…\",()->{RenderedPage.action(this,HakoParser.ORIGIN+\"/ke-sach\",\"read:\"+b.id.substring(b.id.lastIndexOf('-')+1));store.shelfInfo(b.id,\"Không có chương mới tại lần đánh dấu vừa rồi\");return null;},()->message(\"HAKO đã xác nhận đánh dấu đã đọc.\"));}).setNegativeButton(\"Hủy\",null).show();} private void readerMenu(){new AlertDialog.Builder(this).setItems(new String[]{\"Chương trước\",\"Chương tiếp\",\"Đánh dấu đoạn\",\"Ẩn thanh công cụ\",\"Tải trước / tiếp tục\",\"Dừng tải\",\"Mở HAKO\",\"Chạm lật trang: \"+(prefs.getBoolean(\"taps\",true)?\"Bật\":\"Tắt\"),\"Đầu chương\",\"Cuối chương\",\"Đã bắt kịp trên HAKO\",\"Tải lại chương này\"},(d,w)->{ if(w==0)saveThen(()->adjacent(-1));if(w==1)saveThen(()->adjacent(1));if(w==2)bookmarks();if(w==3){bar.setVisibility(View.GONE);Toast.makeText(this,\"Vuốt dọc rồi chạm để hiện công cụ\",Toast.LENGTH_SHORT).show();}if(w==4)startDownloads(bookId,false);if(w==5)Repository.cancel.set(true);if(w==6){Store.Chapter ch=store.chapter(bookId,chapterId);saveThen(()->browse(ch.url));}if(w==7){boolean t=!prefs.getBoolean(\"taps\",true);prefs.edit().putBoolean(\"taps\",t).apply();if(nativeReader!=null)nativeReader.taps(t);}if(w==8&&nativeReader!=null)nativeReader.jump(false);if(w==9&&nativeReader!=null)nativeReader.jump(true);if(w==10)markCaughtUp(store.book(bookId));if(w==11){final String bid=bookId,cid=chapterId;store.state(cid,false,\"\");task(\"Tải lại chương…\",()->{Repository.cancel.set(false);Repository.download(this,store.chapter(bid,cid));return null;},()->showReader(bid,cid));}}).show();} private void confirmBulk() { new AlertDialog.Builder(this) .setTitle(\"Đồng bộ thư viện\") .setMessage( \"Tải phạm vi offline của các truyện đã có trong thư viện, kể cả khi không sạc. Truyện\" + \" chưa chọn vị trí sẽ tải từ chương đầu. Bạn có thể dừng bất cứ lúc nào.\") .setPositiveButton(\"Tải ngay\", (d, w) -> startDownloads(\"\", true)) .setNegativeButton(\"Hủy\", null) .show(); } private void startDownloads(String id, boolean all) { Intent i = new Intent(this, DownloadService.class).putExtra(\"book\", id).putExtra(\"all\", all); startForegroundService(i); } private void bookMenu(Store.Book b) { new AlertDialog.Builder(this) .setTitle(b.title) .setItems( new String[] { \"Mục lục / chọn chương\", \"Tải lại mục lục\", \"Tải truyện này\", \"Mở trên HAKO\", b.dropped?\"Tiếp tục theo dõi\":\"Tạm ngưng / drop\",b.pinned?\"Bỏ ghim\":\"Ghim thường đọc\",\"Đã bắt kịp trên HAKO\" }, (d, w) -> { if (w == 0) contents(b); if (w == 1) task( \"Cập nhật mục lục…\", () -> { prefs.edit().remove(\"catalog-\"+b.id).apply(); Repository.catalog(this, b); return null; }, () -> contents(b)); if (w == 2) startDownloads(b.id, false); if (w == 3) browse(b.url); if(w==5){store.pin(b.id,!b.pinned);frequentList();}if(w==6)markCaughtUp(b); if(w==4){store.dropped(b.id,!b.dropped);bookList(b.followed);} }) .show(); } private void addDialog() { EditText field = new EditText(this); field.setSingleLine(true); field.setHint(\"https://docln.sbs/truyen/…\"); field.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); new AlertDialog.Builder(this) .setTitle(\"Thêm truyện hoặc chương\") .setView(field) .setPositiveButton(\"Thêm\", (d, w) -> addUrl(field.getText().toString().trim())) .setNegativeButton(\"Hủy\", null) .show(); } private void addUrl(String u) { task( \"Đang lấy mục lục…\", () -> { Repository.add(this, u); return null; }, () -> { Store.Book b = store.book(HakoParser.storyId(HakoParser.storyUrl(u))); if (b != null) openBook(b); }); } private void openBook(Store.Book b) { if (store.chapters(b.id).isEmpty()) task( \"Đang lấy mục lục…\", () -> { Repository.catalog(this, b); return null; }, () -> {Store.Book loaded=store.book(b.id);if(!loaded.current.isEmpty()&&store.chapter(loaded.id,loaded.current)!=null)openChapter(loaded.id,loaded.current);else contents(loaded);}); else if (!b.current.isEmpty()&&store.chapter(b.id,b.current)!=null) openChapter(b.id, b.current); else contents(b); } private void contents(Store.Book b) { List chs = store.chapters(b.id); if (chs.isEmpty()) { message(\"Chưa có mục lục. Kết nối mạng và chọn Tải lại mục lục.\"); return; } String[] labels = new String[chs.size()]; int selected = 0; for (int n = 0; n < chs.size(); n++) { Store.Chapter c = chs.get(n); if (c.id.equals(b.current)) selected = n; labels[n] = (store.wasRead(c.id)?\"Đã mở · \":\"\") + (c.ready ? \"✓ \" : store.html(c.id).isFile() ? \"◐ \" : \"↓ \") + c.title + (c.error.isEmpty() ? \"\" : \" · \" + c.error); } AlertDialog dialog = new AlertDialog.Builder(this) .setTitle(b.title + \"\\n✓ đủ offline · ◐ thiếu ảnh · ↓ chưa tải\") .setSingleChoiceItems( labels, selected, (d, w) -> { d.dismiss(); openChapter(b.id, chs.get(w).id); }) .setNegativeButton(\"Đóng\", null) .create(); dialog.show(); } private void openChapter(String bid, String cid) { Store.Chapter c = store.chapter(bid, cid); if (c == null) { message(\"Không tìm thấy chương trong mục lục.\"); return; } if(!Repository.activeBook.equals(bid)){leaveReader();Repository.activeBook=bid;} Repository.cancel.set(false); Repository.pendingBook=bid; if (store.readable(cid)) { showReader(bid, cid); } else task( \"Đang tải \" + c.title + \"…\", () -> { Repository.download(this, c); return null; }, () -> { showReader(bid, cid); }); } private void showReader(String bid,String cid){ Store.Book b=store.book(bid);Store.Chapter c=store.chapter(bid,cid);if(b==null||c==null)return; if(!store.readable(cid)){message(\"Nội dung chưa lưu thành công. Hãy thử tải lại hoặc mở HAKO.\");return;} int pos=b.current.equals(cid)?b.pos:0;float fraction=b.current.equals(cid)?b.fraction:0; reset();bookId=bid;chapterId=cid;lastPos=pos;lastFraction=fraction;prefs.edit().putString(\"lastBook\",bid).apply(); if(!visitSession.equals(bid)){store.visited(bid);visitSession=bid;} nativeReader=new NativeReader(this,store.dir(cid),new NativeReader.Listener(){ public void position(int p,float f,int page,int count){if(isFinishing()||isDestroyed()||!bid.equals(bookId)||!cid.equals(chapterId))return;lastPos=p;lastFraction=f;store.position(bid,cid,p,f);store.readChapter(cid);if(title!=null)title.setText(c.title+\" · \"+(page+1)+\"/\"+count); if(!readerReady){readerReady=true;io.execute(()->store.prune(store.book(bid)));if(!bid.equals(\"demo\"))startDownloads(bid,false);}} public void boundary(int dir){if(!readerReady)return;new AlertDialog.Builder(MainActivity.this).setMessage(dir>0?\"Hết chương. Sang chương tiếp?\":\"Đầu chương. Mở chương trước?\").setPositiveButton(\"Chuyển\",(d,w)->adjacent(dir)).setNegativeButton(\"Ở lại\",null).show();} public void toolbar(){if(bar!=null)bar.setVisibility(bar.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);} });root.addView(nativeReader,new LinearLayout.LayoutParams(-1,0,1)); bar=new LinearLayout(this);bar.setOrientation(1);LinearLayout icons=new LinearLayout(this); String[] labels={\"Trang chính\",\"Mục lục\",\"Lưu vào tủ sách\",\"Chữ\",\"Menu\",\"Thoát\"};Runnable[] actions={()->saveThen(this::library),()->contents(store.book(bid)),this::saveBook,this::settings,this::readerMenu,this::exitApp}; for(int n=0;n chs = store.chapters(bookId); for (int n = 0; n < chs.size(); n++) if (chs.get(n).id.equals(chapterId)) { int target = n + delta; if (target >= 0 && target < chs.size()) openChapter(bookId, chs.get(target).id); else Toast.makeText( this, delta > 0 ? \"Đã đến cuối mục lục\" : \"Đây là chương đầu\", Toast.LENGTH_SHORT) .show(); return; } } private void bookmarks() { saveThen( () -> { String key = \"marks-\" + bookId; JSONArray arr; try { arr = new JSONArray(prefs.getString(key, \"[]\")); } catch (Exception e) { arr = new JSONArray(); } final JSONArray marks = arr; String[] labels = new String[marks.length() + 1]; labels[0] = \"+ Lưu đoạn đang đọc\"; for (int i = 0; i < marks.length(); i++) labels[i + 1] = marks.optJSONObject(i).optString(\"title\") + \" · đoạn \" + (marks.optJSONObject(i).optInt(\"pos\") + 1); new AlertDialog.Builder(this) .setTitle(\"Đánh dấu trên máy\") .setItems( labels, (d, w) -> { try { if (w == 0) { JSONObject m = new JSONObject(); m.put(\"chapter\", chapterId); m.put(\"pos\", lastPos); m.put(\"fraction\", lastFraction); m.put(\"title\", store.chapter(bookId, chapterId).title); marks.put(m); prefs.edit().putString(key, marks.toString()).apply(); Toast.makeText(this, \"Đã lưu đánh dấu\", Toast.LENGTH_SHORT).show(); } else { JSONObject m = marks.getJSONObject(w - 1); String bid = bookId; store.position( bid, m.getString(\"chapter\"), m.optInt(\"pos\"), (float) m.optDouble(\"fraction\")); openChapter(bid, m.getString(\"chapter\")); } } catch (Exception e) { message(\"Không đọc được đánh dấu.\"); } }) .setNeutralButton(\"Xóa đánh dấu…\", (dialog, which) -> removeBookmark(key, marks)) .setNegativeButton(\"Đóng\", null) .show(); }); } private void removeBookmark(String key, JSONArray marks) { if (marks.length() == 0) return; String[] names = new String[marks.length()]; for (int i = 0; i < names.length; i++) names[i] = marks.optJSONObject(i).optString(\"title\") + \" · đoạn \" + (marks.optJSONObject(i).optInt(\"pos\") + 1); new AlertDialog.Builder(this) .setTitle(\"Chọn đánh dấu muốn xóa\") .setItems( names, (d, w) -> { marks.remove(w); prefs.edit().putString(key, marks.toString()).apply(); Toast.makeText(this, \"Đã xóa đánh dấu trên máy\", Toast.LENGTH_SHORT).show(); }) .setNegativeButton(\"Hủy\", null) .show(); } private void importWebHistory(Runnable after){ if(web==null){after.run();return;}web.evaluateJavascript(\"localStorage.getItem('reading_series')\",value->{try{Object raw=new JSONTokener(value).nextValue();if(raw instanceof String){JSONArray list=new JSONArray((String)raw);for(int i=0;i { CookieManager.getInstance().flush(); importWebHistory(this::library); }, () -> web.loadUrl(HakoParser.ORIGIN + \"/ke-sach\"), () -> { String u = web.getUrl(); if (HakoParser.storyId(HakoParser.storyUrl(u == null ? \"\" : u)).isEmpty()) { message(\"Mở trang truyện hoặc chương rồi bấm Lưu truyện này.\"); return; } library(); addUrl(u); } }); status(); web = new WebView(this); WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(false); web.setWebChromeClient(new WebChromeClient()); web.setWebViewClient( new WebViewClient() { public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { String h = r.getUrl().getHost(); if (\"https\".equals(r.getUrl().getScheme()) && HakoParser.isOrigin(r.getUrl().toString())) return false; message( \"Liên kết ngoài HAKO không mở trong ứng dụng này. Đăng nhập bằng tên tài khoản/mật\" + \" khẩu trên HAKO; đăng nhập Google trong WebView chưa được hỗ trợ.\"); return true; } public void onPageFinished(WebView v, String u) { CookieManager.getInstance().flush(); if (status != null) status.setText(\"Mở truyện/chương → Đọc offline. Theo dõi bằng nút trên HAKO.\"); } public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) { if (req.isForMainFrame() && status != null) status.setText(\"Không tải được trang. Kiểm tra mạng và thử lại.\"); } }); root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); web.loadUrl(url); } private String readerFont(){int n=prefs.getInt(\"font2\",0);return n==1?\"DejaVu,serif\":n==2?\"sans-serif\":n==3?\"ReaderCustom,serif\":\"Tinos,serif\";} private void domain(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(HakoParser.ORIGIN);new AlertDialog.Builder(this).setTitle(\"Tên miền HAKO HTTPS\").setView(input).setPositiveButton(\"Kiểm tra\",(d,w)->{String value=input.getText().toString().trim();if(!value.startsWith(\"https://\"))value=\"https://\"+value;final String origin=value.replaceAll(\"/+$\",\"\");try{java.net.URI u=java.net.URI.create(origin);if(u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!u.getPath().isEmpty())throw new Exception();}catch(Exception e){message(\"Chỉ nhập tên miền HTTPS, không có đường dẫn.\");return;}task(\"Kiểm tra tên miền…\",()->{String h=Repository.page(origin,false);if(!h.contains(\"Light Novel\")&&!h.contains(\"HAKO\"))throw new Exception(\"Không nhận diện trang HAKO\");return null;},()->{Repository.cancel.set(true);HakoParser.ORIGIN=origin;prefs.edit().putString(\"origin\",origin).apply();store.rebase(origin);message(\"Đã đổi tên miền. Đăng nhập lại nếu cần.\");});}).setNegativeButton(\"Hủy\",null).show();} private void settings(){ LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(10),0,dp(10),0); Spinner fonts=new Spinner(this);fonts.setAdapter(new ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,new String[]{\"Tinos (có chân)\",\"DejaVu Serif\",\"Không chân hệ thống\",\"Font đã nhập\"}));fonts.setSelection(prefs.getInt(\"font2\",0));box.addView(fonts); TextView preview=text(\"Tiếng Việt: Nguyễn, quyển, tưởng, khuỷu — ă â ê ô ơ ư đ\",18);box.addView(preview); TextView sizeLabel=text(\"Cỡ chữ\",14);box.addView(sizeLabel);SeekBar size=new SeekBar(this);size.setMax(44);size.setProgress(Math.round((prefs.getFloat(\"size2\",18)-12)*2));box.addView(size); TextView lineLabel=text(\"Giãn dòng\",14);box.addView(lineLabel);SeekBar line=new SeekBar(this);line.setMax(8);line.setProgress(Math.round((prefs.getFloat(\"line\",1.5f)-1.2f)*10));box.addView(line); TextView marginLabel=text(\"Lề\",14);box.addView(marginLabel);SeekBar margin=new SeekBar(this);margin.setMax(28);margin.setProgress(prefs.getInt(\"margin\",10));box.addView(margin); CheckBox bold=check(\"Nét đậm (thử trên màn E Ink)\",prefs.getInt(\"weight\",400)>400);box.addView(bold); Runnable update=()->{float fs=12+size.getProgress()/2f;sizeLabel.setText(\"Cỡ chữ: \"+fs+\" sp\");lineLabel.setText(\"Giãn dòng: \"+(1.2f+line.getProgress()/10f));marginLabel.setText(\"Lề: \"+margin.getProgress()+\" dp\");preview.setTextSize(fs);try{int n=fonts.getSelectedItemPosition();android.graphics.Typeface tf=n==0?android.graphics.Typeface.createFromAsset(getAssets(),\"Tinos.ttf\"):n==1?android.graphics.Typeface.createFromAsset(getAssets(),\"DejaVu.ttf\"):n==2?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromFile(new File(getFilesDir(),\"reader-font.ttf\"));preview.setTypeface(tf,bold.isChecked()?1:0);}catch(Exception e){preview.setTypeface(android.graphics.Typeface.SERIF);}}; SeekBar.OnSeekBarChangeListener listener=new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};size.setOnSeekBarChangeListener(listener);line.setOnSeekBarChangeListener(listener);margin.setOnSeekBarChangeListener(listener);fonts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView a,View v,int p,long id){update.run();}public void onNothingSelected(AdapterView a){}});bold.setOnCheckedChangeListener((a,b)->update.run());update.run(); CheckBox taps=check(\"Chạm trên/dưới để lật trang\",prefs.getBoolean(\"taps\",true));box.addView(taps);CheckBox charging=check(\"Đồng bộ tủ sách khi sạc\",prefs.getBoolean(\"charging\",true));box.addView(charging); box.addView(button(\"Nhập font TTF / OTF\",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(\"*/*\").addCategory(Intent.CATEGORY_OPENABLE),88))); box.addView(button(\"Tên miền HAKO\",this::domain));box.addView(button(\"Đọc mẫu offline\",this::demo)); box.addView(text(\"Tải trước 1–2: chờ 4s; 3–5: 12s; 6–10: 30s; 11–15: 60s. Màn hình tắt: ngừng tải trước khi dùng pin. Đọc bản lưu không cần chạy trang HAKO.\",12)); ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle(\"Chữ & tải nội dung\").setView(scroll).setPositiveButton(\"Áp dụng\",(d,w)->{if(fonts.getSelectedItemPosition()==3&&!new File(getFilesDir(),\"reader-font.ttf\").isFile()){message(\"Chưa nhập font. Đang giữ lựa chọn cũ.\");return;}prefs.edit().putInt(\"font2\",fonts.getSelectedItemPosition()).putFloat(\"size2\",12+size.getProgress()/2f).putFloat(\"line\",1.2f+line.getProgress()/10f).putInt(\"margin\",margin.getProgress()).putInt(\"weight\",bold.isChecked()?700:400).putBoolean(\"taps\",taps.isChecked()).putBoolean(\"charging\",charging.isChecked()).apply();ChargeJob.schedule(this,charging.isChecked());if(nativeReader!=null)applyReaderStyle();}).setNegativeButton(\"Đóng\",null).show(); } private CheckBox check(String label, boolean checked) { CheckBox c = new CheckBox(this); c.setText(label); c.setTextColor(INK); c.setChecked(checked); return c; } private void applyReaderStyle(){if(nativeReader==null)return;android.graphics.Typeface face;int choice=prefs.getInt(\"font2\",0);try{face=choice==3?android.graphics.Typeface.createFromFile(new File(getFilesDir(),\"reader-font.ttf\")):choice==2?android.graphics.Typeface.SANS_SERIF:android.graphics.Typeface.createFromAsset(getAssets(),choice==1?\"DejaVu.ttf\":\"Tinos.ttf\");}catch(Exception e){face=android.graphics.Typeface.SERIF;} face=android.graphics.Typeface.create(face,prefs.getInt(\"weight\",400)>=700?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL); nativeReader.style(prefs.getFloat(\"size2\",18f),prefs.getInt(\"margin\",10),prefs.getFloat(\"line\",1.5f),face,prefs.getBoolean(\"taps\",true)); } private void help() { new AlertDialog.Builder(this) .setTitle(\"Hako Pocket 0.3 • Bản thử nghiệm\") .setMessage( \"Đăng nhập HAKO → Nhập kệ sách → chọn truyện → chọn chương.\\n\\n\" + \"✓ là chương có đủ nội dung/ảnh. ◐ là đã có chữ nhưng thiếu ảnh. Nhấn giữ truyện\" + \" để cập nhật mục lục hoặc tải riêng.\\n\\n\" + \"Vị trí đoạn lưu trên APK. Menu Đã bắt kịp cập nhật bộ đếm HAKO khi bạn xác nhận.\\n\\n\" + \"Chú thích hiển thị bằng ký hiệu ✎; bấm để xem. Chưa kiểm chứng mọi định dạng\" + \" HAKO.\\n\\n\" + \"Dùng nút Dừng tải để tạm dừng; lượt tải sau bỏ qua chương đã hoàn tất. Dữ liệu\" + \" nằm trong ứng dụng, sẽ mất nếu gỡ app/xóa dữ liệu.\") .setPositiveButton(\"Đóng\", null) .setNeutralButton(\"Đọc mẫu\", (d, w) -> demo()) .show(); } private void demo() { store.putBook(new HakoParser.Link(\"demo\", \"Hướng dẫn Hako Pocket (offline)\", \"\")); List ls = new ArrayList<>(); for (int n = 1; n <= 12; n++) ls.add(new HakoParser.Link(\"demo-\" + n, \"Chương mẫu \" + n, \"\")); store.catalog(\"demo\", ls); try { for (int n = 1; n <= 12; n++) { StringBuilder b = new StringBuilder(); b.append( \"Chương mẫu \" + n + \"Đây là nội dung mẫu tự viết để thử đọc offline. Không lấy từ truyện\" + \" HAKO.✎ Chú thích mẫuBấm icon để mở chú thích\" + \" ngay trong chương.\"); for (int p = 1; p <= 24; p++) b.append( \"Đoạn \" + p + \". Bạn có thể chạm nửa dưới màn hình để tiến, nửa trên để lùi. Vị trí này được\" + \" lưu trên thiết bị. Đổi cỡ chữ, về thư viện rồi mở lại để thử khả năng tiếp\" + \" tục đọc.\"); Store.write(store.html(\"demo-\" + n), b.toString()); store.state(\"demo-\" + n, true, \"\"); } openBook(store.book(\"demo\")); } catch (Exception e) { message(e.getMessage()); } } protected void onActivityResult(int req, int result, Intent data) { super.onActivityResult(req, result, data); if (req == 88 && result == RESULT_OK && data != null) { Uri u = data.getData(); task( \"Đang nhập font…\", () -> { File dest = new File(getFilesDir(), \"reader-font.tmp\"); try (InputStream in = getContentResolver().openInputStream(u); FileOutputStream out = new FileOutputStream(dest)) { byte[] b = new byte[8192]; int n, total = 0; while ((n = in.read(b)) != -1) { total += n; if (total > 8 * 1024 * 1024) throw new IOException(\"Font tối đa 8 MB\"); out.write(b, 0, n); } } android.graphics.Typeface.createFromFile(dest); if (!dest.renameTo(new File(getFilesDir(), \"reader-font.ttf\"))) throw new IOException(\"Không lưu được font\"); prefs.edit().putBoolean(\"customFont\", true).putInt(\"font2\",3).apply(); return null; }, () -> { applyReaderStyle();message(\"Đã nhập font.\"); }); } } protected void onPause() { if(nativeReader!=null&&readerReady)store.position(bookId,chapterId,lastPos,lastFraction); if (web != null) { CookieManager.getInstance().flush(); } super.onPause(); } protected void onDestroy() { if (updates != null) unregisterReceiver(updates); if (web != null) { web.removeJavascriptInterface(\"Bridge\"); web.destroy(); } io.shutdown(); super.onDestroy(); } public void onBackPressed() { if(nativeReader!=null){saveThen(this::library);return;} if (online && web != null && web.canGoBack()) { web.goBack(); return; } if (web != null) { if(online){importWebHistory(this::library);return;} saveThen(this::library); return; } super.onBackPressed(); } public boolean onKeyUp(int key,KeyEvent event){if(nativeReader!=null&&(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN))return true;return super.onKeyUp(key,event);} public boolean onKeyDown(int key, KeyEvent event) { if(nativeReader!=null){if(key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_PAGE_DOWN||key==KeyEvent.KEYCODE_DPAD_RIGHT){nativeReader.turn(1);return true;}if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_PAGE_UP||key==KeyEvent.KEYCODE_DPAD_LEFT){nativeReader.turn(-1);return true;}} return super.onKeyDown(key, event); } }"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_LOCAL."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## Local Commerce Philosophy\n\nThe user demonstrates a strong reliance on digital-first, app-based services for daily urban living in Ho Chi Minh City [8, ...]. The user prioritizes speed, convenience, and cashless transactions when managing local transportation, express deliveries, utility payments, grooming services, and entertainment [8, ...]. The user systematically leverages local mobile wallets and digital banking applications to handle routine service expenses without relying on cash payments [9, ...].\n\n## Key Priorities & Non-Negotiables\n\n*   **Cashless & App-Based Transactions:** The user consistently utilizes digital banking and mobile wallet accounts to process payments for local services, transportation, utilities, and event registrations [9, ...].\n*   **On-Demand Urban Logistics & Travel:** The user prioritizes on-demand service providers for localized travel and same-day courier solutions across Ho Chi Minh City [85, ...].\n*   **Localized Urban Convenience:** The user shows a factual pattern of selecting service providers located in central and nearby districts of Ho Chi Minh City, such as Gò Vấp, Bình Thạnh, District 4, and District 7 [5, ...].\n\n## Engagement Habits & Patterns\n\n*   **Ride-Hailing & On-Demand Transit:** The user regularly hires motorcycle ride-hailing services like \"be\" and Gojek GoRide for point-to-point transit within Ho Chi Minh City [9, ...].\n*   **Courier & Parcel Delivery Services:** The user frequently utilizes local logistics and courier platforms, including Lalamove, Gojek GoSend, GrabExpress, ViettelPost, and J&T Express, to handle same-day deliveries and document shipments [26, ...].\n*   **Grooming & Personal Care Services:** The user maintains recurring visits to local hair grooming establishments in Ho Chi Minh City, such as 30Shine and Tiệm tóc Cô Tiên [111, ...].\n*   **Cinema & Entertainment Attendance:** The user periodically books cinema tickets online for venues in Ho Chi Minh City, including Beta Ung Văn Khiêm, CGV Vincom Đồng Khởi, and CGV Giga Mall Thủ Đức [8, ...].\n*   **Intercity Bus Travel:** The user books long-distance intercity bus and limousine services through mobile wallets for travel between Ho Chi Minh City and Kon Tum [3, ...].\n*   **Technical Workshops & Industry Expos:** The user consistently registers in advance for local technology expos, photography workshops, and videography training sessions across Ho Chi Minh City [5, ...].\n*   **Utility & Essential Service Management:** The user routinely settles monthly residential electricity bills with Ho Chi Minh City Electric Power Corporation through digital banking apps [15, ...].\n\n## Trusted Local Providers & Establishments\n\n**Ride-Hailing & On-Demand Transit**\n*   \"be\" (ride-hailing platform) [9, ...].\n*   Gojek GoRide (motorcycle transit service) [312].\n\n**Logistics & Express Courier Services**\n*   Lalamove (local freight and van delivery) [85, ...].\n*   Gojek GoSend (short-haul parcel delivery) [307, ...].\n*   GrabExpress (express parcel courier service) [324].\n*   J&T Express HCM / Công ty TNHH MTV Chuyển phát nhanh Thuận Phong - Chi nhánh TP.HCM (express delivery provider) [26, ...].\n*   ViettelPost (express logistics and electronic contract services) [77, ...].\n\n**Personal Care & Grooming Services**\n*   30Shine (barbershop located at 359 Lê Quang Định, Ho Chi Minh City) [111, ...].\n*   Tiệm tóc Cô Tiên (hair salon service in Ho Chi Minh City) [100, ...].\n\n**Cinemas & Entertainment Venues**\n*   Beta Ung Văn Khiêm (Bình Thạnh District, Ho Chi Minh City) [8].\n*   CGV Vincom Đồng Khởi (District 1, Ho Chi Minh City) [79].\n*   CGV Giga Mall Thủ Đức (Thủ Đức, Ho Chi Minh City) [272].\n\n**Event & Workshop Venues**\n*   Nina Next Space (180/1 Nguyễn Tất Thành, District 4, Ho Chi Minh City) [5, ...].\n*   Nina Studio (37 Bế Văn Cấm, District 7, Ho Chi Minh City) [328].\n*   Hello World (92A Nguyễn Hữu Cảnh, Bình Thạnh District, Ho Chi Minh City) [228].\n*   SKY EXPO Vietnam (Quang Trung Software City, District 12, Ho Chi Minh City) [115, ...].\n*   Saigon Exhibition and Convention Center - SECC (District 7, Ho Chi Minh City) [121].\n*   Phú Thọ Indoor Stadium (District 11, Ho Chi Minh City) [314].\n*   Sala Park (Thủ Đức, Ho Chi Minh City) [320].\n\n**Intercity Bus Operators**\n*   Nhà xe Việt Tân Phát (bus operator serving the Kon Tum – Ho Chi Minh City route) [3].\n*   Nhà xe Tư Phầu (limousine bus operator serving the Ho Chi Minh City – Kon Tum route) [74].\n\n**Short-Stay Accommodation**\n*   Cupid Hotel (hourly hotel room provider in Ho Chi Minh City) [76, ...].\n\n**Utilities & Infrastructure Providers**\n*   Tổng công ty điện lực TP.HCM (Ho Chi Minh City Electric Power Corporation for power supply) [15, ...].\n*   Petrolimex Saigon CH21 (fueling station for vehicle gasoline) [30].\n\n**Domain Registration & Web Management**\n*   P.A Việt Nam Ltd (local domain registration and web infrastructure service) [1, ...].\n\n## Preferred Platforms & Community Tools\n\n*   **MoMo:** The user's primary mobile wallet for booking intercity transportation, purchasing cinema tickets, paying for ride-hailing rides, and managing digital service links [3, ...].\n*   **Cake by VPBank:** Digital banking application utilized for peer-to-peer transfers, utility bill payments, and card-based transactions [0, ...].\n*   **VPBank NEO:** Digital banking service used for online account transfers and QR Pay transactions [277, ...].\n*   **Google Maps & Dòng Thời Gian (Timeline):** Navigation and activity-tracking platform used for location history records and contributing local address edits in Ho Chi Minh City [272, ...].\n*   **Bkav eContract & ViettelPost vContract:** Electronic contract signature tools utilized for signing corporate service and logistics agreements [26, ...].\n\n## Local Projects\n\n*   **Residential Housing Lease (2026):** On January 27, 2026, the user documented a residential lease agreement (\"Hợp đồng thuê nhà 2026\") in Ho Chi Minh City [73].\n*   **Web Domain Management Projects:** The user maintains local web domain registrations through P.A Việt Nam, including `nanains.io.vn` registered in September 2025 and `chara.com.vn` up for renewal in April 2024 [1, ...].\n*   **Industry & Technology Conventions:** The user registered for and attended major regional trade expos in Ho Chi Minh City, including Vietnam GameVerse 2024 at Phú Thọ Indoor Stadium in May 2024, IEAE 2025 at SECC in May 2025, and iTECH EXPO 2025 at SKY EXPO in July 2025 [116, ...].\n*   **Photography & Media Production Workshops:** The user actively registers for local creative workshops, including \"Trò Chuyện Về Cinematic Vlog\" (January 2024), KOV.Pictures' two-day \"QUAY-DỰNG PHIM\" video production workshop (March 2024), zShop's \"Chụp Ảnh Đường Phố cùng Kmon Nguyen\" (April 2024), zShop's \"Ứng Dụng Micro Trong Sáng Tạo Nội Dung\" (February 2025), zShop's \"Chụp Hình Quảng Cáo Thương Mại\" (April 2025), and Canon's \"Thực hành Concept Beauty Dát Vàng\" (April 2025) [5, ...]."
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_TRAVEL."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## Travel Style & Interests\n\n* Overland intercity travel connecting major urban centers with regional destinations in Vietnam, primarily between Ho Chi Minh City and the Central Highlands region [1, ...].\n* Travel schedules structured around overnight long-distance transit, utilizing evening departures and early morning arrivals [1, ...].\n\n## Travel Constraints & Dealbreakers\n\n* **Budgetary Considerations**: Reliance on long-distance sleeper and limousine bus transportation for intercity travel [1, ...].\n* **Travel Non-Negotiables & Accessibility Requirements**: N/A\n\n## Travel Habits & Logistics\n\n* **Trip Duration & Transit Timing**: Intercity journeys frequently follow an overnight transit format, with departures typically set between 14:00 and 20:00 and arrivals occurring the following morning between 02:00 and 09:15 [1, ...].\n* **Travel Companions**: Bookings made via the user's account include individual travel reservations for the user as well as ticket bookings made for other passengers [1, ...].\n* **Vehicle Preference**: Consistent selection of upgraded bus travel configurations, including 24-double cabin rooms, 22-room luxury sleepers, and 34-bed limousine buses [1, ...].\n\n## Flight & Accommodation Preferences\n\nN/A\n\n## Preferred Booking Platforms & Loyalty Programs\n\n* **Preferred Booking Platforms**:\n  * **MoMo**: Utilized for purchasing intercity bus tickets across multiple transport providers, including Phương Trang (Futa Buslines), Việt Tân Phát, Phong Phú, and Tư Phầu [1, ...].\n  * **Vexere**: Utilized for reserving intercity bus travel with operators such as Tấn Hưng [34, ...].\n* **Loyalty Programs & Elite Status**: N/A\n\n## Travel History & Destinations\n\n* **Kon Tum, Vietnam**: Bus travel bookings between Ho Chi Minh City and Kon Tum were completed for trips on February 4, 2025; July 25, 2025; July 31, 2025; August 9, 2025; September 20, 2025; and February 9, 2026 [1, ...].\n* **An Khê, Gia Lai, Vietnam**: Bus travel from Ho Chi Minh City to An Khê was booked for February 20, 2025 [34, ...].\n* **Ho Chi Minh City, Vietnam**: Serves as the primary origin and destination hub for intercity overland bus trips [1, ...]."
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_SHOPPING."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## Product & Style Preferences\n\n* **Crafts, 3D Printing, & Hardware Supplies:** The user frequently purchases 3D printing equipment, replacement components, and fabrication materials [232, ...]. These purchases include 3D printing filaments in PLA, PLA-F, PETG, and ABS across various colors such as grey, white, silver, and red [232, ...]. Hardware acquisitions consist of extruders, stepper motors, timing belts, lead screws, pulleys, linear bearings, print head assemblies, hotends, heatbreakers, and belt tensioners [234, ...]. For project finishing, assembly, and maintenance, the user buys orbital sanders, airbrush kits, water trap filters, silicon carbide sanding discs in multiple grits, deburring tools, silicone oil, thinners, primers, metallic lacquer paints, and specialized glues [286, ...].\n* **Cosplay Props & 3D Digital Models:** The user routinely acquires 3D printable STL digital models for cosplay props, weapons, armor, masks, and costume accessories [0, ...]. The themes center on popular anime, manga, and video game franchises, including *Genshin Impact*, *Honkai: Star Rail*, *Zenless Zone Zero*, *Wuthering Waves*, *League of Legends*, *Frieren: Beyond Journey's End*, *Puella Magi Madoka Magica*, *Jujutsu Kaisen*, and *Elden Ring* [0, ...].\n* **Apparel & Cosplay Wear:** The user purchases women's apparel items such as two-piece loungewear sets, lace nightwear, V-neck mini dresses, and themed anime cosplay costumes [3, ...].\n* **Manga, Light Novels, & Books:** The user collects physical manga box sets, special edition volumes, and light novels focusing primarily on Yuri, magical girl, and anime-adapted genres [230, ...].\n* **Wig Styling & Beauty Tools:** The user buys wig maintenance and styling equipment, including foam mannequin heads, salon hair clips, hair crimping irons, and strong-hold hairsprays [279, ...].\n* **Electronics & Accessories:** Consumer electronics purchases include high-refresh-rate portable IPS monitors, smartwatches, smartphones, high-capacity power banks, and Bluetooth adapters for earphones [318, ...].\n\n## Budget & Purchase Constraints\n\n* **Cost Optimization & Bulk Buying:** The user demonstrates a clear pattern of cost-conscious purchasing by ordering bulk 3D printing filament spools (such as 3kg packages) and multi-piece tool sets [232, ...]. \n* **Promotional Discounts:** The user consistently applies e-commerce platform discount vouchers, store-specific coupons, and free shipping promotions on physical retail orders [236, ...].\n* **Free Asset Utilization:** The user actively acquires free digital 3D printable models and promotional software licenses whenever available [1, ...].\n* **Financing:** For higher-value consumer electronics purchases like smartphones and smartwatches, the user has utilized installment loan services [320, ...].\n\n## Shopping Habits & Lifestyle\n\n* **Maker & Fabrication Lifestyle:** The user engages in continuous online shopping for fabrication supplies, hardware parts, tools, and digital 3D models across 2024, 2025, and 2026 [0, ...]. \n* **Digital Model Curation:** The user regularly collects digital STL files for weapons, horns, armor pieces, and headwear, supporting an ongoing workflow of self-printing, sanding, painting, and assembling cosplay components [0, ...].\n* **Online-Centric Purchasing:** Shopping is conducted almost entirely through digital channels, ranging from major e-commerce platforms to specialized digital asset marketplaces and software distributors [0, ...].\n* **Software & Digital Service Subscriptions:** In addition to physical materials, the user maintains active subscriptions for 3D modeling software, creative graphics suites, and online media services [29, ...].\n\n## Preferred Retailers & Brands\n\n* **E-Commerce Marketplaces:** Shopee, Lazada Vietnam, Tiki, Etsy [3, ...].\n* **Digital 3D Model & Cosplay Vendors:** Cults3D, Skookum Props, DangerousLadies, Kosplayit, BrunenG, Gank, Ko-fi [0, ...].\n* **3D Printing & Tool Brands:** Kingroon, Jamg He, Anycubic, Bondtech, TOTAL, Bosny, Samurai, Jumpwind, ATM, Xiaomi [234, ...].\n* **Book & Manga Publishers:** IPM Vietnam, Kim Dong Publishing House [230, ...].\n* **Electronics Brands:** Xiaomi, KZ [320, ...].\n* **Apparel Brands:** VERA Vietnam [7, ...].\n* **Digital Software & Gaming Retailers:** Steam, GOG.com, G2G, SEAGM, MuaKey, RoyalCDKeys, Wincdkey, Paddle [4, ...].\n\n## Preferred E-commerce Platforms & Services\n\n* **Shopee:** Operates as the user's primary online marketplace for physical 3D printing supplies, machine hardware, hand tools, paints, styling accessories, and manga [232, ...].\n* **Lazada Vietnam & Tiki:** Utilized for selecting specific apparel items, light novels, and packaged software products [3, ...].\n* **Cults3D:** Serves as the primary source for downloading digital 3D printable STL models, supplemented by specialized storefronts on Etsy, Skookum Props, and DangerousLadies [0, ...].\n* **Digital Key & Gaming Storefronts:** Steam, G2G, SEAGM, and MuaKey are used for acquiring game passes, software licenses, and digital top-ups [4, ...].\n\n## Evolving Tastes & Recent Discoveries\n\n* **3D Design Software Acquisition:** In November 2025, the user purchased a license for Plasticity 3D modeling software, indicating an expansion from downloading pre-made STL assets toward custom 3D model editing and creation [94, ...].\n* **AI Prototyping Tools:** In June 2026, the user added a subscription for Lovable AI Pro development services, reflecting an interest in AI-assisted software tools [11, ...].\n* **Expanded Franchise Interests:** Throughout 2025 and 2026, the user continuously expanded prop asset acquisitions to include newly released gaming titles and anime series, such as *Strinova*, *Zenless Zone Zero*, *Wuthering Waves*, and *Arknights: Endfield* [6, ...]."
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_DINING."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## Culinary Preferences\n\n*   **Vietnamese Local & Street Food Cuisine**: The user frequently purchases traditional Vietnamese dishes, including Bún đậu mắm tôm from Kim - Bún Đậu Mẹt [105, ...], Bánh cuốn Tây Sơn [90, ...], Bánh canh cua from Cô Liên [77], Bò né [81], Hủ tiếu from Cô Ba Ly [110, ...], Cơm gà Xứ Quảng [106], Bánh mì from Lò Bánh Mì Hà Nội Tuyết Nhi and Bánh Mì Thịt Nướng [108, ...], Bánh tráng from Bánh Tráng Tiệm Chuyên Sỉ và Lẻ [121, ...], and stir-fried macaroni with beef (Nui xào bò lúc lắc) [139, ...].\n*   **Korean Cuisine**: The user frequently dines on Korean dishes, specifically Korean tteokbokki buffets at Spicy Box [4, ...] and Johnson Tteokbokki [100], as well as Korean BBQ buffet items at Subin Giga Mall [135].\n*   **Western & Fast Food**: The user orders American-style fried chicken and burgers, including purchases from Texas Chicken [101] and KFC [103, ...].\n*   **Tea & Beverage Preferences**: The user consistently orders milk tea, fruit tea, and kumquat tea from vendors such as Hồng Trà Ngô Gia [142, ...], Trà Sữa Kim [89, ...], and Tiệm Trà Mix [118].\n*   **Coffee**: The user purchased BioReishi Coffee in March 2024 [42].\n*   **Flavor Profiles**: The user frequently chooses spicy menu options, such as spicy chicken burgers and spicy fried chicken from KFC [104], spicy tteokbokki [4, ...], and spicy BBQ baby octopus [135].\n\n## Dietary Profile & Restrictions\n\n*   **Observed Dietary Choices**: The user's order records demonstrate consistent consumption of various animal proteins, including beef, chicken, and octopus, alongside dairy, wheat, rice, and caffeine [42, ...].\n*   **Dietary Restrictions**: There is no factual evidence in the emails indicating any specific dietary restrictions, , or specialized lifestyle diets [4, ...].\n\n## Eating Habits & Lifestyle\n\n*   **Dining Schedule & Timing**: The user places late-evening food orders and makes evening dining payments, with delivery purchases occurring between 19:56 and 22:43 [139, ...] and dinner payments recorded between 19:55 and 21:38 [66, ...].\n*   **Breakfast Purchases**: The user occasionally completes morning breakfast transactions [85].\n*   **Dining Companionship**: The user engages in both multi-person group dining (e.g., two-person buffet sets at Subin Giga Mall and multi-item fast food combo meals) [104, ...] and single-portion casual meal orders [77, ...].\n*   **Atmosphere Preferences**: The user shows a consistent pattern of dining at casual eateries, Korean buffet spots, fast-food outlets, and local street-food vendor locations [4, ...].\n\n## Restaurant Preference\n\n*   **Spicy Box**: The user made multiple purchases at this Korean tteokbokki buffet establishment between June 2024 and April 2025 [4, ...].\n*   **Kim - Bún Đậu Mẹt**: The user ordered from or visited this establishment multiple times between March 2025 and May 2025 [105, ...].\n*   **Tiệm Cơm Nhà Tròn**: The user completed multiple transactions at this rice eatery in July 2025 and August 2025 [96, ...].\n*   **Nui Xào Bò, Mì & Cơm Bò Xào**: The user placed multiple food delivery orders from this establishment in January 2024 and February 2024 [139, ...].\n*   **Hồng Trà Ngô Gia**: The user placed multiple beverage delivery orders from this shop in January 2024 [142, ...].\n*   **Trà Sữa Kim**: The user completed transactions with this tea vendor in April 2024 and August 2025 [89, ...].\n*   **Bánh Cuốn Tây Sơn**: The user completed multiple transactions with this specialty vendor in August 2025 [90, ...].\n*   **Hủ Tiếu Cô Ba Ly**: The user completed transactions at this noodle shop in February 2025 and April 2025 [110, ...].\n*   **Subin Giga Mall Thủ Đức**: The user dined at this Korean buffet restaurant in March 2024 [135].\n*   **Texas Chicken**: The user purchased food from this fast-food chain in June 2025 [101].\n*   **KFC Vietnam**: The user ordered food delivery from this fast-food establishment in May 2025 [103, ...].\n*   **Johnson Tteokbokki**: The user completed a transaction with this restaurant in July 2025 [100].\n*   **Bánh Canh Cua Cô Liên**: The user paid for a dinner meal at this venue in January 2026 [77].\n*   **Bò Né**: The user completed a payment to this food establishment in January 2026 [81].\n*   **Lò Bánh Mì Hà Nội Tuyết Nhi**: The user completed a transaction at this bakery vendor in April 2025 [108].\n*   **Bánh Mì Thịt Nướng**: The user completed a transaction with this food vendor in April 2025 [114].\n*   **Cơm Gà Xứ Quảng**: The user completed a transaction with this dining merchant in May 2025 [106].\n*   **Hiền Ấn Tượng**: The user completed a transaction with this merchant in August 2025 [92].\n*   **Tiệm Trà Mix**: The user completed a transaction with this beverage establishment in December 2024 [118].\n*   **Bánh Tráng Tiệm Chuyên Sỉ và Lẻ**: The user completed transactions with this seller in July 2024 [121, ...].\n*   **Ministop**: The user made a payment at this convenience store location in July 2026 [62].\n\n## Preferred Delivery Services\n\n*   **Grab / GrabFood**: The user frequently utilized Grab for food delivery orders between January 2024 and June 2026 [1, ...].\n*   **MoMo**: The user frequently used MoMo as a payment gateway for food delivery platforms and direct dining payments between January 2024 and August 2026 [0, ...].\n*   **be**: The user utilized the 'be' service for orders and transport transactions in March 2026 and August 2026 [0, ...].\n\n## Evolving Tastes & Recent Trends\n\n*   **Early 2024 Focus**: In January and February 2024, food delivery orders heavily featured Vietnamese stir-fried beef macaroni (Nui xào bò lúc lắc) paired with kumquat tea or fruit tea from local vendors [139, ...].\n*   **Mid-2024 to Early 2025 Pattern**: Between June 2024 and April 2025, the user frequently visited and made payments to Korean buffet establishments, particularly Spicy Box [4, ...].\n*   **Spring 2025 Pattern**: Between March 2025 and May 2025, the user established a recurring pattern of purchasing traditional Bún đậu mắm tôm from Kim - Bún Đậu Mẹt [105, ...].\n*   **Late 2025 Trends**: In July and August 2025, the user's food purchases shifted toward traditional home-style rice meals from Tiệm Cơm Nhà Tròn and regional specialties from Bánh Cuốn Tây Sơn [90, ...].\n*   **2026 Trends**: In January 2026, the user's dining transactions featured specialized Vietnamese comfort dishes such as Bánh canh cua and Bò né [77, ...]."
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_RELATIONSHIP."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## Legal Name\nNguyễn Gia Huy [1, ...]\n\n## Date of Birth\nSeptember 20, 1999 [201, ...]\n\n## Age\n26 years old [201, ...]\n\n## Immediate & Extended Family\nN/A\n\n## Connections with Direct, Mutual Email Conversations\nN/A\n\n## Pets\nN/A"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following plain text contains details about the user:"
                      }
                    },
                    {
                      "name":  "description",
                      "value":  {
                        "stringValue":  "The user detail segment is PERSONAL_GLOSSARY."
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "## **Personalized Glossary**\n*   **Strinova**:\n    *   **Definition**: A competitive hero-shooter video game played by the user, who actively participates in gameplay modes and submits feedback to developers [4, ...].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: Outbreak Mode, Superstring Agent, Crystallize phase, Steam [56, ...]\n*   **Cults3D**:\n    *   **Definition**: An online marketplace for 3D printable models where the user regularly purchases digital 3D designs for cosplay props [8, ...].\n    *   **Synonyms**: Cults, Cults3D.com [50, ...]\n    *   **Related Terms**: 3D Printing, STL files, Cosplay Props, Goowah, Shokos3DPrints [8, ...]\n*   **Asana**:\n    *   **Definition**: A project management application utilized by the user to coordinate task schedules and timelines for 3D prop fabrication [12, ...].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: Project plan timeline, props project, Task management [15, ...]\n*   **Genshin Impact**:\n    *   **Definition**: An open-world action role-playing game from which the user frequently acquires 3D printable prop files and custom modifications [41, ...].\n    *   **Synonyms**: Genshin [58, ...]\n    *   **Related Terms**: HoYoverse, Primogems, Varesa, Xiao, Arlecchino [41, ...]\n*   **Honkai: Star Rail**:\n    *   **Definition**: A space fantasy role-playing game from which the user sources various 3D prop models and digital accessories [8, ...].\n    *   **Synonyms**: Star Rail, HSR [71, ...]\n    *   **Related Terms**: HoYoverse, Stellar Jades, Acheron, Castorice, Kafka [8, ...]\n*   **Outbreak Mode**:\n    *   **Definition**: A roguelike infection-style game mode in Strinova actively played by the user and friends [56, ...].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: Strinova, Operation Restraint, Crystallize phase [56, ...]\n*   **ACDSee Photo Studio**:\n    *   **Definition**: An image editing software program used by the user, who sought technical support for application startup issues [1, ...].\n    *   **Synonyms**: ACDSee Professional [1, ...]\n    *   **Related Terms**: ACD Systems, Photo editing, Technical support [62, ...]\n*   **Project plan timeline**:\n    *   **Definition**: The primary Asana project workspace set up by the user to organize cosplay prop creation schedules [12, ...].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: Asana, Props project, Workspaces [14, ...]\n*   **Airbrush**:\n    *   **Definition**: A precision painting tool and accessories purchased by the user for surface finishing on 3D-printed cosplay models [52, ...].\n    *   **Synonyms**: HD-130 Airbrush, AB-130 [52, ...]\n    *   **Related Terms**: 3D Printing, Model finishing, Quick disconnect coupling [52, ...]\n*   **PLA (Polylactic Acid)**:\n    *   **Definition**: A 3D printing filament material ordered in bulk by the user for fabricating prop models [51, ...].\n    *   **Synonyms**: PLA Filament [51, ...]\n    *   **Related Terms**: 3D Printing, Jamghe, Kingroon, Volcano Nozzle [55, ...]\n*   **Skookum Props**:\n    *   **Definition**: An online vendor for digital cosplay prop models from which the user ordered 3D asset downloads [0].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: 3D Models, Cosplay Props, Magic Knight Rayearth [0]\n*   **Tripo AI**:\n    *   **Definition**: An artificial intelligence platform for text-to-3D and image-to-3D generation registered to by the user [88].\n    *   **Synonyms**: Tripo, Tripo 3D [88]\n    *   **Related Terms**: Generative AI, 3D Creation, Tripo Studio [88]\n*   **Vzbot**:\n    *   **Definition**: A high-performance 3D printer framework documented in assembly guides and ebooks accessed by the user [218].\n    *   **Synonyms**: Vzbot TaoTac [218]\n    *   **Related Terms**: 3D Printing, TaoTac, DIY 3D Printer [202, ...]\n*   **Klipper**:\n    *   **Definition**: A 3D printer firmware for which the user created a dedicated GitHub personal access token for configuration backups [219].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: GitHub, 3D Printer Firmware, Configuration backup [219]\n*   **Cake by VPBank**:\n    *   **Definition**: A digital banking and payment service used by the user to complete international online e-commerce transactions [28, ...].\n    *   **Synonyms**: CAKE digital bank [28, ...]\n    *   **Related Terms**: ECOM payments, Steam Purchase, Cults3D [50, ...]\n*   **Zenless Zone Zero**:\n    *   **Definition**: An action game by HoYoverse for which the user purchases subscriptions and downloads digital assets [72, ...].\n    *   **Synonyms**: ZZZ [72, ...]\n    *   **Related Terms**: HoYoverse, Inter-Knot Member, Film [287, ...]\n*   **Limbus Company**:\n    *   **Definition**: A video game title referenced in the user's project plans and task deadlines for prop crafting [15, ...].\n    *   **Synonyms**: N/A\n    *   **Related Terms**: Asana, Sanchor lancer, Gregor prop [103, ...]\n*   **GOG.com**:\n    *   **Definition**: A digital video game distribution platform where the user maintains an active account [3, ...].\n    *   **Synonyms**: GOG [3, ...]\n    *   **Related Terms**: Two-step authentication, PC Gaming [3]\n*   **Hola VPN**:\n    *   **Definition**: A virtual private network service for which the user purchased a multi-year subscription and requested account support [89].\n    *   **Synonyms**: Hola VPN Premium [89]\n    *   **Related Terms**: VPN, Subscription restoration, Online security [89]\n*   **Steam Wallet**:\n    *   **Definition**: A digital wallet service used by the user to store funds for in-game purchases and battle passes [4, ...].\n    *   **Synonyms**: Steam Wallet Funds [5, ...]\n    *   **Related Terms**: Steam, Strinova, In-game purchases [10, ...]"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following is file that is returned from a search of the user's workspace resources based on their query:"
                      }
                    },
                    {
                      "name":  "original_url",
                      "value":  {
                        "stringValue":  "https://drive.google.com/open?id=1q5s4x0QgPN0UXlzIpYTimqGAdc9ithKU"
                      }
                    },
                    {
                      "name":  "guri",
                      "value":  {
                        "stringValue":  "workspace_resource://CjMiLwohMXE1czR4MFFnUE4wVVhseklwWVRpbXFHQWRjOWl0aEtVGgp0ZXh0L3BsYWluMAA"
                      }
                    },
                    {
                      "name":  "update_time",
                      "value":  {
                        "stringValue":  "2024-07-06T11:42:32+00:00"
                      }
                    },
                    {
                      "name":  "create_time",
                      "value":  {
                        "stringValue":  "2024-07-06T11:42:32+00:00"
                      }
                    },
                    {
                      "name":  "drive_item_data",
                      "value":  {
                        "structValue":  {
                          "fields":  [
                            {
                              "name":  "item_id",
                              "value":  {
                                "stringValue":  "1q5s4x0QgPN0UXlzIpYTimqGAdc9ithKU"
                              }
                            },
                            {
                              "name":  "title",
                              "value":  {
                                "stringValue":  "laban_macro.txt"
                              }
                            },
                            {
                              "name":  "owner",
                              "value":  {
                                "stringValue":  "nana.kotobuki99@gmail.com"
                              }
                            },
                            {
                              "name":  "mime_type",
                              "value":  {
                                "stringValue":  "text/plain"
                              }
                            },
                            {
                              "name":  "last_modified_time",
                              "value":  {
                                "stringValue":  "2024-07-06T11:42:32Z"
                              }
                            },
                            {
                              "name":  "folder_metadata",
                              "value":  {
                                "structValue":  {
                                  "fields":  [
                                    {
                                      "name":  "files_considered_count",
                                      "value":  {
                                        "numberValue":  0
                                      }
                                    },
                                    {
                                      "name":  "folder_creation_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    },
                                    {
                                      "name":  "last_modified_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "DO NOT DELETE THIS LINE\\*\\*\\* version=1 \\*\\*\\*  \nádsm:Ánh sáng đêm Sương mù  \nhoma:Hộ ma  \nâyya:Ayaya  \ntpcn:Thực phẩm chức năng  \nCBX:SHAPER\\_CALIBRATE AXIS=X  \nCBY:SHAPER\\_CALIBRATE AXIS=Y  \n"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following is file that is returned from a search of the user's workspace resources based on their query:"
                      }
                    },
                    {
                      "name":  "original_url",
                      "value":  {
                        "stringValue":  "https://drive.google.com/open?id=1n4OvnFYwsCJFLY1giiu-nndxFntIfq8U"
                      }
                    },
                    {
                      "name":  "guri",
                      "value":  {
                        "stringValue":  "workspace_resource://CjMiLwohMW40T3ZuRll3c0NKRkxZMWdpaXUtbm5keEZudElmcThVGgp0ZXh0L3BsYWluMAA"
                      }
                    },
                    {
                      "name":  "update_time",
                      "value":  {
                        "stringValue":  "2022-11-29T22:47:24+00:00"
                      }
                    },
                    {
                      "name":  "create_time",
                      "value":  {
                        "stringValue":  "2022-11-15T15:17:20+00:00"
                      }
                    },
                    {
                      "name":  "drive_item_data",
                      "value":  {
                        "structValue":  {
                          "fields":  [
                            {
                              "name":  "item_id",
                              "value":  {
                                "stringValue":  "1n4OvnFYwsCJFLY1giiu-nndxFntIfq8U"
                              }
                            },
                            {
                              "name":  "title",
                              "value":  {
                                "stringValue":  "laban_macro.txt"
                              }
                            },
                            {
                              "name":  "owner",
                              "value":  {
                                "stringValue":  "nana.kotobuki99@gmail.com"
                              }
                            },
                            {
                              "name":  "mime_type",
                              "value":  {
                                "stringValue":  "text/plain"
                              }
                            },
                            {
                              "name":  "last_modified_time",
                              "value":  {
                                "stringValue":  "2022-11-29T22:47:24Z"
                              }
                            },
                            {
                              "name":  "folder_metadata",
                              "value":  {
                                "structValue":  {
                                  "fields":  [
                                    {
                                      "name":  "files_considered_count",
                                      "value":  {
                                        "numberValue":  0
                                      }
                                    },
                                    {
                                      "name":  "folder_creation_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    },
                                    {
                                      "name":  "last_modified_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "DO NOT DELETE THIS LINE\\*\\*\\* version=1 \\*\\*\\*  \nádsm:Ánh sáng đêm Sương mù  \nhoma:Hộ ma  \nâyya:Ayaya  \ntpcn:Thực phẩm chức năng  \n"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following is file that is returned from a search of the user's workspace resources based on their query:"
                      }
                    },
                    {
                      "name":  "original_url",
                      "value":  {
                        "stringValue":  "https://drive.google.com/open?id=17aFLxB6Ryh9Aw9KUju02kAfviqaqCgIS"
                      }
                    },
                    {
                      "name":  "guri",
                      "value":  {
                        "stringValue":  "workspace_resource://CjMiLwohMTdhRkx4QjZSeWg5QXc5S1VqdTAya0FmdmlxYXFDZ0lTGgp0ZXh0L3BsYWluMAA"
                      }
                    },
                    {
                      "name":  "update_time",
                      "value":  {
                        "stringValue":  "2022-07-06T09:54:11+00:00"
                      }
                    },
                    {
                      "name":  "create_time",
                      "value":  {
                        "stringValue":  "2022-07-06T09:54:11+00:00"
                      }
                    },
                    {
                      "name":  "drive_item_data",
                      "value":  {
                        "structValue":  {
                          "fields":  [
                            {
                              "name":  "item_id",
                              "value":  {
                                "stringValue":  "17aFLxB6Ryh9Aw9KUju02kAfviqaqCgIS"
                              }
                            },
                            {
                              "name":  "title",
                              "value":  {
                                "stringValue":  "laban_macro.txt"
                              }
                            },
                            {
                              "name":  "owner",
                              "value":  {
                                "stringValue":  "nana.kotobuki99@gmail.com"
                              }
                            },
                            {
                              "name":  "mime_type",
                              "value":  {
                                "stringValue":  "text/plain"
                              }
                            },
                            {
                              "name":  "last_modified_time",
                              "value":  {
                                "stringValue":  "2022-07-06T09:54:11Z"
                              }
                            },
                            {
                              "name":  "folder_metadata",
                              "value":  {
                                "structValue":  {
                                  "fields":  [
                                    {
                                      "name":  "files_considered_count",
                                      "value":  {
                                        "numberValue":  0
                                      }
                                    },
                                    {
                                      "name":  "folder_creation_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    },
                                    {
                                      "name":  "last_modified_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "DO NOT DELETE THIS LINE\\*\\*\\* version=1 \\*\\*\\*  \nádsm:Ánh sáng đêm Sương mù  \nhoma:Hộ ma  \nâyya:Ayaya  \n"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      },
      {
        "structValue":  {
          "fields":  [
            {
              "name":  "metadata",
              "value":  {
                "structValue":  {
                  "fields":  [
                    {
                      "name":  "context_type",
                      "value":  {
                        "stringValue":  "The following is file that is returned from a search of the user's workspace resources based on their query:"
                      }
                    },
                    {
                      "name":  "original_url",
                      "value":  {
                        "stringValue":  "https://drive.google.com/open?id=1rnIADj6o9n5ZUNNOobYytKV2ilGHdG-fMnbNJ-NGhFk"
                      }
                    },
                    {
                      "name":  "guri",
                      "value":  {
                        "stringValue":  "workspace_resource://ClsiVwosMXJuSUFEajZvOW41WlVOTk9vYll5dEtWMmlsR0hkRy1mTW5iTkotTkdoRmsaJ2FwcGxpY2F0aW9uL3ZuZC5nb29nbGUtYXBwcy5zcHJlYWRzaGVldDAA"
                      }
                    },
                    {
                      "name":  "update_time",
                      "value":  {
                        "stringValue":  "2025-07-07T14:24:11+00:00"
                      }
                    },
                    {
                      "name":  "create_time",
                      "value":  {
                        "stringValue":  "2019-03-04T11:59:12+00:00"
                      }
                    },
                    {
                      "name":  "drive_item_data",
                      "value":  {
                        "structValue":  {
                          "fields":  [
                            {
                              "name":  "item_id",
                              "value":  {
                                "stringValue":  "1rnIADj6o9n5ZUNNOobYytKV2ilGHdG-fMnbNJ-NGhFk"
                              }
                            },
                            {
                              "name":  "title",
                              "value":  {
                                "stringValue":  "AkikoFileSubsRecord"
                              }
                            },
                            {
                              "name":  "owner",
                              "value":  {
                                "stringValue":  "nana.kotobuki99@gmail.com"
                              }
                            },
                            {
                              "name":  "mime_type",
                              "value":  {
                                "stringValue":  "application/vnd.google-apps.spreadsheet"
                              }
                            },
                            {
                              "name":  "last_modified_time",
                              "value":  {
                                "stringValue":  "2025-07-07T14:24:11Z"
                              }
                            },
                            {
                              "name":  "folder_metadata",
                              "value":  {
                                "structValue":  {
                                  "fields":  [
                                    {
                                      "name":  "files_considered_count",
                                      "value":  {
                                        "numberValue":  0
                                      }
                                    },
                                    {
                                      "name":  "folder_creation_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    },
                                    {
                                      "name":  "last_modified_time",
                                      "value":  {
                                        "stringValue":  "1970-01-01T00:00:00Z"
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              }
            },
            {
              "name":  "content",
              "value":  {
                "listValue":  {
                  "values":  [
                    {
                      "structValue":  {
                        "fields":  [
                          {
                            "name":  "text",
                            "value":  {
                              "stringValue":  "# Sheet1\n# Trang tính1\n\n``` \n\n  ,.rt htthy hhwź,uj\nj\n \n```\n"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          ]
        }
      }
    ]
  }
}