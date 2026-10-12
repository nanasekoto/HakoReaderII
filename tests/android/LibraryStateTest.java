package vn.nanase.hako.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import vn.nanase.hako.*;

/** Real restart, contended database and durable independent display metadata. */
public final class LibraryStateTest extends Instrumentation {
 int checks;MainActivity activity;
 void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context ctx=getTargetContext();Store s=Store.get(ctx);android.content.SharedPreferences prefs=ctx.getSharedPreferences("settings",0);
  prefs.edit().putBoolean("charging",false).putBoolean("sync_paused",true).putString("origin","https://invalid.example").commit();
  HakoParser.ORIGIN="https://invalid.example";
  String id="truyen-999004",base="https://invalid.example/truyen/999004-fixture";
  s.putBook(new HakoParser.Link(id,"Saved state fixture",base));s.visited(id);s.pin(id,true);s.followed(id,true,0);
  List<HakoParser.Link> links=new ArrayList<>();for(int i=1;i<=250;i++)links.add(new HakoParser.Link(id+"-"+i,"Tập 1 · Chương "+i,base+"/c"+i+"-fixture"));s.catalog(id,links);
  StringBuilder body=new StringBuilder();for(int i=0;i<80;i++)body.append("<p>Đoạn văn kiểm tra tiếp tục đọc mà không đợi kiểm tra cả thư viện.</p>");
  Store.write(s.html(id+"-3"),body.toString());s.state(id+"-3",true,"");s.position(id,id+"-3",0,0);s.completeThrough(id,id+"-2");
  long revision=s.readingRevision();UiLibrarySnapshot first=UiLibrarySnapshot.load(s,prefs);
  check(first.readingRevision==revision&&s.readingRevision()==revision,"Preparing 250 chapter metadata never parses or classifies chapter files");
  check(first.summary(s.book(id)).stored==1,"Display uses saved ready metadata without opening cached HTML");
  UiLibrarySnapshot persisted=LibraryState.refresh(ctx);
  String json=LibraryState.encode(LibraryState.readSaved(ctx));org.json.JSONObject root=new org.json.JSONObject(json);
  check(root.getJSONArray("recent").length()==1&&root.getJSONArray("favorites").length()==1&&root.getJSONArray("shelf").length()==1,"Separate recent favorite and shelf indexes share one progress record");
  check(!json.contains("<p>")&&json.contains("Saved state fixture"),"Durable display file contains information rather than chapter content");
  UiLibrarySnapshot restored=LibraryState.decode(json);check(restored.books.get(0).current.equals(id+"-3")&&restored.summary(restored.books.get(0)).unread==248,"Saved state round-trip retains chapter identity and sequential unread watermark");
  Field current=LibraryState.class.getDeclaredField("current");current.setAccessible(true);current.set(null,null);
  check(LibraryState.restore(ctx)!=null,"Cold process restores display state from atomic file");
  // Hold the same monitor used by catalog/import. Opening/navigating must not wait for it.
  CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
  Thread blocker=new Thread(()->{synchronized(s){locked.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}});blocker.start();locked.await();
  long started=SystemClock.elapsedRealtime();
  try{
   activity=(MainActivity)startActivitySync(new Intent().setClassName(ctx.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
   check(SystemClock.elapsedRealtime()-started<4000,"Home opens from saved state while database monitor is held");
   call("openLibraryList",new Class<?>[]{int.class},1);
   ListView list=(ListView)field("currentList");check(list!=null&&list.getAdapter().getCount()==1,"Shelf displays immediately without consulting the locked database");
   call("openChapter",new Class<?>[]{String.class,String.class},id,id+"-3");
   final boolean[] responsive={false};runOnMainSync(()->responsive[0]=true);check(responsive[0],"Selected chapter can wait on worker without freezing UI");
  }finally{release.countDown();blocker.join(1000);}
  long limit=SystemClock.elapsedRealtime()+8000;while(field("nativeReader")==null&&SystemClock.elapsedRealtime()<limit){SystemClock.sleep(50);waitForIdleSync();}
  check(field("nativeReader")!=null&&id.concat("-3").equals(field("chapterId")),"After worker is available reader resumes chosen chapter 3");
  s.position(id,id+"-3",4,.3f);LibraryState.refresh(ctx);
  restored=LibraryState.readSaved(ctx);
  check(restored.books.get(0).pos==4&&restored.books.get(0).fraction>.29f,"Reading position updates durable state without a full library scan");
  Map<String,String> keys=new HashMap<>();keys.put("a","a3");
  check(ShelfAnchor.canStopAtUnchanged(Arrays.asList(new ShelfAnchor.Row("new1","n5",2),new ShelfAnchor.Row("new2","m6",1),new ShelfAnchor.Row("a","a3",3)),keys,true),"Two updated books before unchanged unread A stop subsequent shelf pages");
  check(ShelfAnchor.canStopAtUnchanged(Arrays.asList(new ShelfAnchor.Row("a","a3",0)),keys,true),"Shelf stopping is independent of read and unread counts");
  check(!ShelfAnchor.canStopAtUnchanged(Arrays.asList(new ShelfAnchor.Row("a","a4",3)),keys,true)&&!ShelfAnchor.canStopAtUnchanged(Arrays.asList(new ShelfAnchor.Row("a","a3",3)),keys,false),"Changed latest chapter and unsafe ordering cannot stop early");
  java.lang.reflect.Method scope=Repository.class.getDeclaredMethod("inScope",Store.class,Store.Book.class,int.class);scope.setAccessible(true);
  Store.Book recent=new Store.Book();recent.id="recent";recent.stamp=1;
  Store.Book shelf=new Store.Book();shelf.id="shelf";shelf.followed=true;
  check((Boolean)scope.invoke(null,s,recent,Repository.MODE_SYNC_PERSONAL)&&!(Boolean)scope.invoke(null,s,recent,Repository.MODE_SYNC_LIBRARY),"Home refresh selects recent independently of shelf membership");
  check((Boolean)scope.invoke(null,s,shelf,Repository.MODE_SYNC_LIBRARY)&&!(Boolean)scope.invoke(null,s,shelf,Repository.MODE_SYNC_PERSONAL),"Shelf refresh excludes unrelated personal books");
  ctx.getSharedPreferences("download_resume",0).edit().putInt(id,Repository.MODE_READER_NEXT).commit();Repository.resumeInterrupted(ctx);
  check(ctx.getSharedPreferences("download_resume",0).contains(id),"Offline or paused restart preserves unfinished download checkpoint");
  result.putString("stream","PASS TOTAL "+checks+" library state checks\n");finish(0,result);
 }catch(Throwable error){result.putString("stream","FAIL "+error+"\n");finish(1,result);}}
}
