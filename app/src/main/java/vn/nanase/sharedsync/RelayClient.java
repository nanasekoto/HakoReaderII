package vn.nanase.sharedsync;

import android.content.*;
import android.util.AtomicFile;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small HTTPS transport shared by both APKs. No service SDK or always-running connection. */
public final class RelayClient {
    final SharedPreferences prefs;
    final File metadata;
    public RelayClient(Context c){prefs=c.getSharedPreferences("reading_cloud",0);metadata=new File(c.getFilesDir(),"relay-metadata");}
    public boolean ready(){return !prefs.getString("url","").isEmpty()&&!prefs.getString("token","").isEmpty();}
    public synchronized void configure(JSONObject value)throws Exception{String url=value.optString("url"),token=value.optString("token");if(url.isEmpty()||token.isEmpty())return;URL parsed=new URL(url);if(!parsed.getProtocol().equals("https")||parsed.getUserInfo()!=null||parsed.getQuery()!=null||parsed.getRef()!=null)throw new IOException("Địa chỉ đồng bộ không hợp lệ");if(!url.equals(prefs.getString("url","")))prefs.edit().clear().commit();prefs.edit().putString("url",url.replaceAll("/+$","")).putString("token",token).commit();}
    public long revision(String book){return prefs.getLong("revision:"+book,0);}
    public synchronized JSONObject send(JSONObject source)throws Exception{
        JSONObject value=new JSONObject(source.toString());String book=value.getString("book");if(value.has("metadata")){JSONObject metadata=compactMetadata(value.getJSONObject("metadata"));String fingerprint=hexDigest(metadata.toString());if(fingerprint.equals(prefs.getString("metadata_sent:"+book,"")))value.remove("metadata");else value.put("metadata",metadata);}value.put("base_revision",source.optLong("relay_base_revision",0));
        // Retain the event's captured base across retries; a later snapshot is not authorization to rebase it.
        String key="event:"+value.getString("event_id");JSONObject captured=new JSONObject(prefs.getString(key,value.toString()));
        if(!prefs.contains(key))prefs.edit().putString(key,captured.toString()).commit();
        captured.put("position_policy","latest");JSONObject result=request("POST","/events",captured);if(captured.has("metadata"))prefs.edit().putString("metadata_sent:"+book,hexDigest(captured.getJSONObject("metadata").toString())).apply();
        if(result.optBoolean("conflict"))prefs.edit().putString("conflict:"+book,captured.toString()).apply();
        else if(result.has("revision"))prefs.edit().remove("conflict:"+book).putLong("revision:"+book,result.optLong("revision")).apply();
        prefs.edit().remove(key).apply();return result;
    }
    static String hexDigest(String value)throws Exception{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();}
    public static JSONObject compactMetadata(JSONObject source)throws Exception{JSONArray input=source.optJSONArray("chapters"),rows=new JSONArray();for(int i=0;input!=null&&i<input.length();i++){JSONObject item=input.getJSONObject(i);if(item.optBoolean("hidden"))continue;JSONObject row=new JSONObject();for(String key:new String[]{"title","url","volume","kind"})if(item.has(key)&&!item.optString(key).equals("unknown"))row.put(key,item.get(key));rows.put(row);}return new JSONObject().put("book",source.getJSONObject("book")).put("chapters",rows);}
    public JSONObject book(String id)throws Exception{if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Invalid book");return request("GET","/books/"+id,null);}
    public JSONObject snapshot(String book)throws Exception{return book==null||book.isEmpty()?snapshot():request("GET","/snapshot?book="+URLEncoder.encode(book,"UTF-8"),null);}
    public JSONObject snapshot()throws Exception{return request("GET","/snapshot",null);}
    public JSONObject entry(JSONObject summary)throws Exception{if(summary.has("chapters"))return summary;String id=summary.getString("id");if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Mã truyện không hợp lệ");File path=new File(metadata,id+".json");long version=summary.optLong("metadata_revision");if(prefs.getLong("metadata:"+id,-1)==version)try(InputStream in=new AtomicFile(path).openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new IOException("Mục lục quá lớn");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}catch(Exception ignored){}JSONObject result=request("GET","/books/"+id,null);metadata.mkdirs();AtomicFile file=new AtomicFile(path);FileOutputStream out=file.startWrite();try{out.write(result.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}prefs.edit().putLong("metadata:"+id,version).apply();return result;}
    public JSONObject flags(String book,JSONObject flags)throws Exception{return request("POST","/flags",new JSONObject(flags.toString()).put("book",book));}
    public JSONObject bookmark(String book,JSONObject mark)throws Exception{return request("POST","/bookmarks",new JSONObject(mark.toString()).put("book",book));}
    public void observed(String book,long revision){prefs.edit().putLong("revision:"+book,revision).apply();}
    public boolean conflict(String book){return prefs.contains("conflict:"+book);}
    public void resolve(String book,long revision){prefs.edit().remove("conflict:"+book).putLong("revision:"+book,revision).commit();}
    JSONObject request(String method,String path,JSONObject data)throws Exception{
        HttpsURLConnection c=(HttpsURLConnection)new URL(prefs.getString("url","")+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+prefs.getString("token",""));
        try{if(data!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(data.toString().getBytes(StandardCharsets.UTF_8));}}if(c.getResponseCode()!=200)throw new IOException("Cloudflare HTTP "+c.getResponseCode()+" "+path);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("Dữ liệu đồng bộ quá lớn");out.write(buffer,0,n);}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}
    }
    public static String chapterId(String url){java.util.regex.Matcher m=java.util.regex.Pattern.compile("/(truyen|sang-tac|ai-dich)/([0-9]+)[^/]*/c([0-9]+)[^/]*").matcher(android.net.Uri.parse(url).getPath());return m.matches()?m.group(1)+"-"+m.group(2)+"-c"+m.group(3):"";}
    public static JSONObject document(JSONObject entry,JSONArray positions,JSONObject reads,boolean legacy)throws Exception{
        String bid=entry.getString("id");JSONObject book=new JSONObject(entry.getJSONObject("book").toString()).put("id",bid);JSONArray rows=new JSONArray(),input=entry.getJSONArray("chapters");for(int i=0;i<input.length();i++){JSONObject item=new JSONObject(input.getJSONObject(i).toString());String id=chapterId(item.optString("url"));if(id.isEmpty())continue;item.put("id",legacy?id.replaceFirst("-c([0-9]+)$","-$1"):id);rows.put(item);}JSONObject position=null;for(int i=0;positions!=null&&i<positions.length();i++)if(bid.equals(positions.optJSONObject(i).optString("book"))){position=new JSONObject(positions.getJSONObject(i).toString());position.put("legacy_chapter",position.optString("chapter").replaceFirst("-c([0-9]+)$","-$1"));break;}JSONArray known=reads==null?null:reads.optJSONArray(bid),read=new JSONArray();for(int i=0;known!=null&&i<known.length();i++)read.put(legacy?known.getString(i).replaceFirst("-c([0-9]+)$","-$1"):known.getString(i));return new JSONObject().put("book",book).put("chapters",rows).put("read",read).put("position",position);
    }
}
