package vn.nanase.hako.tests;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.text.StaticLayout;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import vn.nanase.hako.*;

/** Offline UI regression suite. Does not claim to test Hako networking or E-ink waveforms. */
public class SmokeTest extends Instrumentation {
  int checks, failures; final StringBuilder log = new StringBuilder(); MainActivity activity;
  void check(boolean value, String label) {
    if (!value) failures++;
    checks++; log.append(value ? "PASS " : "FAIL ").append(label).append('\n');
    Bundle progress=new Bundle();progress.putString("stream",(value?"PASS ":"FAIL ")+label+"\n");sendStatus(0,progress);
  }
  Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
  }
  void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true);
    runOnMainSync(() -> { try { m.invoke(activity, args); } catch (Exception e) { throw new RuntimeException(e); } });
    settle();
  }
  void settle() { waitForIdleSync(); SystemClock.sleep(150); waitForIdleSync(); }
  void key(int action, int code) {
    if(action==KeyEvent.ACTION_DOWN){
      // A newly replaced view/foreground-service notification can briefly shift window focus.
      // Sending DOWN into a different window and UP into this one is not a physical hold here.
      long limit=SystemClock.uptimeMillis()+3000;
      while(!activity.getWindow().getDecorView().hasWindowFocus() && SystemClock.uptimeMillis()<limit){
        SystemClock.sleep(50);waitForIdleSync();
      }
      if(!activity.getWindow().getDecorView().hasWindowFocus())throw new AssertionError("Target window has no keyboard focus");
    }
    sendKeySync(new KeyEvent(action, code));
  }
  void press(int code) { key(KeyEvent.ACTION_DOWN, code); key(KeyEvent.ACTION_UP, code); settle(); }
  void chord() {
    key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP);
    key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN);
    key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN);
    key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP); settle();
  }
  View find(View v, String label) {
    if (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
    if (v instanceof TextView && label.contentEquals(((TextView)v).getText())) return v;
    if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
      View found=find(((ViewGroup)v).getChildAt(i),label); if(found!=null)return found;
    }
    return null;
  }
  void click(String label) throws Exception {
    View v=find(activity.getWindow().getDecorView(),label);
    check(v!=null,"Control exists: "+label);
    while(!v.isClickable() && v.getParent() instanceof View) v=(View)v.getParent();
    final View target=v;
    runOnMainSync(() -> { if(!target.performClick())throw new AssertionError("Not clickable: "+label); }); settle();
  }
  void shot(String name) throws Exception {
    Bitmap image=getUiAutomation().takeScreenshot(); check(image!=null,"Screenshot "+name);
    File dir=new File(getTargetContext().getFilesDir(),"ui-evidence");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
    image.recycle();
  }
  void idleCheck(String name) throws Exception {
    settle();SystemClock.sleep(400);waitForIdleSync();
    java.util.concurrent.atomic.AtomicInteger draws=new java.util.concurrent.atomic.AtomicInteger();
    View decor=activity.getWindow().getDecorView();
    android.view.ViewTreeObserver.OnDrawListener listener=()->draws.incrementAndGet();
    runOnMainSync(()->decor.getViewTreeObserver().addOnDrawListener(listener));
    long cpu=android.os.Process.getElapsedCpuTime(),start=SystemClock.elapsedRealtime();
    SystemClock.sleep(3000);
    long delta=android.os.Process.getElapsedCpuTime()-cpu,elapsed=SystemClock.elapsedRealtime()-start;
    runOnMainSync(()->decor.getViewTreeObserver().removeOnDrawListener(listener));
    log.append("IDLE ").append(name).append(" cpuMs=").append(delta).append(" elapsedMs=").append(elapsed).append(" draws=").append(draws.get()).append('\n');
    check(delta<300,"Idle "+name+" uses under 300 ms process CPU in 3 seconds (emulator)");
    check(draws.get()==0,"Idle "+name+" redraws zero frames without input or progress changes");
    check(!Repository.busy.get()&&!Repository.isSyncing,"Idle "+name+" has no active download");
    check((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)==0,"Idle "+name+" does not force screen on");
  }
  android.app.job.JobInfo chargeFixture(Context ctx){
    ctx.getSharedPreferences("settings",0).edit().putBoolean("charging",true).commit();ChargeJob.schedule(ctx,true);
    android.app.job.JobInfo info=ctx.getSystemService(android.app.job.JobScheduler.class).getPendingJob(40);
    ChargeJob.schedule(ctx,false);ctx.getSharedPreferences("settings",0).edit().putBoolean("charging",false).commit();return info;
  }
  void listCheck(String label) throws Exception {
    click(label); ListView list=(ListView)field(activity,"currentList");
    check(list!=null && list.getCount()>=18,label+" has fixture rows"); shot(label.replace(' ','-'));
    int first=list.getFirstVisiblePosition(), last=list.getLastVisiblePosition();
    View bottom=list.getChildAt(list.getChildCount()-1);
    boolean partial=bottom.getBottom()>list.getHeight()-list.getPaddingBottom();
    check(!partial,label+" last row fits fully within viewport");
    check(last-first+1==6,label+" shows exactly six complete titles");
    log.append("GEOMETRY ").append(label).append(" height=").append(list.getHeight()).append(" first=").append(first).append(" last=").append(last).append(" lastBottom=").append(bottom.getBottom()).append('\n');
    press(KeyEvent.KEYCODE_VOLUME_DOWN);
    shot(label.replace(' ','-')+"-page2");
    int after=list.getFirstVisiblePosition();
    check(after>first && after<=last+(partial?0:1),label+" page forward skips no partial row");
    press(KeyEvent.KEYCODE_VOLUME_UP);
    check(list.getFirstVisiblePosition()==first,label+" page back returns to previous start");
    int previousStart=list.getFirstVisiblePosition();
    while (list.getLastVisiblePosition()<list.getCount()-1) {
      previousStart=list.getFirstVisiblePosition();
      int previousLast=list.getLastVisiblePosition();
      press(KeyEvent.KEYCODE_VOLUME_DOWN);
      check(list.getFirstVisiblePosition()>previousStart && list.getFirstVisiblePosition()<=previousLast+1,
          label+" later page advances without skipping an item");
      if(list.getFirstVisiblePosition()<=previousStart) break;
      View finalCard=list.getChildAt(list.getChildCount()-1);
      check(finalCard.getBottom()<=list.getHeight()-list.getPaddingBottom(),label+" later page cards fit");
    }
    press(KeyEvent.KEYCODE_VOLUME_UP);
    check(list.getFirstVisiblePosition()==previousStart,label+" short final page returns to exact previous start");
    click("Trang chính");
  }
  public void onCreate(Bundle args) { super.onCreate(args); start(); }
  public void onStart() {
    Bundle result=new Bundle();
    try {
      Context ctx=getTargetContext(); Store s=Store.get(ctx);
      ctx.getSharedPreferences("settings",0).edit().putBoolean("charging",false).commit();
      for(int i=1;i<=19;i++) {
        String id="fixture-"+i;
        s.putBook(new HakoParser.Link(id,"Truyện thử "+i+" — tựa dài để kiểm tra tiếng Việt trên màn hình nhỏ", ""));
        s.followed(id,true,i); s.position(id,"",0,0); s.visited(id);s.visited(id);s.visited(id);
      }
      activity=(MainActivity)startActivitySync(new Intent().setClassName("vn.nanase.hako","vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
      check(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName.equals("0.6.4"),"Installed version 0.6.4 (behavior fix)");
      View homeLabel=find(activity.getWindow().getDecorView(),"Vừa đọc");
      LinearLayout homeCell=(LinearLayout)homeLabel.getParent();
      LinearLayout homeRow=(LinearLayout)homeCell.getParent();
      LinearLayout homeGrid=(LinearLayout)homeRow.getParent();
      check(homeGrid.getChildCount()==3 && homeRow.getChildCount()==3,"Home retains nine shortcuts");
      check(homeGrid.getHeight()>activity.getWindow().getDecorView().getHeight()/2,"Home grid uses majority of display height");
      check(Math.abs(homeGrid.getChildAt(0).getHeight()-homeGrid.getChildAt(2).getHeight())<=2,"Home rows share available height equally");
      runOnMainSync(()->{
        android.webkit.WebView verify=new android.webkit.WebView(activity);
        WebSession.configure(verify);
        check(verify.getSettings().getJavaScriptEnabled()&&verify.getSettings().getDomStorageEnabled(),"Login enables JS and DOM storage");
        check(android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(verify),"Login accepts challenge-frame cookies");
        check(WebSession.challengeFrame(android.net.Uri.parse("https://challenges.cloudflare.com/test"))&&WebSession.challengeFrame(android.net.Uri.parse("about:blank"))&&WebSession.challengeFrame(android.net.Uri.parse("about:srcdoc")),"Cloudflare frames are permitted");
        check(!WebSession.challengeFrame(android.net.Uri.parse("https://challenges.cloudflare.com.evil.test/")),"Challenge hostname matching is exact");
        verify.destroy();
      });
      call("webViewInfo",new Class<?>[]{});
      View infoCopy=find(activity.getWindow().getDecorView(),"Sao chép");
      // Dialogs have their own window; inspect through the current accessibility tree.
      android.view.accessibility.AccessibilityNodeInfo infoRoot=getUiAutomation().getRootInActiveWindow();
      check(infoRoot!=null && !infoRoot.findAccessibilityNodeInfosByText("CPU hỗ trợ:").isEmpty(),"Device dialog displays supported CPU ABIs");
      check(infoRoot!=null && !infoRoot.findAccessibilityNodeInfosByText("Gói:").isEmpty(),"Device dialog displays active WebView package");
      getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);settle();
      shot("home"); listCheck("Vừa đọc"); listCheck("Tủ sách"); listCheck("Yêu thích");
      s.putBook(new HakoParser.Link("partial-full","Bộ tải full còn thiếu chương cũ", ""));
      List<HakoParser.Link> partialLinks=new ArrayList<>();
      for(int n=1;n<=3;n++)partialLinks.add(new HakoParser.Link("partial-full-"+n,"Chương "+n,""));
      s.catalog("partial-full",partialLinks);
      for(int n=2;n<=3;n++){Store.write(s.html("partial-full-"+n),"<p>Nội dung thử đã tải đầy đủ, dùng để kiểm tra tiến độ thực sự của bộ truyện lưu full.</p>");s.state("partial-full-"+n,true,"");}
      s.position("partial-full","partial-full-2",0,0);s.setKeepFull("partial-full",true);
      call("frequentList",new Class<?>[]{});
      check(find(activity.getWindow().getDecorView(),"FULL 67%")!=null,"Full progress counts missing old chapters instead of false 100 percent");
      check(s.progress(s.book("partial-full")).percent==67,"Stored full progress counts actual whole-book files");
      s.setKeepFull("partial-full",false);s.state("partial-full-3",false,"");
      check(s.progress(s.book("partial-full")).percent==50,"NEXT progress uses only required next chapters, not old chapters");
      s.setKeepFull("partial-full",true);s.state("partial-full-3",true,"");
      shot("favorites-full-progress");
      call("library",new Class<?>[]{});
      call("onlineList",new Class<?>[]{String.class,String.class},"Mới cập nhật (dữ liệu thử)",
        "https://raw.githubusercontent.com/nanasekoto/HakoReaderII/codex/test-existing-fc1b545/tests/fixtures/story-list.html");
      long limit=SystemClock.elapsedRealtime()+15000;
      ListView online=null;
      while(SystemClock.elapsedRealtime()<limit) {
        online=(ListView)field(activity,"currentList");
        if(online!=null && online.isShown() && online.getCount()>=18)break;
        SystemClock.sleep(100);waitForIdleSync();
      }
      if(online!=null && online.isShown() && online.getCount()>=18) {
        shot("new-updates");
        int oldLast=online.getLastVisiblePosition();
        View bottom=online.getChildAt(online.getChildCount()-1);
        boolean partial=bottom.getBottom()>online.getHeight()-online.getPaddingBottom();
        check(!partial,"New updates last card fits fully");
        check(online.getLastVisiblePosition()-online.getFirstVisiblePosition()+1==6,"New updates shows six complete titles");
        press(KeyEvent.KEYCODE_VOLUME_DOWN);shot("new-updates-page2");
        check(online.getFirstVisiblePosition()<=oldLast+(partial?0:1),"New updates paging skips no item");
      }else check(false,"New updates fixture loaded within 15 seconds");
      call("library",new Class<?>[]{});
      call("demo",new Class<?>[]{});
      NativeReader reader=(NativeReader)field(activity,"nativeReader");
      check(reader!=null && reader.getPageCount()>1,"Demo opens native reader with multiple pages");
      android.text.Spanned rendered=(android.text.Spanned)field(reader,"text");
      boolean heavy=false,large=false;
      for(android.text.style.StyleSpan span:rendered.getSpans(0,rendered.length(),android.text.style.StyleSpan.class))if((span.getStyle()&android.graphics.Typeface.BOLD)!=0)heavy=true;
      for(android.text.style.RelativeSizeSpan span:rendered.getSpans(0,rendered.length(),android.text.style.RelativeSizeSpan.class))if(span.getSizeChange()>1.15f)large=true;
      check(!heavy,"HTML bold uses moderate emphasis instead of default 700 weight");
      check(!large,"HTML chapter heading scale is capped at 1.15 of reading size");
      shot("reader");
      int before=(Integer)field(reader,"page"); press(KeyEvent.KEYCODE_VOLUME_DOWN);
      check((Integer)field(reader,"page")==before+1,"Volume down turns exactly one page");
      int page=(Integer)field(reader,"page");
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP);settle();
      check((Integer)field(reader,"page")==page,"Hold begins without turning page");
      SystemClock.sleep(750);waitForIdleSync();
      check(reader.isTouchLocked(),"Single Volume Up hold locks before release");
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP);settle();
      check((Integer)field(reader,"page")==page,"Lock hold release turns no page");
      runOnMainSync(() -> {
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,100,400,0);
        MotionEvent up=MotionEvent.obtain(now,now+10,MotionEvent.ACTION_UP,100,400,0);
        activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);down.recycle();up.recycle();
      });settle();
      check((Integer)field(reader,"page")==page,"Locked touch does not turn page");
      press(KeyEvent.KEYCODE_MENU);
      check(((View)field(activity,"topBar")).getVisibility()!=View.VISIBLE,"Locked hardware Menu cannot reveal app toolbar");
      check(find(activity.getWindow().getDecorView(),"KHÓA · Giữ Vol+ để mở")==null,"No lock text overlay covers chapter content");
      check(Repository.pocketPaused,"Pocket lock pauses automatic battery downloads");
      idleCheck("locked-reader");
      press(KeyEvent.KEYCODE_BACK);
      check(field(activity,"nativeReader")==reader && reader.isTouchLocked(),"Pocket Back cannot leave reader or unlock touch");
      call("openChapter",new Class<?>[]{String.class,String.class},"demo","demo-2");
      reader=(NativeReader)field(activity,"nativeReader");check(reader.isTouchLocked(),"Lock persists across chapters");
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP);SystemClock.sleep(850);
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP);settle();
      check(!reader.isTouchLocked(),"Single Volume Up hold unlocks");
      check(!Repository.pocketPaused,"Unlock restores foreground automatic download eligibility");
      idleCheck("unlocked-reader");
      press(KeyEvent.KEYCODE_VOLUME_DOWN);press(KeyEvent.KEYCODE_VOLUME_UP);
      check(!reader.isTouchLocked(),"Opposite short page presses do not lock touch");
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP);SystemClock.sleep(1500);
      check(reader.isTouchLocked(),"Long hold toggles only once");
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP);settle();
      call("toggleTouchLock",new Class<?>[]{});
      press(KeyEvent.KEYCODE_VOLUME_DOWN);
      int saved=s.book("demo").pos; float fraction=s.book("demo").fraction;
      call("library",new Class<?>[]{});click("Đọc tiếp");
      check(s.book("demo").pos==saved && Math.abs(s.book("demo").fraction-fraction)<0.02,"Resume paragraph and fraction");
      reader=(NativeReader)field(activity,"nativeReader");
      for(int choice=0;choice<4;choice++) {
        ctx.getSharedPreferences("settings",0).edit().putInt("font2",choice).commit();
        call("applyReaderStyle",new Class<?>[]{});shot("font-"+choice);
      }
      StaticLayout layout=(StaticLayout)field(reader,"layout");
      List<Integer> pages=(List<Integer>)field(reader,"pages");
      int height=reader.getHeight()-(Integer)field(reader,"headerHeight")-(Integer)field(reader,"footerHeight");
      for(int i=0;i<pages.size();i++) {
        int end=i+1<pages.size()?pages.get(i+1):layout.getLineCount();
        check(layout.getLineBottom(end-1)-layout.getLineTop(pages.get(i))<=height,"Page "+i+" contains complete lines");
      }
      s.setKeepFull("demo",true);
      call("openChapter",new Class<?>[]{String.class,String.class},"demo","demo-12");
      reader=(NativeReader)field(activity,"nativeReader");
      final NativeReader lastReader=reader;
      runOnMainSync(()->lastReader.jump(true));settle();press(KeyEvent.KEYCODE_VOLUME_DOWN);
      check((Boolean)field(activity,"caughtUpPrompt"),"End chapter shows one inline Hako confirmation");
      LinearLayout prompt=(LinearLayout)field(activity,"boundaryPrompt");
      View promptCard=prompt.getChildAt(0);
      call("markCaughtUp",new Class<?>[]{Store.Book.class},s.book("demo"));
      check(prompt.getChildCount()==1 && prompt.getChildAt(0)==promptCard,"Repeated end request does not stack another confirmation");
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP);SystemClock.sleep(850);
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP);settle();
      check(reader.isTouchLocked() && prompt.getVisibility()!=View.VISIBLE,"Can pocket-lock while end confirmation is visible");
      call("toggleTouchLock",new Class<?>[]{});
      call("bookList",new Class<?>[]{boolean.class},false);
      ListView recent=(ListView)field(activity,"currentList");
      int demoRow=-1;
      for(int n=0;n<recent.getCount();n++)if(((Store.Book)recent.getItemAtPosition(n)).id.equals("demo")){demoRow=n;break;}
      final int resumeRow=demoRow;
      runOnMainSync(()->recent.setSelection(resumeRow));settle();
      View recentCard=recent.getChildAt(resumeRow-recent.getFirstVisiblePosition());
      runOnMainSync(()->recentCard.performClick());settle();
      check(field(activity,"nativeReader")!=null && field(activity,"bookId").equals("demo"),"Recent card opens saved reader despite full-download child");
      call("bookList",new Class<?>[]{boolean.class},false);
      s.setKeepFull("demo",false);
      Repository.pendingBook="";
      ctx.getSharedPreferences("settings",0).edit().putLong("catalog-demo",System.currentTimeMillis()).commit();
      click("Tải toàn bộ: Hướng dẫn Hako Pocket (offline)");
      check(s.isKeepFull("demo"),"Recent per-book full download retains full cache");
      call("frequentList",new Class<?>[]{});
      ListView favorites=(ListView)field(activity,"currentList");
      check(((Store.Book)favorites.getAdapter().getItem(0)).id.equals("demo"),"Full-download book gets favorite priority");
      runOnMainSync(()->favorites.performItemClick(favorites.getChildAt(0),0,0));settle();
      check(field(activity,"nativeReader")==null && field(activity,"bookId").equals("demo"),"Favorite title opens chapter selection instead of reader");
      ListView chapters=(ListView)field(activity,"currentList");
      check(chapters.getLastVisiblePosition()-chapters.getFirstVisiblePosition()+1>=6,"Chapter selection shows compact complete chapter rows");
      check(chapters.getChildAt(chapters.getChildCount()-1).getBottom()<=chapters.getHeight()-chapters.getPaddingBottom(),"Chapter selection final row is not cut");
      shot("contents");
      // Enter genuine Android touch mode; the first mapped navigation DOWN must still arrive.
      long tapTime=SystemClock.uptimeMillis();
      sendPointerSync(MotionEvent.obtain(tapTime,tapTime,MotionEvent.ACTION_DOWN,100,60,0));
      sendPointerSync(MotionEvent.obtain(tapTime,tapTime+10,MotionEvent.ACTION_UP,100,60,0));settle();
      int chapterStart=chapters.getFirstVisiblePosition();
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_PAGE_UP);SystemClock.sleep(850);
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_PAGE_UP);settle();
      check((Boolean)field(activity,"touchLocked") && chapters.getFirstVisiblePosition()==chapterStart,"Mapped Page Up hold locks chapter picker without paging");
      key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_PAGE_UP);SystemClock.sleep(850);
      key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_PAGE_UP);settle();
      check(!(Boolean)field(activity,"touchLocked"),"Mapped Page Up hold unlocks chapter picker");

      s.putBook(new HakoParser.Link("longtoc","Một tựa truyện thật dài để kiểm tra chọn chương trên màn hình nhỏ", ""));
      List<HakoParser.Link> longChapters=new ArrayList<>();
      for(int n=1;n<=18;n++)longChapters.add(new HakoParser.Link("longtoc-"+n,
          "Chương "+n+" Những ngày bình yên cùng những người bạn và một cuộc hành trình thật dài", ""));
      s.catalog("longtoc",longChapters);
      call("contents",new Class<?>[]{Store.Book.class},s.book("longtoc"));
      ListView longList=(ListView)field(activity,"currentList");
      check(longList.getChildCount()>=6,"Long chapter titles show compact rows");
      for(int i=0;i<longList.getChildCount();i++){
        TextView text=(TextView)longList.getChildAt(i);
        int lines=Math.min(text.getMaxLines(),text.getLayout().getLineCount());
        check(text.getLayout().getLineBottom(lines-1)+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom()<=text.getHeight(),"Long chapter row "+i+" has no vertically clipped text");
      }
      shot("long-chapter-titles");
      click("Thêm yêu thích");check(s.book("longtoc").pinned&&s.favorite(s.book("longtoc")),"Direct star adds manual favorite");
      click("Bỏ yêu thích");check(!s.book("longtoc").pinned&&!s.favorite(s.book("longtoc")),"Direct star removes unvisited manual favorite");
      s.putBook(new HakoParser.Link("queue-test","Hàng chờ offline",""));
      List<HakoParser.Link> queuedChs=new ArrayList<>();queuedChs.add(new HakoParser.Link("queue-1","Chương 1",""));s.catalog("queue-test",queuedChs);
      s.queueCaughtUp("queue-test");check(s.queued("queue-test")&&s.canSubmit("queue-test"),"Offline queue persists exact catalog snapshot");
      queuedChs.add(new HakoParser.Link("queue-2","Chương mới",""));s.catalog("queue-test",queuedChs);
      check(!s.canSubmit("queue-test")&&s.unread("queue-test")==1,"New unread chapter blocks old mark-all request");
      s.readChapter("queue-2");s.extendQueueIfCaughtUp("queue-test");check(s.canSubmit("queue-test"),"Reading all new chapters refreshes snapshot");s.clearQueue("queue-test");check(!s.queued("queue-test"),"Queue acknowledgement persists");
      android.app.job.JobInfo charging=chargeFixture(ctx);
      check(charging.isRequireCharging()&&charging.getRequiredNetwork()!=null&&charging.getRequiredNetwork().hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI),"Automatic sync requires charging plus Wi-Fi");
      s.setKeepFull("demo",true);s.position("demo","demo-10",0,0);s.prune(s.book("demo"));
      check(s.html("demo-2").exists(),"Full offline mode retains old cached chapters");
      s.putBook(new HakoParser.Link("baseline","Kiểm tra mốc đọc HAKO",""));List<HakoParser.Link> baseline=new ArrayList<>();for(int n=1;n<=5;n++)baseline.add(new HakoParser.Link("baseline-"+n,"Chương "+n,""));s.catalog("baseline",baseline);s.shelfInfo("baseline","2 chương mới");ctx.getSharedPreferences("settings",0).edit().putLong("lastShelfSync",System.currentTimeMillis()).commit();s.establishReadBaseline("baseline");
      check(s.unread("baseline")==2,"First Hako baseline imports already-read prefix only once");
      baseline.add(new HakoParser.Link("baseline-6","Chương vừa xuất hiện",""));s.catalog("baseline",baseline);s.shelfInfo("baseline","0 chương mới");s.establishReadBaseline("baseline");check(s.unread("baseline")==3,"Later Hako mark-all counter cannot erase local unread arrivals");
      for(Store.Book book:s.books())if(!book.id.equals("partial-full"))s.dropped(book.id,true);
      s.putBook(new HakoParser.Link("weighted","Truyện kiểm tra tổng tiến độ",""));s.setKeepFull("weighted",true);
      List<HakoParser.Link> weighted=new ArrayList<>();for(int n=1;n<=9;n++)weighted.add(new HakoParser.Link("weighted-"+n,"Chương "+n,""));s.catalog("weighted",weighted);
      String offlineText="<p>Nội dung thử đủ dài để kiểm chứng tiến độ tải ngoại tuyến, không lấy dữ liệu truyện trên web.</p>";
      Store.write(s.html("weighted-1"),offlineText);s.state("weighted-1",true,"");
      check(s.totalOfflinePercent()==25,"Aggregate weights chapter counts: 3 of 12 equals 25 percent");
      Store.write(s.html("partial-full-1"),offlineText);s.state("partial-full-1",true,"");for(int n=2;n<=9;n++){Store.write(s.html("weighted-"+n),offlineText);s.state("weighted-"+n,true,"");}
      ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",false).commit();check(s.totalOfflinePercent()==99,"Complete files without verified refresh do not claim offline-ready 100");
      ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_verified",true).commit();check(s.totalOfflinePercent()==100,"Verified complete required scope reaches aggregate 100");
      result.putString("stream",log+(failures==0?"PASS TOTAL ":"FAIL TOTAL ")+checks+" Android checks; failures="+failures+"\n");
      finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    } catch(Throwable e) {
      result.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);
    }
  }
}
