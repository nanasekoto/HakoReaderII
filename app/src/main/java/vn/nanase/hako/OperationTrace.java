package vn.nanase.hako;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Bounded provenance, never labels containing titles, URLs, SQL or credentials. */
public final class OperationTrace {
 private static final ThreadLocal<Span> active=new ThreadLocal<>();private static final AtomicLong sequence=new AtomicLong();
 private static final ArrayDeque<JSONObject> samples=new ArrayDeque<>();private static final Map<String,long[]> totals=new LinkedHashMap<>();private static final Map<Long,Span> running=new LinkedHashMap<>();
 public static final class Span {final long id=sequence.incrementAndGet(),begun=SystemClock.elapsedRealtime();final String code;final Span parent;final boolean main=android.os.Looper.myLooper()==android.os.Looper.getMainLooper();long wait,run,ui;volatile int books,chapters,other,checks,parses;volatile long cacheMs,cacheMax;boolean workFinished;Span(String c,long queued,Span p){code=c;parent=p;wait=Math.max(0,begun-queued);}}
 public static Span current(){return active.get();}public static Span bind(Span span){Span old=active.get();active.set(span);return old;}public static void restore(Span span){if(span==null)active.remove();else active.set(span);}
 public static Span begin(String code,long queued){return begin(code,queued,active.get());}
 public static Span begin(String code,long queued,Span parent){Span span=new Span(code,queued,parent);active.set(span);synchronized(OperationTrace.class){if(running.size()<16)running.put(span.id,span);}return span;}
 public static Span begin(String code){return begin(code,SystemClock.elapsedRealtime());}
 public static void workDone(Span span){if(!span.workFinished){span.run=SystemClock.elapsedRealtime()-span.begun;span.workFinished=true;}restore(span.parent);}
 public static void end(Span span,String status,long uiMs){if(active.get()==span)workDone(span);span.ui=uiMs;synchronized(OperationTrace.class){try{
  running.remove(span.id);JSONObject row=new JSONObject().put("id",span.id).put("op",span.code).put("startMs",span.begun).put("wait",span.wait).put("run",span.run).put("ui",span.ui).put("thread",span.main?"ui":"worker").put("q",new JSONArray(new int[]{span.books,span.chapters,span.other})).put("cache",new JSONArray(new long[]{span.checks,span.parses,span.cacheMs,span.cacheMax})).put("result",status);if(span.parent!=null)row.put("parent",span.parent.id);samples.addLast(row);while(samples.size()>24)samples.removeFirst();
  long[] sum=totals.get(span.code);if(sum==null&&totals.size()<48){sum=new long[14];totals.put(span.code,sum);}if(sum!=null){sum[0]++;sum[1]+=span.wait;sum[2]+=span.run;sum[3]=Math.max(sum[3],span.wait);sum[4]=Math.max(sum[4],span.run);sum[5]+=span.books;sum[6]+=span.chapters;sum[7]+=span.other;sum[8]+=span.ui;sum[9]=Math.max(sum[9],span.ui);sum[10]+=span.checks;sum[11]+=span.parses;sum[12]+=span.cacheMs;sum[13]=Math.max(sum[13],span.cacheMax);}
 }catch(JSONException ignored){}}}
 public static void cache(boolean parsed,long ms){Span span=active.get();if(span!=null){span.checks++;if(parsed)span.parses++;span.cacheMs+=ms;span.cacheMax=Math.max(span.cacheMax,ms);}}
 public static void query(String sql){Span span=active.get();int kind=sql.toLowerCase(Locale.ROOT).contains("from chapters")?1:sql.toLowerCase(Locale.ROOT).contains("from books")?0:2;if(span!=null){if(kind==0)span.books++;else if(kind==1)span.chapters++;else span.other++;}else synchronized(OperationTrace.class){long[] sum=totals.get("db.outside");if(sum==null){sum=new long[14];totals.put("db.outside",sum);}sum[0]++;sum[5+kind]++;}}
 public static synchronized JSONObject payload()throws JSONException{
  JSONObject defs=new JSONObject(),stats=new JSONObject();for(Map.Entry<String,long[]> entry:totals.entrySet()){String code=entry.getKey();defs.put(code,source(code));stats.put(code,new JSONArray(entry.getValue()));}
  for(JSONObject sample:samples){String code=sample.optString("op");defs.put(code,source(code));}JSONArray pending=new JSONArray();for(Span span:running.values()){defs.put(span.code,source(span.code));JSONObject item=new JSONObject().put("id",span.id).put("op",span.code).put("ageMs",SystemClock.elapsedRealtime()-span.begun).put("wait",span.wait).put("q",new JSONArray(new int[]{span.books,span.chapters,span.other}));if(span.parent!=null)item.put("parent",span.parent.id);pending.put(item);}
  return new JSONObject().put("schema",1).put("capturedElapsedMs",SystemClock.elapsedRealtime()).put("cacheColumns",new JSONArray(Arrays.asList("checks","parses","totalMs","maxMs"))).put("sourceRoot","app/src/main/java/vn/nanase/hako/").put("queryColumns",new JSONArray(Arrays.asList("books","chapters","other"))).put("statsColumns",new JSONArray(Arrays.asList("count","waitTotalMs","runTotalMs","waitMaxMs","runMaxMs","booksQueries","chaptersQueries","otherQueries","uiTotalMs","uiMaxMs","cacheChecks","cacheParses","cacheTotalMs","cacheMaxMs"))).put("definitions",defs).put("totals",stats).put("samples",new JSONArray(samples)).put("inFlight",pending);
 }
 public static String classify(String label){
  if(label.contains("mở chương"))return "read.open";if(label.contains("chuyển chương"))return "read.adjacent";if(label.contains("kiểm tra chương mới"))return "read.next_web";
  if(label.contains("mở mục lục"))return "toc.open";if(label.contains("mở tập"))return "toc.volume";if(label.contains("sắp xếp"))return "toc.sort";
  if(label.contains("tải danh sách"))return "web.list";if(label.contains("lấy mục lục"))return "book.add";if(label.contains("Cập nhật mục lục"))return "web.catalog";if(label.contains("thông tin truyện"))return "book.info";
  if(label.contains("lưu đã đọc")||label.contains("lưu lên HAKO"))return "read.mark";if(label.contains("ghim"))return "book.pin";if(label.contains("theo dõi"))return "book.follow";
  if(label.contains("ảnh còn thiếu"))return "read.images";if(label.contains("Tải lại chương"))return "read.repair";if(label.contains("tên miền"))return "web.domain";if(label.contains("nhập lịch sử"))return "history.import";if(label.contains("cấu hình báo cáo"))return "report.config";if(label.contains("lưu báo cáo"))return "report.save";if(label.contains("mở HAKO"))return "web.open";return "ui.other";
 }
 public static boolean web(String code){return Arrays.asList("web.list","web.catalog","book.add","book.info","web.domain","read.next_web","read.images","read.repair").contains(code);}
 public static String source(String code){if(code.equals("settings.apply"))return "MainActivity.settings";if(code.startsWith("settings."))return "MainActivity.stepChange";switch(code){
  case "settings.adjust":return "MainActivity.settingsVolume";case "read.sync":return "ReadSync.process";case "read.complete":return "Store.completeThrough";case "read.prepare":return "NativeReader.prepare";case "read.layout":return "NativeReader.reflow";case "read.page":return "NativeReader.turn";case "download.enqueue":case "download.prefetch":return "MainActivity.startDownloads";case "read.persist":return "Store.position";case "read.open":return "MainActivity.openChapter";case "read.adjacent":return "MainActivity.adjacent";case "read.next_web":return "MainActivity.loadNextChapter";
  case "toc.open":return "MainActivity.contents";case "toc.volume":return "MainActivity.volumeChapters";case "toc.sort":return "MainActivity.chapterHeader";case "toc.refresh":return "TocState.refresh";
  case "web.list":return "MainActivity.onlineList";case "book.add":return "MainActivity.addUrl";case "book.info_cache":return "MainActivity.openStoryInfo (cached metadata)";case "book.info":return "MainActivity.openStoryInfo";case "net.bind":return "GeckoClient.connect";case "net.request":return "GeckoClient.request";
  case "library.refresh":return "LibraryState.refresh";case "download.run":return "Repository.run";case "report.save":case "report.send":return "MainActivity.saveReport";case "data.export":case "data.import":return "DataTransfer";case "book.clear_files":return "Store.clearDownloaded";case "data.files":return "DataTransfer";case "book.clear":return "Store.clearDownloaded";
  case "web.catalog":return "Repository.catalog";case "read.mark":return "MainActivity.markCaughtUp";case "read.images":return "Repository.downloadImages";case "read.repair":return "Repository.download";
  case "book.pin":case "book.follow":return "MainActivity.bookMenu";case "history.import":return "MainActivity.onActivityResult";case "web.domain":return "MainActivity.domain";case "report.config":return "MainActivity.reportSettings";case "web.open":return "MainActivity.readerMenu";case "db.outside":return "Store.query (outside traced scope)";default:return "MainActivity.task (unclassified)";
 }}
}

