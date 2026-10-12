package vn.nanase.hako.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import vn.nanase.hako.*;

/** Classification order, read completion and actual UI snapshot freshness. */
public final class UnreadStateTest extends Instrumentation {
  private int checks;
  private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
  private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
  private void call(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{
    Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);
    runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();SystemClock.sleep(200);waitForIdleSync();
  }
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle result=new Bundle();try{
    Context ctx=getTargetContext();Store s=Store.get(ctx);
    android.content.SharedPreferences prefs=ctx.getSharedPreferences("settings",0);
    prefs.edit().putString("origin","https://invalid.example").putBoolean("charging",false).putBoolean("sync_paused",true).commit();HakoParser.ORIGIN="https://invalid.example";
    String book="truyen-999002",base="https://invalid.example/truyen/999002-fixture";
    s.putBook(new HakoParser.Link(book,"Unread fixture",base));s.visited(book);s.setKeepFull(book,true);
    List<HakoParser.Link> links=Arrays.asList(
      new HakoParser.Link(book+"-1","Minh họa các tập · Ellie",base+"/c1-art"),
      new HakoParser.Link(book+"-2","Tập 1 · Chương 1",base+"/c2-text"),
      new HakoParser.Link(book+"-3","Minh họa · Chuyện ngắn",base+"/c3-caption"),
      new HakoParser.Link(book+"-4","Tập 1 · Chương mới nhất",base+"/c4-last"));
    s.catalog(book,links);
    Store.write(s.html(book+"-1"),"<img src='https://i.docln.net/missing.jpg'>");
    Store.write(s.html(book+"-2"),"<p>Chương chữ cũ đã đọc.</p>");s.readChapter(book+"-2");
    Store.write(s.html(book+"-3"),"<p>Một câu chuyện ngắn.</p><img src='https://i.docln.net/missing.jpg'>");
    StringBuilder text=new StringBuilder();for(int n=0;n<90;n++)text.append("<p>Đoạn ").append(n).append(" của chương mới nhất. Nội dung đủ dài để phải lật trang và kiểm tra thời điểm ghi nhận đã đọc.</p>");
    Store.write(s.html(book+"-4"),text.toString());s.state(book+"-4",true,"");s.position(book,book+"-4",0,0);
    prefs.edit().putStringSet("new_arrival_ids_"+book,new HashSet<>(Arrays.asList(book+"-1",book+"-4"))).commit();
    UiLibrarySnapshot first=UiLibrarySnapshot.load(s,prefs);UiLibrarySnapshot.Summary before=first.summary(s.book(book));
    check(before.unread==1,"First snapshot classifies image markup before counting unread text");
    check(before.unseenIllustrations==1,"Unseen illustration has its own display counter");
    check(before.pendingReview==1,"Caption candidate is separate from ordinary unread text");
    check(!before.currentRead&&before.remaining==0,"Latest chapter is in progress, with no future chapter count");
    check(before.newCount==1,"Confirmed illustration is excluded from new text badge");
    check(first.readingRevision!=s.readingRevision(),"Snapshot detects classification changes while loading");
    UiLibrarySnapshot stable=UiLibrarySnapshot.load(s,prefs);
    check(stable.readingRevision==s.readingRevision(),"Prepared snapshot becomes stable after classification");
    Store.ReadCounts art=s.readCounts(Collections.singletonList(s.chapter(book,book+"-1")));
    check(art.textUnread==0&&art.illustrationsUnseen==1,"Illustration volume never repeats unread text from another volume");
    MainActivity activity=(MainActivity)startActivitySync(new Intent().setClassName(ctx.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
    call(activity,"openChapter",new Class<?>[]{String.class,String.class},book,book+"-4");
    NativeReader reader=(NativeReader)field(activity,"nativeReader");
    check(reader!=null&&reader.getPageCount()>1,"Actual reader has multiple pages");
    check(!s.wasRead(book+"-4"),"Opening the chapter does not fabricate reading its remaining pages");
    long revision=s.readingRevision();runOnMainSync(()->reader.jump(true));waitForIdleSync();
    check(s.wasRead(book+"-4")&&s.readingRevision()>revision,"Reaching the final page persists read status and invalidates old counts");
    UiLibrarySnapshot after=UiLibrarySnapshot.load(s,prefs);UiLibrarySnapshot.Summary done=after.summary(s.book(book));
    check(done.unread==0&&done.currentRead,"Latest completed text is no longer counted unread");
    check(done.newCount==0,"Read latest chapter disappears from new text badge");
    check(done.unseenIllustrations==1&&done.pendingReview==1,"Reading text does not invent viewing illustrations or reading uncertain captions");
    check(s.unread(book)==1,"Uncertain short text still blocks automatic remote read-all");
    long limit=SystemClock.elapsedRealtime()+8000;UiLibrarySnapshot visible=null;
    while(SystemClock.elapsedRealtime()<limit){visible=(UiLibrarySnapshot)field(activity,"readyLibrary");if(visible!=null&&visible.summary(s.book(book)).currentRead&&visible.readingRevision==s.readingRevision())break;SystemClock.sleep(50);waitForIdleSync();}
    check(visible!=null&&visible.summary(s.book(book)).unread==0&&visible.summary(s.book(book)).currentRead,"Activity refreshes its prepared library immediately after final-page reading");
    call(activity,"contents",new Class<?>[]{Store.Book.class},s.book(book));
    android.widget.ListView list=(android.widget.ListView)field(activity,"currentList");
    limit=SystemClock.elapsedRealtime()+5000;while(list==null&&SystemClock.elapsedRealtime()<limit){SystemClock.sleep(50);waitForIdleSync();list=(android.widget.ListView)field(activity,"currentList");}
    check(list!=null&&list.getCount()==3,"Actual TOC prepares three independent volume rows");
    Object group=list.getAdapter().getItem(0);
    check(field(group,"readSummary").equals("0 chương chữ chưa đọc · 1 minh họa chưa xem"),"Actual illustration row uses only its own prepared unread summary");
    check(s.book(book).current.equals(book+"-4"),"Unread counting and TOC preserve latest reading position");
    result.putString("stream","PASS TOTAL "+checks+" unread state checks\n");finish(0,result);
  }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(1,result);}}
}
