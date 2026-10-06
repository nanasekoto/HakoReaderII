package vn.nanase.hako;
import android.content.*;
import android.os.*;

import android.net.Uri;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
/** One short-lived renderer. No JS bridge, cookies/credentials never exported. */
public final class RenderedPage {
 private static final Handler ui=new Handler(Looper.getMainLooper());
 public static String chapter(Context context,String url)throws Exception {return render(context,url,"");}
 public static String action(Context context,String url,String action)throws Exception{return render(context,url,action);}
 private static synchronized String render(Context context,String url,String action)throws Exception {
  if(Looper.myLooper()==Looper.getMainLooper())throw new IOException("Không tải trên luồng giao diện");
  String script;
  try(InputStream in=context.getAssets().open("extract.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);script=out.toString("UTF-8");
  }
  if(!action.isEmpty())script=action.equals("follow")?
   "(function(){var e=document.querySelector('#collect');if(!e)return JSON.stringify({});if((e.getAttribute('href')||'').indexOf('/login')>=0)return JSON.stringify({error:'Hãy đăng nhập HAKO trước.'});if(e.classList.contains('followed'))return JSON.stringify({html:'OK'});if(!window.__hakoAction){window.__hakoAction=true;e.click();}return JSON.stringify({});})()":
   "(function(){var id="+JSONObject.quote(action.substring(5))+";if(document.querySelector('input[type=password]'))return JSON.stringify({error:'Hãy đăng nhập HAKO trước.'});var e=Array.from(document.querySelectorAll('.mark-read')).find(function(n){return n.getAttribute('data-series')===id});if(window.__hakoReadParent&&!window.__hakoReadParent.querySelector('.mark-read'))return JSON.stringify({html:'OK'});if(e){if(+e.getAttribute('data-unread')===0)return JSON.stringify({html:'OK'});if(!window.__hakoAction){window.__hakoAction=true;window.__hakoReadParent=e.parentElement;e.click();}return JSON.stringify({});}var p=+(new URL(location.href).searchParams.get('page')||1);var links=Array.from(document.querySelectorAll('a[href]')).map(function(a){try{return new URL(a.href)}catch(e){return null}}).filter(function(u){return u&&u.origin===location.origin&&u.pathname==='/ke-sach'&&+u.searchParams.get('page')>p}).sort(function(a,b){return +a.searchParams.get('page')-b.searchParams.get('page')});if(links.length){location.href=links[0].href;return JSON.stringify({});}return JSON.stringify({error:'Không tìm thấy bộ này trên kệ sách. Hãy cập nhật tủ sách.'});})()";
  GeckoClient.init(context);
  return new String(GeckoClient.request(url,script,8*1024*1024),"UTF-8");
 }
}
