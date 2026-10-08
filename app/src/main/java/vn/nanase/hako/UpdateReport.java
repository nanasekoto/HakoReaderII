package vn.nanase.hako;
import android.content.Context;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.*;
/** Bounded, event-driven report: no idle polling or cookies/account details. */
public final class UpdateReport {
 private static final ArrayDeque<String> lines=new ArrayDeque<>();
 private static Context app;private static int pages,checked,skipped,selected;private static long started;
 public static synchronized void begin(Context c){app=c.getApplicationContext();lines.clear();pages=checked=skipped=selected=0;started=System.currentTimeMillis();log(c,"Bắt đầu cập nhật · 0.7.8-Gecko · "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);flush();}
 public static synchronized void log(Context c,String text){app=c.getApplicationContext();String safe=String.valueOf(text).replaceAll("https?://\\S+","[địa chỉ]");lines.addLast(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+" · "+safe);while(lines.size()>600)lines.removeFirst();if(lines.size()%20==0)flush();}
 public static synchronized void page(Context c){pages++;}
 public static synchronized void checked(Context c){checked++;}
 public static synchronized void skipped(Context c){skipped++;}
 public static synchronized void selected(Context c){selected++;}
 public static synchronized String text(Context c){if(app==null||lines.isEmpty())return c.getSharedPreferences("sync_report",0).getString("last","Chưa có báo cáo cập nhật.");StringBuilder b=new StringBuilder();b.append("Trang đã quét: ").append(pages).append(" · Mục lục kiểm tra: ").append(checked).append(" · Bỏ qua: ").append(skipped).append(" · Bộ được chọn tải: ").append(selected).append("\nHàng đợi duy nhất: ").append(Repository.syncDone).append('/').append(Repository.syncTotal).append(" chương\n");for(String l:lines)b.append(l).append('\n');return b.toString();}
 public static synchronized void finish(Context c){log(c,"Kết thúc · "+((System.currentTimeMillis()-started)/1000)+" giây · "+Repository.status);flush();}
 private static void flush(){if(app!=null)app.getSharedPreferences("sync_report",0).edit().putString("last",text(app)).apply();}
}
