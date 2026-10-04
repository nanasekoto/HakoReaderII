package vn.nanase.hako.tests;
import android.app.*;import android.content.*;import android.os.*;import android.text.*;import java.lang.reflect.*;import java.util.*;import vn.nanase.hako.*;
/** Instrumentation intended for an Android device/emulator; compilation is not execution. */
public class SmokeTest extends Instrumentation {
 int checks=0;StringBuilder log=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;log.append("PASS ").append(label).append('\n');}
 Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 MainActivity launch()throws Exception{
  MainActivity a=(MainActivity)startActivitySync(new Intent().setClassName("vn.nanase.hako","vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Method m=MainActivity.class.getDeclaredMethod("openChapter",String.class,String.class);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(a,"demo","demo-8");}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();return a;
 }
 public void onStart(){Bundle out=new Bundle();try{
  Context ctx=getTargetContext();Store s=Store.get(ctx);s.putBook(new HakoParser.Link("demo","Kiểm tra tiếng Việt",""));List<HakoParser.Link> links=new ArrayList<>();for(int i=1;i<=30;i++)links.add(new HakoParser.Link("demo-"+i,"Chương "+i,""));s.catalog("demo",links);
  StringBuilder h=new StringBuilder();for(int i=0;i<60;i++)h.append("<p>Đoạn "+i+". Nguyễn, tưởng, khuỷu, quyền, ă â ê ô ơ ư đ. Nội dung để kiểm tra ngắt dòng và mở lại chính xác.</p>");h.append("<p>Chú thích <details><summary>✎</summary><div>Nội dung chú thích tiếng Việt.</div></details></p>");
  for(int i=1;i<=30;i++){Store.write(s.html("demo-"+i),h.toString());s.state("demo-"+i,true,"");}s.position("demo","demo-8",12,0);
  ctx.getSharedPreferences("settings",0).edit().putBoolean("charging",false).commit();MainActivity a=launch();NativeReader reader=(NativeReader)field(a,"nativeReader");
  check(Boolean.TRUE.equals(field(a,"readerReady")),"Native reader ready");check(s.book("demo").pos==12,"Resume starts at saved paragraph");
  StaticLayout layout=(StaticLayout)field(reader,"layout");List<Integer> pages=(List<Integer>)field(reader,"pages");int margin=(int)field(reader,"margin"),height=reader.getHeight()-2*margin;
  for(int i=0;i<pages.size();i++){int end=i+1<pages.size()?pages.get(i+1):layout.getLineCount();check(layout.getLineBottom(end-1)-layout.getLineTop(pages.get(i))<=height,"No clipped text line");}
  runOnMainSync(()->reader.turn(1));waitForIdleSync();int before=s.book("demo").pos;check(before>12,"Turn moves forward and persists");
  runOnMainSync(()->reader.style(24,10,1.5f,android.graphics.Typeface.SERIF,true));waitForIdleSync();check(Math.abs(s.book("demo").pos-before)<=1,"Font change keeps paragraph");
  int saved=s.book("demo").pos;runOnMainSync(a::finish);waitForIdleSync();MainActivity reopened=launch();check(Math.abs(s.book("demo").pos-saved)<=1,"Reopen retains paragraph");
  s.prune(s.book("demo"));check(!s.html("demo-4").exists()&&s.html("demo-5").exists()&&s.html("demo-23").exists()&&!s.html("demo-24").exists(),"Cache window 3 behind / 15 ahead");
  check(s.wasRead("demo-8")&&!s.wasRead("demo-9"),"Prefetched chapter not marked opened");
  ChargeJob.schedule(ctx,true);check(ctx.getSystemService(android.app.job.JobScheduler.class).getPendingJob(40).isRequireCharging(),"Bulk job requires charging");ChargeJob.schedule(ctx,false);
  out.putString("stream",log+"PASS TOTAL "+checks+" Android checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
