package vn.nanase.hako;
import android.content.SharedPreferences;
import java.util.*;
/** Prepared only on a worker. Adapters read this snapshot, never SQLite or chapter files. */
public final class UiLibrarySnapshot {
 public static final class Summary {
  public int total,stored,unread,newCount,illustrations,missingImages,unseenIllustrations,pendingReview;
  public boolean updated,completedOffline,full,currentRead;
  public long updatedAt;
  public String currentTitle="",latestKey="";
  public int remaining=-1;
  public String readLabel(){return unread+" chương chưa đọc";}
 }
 public final List<Store.Book> books;
 public final Map<String,Summary> summaries;
 public final long loadedAt,loadMillis,lastShelfSync,readingRevision;
 UiLibrarySnapshot(List<Store.Book>b,Map<String,Summary>s,long ms,long stamp,long revision){
  books=Collections.unmodifiableList(b);summaries=Collections.unmodifiableMap(s);
  loadedAt=System.currentTimeMillis();loadMillis=ms;lastShelfSync=stamp;
  readingRevision=revision;
 }
 public Summary summary(Store.Book b){Summary s=summaries.get(b.id);return s==null?new Summary():s;}
 public int compareShelf(Store.Book a,Store.Book b){
  int n=Integer.compare(a.shelfRank,b.shelfRank);return n==0?a.id.compareTo(b.id):n;
 }
 public static UiLibrarySnapshot load(Store store,SharedPreferences prefs){
  long started=android.os.SystemClock.elapsedRealtime();
  long revision=store.readingRevision();
  List<Store.Book> books=store.books();Map<String,Summary> out=new HashMap<>();
  for(Store.Book b:books){
   Summary s=summarize(store,prefs,b);out.put(b.id,s);
  }
  return new UiLibrarySnapshot(books,out,android.os.SystemClock.elapsedRealtime()-started,prefs.getLong("lastShelfSync",0),revision);
 }
 static Summary summarize(Store store,SharedPreferences prefs,Store.Book b){
   Summary s=new Summary();List<Store.Chapter> chapters=store.chapters(b.id);
   s.total=0;s.full=store.isKeepFull(b.id);boolean afterCurrent=false;
   Set<String> read=store.readingIds(b.id,chapters);
   for(Store.Chapter ch:chapters){
    // Lists use metadata only. Content and images are validated when opened/downloaded.
    if(store.ignoredForReading(ch))s.illustrations++;else {s.total++;if(ch.ready)s.stored++;}
    if(afterCurrent&&!store.ignoredForReading(ch)&&!read.contains(ch.id))s.remaining++;
    if(ch.id.equals(b.current)){s.currentTitle=ch.title;s.currentRead=read.contains(ch.id);s.remaining=0;afterCurrent=true;}
   }
   // Classification is retained from actual content opening/downloading, never inferred by scanning files here.
   Store.ReadCounts counts=store.readCounts(chapters);
   s.unread=counts.textUnread;s.unseenIllustrations=counts.illustrationsUnseen;s.pendingReview=counts.pendingReview;
   s.newCount=s.total>0?store.newArrivals(b.id):shelfCount(b.shelfInfo);
   String now=prefs.getString("shelf_key_"+b.id,""),known=prefs.getString("catalog_shelf_key_"+b.id,"");
   s.latestKey=now;
   boolean changed=prefs.getBoolean("shelf_changed_"+b.id,false)||(!now.isEmpty()&&!now.equals(known));
   s.updated=(b.followed&&changed)||(s.newCount>0&&s.stored<s.total)||(b.followed&&s.total==0&&shelfCount(b.shelfInfo)>0);
   s.updatedAt=prefs.getLong("shelf_updated_at_"+b.id,0);
   s.completedOffline=store.isCompleted(b.id)&&s.total>0&&s.stored==s.total&&!changed;
   return s;
 }
 private static int shelfCount(String s){try{return Math.max(0,Integer.parseInt(s.trim().split("\\s+")[0]));}catch(Exception e){return 0;}}
}
