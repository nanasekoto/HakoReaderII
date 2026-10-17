package vn.nanase.hako;
import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
/** Interactive login uses the same Android profile as SystemWebClient. */
public final class SystemBrowserActivity extends Activity {
 private WebView web;private boolean committed,closing;private String url="";
 public void onCreate(Bundle state){super.onCreate(state);HakoParser.ORIGIN=HakoParser.originOf(getIntent().getStringExtra("origin"));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);LinearLayout bar=new LinearLayout(this);root.addView(bar);
  Button back=new Button(this);back.setText("Về app");bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));back.setOnClickListener(v->done(false));
  Button shelf=new Button(this);shelf.setText("Tủ sách");bar.addView(shelf,new LinearLayout.LayoutParams(0,-2,1));shelf.setOnClickListener(v->web.loadUrl(HakoParser.ORIGIN+"/ke-sach"));
  Button read=new Button(this);read.setText("Đọc offline");bar.addView(read,new LinearLayout.LayoutParams(0,-2,1));read.setOnClickListener(v->done(true));
  TextView status=new TextView(this);status.setText("WebView Android · Đăng nhập xong bấm Về app");root.addView(status);
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);CookieManager.getInstance().setAcceptCookie(true);
  web.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !"https".equals(r.getUrl().getScheme());}public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){committed=false;}public void onPageFinished(WebView v,String u){url=u;committed=true;status.setText("Đã mở trang · Đăng nhập xong bấm Về app");}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);String target=getIntent().getStringExtra("url");if(target!=null&&target.startsWith("https://"))web.loadUrl(target);
 }
 private void done(boolean read){if(closing)return;closing=true;CookieManager.getInstance().flush();if(HakoParser.isOrigin(url)){web.evaluateJavascript("localStorage.getItem('reading_series')||'[]'",value->{String history="[]";try{Object decoded=new JSONTokener(value).nextValue();String text=String.valueOf(decoded);if(text.length()<200000)history=text;}catch(Exception ignored){}complete(read,history);});}else complete(read,"[]");}
 private void complete(boolean read,String history){setResult(RESULT_OK,new Intent().putExtra("url",url).putExtra("committed",committed).putExtra("history",history).putExtra("read",read));finish();}
 public void onBackPressed(){if(web.canGoBack())web.goBack();else done(false);}
 protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
