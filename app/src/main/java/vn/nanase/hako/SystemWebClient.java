package vn.nanase.hako;
import android.content.Context;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
/** Short-lived system renderer, sharing the interactive WebView cookie profile. */
public final class SystemWebClient {
 private static final Handler ui=new Handler(Looper.getMainLooper());
 public static synchronized byte[] request(Context c,String url,String script,int max,String origin)throws Exception{
  if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("Không tải trên giao diện");
  if(!url.startsWith("https://"))throw new IOException("Cần HTTPS");
  if(script==null)return asset(url,max,origin);
  CountDownLatch done=new CountDownLatch(1);AtomicReference<byte[]> result=new AtomicReference<>();AtomicReference<Exception> error=new AtomicReference<>();AtomicReference<WebView> holder=new AtomicReference<>();
  ui.post(()->{try{
   WebView view=new WebView(c.getApplicationContext());holder.set(view);view.getSettings().setJavaScriptEnabled(true);view.getSettings().setDomStorageEnabled(true);view.getSettings().setAllowFileAccess(false);view.getSettings().setAllowContentAccess(false);android.webkit.CookieManager.getInstance().setAcceptCookie(true);
   if(!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT))throw new IOException("Cần cập nhật Android System WebView để tải trực tiếp an toàn");
   // Background fetches must not masquerade as a human reading on Hako.
   androidx.webkit.WebViewCompat.addDocumentStartJavaScript(view,"(function(){const set=Storage.prototype.setItem,remove=Storage.prototype.removeItem;Storage.prototype.setItem=function(k,v){if(this===localStorage&&k==='reading_series')return;return set.apply(this,arguments)};Storage.prototype.removeItem=function(k){if(this===localStorage&&k==='reading_series')return;return remove.apply(this,arguments)}})()",java.util.Collections.singleton(origin));
   view.setWebViewClient(new WebViewClient());
   view.loadUrl(url);
   ui.postDelayed(new Runnable(){int tries;public void run(){
    if(done.getCount()==0)return;
    if(Repository.cancel.get()||GeckoClient.authenticating){error.set(new IOException("Đã dừng tải"));done.countDown();return;}
    if(++tries>40){error.set(new IOException("Trang chưa sẵn sàng; mở Đăng nhập để kiểm tra"));done.countDown();return;}
    String current=view.getUrl();if(current==null||!HakoParser.senderOriginAllowed(current,origin)){ui.postDelayed(this,750);return;}
    if(done.getCount()==0)return;
    view.evaluateJavascript("(function(){try{return "+script+"}catch(e){return '{}'}})()",value->{try{
     Object decoded=new JSONTokener(value).nextValue();JSONObject data=new JSONObject(String.valueOf(decoded));
     if(data.has("error"))throw new IOException(data.optString("error"));
     if(data.has("html")&&!data.optString("html").isEmpty()){
      byte[] bytes=data.getString("html").getBytes("UTF-8");if(bytes.length>max)throw new IOException("Nội dung quá lớn");result.set(bytes);done.countDown();return;
     }
    }catch(Exception e){if(e instanceof IOException){error.set(e);done.countDown();return;}}ui.postDelayed(this,750);});
   }},500);
  }catch(Exception e){error.set(e);done.countDown();}});
  try{if(!done.await(40,TimeUnit.SECONDS))throw new IOException("WebView quá thời gian chờ");if(error.get()!=null)throw error.get();if(result.get()==null)throw new IOException("Trang không có nội dung");return result.get();}
  finally{done.countDown();ui.post(()->{WebView view=holder.get();if(view!=null){view.stopLoading();view.destroy();}android.webkit.CookieManager.getInstance().flush();});}
 }
 private static byte[] asset(String url,int max,String origin)throws Exception{
  AtomicReference<String> cookie=new AtomicReference<>("");CountDownLatch ready=new CountDownLatch(1);ui.post(()->{cookie.set(android.webkit.CookieManager.getInstance().getCookie(url));ready.countDown();});if(!ready.await(3,TimeUnit.SECONDS))throw new IOException("Chưa lấy được phiên web");
  javax.net.ssl.HttpsURLConnection connection=(javax.net.ssl.HttpsURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(20000);connection.setRequestProperty("Referer",origin+"/");if(cookie.get()!=null)connection.setRequestProperty("Cookie",cookie.get());
  try{if(connection.getResponseCode()!=200)throw new IOException("Máy chủ ảnh trả HTTP "+connection.getResponseCode());try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Ảnh quá lớn");out.write(b,0,n);}return out.toByteArray();}}finally{connection.disconnect();}
 }
}
