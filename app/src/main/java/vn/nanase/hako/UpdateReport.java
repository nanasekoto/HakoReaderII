package vn.nanase.hako;
import android.content.Context;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.*;
/** Bounded reports by phase, flushed at checkpoints without idle work. */
public final class UpdateReport {
  private static final ArrayDeque<String> shelf=new ArrayDeque<>(),download=new ArrayDeque<>();
  private static Context app;
  private static int pages,checked,skipped,selected,buffered;
  private static long started,lastFlush;
  public static synchronized void begin(Context c){
    app=c.getApplicationContext();shelf.clear();download.clear();
    pages=checked=skipped=selected=buffered=0;started=System.currentTimeMillis();
    String version="unknown";try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}
    log(c,"Bắt đầu cập nhật · "+version+" · "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);flush();
  }
  public static synchronized void log(Context c,String text){
    app=c.getApplicationContext();
    String safe=String.valueOf(text).replaceAll("https?://\\S+","[địa chỉ]");
    ArrayDeque<String> target="SHELF".equals(Repository.syncState)?shelf:download;
    target.addLast(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+" · "+safe);
    while(target.size()>300)target.removeFirst();
    buffered++;
    if(buffered>=40&&System.currentTimeMillis()-lastFlush>=2000)flush();
  }
  public static synchronized void page(Context c){pages++;}
  public static synchronized void checked(Context c){checked++;}
  public static synchronized void skipped(Context c){skipped++;}
  public static synchronized void selected(Context c){selected++;}
  private static String section(ArrayDeque<String> lines){StringBuilder b=new StringBuilder();for(String line:lines)b.append(line).append('\n');return b.toString();}
  public static synchronized String text(Context c){
    if(app==null||(shelf.isEmpty()&&download.isEmpty()))return c.getSharedPreferences("sync_report",0).getString("last","Chưa có báo cáo cập nhật.");
    return "Trang đã quét: "+pages+" · Mục lục kiểm tra: "+checked+" · Bỏ qua: "+skipped+" · Bộ được chọn tải: "+selected+
      "\nHàng đợi duy nhất: "+Repository.syncDone+"/"+Repository.syncTotal+" chương\n\nTỦ SÁCH\n"+section(shelf)+"\nTẢI CHƯƠNG / ĐIỀU PHỐI\n"+section(download);
  }
  public static synchronized void checkpoint(Context c,String text){log(c,text);flush();}
  public static synchronized void finish(Context c){checkpoint(c,"Kết thúc · "+((System.currentTimeMillis()-started)/1000)+" giây · "+Repository.status);}
  public static synchronized org.json.JSONObject metrics(Context c)throws org.json.JSONException{
    if(app==null)return new org.json.JSONObject(c.getSharedPreferences("sync_report",0).getString("metrics","{}"));
    return new org.json.JSONObject().put("pages",pages).put("catalogsChecked",checked).put("catalogsSkipped",skipped).put("booksSelected",selected).put("chaptersDone",Repository.syncDone).put("chaptersQueued",Repository.syncTotal).put("phase",Repository.syncState);
  }
  private static void flush(){
    if(app==null)return;
    app.getSharedPreferences("sync_report",0).edit().putString("last",text(app))
      .putString("last_shelf_sync",section(shelf)).putString("last_download",section(download)).apply();
    try{app.getSharedPreferences("sync_report",0).edit().putString("metrics",metrics(app).toString()).apply();}catch(Exception ignored){}
    lastFlush=System.currentTimeMillis();buffered=0;
  }
}

