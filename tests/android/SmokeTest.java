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
  void listCheck(String label) throws Exception {
    click(label); ListView list=(ListView)field(activity,"currentList");
    check(list!=null && list.getCount()>=18,label+" has fixture rows"); shot(label.replace(' ','-'));
    int first=list.getFirstVisiblePosition(), last=list.getLastVisiblePosition();
    View bottom=list.getChildAt(list.getChildCount()-1);
    boolean partial=bottom.getBottom()>list.getHeight()-list.getPaddingBottom();
    check(!partial,label+" last row fits fully within viewport");
    log.append("GEOMETRY ").append(label).append(" height=").append(list.getHeight()).append(" first=").append(first).append(" last=").append(last).append(" lastBottom=").append(bottom.getBottom()).append('\n');
    press(KeyEvent.KEYCODE_VOLUME_DOWN);
    shot(label.replace(' ','-')+"-page2");
    int after=list.getFirstVisiblePosition();
    check(after>first && after<=last+(partial?0:1),label+" page forward skips no partial row");
    press(KeyEvent.KEYCODE_VOLUME_UP);
    check(list.getFirstVisiblePosition()==first,label+" page back returns to previous start");
    click("Trang chính");
  }
  public void onCreate(Bundle args) { super.onCreate(args); start(); }
  public void onStart() {
    Bundle result=new Bundle();
    try {
      Context ctx=getTargetContext(); Store s=Store.get(ctx);
      ctx.getSharedPreferences("settings",0).edit().putBoolean("charging",false).commit();
      for(int i=1;i<=20;i++) {
        String id="fixture-"+i;
        s.putBook(new HakoParser.Link(id,"Truyện thử "+i+" — tựa dài để kiểm tra tiếng Việt trên màn hình nhỏ", ""));
        s.followed(id,true,i); s.position(id,"",0,0); s.visited(id);
      }
      activity=(MainActivity)startActivitySync(new Intent().setClassName("vn.nanase.hako","vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
      check(ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName.equals("0.5.1"),"Installed version 0.5.1 (behavior fix)");
      shot("home"); listCheck("Vừa đọc"); listCheck("Tủ sách"); listCheck("Thường đọc");
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
        press(KeyEvent.KEYCODE_VOLUME_DOWN);shot("new-updates-page2");
        check(online.getFirstVisiblePosition()<=oldLast+(partial?0:1),"New updates paging skips no item");
      }else check(false,"New updates fixture loaded within 15 seconds");
      call("library",new Class<?>[]{});
      click("Đọc mẫu");
      NativeReader reader=(NativeReader)field(activity,"nativeReader");
      check(reader!=null && reader.getPageCount()>1,"Demo opens native reader with multiple pages");
      shot("reader");
      int before=(Integer)field(reader,"page"); press(KeyEvent.KEYCODE_VOLUME_DOWN);
      check((Integer)field(reader,"page")==before+1,"Volume down turns exactly one page");
      SystemClock.sleep(700);
      int page=(Integer)field(reader,"page");
      key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP);settle();
      check((Integer)field(reader,"page")==page,"First chord key does not flash another page");
      key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN);
      key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN);
      key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP);settle();
      check(reader.isTouchLocked() && (Integer)field(reader,"page")==page,"Clean chord locks and restores final page");
      runOnMainSync(() -> {
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,100,400,0);
        MotionEvent up=MotionEvent.obtain(now,now+10,MotionEvent.ACTION_UP,100,400,0);
        activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);down.recycle();up.recycle();
      }); settle();
      check((Integer)field(reader,"page")==page,"Locked touch does not turn page");
      call("openChapter",new Class<?>[]{String.class,String.class},"demo","demo-2");
      reader=(NativeReader)field(activity,"nativeReader");check(reader.isTouchLocked(),"Lock persists across chapters");
      SystemClock.sleep(700);chord();check(!reader.isTouchLocked(),"Clean chord unlocks");
      SystemClock.sleep(700);
      press(KeyEvent.KEYCODE_VOLUME_DOWN);press(KeyEvent.KEYCODE_VOLUME_UP);
      check(!reader.isTouchLocked(),"Two separate opposite page presses do not lock touch");
      if(reader.isTouchLocked())call("toggleTouchLock",new Class<?>[]{});
      SystemClock.sleep(700);press(KeyEvent.KEYCODE_VOLUME_DOWN);chord();
      check(reader.isTouchLocked(),"Chord immediately after paging locks exactly once");
      if(reader.isTouchLocked())call("toggleTouchLock",new Class<?>[]{});
      SystemClock.sleep(700);
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
      call("contents",new Class<?>[]{Store.Book.class},s.book("demo"));shot("contents");
      s.setKeepFull("demo",true);s.position("demo","demo-10",0,0);s.prune(s.book("demo"));
      check(s.html("demo-2").exists(),"Full offline mode retains old cached chapters");
      result.putString("stream",log+(failures==0?"PASS TOTAL ":"FAIL TOTAL ")+checks+" Android checks; failures="+failures+"\n");
      finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    } catch(Throwable e) {
      result.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);
    }
  }
}
