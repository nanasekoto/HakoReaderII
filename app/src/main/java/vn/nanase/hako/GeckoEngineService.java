package vn.nanase.hako;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.io.*;
import org.json.*;
import org.mozilla.geckoview.*;
/** One native request at a time, no renderer remains after unbinding. */
public final class GeckoEngineService extends Service {
 private Message current;private GeckoSession session;private WebExtension extension;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()){public void handleMessage(Message m){
  if(m.what!=1)return;
  if(current!=null){respond(m,"Gecko đang xử lý yêu cầu khác");return;}
  current=Message.obtain(m);begin();
 }});
 public IBinder onBind(Intent i){GeckoHost.bound=true;return endpoint.getBinder();}
 public boolean onUnbind(Intent i){GeckoHost.bound=false;finish("Đã dừng phiên tải");GeckoHost.idle();return false;}
 private void respond(Message request,String error){try{Message reply=Message.obtain(null,2);reply.arg1=request.arg1;Bundle b=new Bundle();if(error!=null)b.putString("error",error);reply.setData(b);request.replyTo.send(reply);}catch(Exception ignored){}}
 private void finish(String error){if(session!=null){session.close();session=null;}Message m=current;current=null;if(m!=null)respond(m,error);}
 private void begin(){
  final Message job=current;final Bundle b=job.getData();String url=b.getString("url","");
  if(!url.startsWith("https://")){finish("Chỉ hỗ trợ HTTPS");return;}
  GeckoRuntime runtime=GeckoHost.get(this);
  ui.postDelayed(()->{if(current==job)finish("Trang chưa sẵn sàng sau 45 giây; không tải lại tự động");},45000);
  if(b.getString("script")==null){
   try{new GeckoWebExecutor(runtime).fetch(new WebRequest.Builder(url).header("Referer",HakoParser.ORIGIN+"/").build(),GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS).accept(response->{
    if(current!=job)return;
    if(response.statusCode!=200){if(response.statusCode==403||response.statusCode==429)Repository.cooldown(this,30*60*1000L);finish("HAKO trả HTTP "+response.statusCode+". Dừng tải; mở Đăng nhập để kiểm tra.");return;}
    new Thread(()->{String error=null;try(InputStream in=response.body;FileOutputStream out=new FileOutputStream(b.getString("file"))){byte[] data=new byte[8192];int n,total=0;while((n=in.read(data))!=-1){total+=n;if(total>b.getInt("max"))throw new IOException("Tài nguyên quá lớn");out.write(data,0,n);}out.getFD().sync();}catch(Exception e){error="Không lưu được tài nguyên";}final String err=error;ui.post(()->{if(current==job)finish(err);});},"Gecko asset").start();
   },error->{if(current==job)finish("Không tải được trang qua Gecko");});}catch(Exception e){finish("Không khởi động được yêu cầu Gecko");}
   return;
  }
  if(extension==null){runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/gecko-bridge/","hako-lite@nanase.vn").accept(ext->{extension=ext;if(current==job)render(job);},e->{if(current==job)finish("Không cài được bộ trích xuất Gecko");});}
  else render(job);
 }
 private void render(Message job){
  Bundle b=job.getData();session=new GeckoSession();final GeckoSession target=session;
  target.getWebExtensionController().setMessageDelegate(extension,new WebExtension.MessageDelegate(){
   public GeckoResult<Object> onMessage(String app,Object message,WebExtension.MessageSender sender){
    if(current!=job||sender.session!=target||!(message instanceof JSONObject))return null;
    JSONObject msg=(JSONObject)message;
    if(msg.optBoolean("hello")){
     try{if(!HakoParser.isOrigin(sender.url)&&!sender.url.equals(b.getString("url")))return null;JSONObject command=new JSONObject();command.put("script",b.getString("script"));return GeckoResult.fromValue(command);}catch(Exception ignored){return null;}
    }
    if(msg.has("error")){Repository.cooldown(GeckoEngineService.this,30*60*1000L);finish(msg.optString("error"));return null;}
    String html=msg.optString("html","");if(html.isEmpty())return null;
    byte[] bytes;try{bytes=html.getBytes("UTF-8");if(bytes.length>b.getInt("max")){finish("Nội dung quá lớn");return null;}
     try(FileOutputStream out=new FileOutputStream(b.getString("file"))){out.write(bytes);out.getFD().sync();}finish(null);
    }catch(Exception e){finish("Không lưu được nội dung Gecko");}return null;
   }
  },"hako");
  target.open(GeckoHost.get(this));target.loadUri(b.getString("url"));
 }
}
