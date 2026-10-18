package vn.nanase.hako;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;
/** Durable, bounded prepared metadata; used on workers, never parsed in list adapters. */
public final class PreparedViews {
 static AtomicFile file(Context c,String url)throws Exception{android.net.Uri u=android.net.Uri.parse(url);String key=u.getEncodedPath()+"?"+u.getEncodedQuery();byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return new AtomicFile(new File(new File(c.getFilesDir(),"prepared-views"),id+".json"));}
 public static JSONObject load(Context c,String url){try{return new JSONObject(new String(file(c,url).readFully(),StandardCharsets.UTF_8));}catch(Exception e){return null;}}
 public static void save(Context c,String url,JSONObject data)throws Exception{if(!data.has("books")&&!data.has("book"))return;AtomicFile target=file(c,url);FileOutputStream out=target.startWrite();try{out.write(data.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}catch(Exception e){target.failWrite(out);throw e;}File[] files=target.getBaseFile().getParentFile().listFiles((d,n)->n.endsWith(".json"));if(files!=null&&files.length>100){java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));for(int n=0;n<files.length-100;n++)files[n].delete();}}
}
