package vn.nanase.hako;

import android.content.*;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** QA sources only. Uses the real Store, cache validator, prepared views and reader. */
public final class FixtureLibrary {
 public static final String PACKAGE="vn.nanase.hako.qa", ORIGIN="https://docln.sbs";
 public static void requireLab(Context c){if(!PACKAGE.equals(c.getPackageName()))throw new SecurityException("Fixture data requires isolated QA application");}
 public static JSONObject data(Context c)throws Exception{requireLab(c);try(InputStream in=c.getAssets().open("library-fixture.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new JSONObject(out.toString("UTF-8"));}}
 static String hash(String value)throws Exception{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 static void image(File file,int width,int height,int color)throws Exception{file.getParentFile().mkdirs();Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(b);canvas.drawColor(color);Paint p=new Paint();p.setColor(Color.WHITE);p.setTextSize(Math.max(20,width/12));canvas.drawText("HAKO LAB",width/12,height/2,p);try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
 public static void seed(Context c,boolean eink)throws Exception{
  requireLab(c);JSONObject fixture=data(c);Store s=Store.get(c);SharedPreferences p=c.getSharedPreferences("settings",0);
  p.edit().putString("origin",ORIGIN).putBoolean("auto_wifi_v6",true).putBoolean("charging",false).putString("content_source","server").putBoolean("sync_paused",true).putBoolean("eink_mode",eink).putBoolean("reader_scroll",!eink).putBoolean("reader_swipes",true).putBoolean("reader_volume",true).putBoolean("sync_verified",false).putBoolean("migrated_font_v3",true).putFloat("size2",22).putInt("font2",0).commit();
  ServerBridge.prefs(c).edit().clear().putString("server","https://example.invalid/lab").putString("position_status","Môi trường kiểm thử · Không gửi dữ liệu thật").commit();HakoParser.ORIGIN=ORIGIN;
  JSONArray rows=new JSONArray(),books=fixture.getJSONArray("books"),paragraphs=fixture.getJSONArray("paragraphs");
  for(int i=0;i<books.length();i++){
   JSONObject b=books.getJSONObject(i);String id=b.getString("id"),url=ORIGIN+"/"+b.getString("path"),asset=hash(id)+"-color.png";
   s.putBook(new HakoParser.Link(id,b.getString("title"),url));ArrayList<HakoParser.Link> links=new ArrayList<>();
   for(int n=1;n<=b.getInt("chapters");n++){String volume="Tập "+((n-1)/50+1),title=n==1&&i==2?"Minh họa":n==2&&i==2?"Thông báo tác giả":"Chương "+n+(n%7==0?" · Tên chương dài để kiểm tra xuống dòng và phím chuyển màn":"");links.add(new HakoParser.Link(id+"-"+n,volume+" · "+title,url+"/c"+n+"-lab"));}
   s.catalog(id,links);int cached=b.getInt("cached");
   for(int n=1;n<=cached;n++){
    String cid=id+"-"+n;StringBuilder html=new StringBuilder();
    if(i==2&&n==1){if(!eink){image(new File(s.dir(cid),"portrait.png"),240,720,Color.rgb(150,65,180));html.append("<p><img src='https://offline.hako.invalid/").append(cid).append("/portrait.png'></p>");}else{p.edit().putInt("chapter_kind_"+cid,2).putBoolean("chapter_kind_attempted_"+cid,true).commit();continue;}}
    else {int repeat=i==2&&n==2?1:n==1?2:24;for(int k=0;k<repeat;k++)html.append("<p>").append(paragraphs.getString(k%paragraphs.length())).append("</p>");if(n==3)html.append("<p>Ghi chú<details><summary>1</summary>Chú thích mẫu tiếng Việt đầy đủ.</details></p>");if(i==2&&n==3&&!eink){image(new File(s.dir(cid),"landscape.png"),720,240,Color.rgb(20,135,150));html.append("<p><img src='https://offline.hako.invalid/").append(cid).append("/landscape.png'></p>");}if(i==2&&n==4&&!eink)html.append("<p><img src='https://offline.hako.invalid/").append(cid).append("/missing.png'></p>");}
    Store.write(s.html(cid),html.toString());s.classify(cid,html.toString());s.state(cid,s.readable(cid),"");
   }
   if(i<3){s.visited(id);s.position(id,id+"-"+(i==2?3:2),0,0);s.pin(id,b.optBoolean("favorite"));s.followed(id,true,i);}
   if(b.optBoolean("completed"))s.setCompleted(id,true);
   JSONObject book=new JSONObject().put("id",id).put("title",b.getString("title")).put("url",url).put("author","Tác giả kiểm thử").put("artist","Họa sĩ kiểm thử").put("summary",b.optString("summary")).put("status",i==0?"Đã hoàn thành":"Đang tiến hành").put("category",b.getString("category")).put("tags",b.getJSONArray("tags")).put("views",i==3?"":"123.456").put("rating",i==3?"":"4,75 / 120").put("updated_text","18/10/2026 10:00").put("chapter_count",b.getInt("chapters")).put("cover_asset",asset).put("cover_url","");
   StoryInfo.prepared(book).save(c);if(!eink)image(new File(new File(c.getCacheDir(),"covers"),hash(asset)+".png"),160,240,Color.rgb(25+40*i,80,135));
   rows.put(new JSONObject(book.toString()).put("latest_chapter","Chương "+b.getInt("chapters")+" · Nội dung mới nhất").put("updated_text","18/10/2026"));
   JSONArray chapters=new JSONArray();for(HakoParser.Link link:links)chapters.put(new JSONObject().put("id",link.id).put("title",link.title).put("url",link.url));PreparedViews.save(c,url,new JSONObject().put("book",book).put("chapters",chapters));
  }
  JSONObject listing=new JSONObject().put("books",rows).put("updated_at",System.currentTimeMillis()/1000);
  for(String query:new String[]{"truyendich=1&sapxep=capnhat","truyendich=1&sapxep=truyenmoi","sapxep=capnhat","sapxep=truyenmoi"})PreparedViews.save(c,ORIGIN+"/danh-sach?"+query,listing);
  PreparedViews.save(c,ORIGIN+"/tim-kiem?keywords=mau",listing);PreparedViews.save(c,ORIGIN+"/tim-kiem?keywords=rong",new JSONObject().put("books",new JSONArray()).put("updated_at",System.currentTimeMillis()/1000));
  p.edit().putString("lastBook","truyen-999910").putBoolean("lab_seeded",true).commit();LibraryState.refresh(c);
 }
}
