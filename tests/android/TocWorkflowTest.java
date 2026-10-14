package vn.nanase.hako.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import vn.nanase.hako.*;

public final class TocWorkflowTest extends Instrumentation {
 int checks;MainActivity activity;
 void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 long count(String name)throws Exception{return Diagnostics.payload().getJSONObject("counts").optLong(name);}
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();Diagnostics.start(c);Store s=Store.get(c);c.getSharedPreferences("settings",0).edit().putBoolean("sync_paused",true).commit();s.putBook(new HakoParser.Link("demo","TOC fixture",""));
  List<HakoParser.Link> links=new ArrayList<>();for(int i=1;i<=300;i++)links.add(new HakoParser.Link("demo-"+i,"Tập "+(i<=150?1:2)+" · Chương "+i,""));s.catalog("demo",links);s.chapters("demo");TocState.refresh(c,"demo");SystemClock.sleep(500);
  synchronized(s){long before=count("db.query"),validation=count("cache.check");boolean allFound=true;for(int i=1;i<=300;i++)allFound&=s.chapter("demo","demo-"+i)!=null;check(allFound,"All 300 cached chapters remain selectable");for(int i=0;i<10;i++)s.chapters("demo");check(count("db.query")==before,"Warm catalog and 300 chapter selections issue no SQL queries");check(count("cache.check")==validation,"Metadata selection never validates chapter files");}
  long writes=count("db.catalog_row_write");s.catalog("demo",links);check(count("db.catalog_row_write")==writes,"Unchanged catalog writes no chapter rows");
  s.state("demo-1",true,"");check(s.chapter("demo","demo-1").ready,"State update replaces cached chapter metadata immediately");check(!s.readable("demo-1"),"Ready metadata cannot bypass actual missing-content validation");
  s.position("demo","demo-3",0,0);s.completeThrough("demo","demo-2");TocState.Entry snapshot=TocState.refresh(c,"demo");SystemClock.sleep(500);TocState.Entry disk=TocState.readSaved(c,"demo");check(disk.chapters.size()==300&&disk.book.current.equals("demo-3")&&disk.read.contains("demo-1")&&disk.read.contains("demo-2"),"Per-book disk snapshot preserves metadata position and sequential reading watermark");
  File base=new File(c.getFilesDir(),"toc-state/demo.json");long baseStamp=base.lastModified();s.position("demo","demo-3",50,.2f);TocState.refresh(c,"demo");SystemClock.sleep(500);check(base.lastModified()==baseStamp&&TocState.readSaved(c,"demo").book.pos==50,"Reading position writes only small TOC progress file without rewriting chapter base");
  LibraryState.refresh(c);long tocBefore=count("toc.refresh"),persistBefore=count("toc.persist");s.position("demo","demo-3",51,.3f);SystemClock.sleep(350);check(count("toc.refresh")==tocBefore&&count("toc.persist")==persistBefore,"Same-chapter checkpoint does not refresh or persist TOC");check(TocState.peek("demo").book.pos==51&&LibraryState.peek().books.stream().anyMatch(b->b.id.equals("demo")&&b.pos==51),"RAM TOC and library consumers observe latest checkpoint without chapter recount");
  TocState.refresh(c,"demo");long noopBefore=count("toc.persist");TocState.refresh(c,"demo");check(count("toc.persist")==noopBefore,"Identical TOC data skips disk writes");long structureBefore=count("toc.base_write");s.state("demo-300",true,"");SystemClock.sleep(350);TocState.refresh(c,"demo");check(count("toc.base_write")==structureBefore&&TocState.readSaved(c,"demo").chapters.get(299).ready,"Ready change persists separately and restores without rewriting chapter structure");
  synchronized(s){long before=count("db.query");s.canSubmitServer("demo");check(count("db.query")-before<=3,"Server completion checks batch 300 chapters rather than query each ID");}
  LibraryState.refresh(c);activity=(MainActivity)startActivitySync(new Intent().setClassName(c.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();call("contents",new Class<?>[]{Store.Book.class},s.book("demo"));long preparedUntil=SystemClock.elapsedRealtime()+5000;while(field("tocGroups")==null&&SystemClock.elapsedRealtime()<preparedUntil){SystemClock.sleep(25);waitForIdleSync();}check(field("tocGroups")!=null,"Initial TOC preparation completes before locked-database navigation test");
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Thread blocker=new Thread(()->{synchronized(s){entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(Exception ignored){}}});blocker.start();entered.await(2,TimeUnit.SECONDS);
  try{long uiHits=count("toc.ui_hit");long start=SystemClock.elapsedRealtime();call("contents",new Class<?>[]{Store.Book.class},snapshot.book);check(SystemClock.elapsedRealtime()-start<1500&&count("toc.ui_hit")>uiHits,"Warm TOC renders directly from snapshot while database monitor is held");List<?> groups=(List<?>)field("tocGroups");Object group=groups.get(0);start=SystemClock.elapsedRealtime();call("volumeChapters",new Class<?>[]{Store.Book.class,group.getClass()},snapshot.book,group);check(SystemClock.elapsedRealtime()-start<1500,"Choosing a cached volume renders while database monitor is held");}finally{release.countDown();blocker.join(1000);}
  Store.write(s.html("demo-1"),"<p>Actual readable text for persistent validation index.</p>");check(s.readable("demo-1"),"Fixture validates actual readable content");File index=new File(c.getFilesDir(),"validation-index.json");long until=SystemClock.elapsedRealtime()+5000;while(!index.isFile()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);check(index.isFile(),"Validation fingerprint index persists without filesystem scanning");
  for(String name:new String[]{"validatedFiles","validatedImages"}){Field f=Store.class.getDeclaredField(name);f.setAccessible(true);((Map<?,?>)f.get(s)).clear();}Field loaded=Store.class.getDeclaredField("validationLoaded");loaded.setAccessible(true);loaded.setBoolean(s,false);long parses=count("cache.parse");check(s.readable("demo-1")&&count("cache.parse")==parses,"Cold index restoration reuses unchanged validation without HTML parse");
  Store.write(s.html("demo-1"),"<p>Changed chapter text with a different length invalidates persisted validation.</p>");check(s.readable("demo-1")&&count("cache.parse")>parses,"HTML replacement invalidates persisted validation and is reparsed");
  s.state("demo-299",true,"");UiLibrarySnapshot published=LibraryState.refresh(c);c.sendBroadcast(new Intent(Repository.EVENT).setPackage(c.getPackageName()).putExtra("books_changed",true));long displayLimit=SystemClock.elapsedRealtime()+2000;while(field("readyLibrary")!=published&&SystemClock.elapsedRealtime()<displayLimit){SystemClock.sleep(25);waitForIdleSync();}check(field("readyLibrary")==published,"Already-published download changes reach UI without forcing another snapshot query");
  result.putString("stream","PASS TOTAL "+checks+" TOC workflow checks\n");finish(0,result);
 }catch(Throwable error){result.putString("stream","FAIL "+error+"\n");finish(1,result);}}
}


