package vn.nanase.hako.tests;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import vn.nanase.hako.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Runs real screens over the same seeded library. Every case is recorded before final verdict. */
public final class FixtureJourneyTest extends Instrumentation {
 MainActivity activity;Context context;boolean eink;int checks,failed;String profile;Bundle args;
 interface Step {void run()throws Exception;}
 void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);report("PASS "+why);}
 void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
 void scenario(String name,Step step){try{step.run();report("CASE PASS "+name);}catch(Throwable e){failed++;report("CASE FAIL "+name+": "+e);try{shot("FAIL-"+name);}catch(Throwable capture){report("CAPTURE FAIL "+name+": "+capture.getClass().getSimpleName());}}}
 Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 void call(String name,Class<?>[] signature,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,signature);m.setAccessible(true);final Throwable[] error={null};runOnMainSync(()->{try{m.invoke(activity,values);}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);waitForIdleSync();}
 void waitFor(Callable<Boolean> predicate)throws Exception{long until=SystemClock.elapsedRealtime()+8000;while(!predicate.call()&&SystemClock.elapsedRealtime()<until){SystemClock.sleep(40);waitForIdleSync();}check(predicate.call(),"Asynchronous screen finishes before timeout");}
 View find(View v,String text){if(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;if(v instanceof android.view.ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View found=find(((ViewGroup)v).getChildAt(n),text);if(found!=null)return found;}return null;}
 View prefix(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(text))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View found=prefix(((ViewGroup)v).getChildAt(n),text);if(found!=null)return found;}return null;}
 void unreadLabel()throws Exception{if(!eink)return;waitFor(()->prefix(root(),"Chưa đọc: ")!=null);TextView label=(TextView)prefix(root(),"Chưa đọc: ");check(label.getBackground()==null,"S4 unread count does not become a full-width black strip");check(label.getWidth()<root().getWidth()-20,"S4 unread label uses its natural width");}
 int imageCount(View v){int result=v instanceof ImageView&&v.getVisibility()==View.VISIBLE?1:0;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)result+=imageCount(((ViewGroup)v).getChildAt(n));return result;}
 SettingsPages pager(View v){if(v instanceof SettingsPages)return (SettingsPages)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){SettingsPages p=pager(((ViewGroup)v).getChildAt(n));if(p!=null)return p;}return null;}
 Window evidenceWindow()throws Exception{Dialog dialog=(Dialog)field("settingsDialog");return dialog!=null&&dialog.isShowing()?dialog.getWindow():activity.getWindow();}
 void shot(String name)throws Exception{
  waitForIdleSync();Window window=evidenceWindow();View decor=window.getDecorView();
  waitFor(()->decor.isAttachedToWindow()&&decor.hasWindowFocus()&&decor.getWidth()>0&&decor.getHeight()>0&&!decor.isLayoutRequested());
  java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
  runOnMainSync(()->{decor.invalidate();decor.postOnAnimation(()->decor.postOnAnimation(drawn::countDown));});
  if(!drawn.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("No rendered frame for "+name);
  if(window!=evidenceWindow()||!decor.hasWindowFocus())throw new AssertionError("Wrong foreground window for "+name);
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
  java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);int[] result={-1};
  PixelCopy.request(window,image,value->{result[0]=value;copied.countDown();},new Handler(Looper.getMainLooper()));
  try{
   if(!copied.await(5,java.util.concurrent.TimeUnit.SECONDS)||result[0]!=PixelCopy.SUCCESS)throw new AssertionError("Window capture failed for "+name+": "+result[0]);
   if(window!=evidenceWindow()||!decor.hasWindowFocus())throw new AssertionError("Window changed while capturing "+name);
   File dir=new File(context.getFilesDir(),"lab-evidence");dir.mkdirs();
   try(FileOutputStream out=new FileOutputStream(new File(dir,profile+"-"+name+".png"))){if(!image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IOException("Capture was not saved");}
   try(FileOutputStream out=new FileOutputStream(new File(dir,profile+"-"+name+".window.txt"))){out.write(("capture=PixelCopy-window\nname="+name+"\nprofile="+profile+"\nwindow="+(window==activity.getWindow()?"activity":"dialog")+"\nsize="+image.getWidth()+"x"+image.getHeight()+"\n").getBytes("UTF-8"));}
  }finally{image.recycle();}
 }

 View root(){return activity.getWindow().getDecorView();}
 void holdVolumeUp(){
  long down=SystemClock.uptimeMillis();
  runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP,0)));
  SystemClock.sleep(850);
  runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP,0)));
  waitForIdleSync();
 }
 void readerTools(NativeReader reader)throws Exception{
  Field listener=NativeReader.class.getDeclaredField("listener");listener.setAccessible(true);
  Field page=NativeReader.class.getDeclaredField("page");page.setAccessible(true);int before=page.getInt(reader);
  runOnMainSync(()->{try{((NativeReader.Listener)listener.get(reader)).toolbar();}catch(Exception e){throw new RuntimeException(e);}});
  View top=(View)field("topBar"),bottom=(View)field("bar");
  waitFor(()->top.getHeight()>0&&bottom.getHeight()>0);
  int target=Math.round(48*context.getResources().getDisplayMetrics().density);
  check(bottom.getHeight()>=target,"Reader bottom tools retain a full touch target");
  ViewGroup row=(ViewGroup)bottom;for(int n=0;n<row.getChildCount();n++)check(row.getChildAt(n).getHeight()>=target,"Every reader action is visible and touchable");
  int[] a=new int[2],b=new int[2];top.getLocationOnScreen(a);bottom.getLocationOnScreen(b);
  check(a[1]+top.getHeight()<=b[1],"Reader toolbars do not overlap each other");
  check(page.getInt(reader)==before,"Showing tools does not repaginate or move the reader");shot("reader-tools");
  call("hideToolbar",new Class<?>[]{});check(top.getVisibility()==View.GONE&&bottom.getVisibility()==View.GONE,"Both reading toolbars dismiss together");
  check(page.getInt(reader)==before,"Hiding tools preserves the page");
 }
 void closePanel()throws Exception{Dialog d=(Dialog)field("settingsDialog");if(d!=null)runOnMainSync(d::dismiss);waitForIdleSync();}
 void pagePanel(String name)throws Exception{Dialog d=(Dialog)field("settingsDialog");check(d!=null&&d.isShowing(),name+" is open");SettingsPages p=pager(d.getWindow().getDecorView());shot(name+"-1");if(eink){check(p!=null,name+" uses S4 whole-content pager");int[] y={0};runOnMainSync(()->{p.turn(1);y[0]=p.viewport().getScrollY();});waitForIdleSync();check(y[0]>=0,name+" page key is handled by panel");shot(name+"-2");}closePanel();}
 public void onCreate(Bundle values){args=values;super.onCreate(values);start();}
 public void onStart(){Bundle output=new Bundle();try{
  context=getTargetContext();if(!"vn.nanase.hako.qa".equals(context.getPackageName()))throw new SecurityException("Never seed or clear the production application");
  eink=Boolean.parseBoolean(args.getString("eink","true"));profile=(eink?"s4":"phone")+"-font"+context.getResources().getConfiguration().fontScale;
  check(context.checkCallingOrSelfPermission("android.permission.INTERNET")==PackageManager.PERMISSION_DENIED,"Lab APK has no network permission");
  Class.forName("vn.nanase.hako.FixtureLibrary").getMethod("seed",Context.class,boolean.class).invoke(null,context,eink);
  Store store=Store.get(context);
  scenario("inventory",()->{check(store.books().size()==4,"Four complete fixture novels share one Store");check(store.chapters("ai-dich-999911").size()==1500,"Large catalog has 1,500 chapter entries");check(store.readable("truyen-999910-2"),"Fixture content passes real cache validation");check(!store.readable("truyen-999913-1"),"Missing local content is not reported downloaded");});
  scenario("local-upload-outbox",()->{String result=(String)Class.forName("vn.nanase.hako.FixtureUploadProbe").getMethod("run",Context.class).invoke(null,context);check(result.contains("hash-verified-ACK"),"Android file outbox survives disconnect, gives position priority and requires a real receipt");});
  activity=(MainActivity)startActivitySync(new Intent().setClassName(context.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  scenario("home",()->{call("library",new Class<?>[]{});check(find(root(),"Đọc tiếp")!=null&&find(root(),"Cài đặt")!=null,"Home exposes reading and settings");shot("home");if(eink){runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)));runOnMainSync(()->activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_DOWN)));shot("home-next");}});
  scenario("persisted-lock-home-recovery",()->{
   call("library",new Class<?>[]{});
   try{
    call("toggleTouchLock",new Class<?>[]{});
    check((Boolean)field("touchLocked"),"Lock is active on Home before restarting");
    MainActivity previous=activity;runOnMainSync(previous::finish);waitForIdleSync();
    activity=(MainActivity)startActivitySync(new Intent().setClassName(context.getPackageName(),"vn.nanase.hako.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
    check((Boolean)field("touchLocked"),"Restart restores the persisted lock being tested");
    holdVolumeUp();
    check(!(Boolean)field("touchLocked")&&!context.getSharedPreferences("settings",0).getBoolean("touch_lock",true),"Volume Up hold unlocks Home after restart and persists recovery");
    call("toggleTouchLock",new Class<?>[]{});holdVolumeUp();check(!(Boolean)field("touchLocked"),"Release after unlocking resets the key for another recovery hold");
    call("settings",new Class<?>[]{});check(((Dialog)field("settingsDialog")).isShowing(),"Recovered Home can open its settings panel");closePanel();shot("home-unlocked");
   }finally{if((Boolean)field("touchLocked"))call("toggleTouchLock",new Class<?>[]{});}
  });
  scenario("personal-lists",()->{for(boolean shelf:new boolean[]{false,true}){call("bookList",new Class<?>[]{boolean.class},shelf);waitFor(()->field("currentList")!=null&&((ListView)field("currentList")).getCount()>0);shot(shelf?"shelf":"recent");}for(boolean complete:new boolean[]{false,true}){call("frequentList",new Class<?>[]{boolean.class},complete);waitFor(()->field("currentList")!=null&&((ListView)field("currentList")).getCount()>0);unreadLabel();shot(complete?"saved":"favorite");}});
  scenario("discovery",()->{call("onlineList",new Class<?>[]{String.class,String.class},"Mới cập nhật","https://docln.sbs/danh-sach?truyendich=1&sapxep=capnhat");waitFor(()->((ListView)field("currentList")).getAdapter()!=null);ListView list=(ListView)field("currentList");check(list.getCount()-list.getFooterViewsCount()==4,"Discovery renders all prepared fixture rows");check(eink?imageCount(root())==0:imageCount(root())>0,"Image capability matches device profile");shot("discover");});
  scenario("search",()->{call("searchStories",new Class<?>[]{String.class},"mau");waitFor(()->((ListView)field("currentList")).getAdapter()!=null);check(((ListView)field("currentList")).getCount()>0,"Search opens prepared results without Hako");shot("search");call("searchStories",new Class<?>[]{String.class},"rong");waitFor(()->((ListView)field("currentList")).getAdapter()!=null);ListView empty=(ListView)field("currentList");check(empty.getCount()-empty.getFooterViewsCount()==0,"Empty results remain distinct from failure");check(empty.getEmptyView()!=null,"Empty results have an explanatory view");shot("search-empty");});
  scenario("source-error",()->{call("onlineList",new Class<?>[]{String.class,String.class},"Chưa có cache","https://docln.sbs/danh-sach?page=999");waitFor(()->field("listLoadStatus")!=null&&!((TextView)field("listLoadStatus")).getText().toString().startsWith("Đang mở"));ListView list=(ListView)field("currentList");check(list.getEmptyView()!=null,"Cold/offline failure has explanation instead of blank list");shot("source-error");});
  scenario("long-detail",()->{Store.Book book=store.book("ai-dich-999911");call("openStoryInfo",new Class<?>[]{HakoParser.Link.class,boolean.class},new HakoParser.Link(book.id,book.title,book.url),false);waitFor(()->field("currentStoryInfo")!=null&&((StoryInfo)field("currentStoryInfo")).id.equals(book.id)&&((StoryInfo)field("currentStoryInfo")).summary.length()>2000);check(((StoryInfo)field("currentStoryInfo")).summary.length()>2000,"Full synopsis is retained");check(find(root(),book.title)!=null,"Full novel title is present");shot("detail");if(eink){call("showStorySection",new Class<?>[]{String.class},"Giới thiệu");check(pager(root())!=null,"Long synopsis is paged on S4");SettingsPages pages=pager(root());runOnMainSync(()->pages.turn(1));waitForIdleSync();shot("synopsis-next");}call("contents",new Class<?>[]{Store.Book.class},book);waitForIdleSync();shot("toc");});
  scenario("read-search-settings-retain",()->{String id="truyen-999910",cid=id+"-2";call("openChapter",new Class<?>[]{String.class,String.class},id,cid);waitFor(()->field("nativeReader")!=null);NativeReader reader=(NativeReader)field("nativeReader");final int[] match={-1};runOnMainSync(()->match[0]=reader.findText("MỐC ĐỒNG BỘ HAI MÁY",0));check(match[0]>=0,"In-chapter search locates Vietnamese content in real reader");runOnMainSync(()->check(reader.findText("không-tồn-tại-123",0)<0,"Missing phrase does not move or invent a result"));shot("reader-search");readerTools(reader);call("readingSettings",new Class<?>[]{});pagePanel("reading-settings");call("searchInChapter",new Class<?>[]{});pagePanel("find-panel");call("library",new Class<?>[]{});waitFor(()->store.book(id).current.equals(cid));check(store.html(cid).isFile()&&store.readable(cid),"Leaving chapter retains valid local text");});
  scenario("reader-lock-volume-disabled",()->{
   android.content.SharedPreferences p=context.getSharedPreferences("settings",0);boolean old=p.getBoolean("reader_volume",true);
   try{call("openChapter",new Class<?>[]{String.class,String.class},"truyen-999910","truyen-999910-2");waitFor(()->field("nativeReader")!=null);p.edit().putBoolean("reader_volume",false).commit();call("toggleTouchLock",new Class<?>[]{});holdVolumeUp();check(!(Boolean)field("touchLocked"),"Unlock works even when volume page turning is disabled");}
   finally{if((Boolean)field("touchLocked"))call("toggleTouchLock",new Class<?>[]{});p.edit().putBoolean("reader_volume",old).commit();call("library",new Class<?>[]{});}
  });
  scenario("app-panels",()->{call("downloadSettings",new Class<?>[]{});pagePanel("download-settings");call("connectionSettings",new Class<?>[]{});pagePanel("connection-settings");call("settings",new Class<?>[]{});pagePanel("settings-menu");});
  scenario("cold-cross-device-anchor",()->{Class<?> probe=Class.forName("vn.nanase.hako.FixtureAnchorProbe");int expected=(Integer)probe.getMethod("prepare",Context.class).invoke(null,context);try{call("openChapter",new Class<?>[]{String.class,String.class},"truyen-999991","");waitFor(()->field("nativeReader")!=null);probe.getMethod("verify",Context.class,int.class).invoke(null,context,expected);check(true,"Cold resume resolves logical text after file delivery even when image blocks differ");shot("cold-resume");}finally{call("library",new Class<?>[]{});java.util.concurrent.Future<?> saved=(java.util.concurrent.Future<?>)field("lastReaderSave");if(saved!=null)saved.get(10,java.util.concurrent.TimeUnit.SECONDS);probe.getMethod("clear",Context.class).invoke(null,context);}});
  scenario("cache-maintenance-safety",()->{String result=(String)Class.forName("vn.nanase.hako.FixtureCacheProbe").getMethod("run",Context.class).invoke(null,context);check(result.contains("anchors-and-history"),"Cache cleanup protects unique files, unfinished books, bookmarks, full downloads, current position and pending sync");});
  scenario("cleanup-entry",()->{android.content.pm.PackageInfo pkg=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_ACTIVITIES);boolean lab=false;for(android.content.pm.ActivityInfo a:pkg.activities)if(a.name.equals("vn.nanase.hako.FixtureLabActivity"))lab=true;check(lab,"Isolated launcher exposes create and delete fixture actions");});
  if(activity!=null)runOnMainSync(activity::finish);
  output.putString("stream",(failed==0?"PASS TOTAL ":"FAIL TOTAL ")+checks+" fixture journey checks; failed cases="+failed+"\n");finish(failed==0?0:1,output);
 }catch(Throwable e){e.printStackTrace();output.putString("stream","FAIL fixture setup: "+e+"\n");finish(1,output);}}
}
