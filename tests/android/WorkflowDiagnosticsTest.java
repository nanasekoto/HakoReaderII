package vn.nanase.hako.tests;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import vn.nanase.hako.*;
import org.json.*;

public final class WorkflowDiagnosticsTest extends Instrumentation {
 int checks;MainActivity activity;
 void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
 View find(View root,String label){if(label.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof android.widget.TextView&&label.equals(((android.widget.TextView)root).getText().toString()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context ctx=getTargetContext();Store s=Store.get(ctx);android.content.SharedPreferences p=ctx.getSharedPreferences("settings",0);p.edit().putBoolean("sync_paused",true).putBoolean("charging",false).putString("origin","https://invalid.example").commit();HakoParser.ORIGIN="https://invalid.example";
  String id="truyen-999006",base="https://invalid.example/truyen/999006-fixture";
  s.putBook(new HakoParser.Link(id,"Workflow fixture",base));s.visited(id);
  s.catalog(id,Arrays.asList(new HakoParser.Link(id+"-1","Tập 1 · Minh họa",base+"/c1-art"),new HakoParser.Link(id+"-2","Tập 1 · Chương mới nhất",base+"/c2-last")));
  String imageName="image-0123456789abcdef01234567.bin";String imageSrc="https://offline.hako.invalid/"+id+"-2/"+imageName;File imageFile=new File(s.dir(id+"-2"),imageName);imageFile.getParentFile().mkdirs();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.RGB_565);try(FileOutputStream out=new FileOutputStream(imageFile)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
  StringBuilder html=new StringBuilder();for(int i=0;i<100;i++)html.append("<p>Đoạn văn dài kiểm tra chuẩn bị ngoài giao diện và đọc tiếp đúng chương mới nhất.</p>");html.append("<img src='").append(imageSrc).append("'><details><summary>Ghi chú</summary>Nội dung chú thích riêng.</details>");Store.write(s.html(id+"-2"),html.toString());s.state(id+"-2",true,"");s.position(id,id+"-2",0,0);LibraryState.refresh(ctx);
  activity=(MainActivity)startActivitySync(new Intent().setClassName(ctx.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  View report=find(activity.getWindow().getDecorView(),"Gửi báo cáo"),heading=find(activity.getWindow().getDecorView(),"HAKO POCKET");
  check(report!=null&&heading!=null&&report.getParent()==heading.getParent(),"Report button sits beside Hako Pocket in same header row");
  int[] reportAt=new int[2],headingAt=new int[2];report.getLocationOnScreen(reportAt);heading.getLocationOnScreen(headingAt);check(reportAt[0]>headingAt[0]&&report.getWidth()<200,"Report control is small and on right side");
  android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(FileOutputStream out=new FileOutputStream(new File(ctx.getFilesDir(),"main-page.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
  check(find(activity.getWindow().getDecorView(),"Đọc tiếp")!=null&&find(activity.getWindow().getDecorView(),"Tủ sách")!=null,"Main workflow buttons remain present");
  check(!ReportJob.configured(ctx),"No email endpoint or secret is embedded in app");
  check(ReportJob.validEndpoint("https://script.google.com/macros/s/fixture_id/exec")&&!ReportJob.validEndpoint("http://script.google.com/macros/s/id/exec")&&!ReportJob.validEndpoint("https://attacker.example/macros/s/id/exec"),"Report endpoint restricts HTTPS Google Apps Script");
  check(!ReportJob.acknowledged(new JSONObject().put("ok",true).put("reportId","old"),"new")&&!ReportJob.acknowledged(new JSONObject().put("reportId","new"),"new"),"Report stays queued without matching positive acknowledgement");
  check(ReportJob.acknowledged(new JSONObject().put("ok",true).put("reportId","new"),"new"),"Matching service acknowledgement accepts report");
  call("openChapter",new Class<?>[]{String.class,String.class},id,id+"-2");long limit=SystemClock.elapsedRealtime()+8000;while(field("nativeReader")==null&&SystemClock.elapsedRealtime()<limit){SystemClock.sleep(25);waitForIdleSync();}
  check(field("nativeReader")!=null&&field("chapterId").equals(id+"-2"),"Unified open request displays selected latest chapter");
  Field textField=NativeReader.class.getDeclaredField("text");textField.setAccessible(true);android.text.Spanned text=(android.text.Spanned)textField.get(field("nativeReader"));boolean note=false;for(android.text.style.CharacterStyle span:text.getSpans(0,text.length(),android.text.style.CharacterStyle.class))if(span.getClass().getSimpleName().equals("Note"))note=true;check(note&&text.getSpans(0,text.length(),android.text.style.ImageSpan.class).length>0,"Worker preparation preserves footnotes and local image spans");
  JSONObject counts=Diagnostics.payload().getJSONObject("counts");check(counts.optLong("reader.parse_worker")>0&&counts.optLong("reader.parse_ui")==0,"Actual chapter parse runs on worker rather than UI");
  call("openChapter",new Class<?>[]{String.class,String.class},id,id+"-2");SystemClock.sleep(300);waitForIdleSync();check(Diagnostics.payload().getJSONObject("counts").optLong("reader.prepared_hit")>0,"Reopening unchanged selected content reuses bounded prepared cache");
  long parses=Diagnostics.payload().getJSONObject("counts").optLong("reader.parse_worker");image=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.RGB_565);try(FileOutputStream out=new FileOutputStream(imageFile)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();call("openChapter",new Class<?>[]{String.class,String.class},id,id+"-2");SystemClock.sleep(300);waitForIdleSync();check(Diagnostics.payload().getJSONObject("counts").optLong("reader.parse_worker")>parses,"Changed image fingerprint invalidates prepared chapter cache");
  check(s.book(id).current.equals(id+"-2")&&!s.wasRead(id+"-2"),"Opening and preparing do not invent final-page completion");
  check(Diagnostics.payload().getJSONObject("timings").has("reader.open_to_ui")&&Diagnostics.payload().getJSONObject("timings").has("reader.prepare"),"Report contains real stage timings and sample windows");
  UiLibrarySnapshot snap=LibraryState.refresh(ctx);snap.prepareIndices();check(snap.index(4)!=null&&snap.index(4).size()==1,"Recent index is prepared once for navigation");
  HakoParser.Link row=new HakoParser.Link(id,"Workflow fixture",base);row.info="3 chương mới";s.mergeShelf(Arrays.asList(row));s.mergeShelf(Arrays.asList(row));LibraryState.refresh(ctx);
  long ranks=Diagnostics.payload().getJSONObject("counts").optLong("shelf.rank_write");s.mergeShelf(Arrays.asList(row));check(Diagnostics.payload().getJSONObject("counts").optLong("shelf.rank_write")==ranks,"Unchanged shelf import does not rewrite ranks");
  File queued=Diagnostics.queue(ctx);check(queued.isFile()&&new JSONObject(Store.read(queued)).getJSONObject("counts").has("db.query"),"Offline report persists aggregate query counters");
  check(!Store.read(queued).contains(html.toString())&&!Store.read(queued).contains("Workflow fixture")&&!Store.read(queued).contains("invalid.example"),"Diagnostic payload excludes novel text titles and URLs");
  for(int i=0;i<10;i++)Diagnostics.queue(ctx);check(new File(ctx.getFilesDir(),"report-outbox").listFiles((d,n)->n.endsWith(".json")).length<=8,"Outbox retention is bounded");
  result.putString("stream","PASS TOTAL "+checks+" workflow diagnostics checks\n");finish(0,result);
 }catch(Throwable e){result.putString("stream","FAIL "+e+"\n");finish(1,result);}}
}

