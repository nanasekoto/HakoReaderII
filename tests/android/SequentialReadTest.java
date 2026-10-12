package vn.nanase.hako.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import vn.nanase.hako.*;

public final class SequentialReadTest extends Instrumentation {
  int checks;MainActivity activity;
  void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
  Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();SystemClock.sleep(200);waitForIdleSync();}
  View find(View root,String label){if(label.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View hit=find(((ViewGroup)root).getChildAt(n),label);if(hit!=null)return hit;}return null;}
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle result=new Bundle();try{
    Context ctx=getTargetContext();Store s=Store.get(ctx);android.content.SharedPreferences prefs=ctx.getSharedPreferences("settings",0);
    prefs.edit().putString("origin","https://invalid.example").putBoolean("charging",false).putBoolean("sync_paused",true).commit();HakoParser.ORIGIN="https://invalid.example";
    String book="truyen-999003",base="https://invalid.example/truyen/999003-fixture";
    s.putBook(new HakoParser.Link(book,"Sequential fixture",base));s.visited(book);s.setKeepFull(book,true);
    List<HakoParser.Link> links=new ArrayList<>();
    for(int n=1;n<=5;n++)links.add(new HakoParser.Link(book+"-"+n,n==1||n==4?"Tập 1 · Minh họa":"Tập 1 · Chương "+(n==2?1:n==3?2:3),base+"/c"+n));
    s.catalog(book,links);
    for(int n=1;n<=5;n++){Store.write(s.html(book+"-"+n),n==1||n==4?"<img src='https://i.docln.net/missing.jpg'>":"<p>Chương chữ thật dành cho kiểm tra mốc đọc.</p>");s.readable(book+"-"+n);}
    s.completeThrough(book,book+"-3");
    check(s.wasRead(book+"-2")&&s.wasRead(book+"-3"),"Finishing chapter 2 implicitly completes earlier text");
    check(s.unread(book)==1,"Only text after the completion watermark is unread");
    check(!s.wasRead(book+"-1")&&s.unreadIllustrations(book)==0,"Illustrations carry no read or unread progress");
    check(!s.queued(book),"Reading only part of known text does not queue mark-all");
    s.completeThrough(book,book+"-5");
    check(s.unread(book)==0&&s.queued(book),"Finishing latest text queues completion automatically");
    check(prefs.getString("read_queue_tip_"+book,"").equals(book+"-5"),"Durable queue stores the completed latest chapter identity");
    prefs.edit().putBoolean("catalog_failed_"+book,false).putBoolean("sync_paused",false).commit();Repository.syncPaused=false;
    check(!ReadSync.connected(ctx),"Regression fixture is offline");
    check(ReadSync.process(ctx)&&s.queued(book),"Offline synchronization retains the pending completion");
    check(s.canSubmitServer(book),"Fresh unchanged latest text permits queued mark-all without image consent");
    prefs.edit().putBoolean("sync_paused",true).commit();Repository.syncPaused=true;s.position(book,book+"-5",0,0);
    activity=(MainActivity)startActivitySync(new Intent().setClassName(ctx.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
    call("contents",new Class<?>[]{Store.Book.class},s.book(book));
    ListView list=null;long limit=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<limit){list=(ListView)field("currentList");if(list!=null)break;SystemClock.sleep(50);waitForIdleSync();}
    check(list!=null&&list.getAdapter().getItem(0).toString().contains("[Minh họa]"),"Illustration TOC label has no seen or unread state");
    View sort=find(activity.getWindow().getDecorView(),"Sắp xếp chương");check(sort!=null,"Clock is replaced by actionable chapter sorting");
    runOnMainSync(()->sort.performClick());waitForIdleSync();SystemClock.sleep(200);
    AccessibilityNodeInfo root=null;List<AccessibilityNodeInfo> options=Collections.emptyList();
    long dialogLimit=SystemClock.elapsedRealtime()+5000;
    while(SystemClock.elapsedRealtime()<dialogLimit){root=getUiAutomation().getRootInActiveWindow();if(root!=null){options=root.findAccessibilityNodeInfosByText("Chương mới nhất trước");if(!options.isEmpty())break;}SystemClock.sleep(100);waitForIdleSync();}
    if(options.isEmpty())throw new AssertionError("Sort option not visible");
    android.graphics.Rect bounds=new android.graphics.Rect();options.get(0).getBoundsInScreen(bounds);
    long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();waitForIdleSync();SystemClock.sleep(200);waitForIdleSync();
    check(prefs.getBoolean("chapter_desc_"+book,false),"Actual sort dialog selects newest first");
    list=(ListView)field("currentList");check(list.getAdapter().getItem(0).toString().contains("Chương 3"),"Newest chapter moves to first display row");
    check(s.chapters(book).get(0).id.equals(book+"-1")&&s.book(book).current.equals(book+"-5"),"Visual sorting leaves canonical reading order and position unchanged");
    final ListView sorted=list;runOnMainSync(()->sorted.performItemClick(sorted.getChildAt(0),0,0));waitForIdleSync();long openLimit=SystemClock.elapsedRealtime()+8000;while(!field("chapterId").equals(book+"-5")&&SystemClock.elapsedRealtime()<openLimit){SystemClock.sleep(25);waitForIdleSync();}
    check(field("chapterId").equals(book+"-5"),"Sorted first row opens its own actual chapter");
    call("adjacent",new Class<?>[]{int.class},1);check(field("chapterId").equals(book+"-5"),"Offline end-of-book stays at latest text rather than chapter 1");
    links.add(new HakoParser.Link(book+"-6","Tập 2 · Minh họa",base+"/c6"));s.catalog(book,links);prefs.edit().putBoolean("catalog_failed_"+book,false).commit();
    check(s.canSubmitServer(book)&&s.unread(book)==0,"New illustration alone does not block queued completion");
    links.add(new HakoParser.Link(book+"-7","Tập 2 · Chương 4",base+"/c7"));s.catalog(book,links);prefs.edit().putBoolean("catalog_failed_"+book,false).commit();
    check(s.unread(book)==1&&!s.canSubmitServer(book)&&s.queued(book),"Newer text blocks old queued completion and remains unread");
    StringBuilder body=new StringBuilder();for(int n=0;n<70;n++)body.append("<p>Đoạn mới ").append(n).append(" đủ dài để kiểm tra chuyển chương mới và đọc hết trang cuối mà không quay về chương đầu.</p>");Store.write(s.html(book+"-7"),body.toString());s.state(book+"-7",true,"");
    call("adjacent",new Class<?>[]{int.class},1);openLimit=SystemClock.elapsedRealtime()+8000;while(!field("chapterId").equals(book+"-7")&&SystemClock.elapsedRealtime()<openLimit){SystemClock.sleep(25);waitForIdleSync();}check(field("chapterId").equals(book+"-7")&&s.unread(book)==1,"Next action skips illustration and opens newly arrived text, still in progress");
    NativeReader reader=(NativeReader)field("nativeReader");runOnMainSync(()->reader.jump(true));waitForIdleSync();
    long finishLimit=SystemClock.elapsedRealtime()+5000;while(s.unread(book)>0&&SystemClock.elapsedRealtime()<finishLimit)SystemClock.sleep(25);
    check(s.unread(book)==0&&s.completedTip(book).equals(book+"-7")&&s.canSubmitServer(book),"Finishing new latest text advances watermark and refreshes queued eligibility");
    result.putString("stream","PASS TOTAL "+checks+" sequential reading checks\n");finish(0,result);
  }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(1,result);}}
}
