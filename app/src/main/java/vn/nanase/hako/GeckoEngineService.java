package vn.nanase.hako;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.io.*;
import org.json.*;
import org.mozilla.geckoview.*;
/** One native request at a time, no renderer remains after unbinding. */
public final class GeckoEngineService extends Service {
 private static boolean sameOrigin(String a,String b){
  android.net.Uri x=android.net.Uri.parse(a),y=android.net.Uri.parse(b);
  return "https".equals(x.getScheme())&&"https".equals(y.getScheme())&&x.getHost()!=null&&x.getHost().equalsIgnoreCase(y.getHost())&&x.getPort()==y.getPort();
 }
 private Message current;private GeckoSession session;private WebExtension extension;private InputStream activeBody;private Runnable deadline;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()){public void handleMessage(Message m){
  if(m.what!=1)return;
  if(current!=null){respond(m,"Gecko đang xử lý yêu cầu khác");return;}
  current=Message.obtain(m);begin();
 }});
 public IBinder onBind(Intent i){GeckoHost.bound=true;GeckoHost.active();return endpoint.getBinder();}
 public boolean onUnbind(Intent i){GeckoHost.bound=false;finish("Đã dừng phiên tải");GeckoHost.idle();return false;}
 private void respond(Message request,String error){try{Message reply=Message.obtain(null,2);reply.arg1=request.arg1;Bundle b=new Bundle();if(error!=null)b.putString("error",error);reply.setData(b);request.replyTo.send(reply);}catch(Exception ignored){}}
 private void finish(String error){if(current!=null)android.util.Log.i("HakoGecko",error==null?"Request completed":"Request stopped");if(deadline!=null){ui.removeCallbacks(deadline);deadline=null;}if(session!=null){session.close();session=null;}if(activeBody!=null){try{activeBody.close();}catch(Exception ignored){}activeBody=null;}Message m=current;current=null;if(m!=null)respond(m,error);}
 private void begin(){
  final Message job=current;final Bundle b=job.getData();String url=b.getString("url","");
  if(!url.startsWith("https://")){finish("Chỉ hỗ trợ HTTPS");return;}
  HakoParser.ORIGIN=b.getString("origin","https://docln.sbs");
  try{File output=new File(b.getString("file"));if(!output.getCanonicalFile().getParentFile().equals(getCacheDir().getCanonicalFile())||b.getInt("max")<1||b.getInt("max")>16*1024*1024){finish("Yêu cầu lưu không hợp lệ");return;}}catch(Exception e){finish("Yêu cầu lưu không hợp lệ");return;}
  GeckoRuntime runtime;try{runtime=GeckoHost.get(this);}catch(Exception e){finish("Gecko đang đóng; thử lại sau vài giây");return;}
  deadline=()->{if(current==job)finish("Trang chưa sẵn sàng sau 45 giây; không tải lại tự động");};ui.postDelayed(deadline,45000);
  if(b.getString("script")==null){
   try{new GeckoWebExecutor(runtime).fetch(new WebRequest.Builder(url).header("Referer",HakoParser.ORIGIN+"/").header("Accept","image/avif,image/webp,image/*,*/*;q=0.8").build()).accept(response->{
    if(current!=job)return;
    activeBody=response.body;
    if(response.statusCode!=200){finish("HAKO trả HTTP "+response.statusCode+". Dừng tải; mở Đăng nhập để kiểm tra.");return;}
    new Thread(()->{String error=null;try(InputStream in=response.body;FileOutputStream out=new FileOutputStream(b.getString("file"))){byte[] data=new byte[8192];int n,total=0;while((n=in.read(data))!=-1){total+=n;if(total>b.getInt("max"))throw new IOException("Tài nguyên quá lớn");out.write(data,0,n);}out.getFD().sync();}catch(Exception e){error="Không lưu được tài nguyên";}final String err=error;ui.post(()->{if(current==job)finish(err);});},"Gecko asset").start();
   },error->{if(current==job)finish("Không tải được trang qua Gecko");});}catch(Exception e){finish("Không khởi động được yêu cầu Gecko");}
   return;
  }
  if(extension==null){runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/gecko-bridge/","hako-lite@nanase.vn").accept(ext->{extension=ext;if(current==job)render(job);},e->{if(current==job)finish("Không cài được bộ trích xuất Gecko");});}
  else render(job);
 }
 private void render(Message job){
  Bundle b=job.getData();session=new GeckoSession();final GeckoSession target=session;
  android.util.Log.i("HakoGecko","Renderer opened; extension flags="+extension.flags);
  target.setProgressDelegate(new GeckoSession.ProgressDelegate(){public void onPageStart(GeckoSession s,String uri){android.util.Log.i("HakoGecko","Page started");}public void onPageStop(GeckoSession s,boolean success){android.util.Log.i("HakoGecko","Page stopped: "+success);}});
  target.setNavigationDelegate(new GeckoSession.NavigationDelegate(){public GeckoResult<String> onLoadError(GeckoSession s,String uri,WebRequestError error){if(current==job)finish("Không tải được trang; kiểm tra mạng hoặc mở Đăng nhập.");return null;}});
  target.getWebExtensionController().setMessageDelegate(extension,new WebExtension.MessageDelegate(){
   public GeckoResult<Object> onMessage(String app,Object message,WebExtension.MessageSender sender){
    android.util.Log.i("HakoGecko","Bridge message; current job="+(current==job)+" same session="+(sender.session==target)+" type="+(message==null?"null":message.getClass().getSimpleName()));
    if(current!=job||sender.session!=target||!(message instanceof JSONObject))return null;
    JSONObject msg=(JSONObject)message;
    if(msg.has("phase")){android.util.Log.i("HakoGecko","Bridge phase: "+msg.optString("phase")+" ready="+msg.optString("ready")+" hasScript="+msg.optBoolean("hasScript"));return null;}
    if(msg.optBoolean("hello")){
     boolean approved=HakoParser.isOrigin(sender.url)||sameOrigin(b.getString("url"),sender.url);
     android.util.Log.i("HakoGecko","Bridge hello: approved="+approved+" scriptChars="+b.getString("script","").length());
     if(!approved){finish("Địa chỉ trang trích xuất không khớp yêu cầu");return null;}
     return GeckoResult.fromValue(b.getString("script"));
    }
    if(msg.has("error")){finish(msg.optString("error"));return null;}
    String html=msg.optString("html","");if(html.isEmpty())return null;
    try{final byte[] bytes=html.getBytes("UTF-8");if(bytes.length>b.getInt("max")){finish("Nội dung quá lớn");return null;}
     new Thread(()->{String error=null;try(FileOutputStream out=new FileOutputStream(b.getString("file"))){out.write(bytes);out.getFD().sync();}catch(Exception e){error="Không lưu được nội dung Gecko";}final String err=error;ui.post(()->{if(current==job)finish(err);});},"Gecko content").start();
    }catch(Exception e){finish("Không lưu được nội dung Gecko");}return null;
   }
  },"hako");
  target.open(GeckoHost.get(this));target.setActive(true);target.loadUri(b.getString("url"));
 }
}
