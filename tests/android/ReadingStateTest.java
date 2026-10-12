package vn.nanase.hako.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import vn.nanase.hako.*;

/** Actual Store + Activity + Repository, offline fixtures; no Hako traffic required. */
public final class ReadingStateTest extends Instrumentation {
  private int checks;
  private MainActivity activity;
  private void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
  private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void call(String name,Class<?>[] types,Object... args)throws Exception{
    Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);
    runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});
    waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
  }
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle result=new Bundle();try{
    Context ctx=getTargetContext();Store s=Store.get(ctx);
    ctx.getSharedPreferences("settings",0).edit().putString("origin","https://invalid.example").putBoolean("charging",false).putBoolean("sync_paused",true).putBoolean("sync_verified",false).commit();
    HakoParser.ORIGIN="https://invalid.example";
    String book="truyen-999001";
    s.putBook(new HakoParser.Link(book,"Reading fixture","https://invalid.example/truyen/999001-fixture"));s.visited(book);s.setKeepFull(book,true);
    List<HakoParser.Link> links=new ArrayList<>();
    links.add(new HakoParser.Link("truyen-999001-1","Tập 1 · Minh họa","https://invalid.example/truyen/999001-fixture/c1-art"));
    links.add(new HakoParser.Link("truyen-999001-2","Chương 1","https://invalid.example/truyen/999001-fixture/c2-old"));
    links.add(new HakoParser.Link("truyen-999001-3","Minh họa · Chuyện ngắn","https://invalid.example/truyen/999001-fixture/c3-caption"));
    links.add(new HakoParser.Link("truyen-999001-4","Chương mới nhất","https://invalid.example/truyen/999001-fixture/c4-last"));
    s.catalog(book,links);
    Store.write(s.html("truyen-999001-1"),"<img src='https://i.docln.net/missing.jpg'>");s.readable("truyen-999001-1");
    Store.write(s.html("truyen-999001-3"),"<p>Chuyện ngắn vẫn phải đọc.</p><img src='https://i.docln.net/missing.jpg'>");s.readable("truyen-999001-3");
    Store.write(s.html("truyen-999001-4"),"<p>Chương mới nhất đã có nội dung hợp lệ.</p>");s.state("truyen-999001-4",true,"");
    s.position(book,"truyen-999001-2",0,0);Store.Book stale=s.book(book);
    s.position(book,"truyen-999001-4",0,0);
    check(s.resumeIndex(stale,s.chapters(book))==3,"Resume ignores stale list position and keeps latest saved chapter");
    check(s.readingStart(stale,s.chapters(book))==3,"Download range uses same latest reading anchor");
    check(!s.needsReadingDownload(book),"Opening latest cached chapter does not backfill missing old full chapters");
    check(s.adjacentIndex(s.chapters(book),"truyen-999001-4",1)==-1,"End of book has no wrapped next chapter");
    check(s.adjacentIndex(s.chapters(book),"truyen-999001-2",1)==2,"Candidate short text is not silently skipped");
    s.position(book,"truyen-999001-3",0,0);
    check(s.resumeIndex(stale,s.chapters(book))==2,"Saved candidate chapter can be resumed");
    s.position(book,"truyen-999001-4",0,0);
    activity=(MainActivity)startActivitySync(new Intent().setClassName(ctx.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
    call("openBookDirect",new Class<?>[]{Store.Book.class},stale);
    check("truyen-999001-4".equals(field("chapterId")),"Actual Activity opens latest chapter even with an old list snapshot");
    check(ctx.getSharedPreferences("settings",0).getBoolean("sync_paused",false),"Reading never overrides Pause");
    call("adjacent",new Class<?>[]{int.class},1);
    check("truyen-999001-4".equals(field("chapterId"))&&s.book(book).current.equals("truyen-999001-4"),"Actual next action stays at last chapter and preserves database position");
    String saved=Store.read(s.html("truyen-999001-4"));long stamp=s.html("truyen-999001-4").lastModified();
    ctx.getSharedPreferences("settings",0).edit().putBoolean("sync_paused",false).commit();Repository.syncPaused=false;Repository.pocketPaused=false;Repository.cancel.set(false);
    Repository.run(ctx,book,Repository.MODE_READER_NEXT,false);
    check("COMPLETED".equals(Repository.syncState),"Reader download completes offline without contacting the invalid fixture server");
    check(!s.html("truyen-999001-2").exists(),"Reader request does not fetch old missing full chapters");
    check(saved.equals(Store.read(s.html("truyen-999001-4")))&&stamp==s.html("truyen-999001-4").lastModified(),"Valid latest chapter is reused without overwrite");
    check(s.isKeepFull(book),"Reader request preserves full retention choice");
    Store.write(s.html("truyen-999001-2"),"<p>Chữ cũ đã lưu.</p>");s.state("truyen-999001-2",true,"");
    check(s.totalOfflinePercent()==100,"Valid saved text reaches 100 without a library verification flag");
    check(s.missingImages("truyen-999001-1")==1,"Missing illustration remains reported separately");
    check(s.book(book).current.equals("truyen-999001-4"),"Download and progress checks never move reading position");
    result.putString("stream","PASS TOTAL "+checks+" reading state checks\n");finish(0,result);
  }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(1,result);}}
}
