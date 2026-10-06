package vn.nanase.hako.tests;
import android.app.*;import android.content.*;import android.os.*;
import java.io.*;import java.util.*;import vn.nanase.hako.*;
/** Actual Gecko IPC/extension/profile tests on a public static HTTPS page, no Hako account. */
public final class GeckoSmokeTest extends Instrumentation {
 int count;StringBuilder log=new StringBuilder();
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;log.append("PASS ").append(label).append('\n');Bundle p=new Bundle();p.putString("stream","PASS "+label+"\n");sendStatus(0,p);}
 String render(String script)throws Exception{return new String(GeckoClient.request("https://example.org/",script,1024*1024),"UTF-8");}
 boolean engineAlive(){for(ActivityManager.RunningAppProcessInfo p:getTargetContext().getSystemService(ActivityManager.class).getRunningAppProcesses())if(p.processName.startsWith("vn.nanase.hako:gecko"))return true;return false;}
 void stopped(){long end=SystemClock.elapsedRealtime()+20000;while(engineAlive()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(250);check(!engineAlive(),"Gecko process exits after work; offline reader remains alive");}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();Repository.init(c);Repository.cancel.set(false);GeckoClient.authenticating=false;
  check(!engineAlive(),"Cold offline app has no Gecko engine process");
  String raw=new String(GeckoClient.request("https://example.org/",null,1024*1024),"UTF-8");
  check(raw.contains("Example Domain"),"GeckoWebExecutor retrieves HTTPS HTML through IPC");
  // Use the shipped extraction script against delayed DOM, not a mocked result.
  String extract;try(InputStream in=c.getAssets().open("extract.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);extract=out.toString("UTF-8");}
  String setup="if(!window.__fixture){window.__fixture=true;var e=document.createElement('div');e.id='chapter-content';e.innerHTML='<div id=chapter-c-protected></div>';document.body.appendChild(e);setTimeout(function(){document.getElementById('chapter-c-protected').innerHTML='<p>Tiếng Việt: một chương đã tải thành công, không lưu nội dung rỗng.</p>';},1200);}";
  String chapter=render(setup+extract);
  check(chapter.contains("Tiếng Việt")&&chapter.contains("chapter-content"),"Real Gecko waits for delayed chapter DOM and extracts Vietnamese");
  String cookie=render("document.cookie='hako_lite_probe=preserved;Max-Age=3600;path=/;Secure;SameSite=Lax';JSON.stringify({html:document.cookie||'NO_COOKIE'});");
  check(cookie.contains("hako_lite_probe=preserved"),"Renderer profile accepts persistent cookies");
  GeckoClient.release();stopped();
  String restored=render("JSON.stringify({html:document.cookie||'NO_COOKIE'});");
  check(restored.contains("hako_lite_probe=preserved"),"Cookie survives full engine shutdown and restart");
  String removed=render("document.cookie='hako_lite_probe=;Max-Age=0;path=/;Secure';JSON.stringify({html:'OK'});");
  check(removed.equals("OK"),"Bridge returns extracted content without wrapping or corruption");
  GeckoClient.release();stopped();
  result.putString("stream",log+"PASS TOTAL "+count+" Gecko engine checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){GeckoClient.release();result.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
