package vn.nanase.hako;
import android.content.SharedPreferences;
import java.util.*;
/** Prepared only on a worker. Adapters read this snapshot, never SQLite or chapter files. */
public final class UiLibrarySnapshot {
 public static final class Summary {
  public int total,stored,unread,newCount,illustrations,missingImages,unseenIllustrations,pendingReview,scopeTotal,scopeStored;
  public boolean updated,completedOffline,full,currentRead,sharedComplete;
  public int sharedStored,sharedTotal;
  public String sharedLabel(){return sharedStored>0?" · Nơi khác lưu "+sharedStored+"/"+sharedTotal:"";}
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
 private volatile Map<Integer,List<Store.Book>> indices;
 public List<Store.Book> index(int mode){Map<Integer,List<Store.Book>> cache=indices;return cache==null?null:cache.get(mode);}
 private boolean member(Store.Book b,int mode){return mode==1?b.followed:mode==6?b.followed&&shelfCount(b.shelfInfo)>0:mode==4?b.stamp>0:!b.id.equals("demo")&&(mode==2?(b.pinned||b.visits>=3):(summary(b).completedOffline||summary(b).sharedComplete));}
 private Comparator<Store.Book> ordering(int mode){return (a,b)->{int n;if(mode==1||mode==6)return compareShelf(a,b);if(mode==4){n=Long.compare(b.stamp,a.stamp);return n!=0?n:a.id.compareTo(b.id);}n=Integer.compare(b.visits,a.visits);if(n==0)n=Long.compare(b.stamp,a.stamp);if(n==0)n=a.title.compareToIgnoreCase(b.title);return n!=0?n:a.id.compareTo(b.id);};}
 public void prepareIndices(){prepareIndices(null,null);}
 public void prepareIndices(UiLibrarySnapshot previous,Set<String> changed){
  if(indices!=null)return;long begun=Diagnostics.now();Map<Integer,List<Store.Book>> result=new HashMap<>();
  Map<String,Store.Book> fresh=new HashMap<>(),old=new HashMap<>();for(Store.Book b:books)fresh.put(b.id,b);if(previous!=null)for(Store.Book b:previous.books)old.put(b.id,b);
  for(int mode:new int[]{1,2,3,4,6}){
   Comparator<Store.Book> comparator=ordering(mode);List<Store.Book> prior=previous==null?null:previous.index(mode);
   if(prior!=null&&changed!=null&&changed.isEmpty()){result.put(mode,prior);continue;}
   List<Store.Book> list=new ArrayList<>();
   if(prior!=null&&changed!=null&&changed.size()<=Math.max(1,Math.min(32,books.size()/8))){list.addAll(prior);for(String id:changed){Store.Book before=old.get(id);if(before!=null){int at=Collections.binarySearch(list,before,comparator);if(at>=0)list.remove(at);}Store.Book after=fresh.get(id);if(after!=null&&member(after,mode)){int at=Collections.binarySearch(list,after,comparator);list.add(at<0?-at-1:at,after);}}Diagnostics.count("index.incremental");}
   else{for(Store.Book b:books)if(member(b,mode))list.add(b);list.sort(comparator);Diagnostics.count("index.rebuild");}
   result.put(mode,Collections.unmodifiableList(list));
  }
  indices=Collections.unmodifiableMap(result);Diagnostics.elapsed("library.indices",begun);
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
   Summary s=new Summary();List<Store.Chapter> chapters=store.chapters(b.id);chapters.removeIf(ch->store.isHidden(b.id,ch.id));
   s.total=0;s.full=store.isKeepFull(b.id);boolean afterCurrent=false;
   Set<String> read=store.readingIds(b.id,chapters);
   List<String> ids=new ArrayList<>();Set<String> images=new HashSet<>();for(Store.Chapter ch:chapters){ids.add(ch.id);if(store.ignoredForReading(ch))images.add(ch.id);}
   String current=images.contains(b.current)?prefs.getString("reading_text_"+b.id,""):b.current;
   int selected=ReadingNavigation.resume(ids,current,read,images),start=selected<0?0:chapters.get(selected).ord;
   boolean retained=s.full||store.isCompleted(b.id),toEnd=FetchPolicy.toEnd(b.followed,store.favorite(b),b.visits>0,retained);
   for(Store.Chapter ch:chapters){
    // Lists use metadata only. Content and images are validated when opened/downloaded.
    if(store.ignoredForReading(ch))s.illustrations++;else {s.total++;if(ch.ready)s.stored++;}
    if(!store.ignoredForReading(ch)&&FetchPolicy.inDownloadRange(ch.ord,start,toEnd,retained)){s.scopeTotal++;if(ch.ready)s.scopeStored++;}
    if(afterCurrent&&!store.ignoredForReading(ch)&&!read.contains(ch.id))s.remaining++;
    if(ch.id.equals(b.current)){s.currentTitle=ch.title;s.currentRead=read.contains(ch.id);s.remaining=0;afterCurrent=true;}
   }
   // Classification is retained from actual content opening/downloading, never inferred by scanning files here.
   Set<String> knownIds=new HashSet<>(ids);
   for(Store.Chapter ch:chapters)if(!images.contains(ch.id)&&!read.contains(ch.id))s.unread++;
   if(s.total==0)s.newCount=shelfCount(b.shelfInfo);
   else if(prefs.contains("new_arrival_ids_"+b.id)){for(String id:prefs.getStringSet("new_arrival_ids_"+b.id,Collections.emptySet()))if(knownIds.contains(id)&&!images.contains(id)&&!read.contains(id))s.newCount++;}
   else s.newCount=prefs.getInt("new_arrivals_"+b.id,0);
   String now=prefs.getString("shelf_key_"+b.id,""),known=prefs.getString("catalog_shelf_key_"+b.id,"");
   s.latestKey=now;
   boolean changed=prefs.getBoolean("shelf_changed_"+b.id,false)||(!now.isEmpty()&&!now.equals(known));
   s.updated=(b.followed&&changed)||(s.newCount>0&&s.stored<s.total)||(b.followed&&s.total==0&&shelfCount(b.shelfInfo)>0);
   s.updatedAt=prefs.getLong("shelf_updated_at_"+b.id,0);
   try{org.json.JSONObject remote=new org.json.JSONObject(prefs.getString("shared_availability_"+b.id,"{}"));s.sharedStored=remote.optInt("stored");s.sharedTotal=remote.optInt("total");s.sharedComplete=remote.optBoolean("complete");}catch(Exception ignored){}
   s.completedOffline=store.isCompleted(b.id)&&s.total>0&&s.stored==s.total&&!changed;
   return s;
 }
 private static int shelfCount(String s){try{return Math.max(0,Integer.parseInt(s.trim().split("\\s+")[0]));}catch(Exception e){return 0;}}
}
