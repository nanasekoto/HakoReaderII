package vn.nanase.hako;

import android.content.*;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
import java.util.concurrent.*;

/** Home-prepared content written into Pocket's existing validated chapter cache. */
public final class ServerSource {
 private static final ScheduledExecutorService images=Executors.newSingleThreadScheduledExecutor();
 private static final Set<String> repairing=ConcurrentHashMap.newKeySet();
 public static boolean preferred(Context c){return ServerBridge.enabled(c)&&c.getSharedPreferences("settings",0).getBoolean("prefer_server",true);}
 static boolean available(Context c){return preferred(c)&&System.currentTimeMillis()>=ServerBridge.prefs(c).getLong("content_retry_after",0);}
 static void failed(Context c,Exception e){ServerBridge.prefs(c).edit().putLong("content_retry_after",System.currentTimeMillis()+15000).putString("source_status","Server chưa sẵn sàng · Dùng nguồn Hako trực tiếp").apply();UpdateReport.log(c,"Nguồn server chưa sẵn sàng; chuyển sang Hako trực tiếp");}
 static JSONObject request(Context c,String method,String path,JSONObject payload)throws Exception{
  return new JSONObject(new String(bytes(c,method,path,payload,8*1024*1024),"UTF-8"));
 }
 static byte[] bytes(Context c,String method,String path,JSONObject payload,int max)throws Exception{
  URL url=new URL(ServerBridge.prefs(c).getString("server","https://nanains.duckdns.org/hako")+path);
  if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Server phải dùng HTTPS");
  HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);connection.setConnectTimeout(4000);connection.setReadTimeout(20000);connection.setRequestProperty("Authorization","Bearer "+ServerBridge.prefs(c).getString("token",""));
  try{if(payload!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes("UTF-8"));}}
   int code=connection.getResponseCode();if(code<200||code>=300)throw new IOException("Nguồn server đang chờ ("+code+")");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Nội dung server vượt giới hạn");out.write(b,0,n);}}return out.toByteArray();
  }finally{connection.disconnect();}
 }
 static JSONObject prepare(Context c,String path,String url,boolean force,boolean repair)throws Exception{
  JSONObject value=new JSONObject().put("url",url).put("eink",DeviceProfile.eink(c)).put("refresh",force).put("repair",repair);
  for(int i=0;i<60;i++){if(Repository.downloadCancelled())throw new InterruptedIOException("Đã dừng tải");JSONObject result=request(c,"POST",path,value);value.put("refresh",false).put("repair",false);if(!result.has("job"))return result;value.put("job",result.getString("job"));Thread.sleep(600);}
  throw new IOException("Server đang chuẩn bị nội dung; có thể thử lại sau");
 }
 public static List<HakoParser.Link> discover(Context c,String url)throws Exception{
  JSONObject result=request(c,"GET","/v2/pocket/discover?url="+URLEncoder.encode(url,"UTF-8"),null);JSONArray rows=result.getJSONArray("books");List<HakoParser.Link> links=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);HakoParser.Link link=new HakoParser.Link(row.getString("id"),row.getString("title"),row.getString("url"));link.info=row.optString("info");links.add(link);}return links;
 }
 static String html(Context c,String url)throws Exception{return request(c,"GET","/v2/pocket/page?url="+URLEncoder.encode(url,"UTF-8"),null).getString("html");}
 static Store.Book catalog(Context c,String url,boolean force)throws Exception{
  JSONObject data=prepare(c,"/v2/pocket/catalog",HakoParser.storyUrl(url),force,false);JSONObject b=data.getJSONObject("book");String bid=b.getString("id");Store store=Store.get(c);store.putBook(new HakoParser.Link(bid,b.getString("title"),b.getString("url")));JSONArray chapters=data.getJSONArray("chapters");List<HakoParser.Link> rows=new ArrayList<>();
  for(int i=0;i<chapters.length();i++){JSONObject ch=chapters.getJSONObject(i);String link=ch.getString("url"),id=HakoParser.chapterId(link);if(!id.isEmpty())rows.add(new HakoParser.Link(id,ch.getString("title"),link));}if(rows.isEmpty())throw new IOException("Server chưa có mục lục hợp lệ");store.catalog(bid,rows);store.establishReadBaseline(bid);return store.book(bid);
 }
 static boolean download(Context c,Store.Chapter chapter,boolean force,boolean imagesOnly)throws Exception{
  JSONObject doc=prepare(c,"/v2/pocket/chapter",chapter.url,force&&!imagesOnly,imagesOnly);boolean complete=save(c,chapter,doc);
  if(!complete){scheduleImages(c,chapter,0);if(!Store.get(c).readable(chapter.id))throw new PendingImages();}
  ServerBridge.prefs(c).edit().putString("source_status","Đang dùng nội dung từ server").apply();return true;
 }
 static final class PendingImages extends IOException{PendingImages(){super("Server đang chuẩn bị ảnh minh họa; chữ đã tải vẫn được giữ");}}
 static boolean save(Context c,Store.Chapter chapter,JSONObject doc)throws Exception{
  Store store=Store.get(c);File dir=store.dir(chapter.id);dir.mkdirs();JSONArray assets=doc.getJSONArray("images");int missing=0;
  for(int i=0;i<assets.length();i++){if(Repository.downloadCancelled())throw new InterruptedIOException("Đã dừng tải");JSONObject image=assets.getJSONObject(i);String name=image.getString("name"),asset=image.optString("asset");if(!name.matches("image-[a-f0-9]{24}\\.bin"))throw new IOException("Tên ảnh không hợp lệ");File file=new File(dir,name);if(Store.imageFileReadable(file))continue;if(!asset.matches("[a-f0-9]{64}(?:-color|-eink)?\\.png")){missing++;continue;}
   try{byte[] data=bytes(c,"GET","/v2/images/"+asset,null,20*1024*1024);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Ảnh server không hợp lệ");AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();try{out.write(data);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}}catch(IOException e){missing++;}
  }
  String html=doc.getString("html");if(!HakoParser.validContent(html))throw new IOException("Nội dung server không hợp lệ");synchronized(store){Store.Book b=store.book(chapter.book);if(b==null||!FetchPolicy.allowCache(b.followed||store.syncTarget(b),chapter.book.equals(Repository.activeBook),store.isKeepFull(b.id)))return true;Store.write(store.html(chapter.id),html);store.classify(chapter.id,html);store.state(chapter.id,store.readable(chapter.id),missing==0?"":"Đang chờ "+missing+" ảnh từ server");}
  return missing==0;
 }
 private static void scheduleImages(Context c,Store.Chapter ch,int attempt){
  if(attempt>=12||repairing.size()>=16||!repairing.add(ch.id))return;Context app=c.getApplicationContext();images.schedule(()->{boolean retry=false;try{if(!preferred(app)||Repository.downloadCancelled()||(Repository.pocketPaused&&!ch.book.equals(Repository.activeBook)))return;JSONObject doc=request(app,"POST","/v2/pocket/chapter",new JSONObject().put("url",ch.url).put("eink",DeviceProfile.eink(app)));if(doc.has("html")){retry=!save(app,ch,doc);if(!retry)app.sendBroadcast(new Intent(Repository.EVENT).setPackage(app.getPackageName()).putExtra("server_images_changed",ch.id));}else retry=true;}catch(Exception e){retry=true;}finally{repairing.remove(ch.id);if(retry)scheduleImages(app,ch,attempt+1);}},3,TimeUnit.SECONDS);
 }
}
