package vn.nanase.hako;
import android.net.Uri;
import android.webkit.*;
/** Shared real WebView configuration; no forged UA, cookie export or challenge retries. */
final class WebSession {
 static void configure(WebView view){
  WebSettings s=view.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);
  s.setCacheMode(WebSettings.LOAD_DEFAULT);
  // Keep the actual engine's default UA, identical for login and chapter rendering.
  CookieManager cm=CookieManager.getInstance();
  cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(view,true);
 }
 static boolean challengeFrame(Uri uri){
  String u=uri.toString(),host=uri.getHost();
  return u.equals("about:blank")||u.equals("about:srcdoc")||
    ("https".equals(uri.getScheme())&&("challenges.cloudflare.com".equals(host)||
     HakoParser.isOrigin(u)));
 }
 static String version(){
  android.content.pm.PackageInfo p=WebView.getCurrentWebViewPackage();
  return p==null?"không rõ":p.versionName;
 }
}
