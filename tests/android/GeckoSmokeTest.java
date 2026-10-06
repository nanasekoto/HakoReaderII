package vn.nanase.hako.tests;
import android.app.*;import android.content.*;import android.os.*;
import java.io.*;import java.util.*;import vn.nanase.hako.*;
/** Actual Gecko IPC/extension/profile tests on a public static HTTPS page, no Hako account. */
public final class GeckoSmokeTest extends Instrumentation {
 int count;StringBuilder log=new StringBuilder();
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;log.append("PASS ").append(label).append('\n');Bundle p=new Bundle();p.putString("stream","PASS "+label+"\n");sendStatus(0,p);}
 String render(String script)throws Exception{return new String(GeckoClient.request("https://httpbingo.org/html",script,1024*1024),"UTF-8");}
 boolean engineAlive(){for(ActivityManager.RunningAppProcessInfo p:getTargetContext().getSystemService(ActivityManager.class).getRunningAppProcesses())if(p.processName.startsWith("vn.nanase.hako:"))return true;return false;}
 void stopped(){long end=SystemClock.elapsedRealtime()+20000;while(engineAlive()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(250);check(!engineAlive(),"Gecko process exits after work; offline reader remains alive");}
 void loginFlow(Context c)throws Exception{
  c.getSharedPreferences("settings",0).edit().putBoolean("charging",false).putBoolean("auto_wifi_v6",true).commit();
  MainActivity a=(MainActivity)startActivitySync(new Intent().setClassName("vn.nanase.hako","vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  java.lang.reflect.Method browse=MainActivity.class.getDeclaredMethod("browse",String.class);browse.setAccessible(true);
  runOnMainSync(()->{try{browse.invoke(a,"https://httpbingo.org/html");}catch(Exception e){throw new RuntimeException(e);}});
  android.view.accessibility.AccessibilityNodeInfo root=null;long until=SystemClock.elapsedRealtime()+30000;
  while(SystemClock.elapsedRealtime()<until){root=getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Về app").isEmpty())break;SystemClock.sleep(100);}
  check(root!=null&&!root.findAccessibilityNodeInfosByText("Về app").isEmpty(),"Account handoff opens isolated Gecko screen with direct return button");
  check(GeckoClient.authenticating,"Worker requests are blocked while interactive browser is open");
  SystemClock.sleep(1500);
  android.graphics.Bitmap image=getUiAutomation().takeScreenshot();File dir=new File(c.getFilesDir(),"ui-evidence");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"gecko-login.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
  root=getUiAutomation().getRootInActiveWindow();check(root.findAccessibilityNodeInfosByText("Về app").get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"Return button can be activated on the visible Gecko screen");
  until=SystemClock.elapsedRealtime()+10000;while((GeckoClient.authenticating||!a.getWindow().getDecorView().hasWindowFocus())&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
  check(!GeckoClient.authenticating&&a.getWindow().getDecorView().hasWindowFocus(),"Returning restores native screen and unblocks downloads");
  stopped();
 }
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();Repository.init(c);Repository.cancel.set(false);GeckoClient.authenticating=false;
  check(!engineAlive(),"Cold offline app has no Gecko engine process");
  String raw=new String(GeckoClient.request("https://httpbingo.org/html",null,1024*1024),"UTF-8");
  check(raw.contains("Herman Melville"),"GeckoWebExecutor retrieves HTTPS HTML through IPC");
  // Use the shipped extraction script against delayed DOM, not a mocked result.
  String extract;try(InputStream in=c.getAssets().open("extract.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);extract=out.toString("UTF-8");}
  String setup="if(!window.__fixture){window.__fixture=true;var e=document.createElement('div');e.id='chapter-content';e.innerHTML='<div id=chapter-c-protected></div>';document.body.appendChild(e);setTimeout(function(){document.getElementById('chapter-c-protected').innerHTML='<p>Tiếng Việt: một chương đã tải thành công, không lưu nội dung rỗng.</p>';},1200);}";
  String chapter=render(setup+extract);
  check(chapter.contains("Tiếng Việt")&&chapter.contains("chapter-content"),"Real Gecko waits for delayed chapter DOM and extracts Vietnamese");
  String cookie=render("document.cookie='hako_lite_probe=preserved;Max-Age=3600;path=/;Secure;SameSite=Lax';JSON.stringify({html:document.cookie||'NO_COOKIE'});");
  check(cookie.contains("hako_lite_probe=preserved"),"Renderer profile accepts persistent cookies");
  String echoed=new String(GeckoClient.request("https://httpbingo.org/cookies",null,1024*1024),"UTF-8");
  check(echoed.contains("hako_lite_probe")&&echoed.contains("preserved"),"HTTP executor sends cookie from rendered login profile");
  GeckoClient.release();stopped();
  loginFlow(c);
  String restored=render("JSON.stringify({html:document.cookie||'NO_COOKIE'});");
  check(restored.contains("hako_lite_probe=preserved"),"Cookie survives full engine shutdown and restart");
  String removed=render("document.cookie='hako_lite_probe=;Max-Age=0;path=/;Secure';JSON.stringify({html:'OK'});");
  check(removed.equals("OK"),"Bridge returns extracted content without wrapping or corruption");
  GeckoClient.release();stopped();
  result.putString("stream",log+"PASS TOTAL "+count+" Gecko engine checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){GeckoClient.release();result.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
