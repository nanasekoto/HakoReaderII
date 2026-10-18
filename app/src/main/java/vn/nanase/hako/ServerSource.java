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
 public static String mode(Context c){return c.getSharedPreferences("settings",0).getString("content_source",c.getSharedPreferences("settings",0).getBoolean("prefer_server",true)?"auto":"web");}
 public static boolean preferred(Context c){return (ServerBridge.enabled(c)||mode(c).equals("server"))&&!mode(c).equals("web");}
 static void directAllowed(Context c)throws IOException{if(mode(c).equals("server"))throw new IOException("Chỉ server · Chưa kết nối được, vẫn đọc được bản đã lưu");}
 static boolean available(Context c){return preferred(c)&&(mode(c).equals("server")||System.currentTimeMillis()>=ServerBridge.prefs(c).getLong("content_retry_after",0));}
 static void failed(Context c,Exception e)throws IOException{ServerBridge.prefs(c).edit().putLong("content_retry_after",System.currentTimeMillis()+15000).putString("source_status","Server chưa sẵn sàng · Dùng nguồn Hako trực tiếp").apply();UpdateReport.log(c,"Nguồn server chưa sẵn sàng; chuyển sang Hako trực tiếp");directAllowed(c);}
 static JSONObject request(Context c,String method,String path,JSONObject payload)throws Exception{
  return new JSONObject(new String(bytes(c,method,path,payload,8*1024*1024),"UTF-8"));
 }
 static byte[] bytes(Context c,String method,String path,JSONObject payload,int max)throws Exception{
  URL url=new URL(ServerBridge.prefs(c).getString("server","https://nanains.duckdns.org/hako")+path);
  if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Server phải dùng HTTPS");
  HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);connection.setConnectTimeout(4000);connection.setReadTimeout(20000);connection.setRequestProperty("Authorization","Bearer "+ServerBridge.prefs(c).getString("token",""));
  try{if(payload!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes("UTF-8"));}}
   int code=connection.getResponseCode();if(code<200||code>=300)throw new IOException("Nguồn server đang chờ ("+code+")");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in="gzip".equalsIgnoreCase(connection.getContentEncoding())?new java.util.zip.GZIPInputStream(connection.getInputStream()):connection.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Nội dung server vượt giới hạn");out.write(b,0,n);}}ServerBridge.prefs(c).edit().putLong("source_last_ok",System.currentTimeMillis()).putString("source_status","Server đã kết nối").apply();return out.toByteArray();
  }finally{connection.disconnect();}
 }
 static JSONObject prepare(Context c,String path,String url,boolean force,boolean repair)throws Exception{
  JSONObject value=new JSONObject().put("url",url).put("eink",DeviceProfile.eink(c)).put("text_only",!DeviceProfile.images(c)).put("refresh",force).put("repair",repair);
  for(int i=0;i<60;i++){if(Repository.downloadCancelled())throw new InterruptedIOException("Đã dừng tải");JSONObject result=request(c,"POST",path,value);value.put("refresh",false).put("repair",false);if(!result.has("job"))return result;value.put("job",result.getString("job"));Thread.sleep(600);}
  throw new IOException("Server đang chuẩn bị nội dung; có thể thử lại sau");
 }
 public static List<HakoParser.Link> discover(Context c,String url)throws Exception{
  JSONObject result=request(c,"GET","/v2/pocket/discover?url="+URLEncoder.encode(url,"UTF-8"),null);JSONArray rows=result.getJSONArray("books");List<HakoParser.Link> links=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);HakoParser.Link link=new HakoParser.Link(row.getString("id"),row.getString("title"),HakoParser.sharedUrl(row.getString("url")));link.info=row.optString("info");links.add(link);}return links;
 }
 public static JSONObject view(Context c,String url,boolean refresh)throws Exception{return request(c,"GET","/v3/pocket/view?url="+URLEncoder.encode(url,"UTF-8")+"&refresh="+refresh,null);}
 public static void shelf(Context c,boolean refresh)throws Exception{
  JSONObject data=request(c,"GET","/v3/pocket/shelf?refresh="+refresh,null);JSONArray books=data.getJSONArray("books");List<HakoParser.Link> rows=new ArrayList<>();
  for(int n=0;n<books.length();n++){JSONObject b=books.getJSONObject(n);HakoParser.Link row=new HakoParser.Link(b.getString("id"),b.getString("title"),HakoParser.sharedUrl(b.getString("url")));row.info=b.optString("info",b.optString("unread")+" chương chưa đọc");row.updatedAt=(long)(b.optDouble("updated",0)*1000);row.latestKey=b.optString("latest_key");rows.add(row);}
  Store store=Store.get(c);if(data.optBoolean("ready"))store.replaceShelf(rows);else store.mergeShelf(rows,0);
  for(HakoParser.Link row:rows)c.getSharedPreferences("settings",0).edit().putString("shelf_key_"+row.id,row.latestKey).putLong("shelf_updated_at_"+row.id,row.updatedAt).apply();
  if(data.optDouble("updated_at")>0)c.getSharedPreferences("settings",0).edit().putLong("lastShelfSync",(long)(data.optDouble("updated_at")*1000)).apply();
  LibraryState.refresh(c);
 }
 static String html(Context c,String url)throws Exception{return request(c,"GET","/v2/pocket/page?url="+URLEncoder.encode(url,"UTF-8"),null).getString("html");}
 static Store.Book catalog(Context c,String url,boolean force)throws Exception{
  JSONObject data=prepare(c,"/v2/pocket/catalog",HakoParser.storyUrl(url),force,false);JSONObject b=data.getJSONObject("book");String bid=b.getString("id");Store store=Store.get(c);store.putBook(new HakoParser.Link(bid,b.getString("title"),HakoParser.sharedUrl(b.getString("url"))));JSONArray chapters=data.getJSONArray("chapters");List<HakoParser.Link> rows=new ArrayList<>();
  for(int i=0;i<chapters.length();i++){JSONObject ch=chapters.getJSONObject(i);String link=HakoParser.sharedUrl(ch.getString("url")),id=HakoParser.chapterId(link);if(!id.isEmpty())rows.add(new HakoParser.Link(id,ch.optString("volume").isEmpty()?ch.getString("title"):ch.getString("volume")+" · "+ch.getString("title"),link));}if(rows.isEmpty())throw new IOException("Server chưa có mục lục hợp lệ");store.catalog(bid,rows);store.establishReadBaseline(bid);return store.book(bid);
 }
 static boolean download(Context c,Store.Chapter chapter,boolean force,boolean imagesOnly)throws Exception{
  JSONObject doc=prepare(c,"/v2/pocket/chapter",chapter.url,force&&!imagesOnly,imagesOnly);boolean complete=save(c,chapter,doc);
  if(!complete){scheduleImages(c,chapter,0);if(!Store.get(c).readable(chapter.id))throw new PendingImages();}
  ServerBridge.prefs(c).edit().putString("source_status","Đang dùng nội dung từ server").apply();return true;
 }
 static final class PendingImages extends IOException{PendingImages(){super("Server đang chuẩn bị ảnh minh họa; chữ đã tải vẫn được giữ");}}
 static boolean save(Context c,Store.Chapter chapter,JSONObject doc)throws Exception{return save(c,chapter,doc,null);}
 static boolean save(Context c,Store.Chapter chapter,JSONObject doc,Map<String,byte[]> packed)throws Exception{
  Store store=Store.get(c);
  if(!DeviceProfile.images(c)&&"illustration".equals(doc.optString("kind"))){c.getSharedPreferences("settings",0).edit().putInt("chapter_kind_"+chapter.id,2).putBoolean("illustration_attempted_"+chapter.id,true).apply();return true;}
  File dir=store.dir(chapter.id);dir.mkdirs();JSONArray assets=doc.getJSONArray("images");int missing=0;
  for(int i=0;DeviceProfile.images(c)&&i<assets.length();i++){if(Repository.downloadCancelled())throw new InterruptedIOException("Đã dừng tải");JSONObject image=assets.getJSONObject(i);String name=image.getString("name"),asset=image.optString("asset");if(!name.matches("image-[a-f0-9]{24}\\.bin"))throw new IOException("Tên ảnh không hợp lệ");File file=new File(dir,name);if(Store.imageFileReadable(file))continue;if(!asset.matches("[a-f0-9]{64}(?:-color|-eink)?\\.png")){missing++;continue;}
   try{if(packed!=null&&!packed.containsKey(asset)){missing++;continue;}byte[] data=packed==null?bytes(c,"GET","/v2/images/"+asset,null,20*1024*1024):packed.get(asset);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Ảnh server không hợp lệ");AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();try{out.write(data);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}}catch(IOException e){missing++;}
  }
  String html=doc.getString("html");if(!DeviceProfile.images(c)){org.jsoup.nodes.Document plain=org.jsoup.Jsoup.parseBodyFragment(html);plain.select("img").remove();html=plain.body().html();}if(!HakoParser.validContent(html))throw new IOException("Nội dung server không hợp lệ");synchronized(store){Store.Book b=store.book(chapter.book);if(b==null||!FetchPolicy.allowCache(b.followed||store.syncTarget(b),chapter.book.equals(Repository.activeBook),store.isKeepFull(b.id)))return true;Store.write(store.html(chapter.id),html);store.classify(chapter.id,html);store.state(chapter.id,store.readable(chapter.id),missing==0?"":"Đang chờ "+missing+" ảnh từ server");}
  return missing==0;
 }
 static void repairCachedImages(Context c,Store.Chapter ch){if(DeviceProfile.images(c)&&available(c)&&Store.get(c).missingImages(ch.id)>0)scheduleImages(c,ch,0);}
 private static void scheduleImages(Context c,Store.Chapter ch,int attempt){
  if(!DeviceProfile.images(c)||attempt>=12||repairing.size()>=16||!repairing.add(ch.id))return;Context app=c.getApplicationContext();images.schedule(()->{boolean retry=false;try{if(!preferred(app)||Repository.downloadCancelled()||(Repository.pocketPaused&&!ch.book.equals(Repository.activeBook)))return;JSONObject doc=request(app,"POST","/v2/pocket/chapter",new JSONObject().put("url",ch.url).put("eink",DeviceProfile.eink(app)).put("text_only",!DeviceProfile.images(app)));if(doc.has("html")){retry=!save(app,ch,doc);if(!retry)app.sendBroadcast(new Intent(Repository.EVENT).setPackage(app.getPackageName()).putExtra("server_images_changed",ch.id));}else retry=true;}catch(Exception e){retry=true;}finally{repairing.remove(ch.id);if(retry)scheduleImages(app,ch,attempt+1);}},3,TimeUnit.SECONDS);
 }
 public static boolean pack(Context c,List<Store.Chapter> chapters)throws Exception{
  if(chapters.isEmpty()||!available(c))return false;Store store=Store.get(c);
  for(int start=0;start<chapters.size();start+=15){List<Store.Chapter> group=chapters.subList(start,Math.min(chapters.size(),start+15));JSONArray urls=new JSONArray();Map<String,Store.Chapter> allowed=new HashMap<>();for(Store.Chapter ch:group){if(!store.readable(ch.id)){urls.put(ch.url);allowed.put(ch.id,ch);}}if(urls.length()==0)continue;
   JSONObject request=new JSONObject().put("urls",urls).put("eink",DeviceProfile.eink(c)).put("text_only",!DeviceProfile.images(c));byte[] archive=null;
   for(int attempt=0;attempt<40;attempt++){if(Repository.downloadCancelled()||Repository.pocketPaused)throw new InterruptedIOException("Đã dừng tải gói");byte[] response=bytes(c,"POST","/v3/pocket/pack",request,13*1024*1024);if(response.length>2&&response[0]=='P'&&response[1]=='K'){archive=response;break;}JSONObject pending=new JSONObject(new String(response,"UTF-8"));if(!pending.has("job"))throw new IOException("Gói không hợp lệ");Thread.sleep(3000);}
   if(archive==null)throw new IOException("Server đang chuẩn bị gói");Map<String,byte[]> files=new HashMap<>();int total=0,count=0;
   try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(archive))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(++count>200||(!name.equals("manifest.json")&&!name.matches("images/[a-f0-9]{64}(?:-color|-eink)?\\.png"))||files.containsKey(name))throw new IOException("Gói có đường dẫn không hợp lệ");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>16*1024*1024||out.size()+n>8*1024*1024)throw new IOException("Gói vượt giới hạn giải nén");out.write(buffer,0,n);}files.put(name,out.toByteArray());}}
   if(!files.containsKey("manifest.json"))throw new IOException("Thiếu thông tin gói");JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),"UTF-8"));if(manifest.optInt("schema")!=1||!group.get(0).book.equals(manifest.getJSONObject("book").getString("id")))throw new IOException("Gói sai truyện");Map<String,byte[]> assets=new HashMap<>();for(String name:files.keySet())if(name.startsWith("images/"))assets.put(name.substring(7),files.get(name));JSONArray docs=manifest.getJSONArray("chapters");Set<String> seen=new HashSet<>();for(int n=0;n<docs.length();n++){JSONObject doc=docs.getJSONObject(n);String id=doc.getString("chapter");if(!allowed.containsKey(id)||!seen.add(id)||!HakoParser.validContent(doc.getString("html")))throw new IOException("Gói có chương ngoài phạm vi");}
   for(int n=0;n<docs.length();n++){JSONObject doc=docs.getJSONObject(n);Store.Chapter ch=allowed.get(doc.getString("chapter"));if(!save(c,ch,doc,assets))scheduleImages(c,ch,0);}ServerBridge.prefs(c).edit().putString("source_status","Đã nhận gói "+docs.length()+" chương từ server").apply();
  }return true;
 }
}
