package vn.nanase.hako;
import android.content.SharedPreferences;
import java.util.*;
/** Prepared only on a worker. Adapters read this snapshot, never SQLite or chapter files. */
public final class UiLibrarySnapshot {
 public static final class Summary {
  public int total,stored,unread,newCount;
  public boolean updated,completedOffline,full;
  public long updatedAt;
  public String currentTitle="";
  public int remaining=-1;
 }
 public final List<Store.Book> books;
 public final Map<String,Summary> summaries;
 public final long loadedAt,loadMillis,lastShelfSync;
 private UiLibrarySnapshot(List<Store.Book>b,Map<String,Summary>s,long ms,long stamp){
  books=Collections.unmodifiableList(b);summaries=Collections.unmodifiableMap(s);
  loadedAt=System.currentTimeMillis();loadMillis=ms;lastShelfSync=stamp;
 }
 public Summary summary(Store.Book b){Summary s=summaries.get(b.id);return s==null?new Summary():s;}
 public int compareShelf(Store.Book a,Store.Book b){
  Summary x=summary(a),y=summary(b);int n=Boolean.compare(y.updated,x.updated);
  if(n==0)n=Long.compare(y.updatedAt,x.updatedAt);
  if(n==0)n=Integer.compare(a.shelfRank,b.shelfRank);
  return n==0?a.title.compareToIgnoreCase(b.title):n;
 }
 public static UiLibrarySnapshot load(Store store,SharedPreferences prefs){
  long started=android.os.SystemClock.elapsedRealtime();
  List<Store.Book> books=store.books();Map<String,Summary> out=new HashMap<>();
  for(Store.Book b:books){
   Summary s=new Summary();List<Store.Chapter> chapters=store.chapters(b.id);
   s.total=chapters.size();s.unread=store.unread(b.id);s.full=store.isKeepFull(b.id);
   for(Store.Chapter ch:chapters){
    if(ch.ready)s.stored++;
    if(ch.id.equals(b.current)){s.currentTitle=ch.title;s.remaining=Math.max(0,s.total-ch.ord-1);}
   }
   s.newCount=s.total>0?store.newArrivals(b.id):shelfCount(b.shelfInfo);
   String now=prefs.getString("shelf_key_"+b.id,""),known=prefs.getString("catalog_shelf_key_"+b.id,"");
   boolean changed=prefs.getBoolean("shelf_changed_"+b.id,false)||(!now.isEmpty()&&!now.equals(known));
   s.updated=(b.followed&&changed)||(s.newCount>0&&s.stored<s.total)||(b.followed&&s.total==0&&shelfCount(b.shelfInfo)>0);
   s.updatedAt=prefs.getLong("shelf_updated_at_"+b.id,0);
   s.completedOffline=store.isCompleted(b.id)&&s.total>0&&s.stored==s.total&&!changed;
   out.put(b.id,s);
  }
  return new UiLibrarySnapshot(books,out,android.os.SystemClock.elapsedRealtime()-started,prefs.getLong("lastShelfSync",0));
 }
 private static int shelfCount(String s){try{return Math.max(0,Integer.parseInt(s.trim().split("\\s+")[0]));}catch(Exception e){return 0;}}
}
