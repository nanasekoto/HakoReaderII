package vn.nanase.hako;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.Html;
import android.util.AtomicFile;
import android.widget.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Optional shared progress. Failure never blocks local reading or starts Gecko. */
public final class ServerBridge {
    static final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1);
    static final ThreadLocal<Boolean> applying=new ThreadLocal<>();
    static final Object gate=new Object();static ScheduledFuture<?> scheduled;
    static volatile String activeBook="";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("server_bridge",0);}
    static boolean enabled(Context c){return !prefs(c).getString("token","").isEmpty();}
    static boolean importing(){return Boolean.TRUE.equals(applying.get());}
    static void status(Context c,String text){prefs(c).edit().putString("status",text).apply();}
    public static void localPosition(Context context,String book,String chapter,int paragraph,float fraction){
        if(importing()||!enabled(context)||book.equals("demo"))return;
        Context c=context.getApplicationContext();synchronized(gate){try{android.content.SharedPreferences p=prefs(c);long seq=p.getLong("sequence",0)+1;JSONObject pending=new JSONObject().put("book",book).put("chapter",chapter).put("paragraph",paragraph).put("fraction",fraction).put("sequence",seq).put("event_id",UUID.randomUUID().toString()).put("base_revision",p.getLong("revision:"+book,0)).put("session",p.getString("session:"+book,""));if(pending.optString("session").isEmpty()){pending.put("session",UUID.randomUUID().toString());}p.edit().putLong("sequence",seq).putString("session:"+book,pending.getString("session")).putString("pending:"+book,pending.toString()).commit();}catch(Exception ignored){}if(scheduled!=null)scheduled.cancel(false);scheduled=worker.schedule(()->sync(c),1800,TimeUnit.MILLISECONDS);}
    }
    public static void completed(Context c,String book){if(importing()||!enabled(c))return;Store.Book b=Store.get(c).book(book);if(b!=null&&!b.current.isEmpty())localPosition(c,book,b.current,b.pos,b.fraction);}
    public static void flags(Context c,String book){if(importing()||!enabled(c))return;prefs(c).edit().putBoolean("flags:"+book,true).apply();kick(c);}
    public static void kick(Context context){if(!enabled(context))return;Context c=context.getApplicationContext();worker.execute(()->sync(c));}
    static JSONObject request(Context c,String method,String path,JSONObject value,boolean auth)throws Exception{
        URL url=new URL(prefs(c).getString("server","https://nanains.duckdns.org/hako")+path);if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException("Cần địa chỉ HTTPS");HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(10000);connection.setRequestMethod(method);if(auth)connection.setRequestProperty("Authorization","Bearer "+prefs(c).getString("token",""));
        try{if(value!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(OutputStream out=connection.getOutputStream()){out.write(value.toString().getBytes(StandardCharsets.UTF_8));}}int code=connection.getResponseCode();if(code<200||code>=300)throw new IOException("Đồng bộ đang chờ ("+code+")");try(InputStream in=connection.getInputStream()){ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(data.size()+n>8*1024*1024)throw new IOException("Dữ liệu quá lớn");data.write(buffer,0,n);}return new JSONObject(data.toString("UTF-8"));}}finally{connection.disconnect();}
    }
    static ArrayList<String> paragraphs(Store store,String cid){ArrayList<String> rows=new ArrayList<>();try{File file=new File(store.dir(cid),"content.html");if(file.length()>4*1024*1024)return rows;String html=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);Element body=Jsoup.parseBodyFragment(html).body();for(Element e:new ArrayList<Element>(body.select("details"))){Element summary=e.selectFirst("summary");if(summary!=null)summary.remove();e.replaceWith(new Element("span").text(" [✎] "));}for(Element e:body.children()){String text=Html.fromHtml(e.outerHtml(),Html.FROM_HTML_MODE_LEGACY).toString().trim();if(!text.isEmpty())rows.add(text);}}catch(Exception ignored){}return rows;}
    static JSONObject locator(Store store,JSONObject pending)throws Exception{int p=pending.optInt("paragraph");float f=(float)pending.optDouble("fraction");ArrayList<String> rows=paragraphs(store,pending.getString("chapter"));String quote="";if(p<rows.size()){String text=rows.get(p);int at=Math.min(text.length(),Math.round(f*text.length()));quote=text.substring(at).replaceAll("\\s+"," ").trim();if(quote.length()>180)quote=quote.substring(0,180);}return new JSONObject().put("paragraph",p).put("fraction",f).put("quote",quote);}
    static void sync(Context c){if(!enabled(c))return;try{
        Store store=Store.get(c);android.content.SharedPreferences p=prefs(c);Map<String,?> snapshot=p.getAll();
        for(String key:snapshot.keySet())if(key.startsWith("pending:")){
            String saved=String.valueOf(snapshot.get(key));JSONObject pending=new JSONObject(saved);String bid=pending.getString("book");Store.Book b=store.book(bid);if(b==null)continue;Store.Chapter selected=store.chapter(bid,pending.getString("chapter"));if(selected==null)continue;
            JSONArray links=new JSONArray();List<Store.Chapter> chapters=store.chapters(bid);for(Store.Chapter ch:chapters)links.put(new JSONObject().put("title",ch.title).put("url",ch.url));JSONArray read=new JSONArray();for(String id:store.readingIds(bid,chapters))read.put(id);
            JSONObject payload=new JSONObject().put("book",new JSONObject().put("title",b.title).put("url",b.url)).put("chapters",links).put("chapter_url",selected.url).put("locator",locator(store,pending)).put("read",read);for(String field:new String[]{"sequence","event_id","base_revision","session"})payload.put(field,pending.get(field));
            JSONObject result=request(c,"POST","/v2/bridge/push",payload,true);
            synchronized(gate){android.content.SharedPreferences.Editor editor=p.edit();if(saved.equals(p.getString(key,"")))editor.remove(key);if(result.optBoolean("conflict")){editor.putString("conflict:"+bid,pending.toString());JSONObject remote=result.optJSONObject("current");if(remote!=null)editor.putString("remote:"+bid,remote.toString());}else if(result.has("revision"))editor.putLong("revision:"+bid,result.optLong("revision"));editor.commit();}
        }
        for(String key:p.getAll().keySet())if(key.startsWith("flags:")&&p.getBoolean(key,false)){String bid=key.substring(6);Store.Book b=store.book(bid);if(b!=null){request(c,"POST","/v1/books/"+bid+"/flags",new JSONObject().put("favorite",b.pinned).put("completed",store.isCompleted(bid)),true);p.edit().remove(key).apply();}}
        JSONObject library=request(c,"GET","/v1/library",null,true);JSONArray books=library.optJSONArray("books"),positions=library.optJSONArray("positions");HashMap<String,JSONObject> remotePositions=new HashMap<>();for(int n=0;positions!=null&&n<positions.length();n++){JSONObject position=positions.getJSONObject(n);remotePositions.put(position.optString("book"),position);}
        for(int n=0;books!=null&&n<books.length();n++){JSONObject book=books.getJSONObject(n);String bid=book.getString("id");JSONObject remote=remotePositions.get(bid);Store.Book local=store.book(bid);long revision=remote==null?0:remote.optLong("revision");if(local!=null&&revision<=p.getLong("pulled:"+bid,0)&&book.optString("read_revision").equals(p.getString("readstate:"+bid,"")))continue;JSONObject data=request(c,"GET","/v2/bridge/books/"+bid,null,true);apply(c,data);p.edit().putLong("pulled:"+bid,revision).putString("readstate:"+bid,book.optString("read_revision")).apply();}
        status(c,"Đã đồng bộ hai chiều");
    }catch(Exception e){status(c,"Chờ kết nối · Tiến độ vẫn lưu trên máy");}}
    static void apply(Context c,JSONObject data)throws Exception{Store store=Store.get(c);android.content.SharedPreferences p=prefs(c);JSONObject book=data.getJSONObject("book");String bid=book.getString("id");applying.set(true);try{store.putBook(new HakoParser.Link(bid,book.getString("title"),book.getString("url")));JSONArray chapters=data.getJSONArray("chapters");ArrayList<HakoParser.Link> links=new ArrayList<>();for(int i=0;i<chapters.length();i++){JSONObject ch=chapters.getJSONObject(i);links.add(new HakoParser.Link(ch.getString("id"),ch.optString("title"),ch.getString("url")));}if(!links.isEmpty()){store.catalog(bid,links);c.getSharedPreferences("settings",0).edit().putBoolean("catalog_failed_"+bid,true).apply();}JSONArray read=data.optJSONArray("read");for(int i=0;read!=null&&i<read.length();i++)store.readChapter(read.getString(i));JSONObject position=data.optJSONObject("position");if(position==null)return;
            p.edit().putString("remote:"+bid,position.toString()).apply();if(activeBook.equals(bid)||p.contains("pending:"+bid)||p.contains("conflict:"+bid))return;
            String cid=position.optString("legacy_chapter",position.optString("chapter").replaceFirst("-c([0-9]+)$","-$1"));JSONObject loc=position.optJSONObject("portable");int paragraph=loc==null?0:loc.optInt("paragraph");float fraction=loc==null?0:(float)loc.optDouble("fraction");String quote=loc==null?"":loc.optString("quote");ArrayList<String> rows=paragraphs(store,cid);if(quote.length()>=8)for(int i=0;i<rows.size();i++){String text=rows.get(i).replaceAll("\\s+"," ").trim();int at=text.indexOf(quote);if(at>=0){paragraph=i;fraction=at/(float)Math.max(1,text.length());break;}}
            store.position(bid,cid,paragraph,fraction);p.edit().putLong("revision:"+bid,position.optLong("revision")).apply();
        }finally{applying.remove();}}
    public static void settings(Activity activity){Context c=activity.getApplicationContext();LinearLayout box=new LinearLayout(activity);box.setOrientation(1);TextView state=new TextView(activity);state.setPadding(20,16,20,16);state.setText(prefs(c).getString("status","Liên kết để dùng chung tiến độ với Hako Server. Mỗi app vẫn đọc độc lập khi mất kết nối."));box.addView(state);EditText code=new EditText(activity);code.setHint("Mã 6 số từ /hako");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);box.addView(code);AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Đồng bộ Hako Server").setView(box).setPositiveButton("Liên kết",null).setNeutralButton("Đồng bộ ngay",(d,w)->{kick(c);Toast.makeText(activity,"Đang đối chiếu dữ liệu đọc",Toast.LENGTH_SHORT).show();}).setNegativeButton("Đóng",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=code.getText().toString().trim();if(!value.matches("[0-9]{6}")){state.setText("Nhập đủ 6 chữ số");return;}state.setText("Đang chờ duyệt trên /hako…");worker.execute(()->{try{JSONObject ticket=request(c,"POST","/pair/request",new JSONObject().put("code",value).put("name","Hako Pocket · "+Build.MODEL),false);for(int i=0;i<150;i++){Thread.sleep(1800);JSONObject result=request(c,"POST","/pair/poll",ticket,false);if(result.optString("state").equals("rejected"))throw new IOException("Yêu cầu bị từ chối");if(result.optString("state").equals("approved")){prefs(c).edit().putString("token",result.getString("token")).putString("device",result.getString("device")).commit();activity.runOnUiThread(()->state.setText("Đã liên kết. Đang đồng bộ thư viện và vị trí đọc."));for(Store.Book existing:Store.get(c).books())if(existing.current!=null&&!existing.current.isEmpty())localPosition(c,existing.id,existing.current,existing.pos,existing.fraction);sync(c);return;}}throw new IOException("Mã đã hết hạn");}catch(Exception e){activity.runOnUiThread(()->state.setText("Chưa liên kết. Tạo mã mới trên /hako rồi thử lại."));}});}));dialog.show();}
}
