package vn.nanase.hako;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
  private static Store instance;

  public static synchronized Store get(Context c) {
    if (instance == null) instance = new Store(c.getApplicationContext());
    return instance;
  }

  private final Map<String,Chapter> chapterLookup=new HashMap<>();
  private final LinkedHashMap<String,List<Chapter>> chapterCache=new LinkedHashMap<>(8,.75f,true);
  private void cacheChapters(String book,List<Chapter> chapters){List<Chapter> replaced=chapterCache.put(book,Collections.unmodifiableList(chapters));if(replaced!=null)for(Chapter ch:replaced)chapterLookup.remove(ch.id);for(Chapter ch:chapters)chapterLookup.put(ch.id,ch);int total=0;for(List<Chapter> list:chapterCache.values())total+=list.size();while(chapterCache.size()>4||(total>16000&&chapterCache.size()>1)){String first=chapterCache.keySet().iterator().next();List<Chapter> removed=chapterCache.remove(first);total-=removed.size();for(Chapter ch:removed)chapterLookup.remove(ch.id);}}
  private Chapter cachedId(String id){return chapterLookup.get(id);}
  private Chapter copyChapter(Chapter ch){if(ch==null)return null;Chapter out=new Chapter();out.id=ch.id;out.book=ch.book;out.title=ch.title;out.url=ch.url;out.ord=ch.ord;out.ready=ch.ready;out.error=ch.error;return out;}
  private void updateCachedState(Chapter owner,boolean ready,String error){if(owner==null)return;List<Chapter> prior=chapterCache.get(owner.book);if(prior==null)return;List<Chapter> next=new ArrayList<>(prior);for(int i=0;i<next.size();i++)if(next.get(i).id.equals(owner.id)){Chapter changed=copyChapter(next.get(i));changed.ready=ready;changed.error=error;next.set(i,changed);break;}cacheChapters(owner.book,next);}
  public final File cache;
  private final Context context;
  private final java.util.concurrent.ConcurrentHashMap<String,String> chapterTitles=new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong readingRevision=new java.util.concurrent.atomic.AtomicLong();
  public long readingRevision(){return readingRevision.get();}
  private void bookMetadataChanged(String id){LibraryState.changed(context,id,false);TocState.metadataChanged(book(id));}
  private void displayChanged(String id,boolean recount){LibraryState.changed(context,id,recount);TocState.changed(context,id);}

  private Store(Context c) {
    super(c, "hako.db", null, 3);
    this.context = c.getApplicationContext();
    cache = new File(c.getFilesDir(), "chapters");
    cache.mkdirs();
  }

  public boolean isKeepFull(String id) {
    return context.getSharedPreferences("settings", 0).getBoolean("keep_full_" + id, false);
  }

  public void setKeepFull(String id, boolean keep) {displayChanged(id,true);
    context.getSharedPreferences("settings", 0).edit().putBoolean("keep_full_" + id, keep).apply();
  }

  public boolean isCompleted(String id){return context.getSharedPreferences("settings",0).getBoolean("completed_"+id,false);}
  public void setCompleted(String id,boolean value){displayChanged(id,true);context.getSharedPreferences("settings",0).edit().putBoolean("completed_"+id,value).apply();}
  public int newArrivals(String id){android.content.SharedPreferences p=context.getSharedPreferences("settings",0);if(!p.contains("new_arrival_ids_"+id))return p.getInt("new_arrivals_"+id,0);int n=0;for(String chapter:p.getStringSet("new_arrival_ids_"+id,Collections.emptySet()))if(!illustration(chapter)&&!wasRead(chapter))n++;return n;}
  public synchronized void mergeShelf(List<HakoParser.Link> links){mergeShelf(links,0);}
  public static void traceQuery(String sql){OperationTrace.query(sql);Diagnostics.count("db.query");String lower=sql.toLowerCase(java.util.Locale.ROOT);Diagnostics.count(lower.contains("from chapters")?"db.chapters_query":lower.contains("from books")?"db.books_query":"db.other_query");}
  private Cursor query(String sql,String[] args){traceQuery(sql);return getReadableDatabase().rawQuery(sql,args);}
  private boolean shelfIndexReady;
  private List<Book> shelfBooks(){
    if(!shelfIndexReady){getWritableDatabase().execSQL("CREATE INDEX IF NOT EXISTS book_shelf_rank ON books(followed,shelf_rank)");shelfIndexReady=true;}
    List<Book> result=new ArrayList<>();try(Cursor c=query("SELECT id,title,url,shelf_info,shelf_rank FROM books WHERE followed=1 ORDER BY shelf_rank",null)){while(c.moveToNext()){Book b=new Book();b.id=c.getString(0);b.title=c.getString(1);b.url=c.getString(2);b.shelfInfo=c.getString(3);b.shelfRank=c.getInt(4);b.followed=true;result.add(b);}}return result;
  }
  public synchronized void mergeShelf(List<HakoParser.Link> links,int firstRank){
    List<Book> previous=shelfBooks();Map<String,Book> before=new HashMap<>();for(Book b:previous)before.put(b.id,b);
    LinkedHashSet<String> observed=new LinkedHashSet<>();for(HakoParser.Link link:links)observed.add(link.id);
    LinkedHashSet<String> order=new LinkedHashSet<>();for(Book b:previous)if(order.size()<firstRank&&!observed.contains(b.id))order.add(b.id);order.addAll(observed);for(Book b:previous)order.add(b.id);
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
      for(HakoParser.Link link:links){Book old=before.get(link.id);if(old==null||!Objects.equals(old.title,link.title)||!Objects.equals(old.url,link.url))putBook(link);if(old==null||!Objects.equals(old.shelfInfo,link.info))shelfInfo(link.id,link.info);}
      int rank=0;for(String id:order){Book old=before.get(id);if(old==null||old.shelfRank!=rank){Diagnostics.count("shelf.rank_write");followed(id,true,rank);}rank++;}
      d.setTransactionSuccessful();
    }finally{d.endTransaction();}
  }
  public boolean remoteReadEligible(String id){Book b=book(id);return b!=null&&b.followed&&!id.equals("demo")&&!HakoParser.storyId(HakoParser.storyUrl(b.url)).isEmpty();}
  public synchronized void queueIfAllRead(String id){if(remoteReadEligible(id)&&!chapters(id).isEmpty()&&unread(id)==0){if(!canSubmit(id))queueCaughtUp(id);}}

  public synchronized void establishReadBaseline(String id){
    android.content.SharedPreferences prefs=context.getSharedPreferences("settings",0);
    if(prefs.getBoolean("read_baseline_"+id,false)||queued(id))return;
    long shelf=prefs.getLong("lastShelfSync",0);if(shelf==0||System.currentTimeMillis()-shelf>120000)return;
    Book b=book(id);if(b==null)return;
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d+) chương mới").matcher(b.shelfInfo.trim());
    if(!m.find())return;
    int count;try{count=Integer.parseInt(m.group(1));}catch(Exception e){return;}
    List<Chapter> all=chapters(id);if(all.isEmpty()||count>all.size())return;
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(int i=0;i<all.size()-count;i++)readChapter(all.get(i).id);d.setTransactionSuccessful();}finally{d.endTransaction();}
    prefs.edit().putBoolean("read_baseline_"+id,true).commit();
  }
  public boolean favorite(Book b){return b.pinned||b.visits>=3;}
  public boolean syncTarget(Book b){return !downloadsSuppressed(b.id)&&!b.id.equals("demo")&&!b.dropped&&DownloadScope.eligible(b.visits>0,isKeepFull(b.id),context.getSharedPreferences("settings",0).getBoolean("auto_dl_"+b.id,false));}
  public boolean hasShelfUpdate(Book b){
    android.content.SharedPreferences p=context.getSharedPreferences("settings",0);
    String now=p.getString("shelf_key_"+b.id,""), old=p.getString("catalog_shelf_key_"+b.id,"");
    if(b.followed&&!now.isEmpty()&&!now.equals(old))return true;
    if(p.getBoolean("shelf_changed_"+b.id,false))return true;
    if(newArrivals(b.id)>0)for(Chapter ch:chapters(b.id))if(!ch.ready)return true;
    return b.followed&&chapters(b.id).isEmpty()&&shelfUnread(b)>0;
  }
  private int shelfUnread(Book b){try{return Integer.parseInt(b.shelfInfo.trim().split("\\s+")[0]);}catch(Exception e){return 0;}}
  public long shelfUpdatedAt(Book b){return context.getSharedPreferences("settings",0).getLong("shelf_updated_at_"+b.id,0);}
  public void sortDownloads(List<Book> books){
    Map<String,Integer> ranks=new HashMap<>();Map<String,Long> times=new HashMap<>();
    for(Book b:books){ranks.put(b.id,DownloadPriority.rank(hasShelfUpdate(b),b.visits,isCompleted(b.id)));times.put(b.id,shelfUpdatedAt(b));}
    books.sort((a,b)->{int ar=ranks.get(a.id),br=ranks.get(b.id),n=Integer.compare(ar,br);if(n!=0)return n;if(ar==0){n=Long.compare(times.get(b.id),times.get(a.id));if(n!=0)return n;}n=Integer.compare(b.visits,a.visits);if(n!=0)return n;n=Long.compare(b.stamp,a.stamp);if(n!=0)return n;n=Integer.compare(a.shelfRank,b.shelfRank);return n!=0?n:a.id.compareTo(b.id);});
  }
  public int compareDownloads(Book a,Book b){
    int ar=DownloadPriority.rank(hasShelfUpdate(a),a.visits,isCompleted(a.id)),br=DownloadPriority.rank(hasShelfUpdate(b),b.visits,isCompleted(b.id));
    int n=Integer.compare(ar,br);if(n!=0)return n;
    if(ar==0){n=Long.compare(shelfUpdatedAt(b),shelfUpdatedAt(a));if(n!=0)return n;}
    n=Integer.compare(b.visits,a.visits);if(n!=0)return n;
    n=Long.compare(b.stamp,a.stamp);if(n!=0)return n;
    n=Integer.compare(a.shelfRank,b.shelfRank);return n!=0?n:a.id.compareTo(b.id);
  }
  public int compareShelf(Book a,Book b){int n=Integer.compare(a.shelfRank,b.shelfRank);return n!=0?n:a.id.compareTo(b.id);}
  public boolean completedOffline(Book b){if(!isCompleted(b.id))return false;android.content.SharedPreferences p=context.getSharedPreferences("settings",0);String current=p.getString("shelf_key_"+b.id,""),known=p.getString("catalog_shelf_key_"+b.id,"");if(p.getBoolean("shelf_changed_"+b.id,false)||(!current.isEmpty()&&!current.equals(known)))return false;List<Chapter> all=chapters(b.id);if(all.isEmpty())return false;for(Chapter ch:all)if(!illustration(ch.id)&&!ch.ready)return false;return true;}
  private Set<String> readIds(String book){Set<String> ids=new HashSet<>();try(Cursor c=query("SELECT c.id FROM chapters c JOIN read_chapters r ON r.id=c.id WHERE c.book=?",new String[]{book})){while(c.moveToNext())ids.add(c.getString(0));}return ids;}
  public Set<String> readingIds(String book,List<Chapter> chapters){Set<String> ids=readIds(book);String tip=completedTip(book);int end=-1;for(Chapter ch:chapters)if(ch.id.equals(tip))end=ch.ord;for(Chapter ch:chapters)if(ch.ord<=end&&!ignoredForReading(ch))ids.add(ch.id);return ids;}
  public boolean ignoredForReading(Chapter ch){int kind=context.getSharedPreferences("settings",0).getInt("chapter_kind_"+ch.id,0);return kind==2||kind==3||(kind==0&&IllustrationPolicy.hint(ch.title));}
  public boolean ignoredForReading(String id){Chapter ch=chapterById(id);return ch!=null&&ignoredForReading(ch);}
  private synchronized Chapter chapterById(String id){Chapter known=cachedId(id);if(known!=null){Diagnostics.count("db.chapter_memory_hit");return copyChapter(known);}try(Cursor c=query("SELECT book FROM chapters WHERE id=?",new String[]{id})){return c.moveToFirst()?chapter(c.getString(0),id):null;}}
  public String completedTip(String book){
    String tip=context.getSharedPreferences("settings",0).getString("read_tip_"+book,"");
    if(!tip.isEmpty())return tip;
    Set<String> read=readIds(book);List<Chapter> all=chapters(book);for(int i=all.size()-1;i>=0;i--){Chapter ch=all.get(i);if(read.contains(ch.id)&&!ignoredForReading(ch))return ch.id;}
    return "";
  }
  public synchronized void completeThrough(String book,String chapter){OperationTrace.Span trace=OperationTrace.begin("read.complete");try{displayChanged(book,true);
    Chapter ch=chapter(book,chapter);if(ch==null||!book.equals(ch.book)||ignoredForReading(ch))return;
    Chapter old=chapter(book,completedTip(book));
    if((old==null||ch.ord>=old.ord)&&!chapter.equals(context.getSharedPreferences("settings",0).getString("read_tip_"+book,""))){context.getSharedPreferences("settings",0).edit().putString("read_tip_"+book,chapter).commit();readingRevision.incrementAndGet();}
    readChapter(chapter);queueIfAllRead(book);
  }finally{OperationTrace.end(trace,"closed",0);}}
  public int unread(String id){List<Chapter> all=chapters(id);Chapter tip=chapter(id,completedTip(id));int end=tip==null?-1:tip.ord;Set<String> read=readIds(id);int count=0;for(Chapter ch:all)if(!ignoredForReading(ch)&&ch.ord>end&&!read.contains(ch.id))count++;return count;}
  public static final class ReadCounts {
    public int textUnread,illustrationsUnseen,pendingReview;
    public String label(){return textUnread+" chương chưa đọc";}
  }
  /** Display counts are separate from conservative remote read-all eligibility. */
  public ReadCounts readCounts(List<Chapter> chapters){
    ReadCounts counts=new ReadCounts();if(chapters.isEmpty())return counts;
    String book=chapters.get(0).book;Chapter tip=chapter(book,completedTip(book));int end=tip==null?-1:tip.ord;Set<String> read=readIds(book);
    for(Chapter ch:chapters){
      if(!ignoredForReading(ch)&&ch.ord>end&&!read.contains(ch.id))counts.textUnread++;
    }
    return counts;
  }
  public boolean illustration(String id){return context.getSharedPreferences("settings",0).getInt("chapter_kind_"+id,0)==2;}
  public boolean illustrationHint(Chapter ch){int kind=context.getSharedPreferences("settings",0).getInt("chapter_kind_"+ch.id,0);return kind==2||kind==3||(kind==0&&IllustrationPolicy.hint(ch.title));}
  private void rememberTitle(String id,String title){String previous=chapterTitles.put(id,title==null?"":title);if(!java.util.Objects.equals(previous,title))validatedFiles.remove(id);}
  public void classify(String id,String html){int kind=IllustrationPolicy.kind(chapterTitles.get(id),html);android.content.SharedPreferences p=context.getSharedPreferences("settings",0);if(p.getInt("chapter_kind_"+id,0)!=kind){p.edit().putInt("chapter_kind_"+id,kind).apply();readingRevision.incrementAndGet();Chapter owner=chapterById(id);if(owner!=null)displayChanged(owner.book,true);}}
  public boolean cachedIllustration(String id){android.content.SharedPreferences p=context.getSharedPreferences("settings",0);return illustration(id)&&html(id).isFile()&&fileStamp(html(id)).equals(p.getString("illustration_cached_"+id,""));}
  public int unreadIllustrations(String book){return 0;}
  public int missingImages(String id){
    try{String stamp=contentStamp(id,html(id));Integer cached=imageCounts.get(id);if(cached!=null&&stamp.equals(imageCountStamps.get(id)))return cached;int n=0;for(String src:HakoParser.offlineContent(read(html(id))).images){String name=HakoParser.offlineImageName(id,src);if(name.isEmpty()||!imageFileReadable(new File(dir(id),name)))n++;}if(stamp.equals(contentStamp(id,html(id)))){imageCounts.put(id,n);imageCountStamps.put(id,stamp);}return n;}catch(Exception e){return 0;}
  }
  public boolean autoIllustration(Chapter ch){android.content.SharedPreferences p=context.getSharedPreferences("settings",0);if(illustration(ch.id)&&fileStamp(html(ch.id)).equals(p.getString("illustration_cached_"+ch.id,""))){UpdateReport.log(context,ch.title+": dùng khung minh họa đã lưu · ảnh thiếu chỉ tải lại khi yêu cầu");return false;}return IllustrationPolicy.shouldAttempt(illustrationHint(ch),p.getBoolean("illustration_attempted_"+ch.id,false),false);}

  public synchronized void resetUnreadForFull(String book){getWritableDatabase().delete("read_chapters","id IN (SELECT id FROM chapters WHERE book=?)",new String[]{book});context.getSharedPreferences("settings",0).edit().remove("read_tip_"+book).remove("reading_text_"+book).remove("read_queue_tip_"+book).remove("read_no_button_tip_"+book).commit();clearQueue(book);ContentValues v=new ContentValues();v.put("current","");v.put("pos",0);v.put("fraction",0);getWritableDatabase().update("books",v,"id=?",new String[]{book});readingRevision.incrementAndGet();displayChanged(book,true);}
  public boolean queued(String id){return context.getSharedPreferences("read_queue",0).contains(id);}
  public synchronized void queueCaughtUp(String id){queueCaughtUp(id,false);}
  public synchronized void queueCaughtUp(String id,boolean acceptIllustrations){
    if(!remoteReadEligible(id))return;
    if(chapters(id).isEmpty()||unread(id)>0)throw new IllegalStateException("Chỉ đồng bộ đọc hết sau khi đã đọc hết các chương offline");
    if(completedTip(id).equals(context.getSharedPreferences("settings",0).getString("read_no_button_tip_"+id,"!")))return;
    context.getSharedPreferences("settings",0).edit().putBoolean("read_baseline_"+id,true).commit();
    org.json.JSONArray ids=new org.json.JSONArray();
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(Chapter c:chapters(id)){ids.put(c.id);}d.setTransactionSuccessful();}finally{d.endTransaction();}
    if(ids.length()==0)throw new IllegalStateException("Chưa có mục lục để xác nhận đã đọc");
    String tip=completedTip(id);if(tip.isEmpty())throw new IllegalStateException("Chưa có mốc chương đã đọc hết");
    String payload=ids.toString();
    android.content.SharedPreferences queues=context.getSharedPreferences("read_queue",0);
    if(payload.equals(queues.getString(id,""))&&tip.equals(queuedTip(id)))return;
    context.getSharedPreferences("settings",0).edit().putString("read_queue_tip_"+id,tip).putString("read_queue_generation_"+id,UUID.randomUUID().toString()).commit();
    if(!queues.edit().putString(id,payload).commit())throw new IllegalStateException("Không lưu được hàng chờ");
  }
  public static final class ReadRequest {
    public final String id,payload,tip,generation;
    ReadRequest(String id,String payload,String tip,String generation){this.id=id;this.payload=payload;this.tip=tip;this.generation=generation;}
  }
  public synchronized ReadRequest readRequest(String id){if(!queued(id))return null;return new ReadRequest(id,context.getSharedPreferences("read_queue",0).getString(id,""),queuedTip(id),context.getSharedPreferences("settings",0).getString("read_queue_generation_"+id,"legacy"));}
  public synchronized Map<String,ReadRequest> readRequests(){Map<String,ReadRequest> all=new HashMap<>();for(String id:queuedBooks())all.put(id,readRequest(id));return all;}
  public synchronized boolean sameReadRequest(ReadRequest request){if(request==null)return false;ReadRequest now=readRequest(request.id);return now!=null&&request.payload.equals(now.payload)&&request.tip.equals(now.tip)&&request.generation.equals(now.generation);}
  public synchronized boolean retireReadRequest(ReadRequest request,boolean noButton){if(!sameReadRequest(request))return false;if(noButton)context.getSharedPreferences("settings",0).edit().putString("read_no_button_tip_"+request.id,request.tip).commit();clearQueue(request.id);return true;}
  public synchronized void noReadButton(String id){retireReadRequest(readRequest(id),true);}
  public synchronized void readButtonReturned(String id){
    android.content.SharedPreferences p=context.getSharedPreferences("settings",0);
    if(!p.contains("read_no_button_tip_"+id))return;
    p.edit().remove("read_no_button_tip_"+id).commit();
    if(!completedTip(id).isEmpty()&&unread(id)==0)queueIfAllRead(id);
  }
  public Set<String> queuedBooks(){return new HashSet<>(context.getSharedPreferences("read_queue",0).getAll().keySet());}
  private String queuedTip(String book){
    android.content.SharedPreferences prefs=context.getSharedPreferences("settings",0);String tip=prefs.getString("read_queue_tip_"+book,"");if(!tip.isEmpty())return tip;
    try{org.json.JSONArray ids=new org.json.JSONArray(context.getSharedPreferences("read_queue",0).getString(book,"[]"));for(int i=0;i<ids.length();i++){Chapter ch=chapter(book,ids.getString(i));if(ch!=null&&!ignoredForReading(ch))tip=ch.id;}if(!tip.isEmpty())prefs.edit().putString("read_queue_tip_"+book,tip).commit();}catch(Exception ignored){}
    return tip;
  }
  public synchronized boolean canSubmit(String id){
    if(!queued(id)||chapters(id).isEmpty()||unread(id)>0)return false;
    return completedTip(id).equals(queuedTip(id));
  }
  public void extendQueueIfCaughtUp(String id){if(queued(id)&&!canSubmit(id)&&unread(id)==0)queueCaughtUp(id);}
  public void clearQueue(String id){context.getSharedPreferences("read_queue",0).edit().remove(id).commit();}
  public int totalOfflinePercent(){int total=0,done=0;boolean unknown=false;for(Book b:books())if(syncTarget(b)){BookProgress p=progress(b);if(p.totalChs==0)unknown=true;total+=p.newChsTotal;done+=p.newChsDownloaded;}return unknown?0:total==0?100:Math.min(done==total?100:99,Math.round(done*100f/total));}
  public static class BookProgress {
    public final int totalChs;
    public final int oldChs;
    public final int newChsTotal;
    public final int newChsDownloaded;
    public final int percent;

    public BookProgress(int total, int old, int newTotal, int newDownloaded, int pct) {
      this.totalChs = total;
      this.oldChs = old;
      this.newChsTotal = newTotal;
      this.newChsDownloaded = newDownloaded;
      this.percent = pct;
    }
  }

  private List<String> chapterIds(List<Chapter> chapters){List<String> ids=new ArrayList<>();for(Chapter ch:chapters)ids.add(ch.id);return ids;}
  private Set<String> illustrationIds(List<Chapter> chapters){Set<String> ids=new HashSet<>();for(Chapter ch:chapters)if(ignoredForReading(ch))ids.add(ch.id);return ids;}
  public int resumeIndex(Book snapshot,List<Chapter> chapters){
    Book latest=book(snapshot.id);Book b=latest==null?snapshot:latest;
    String current=b.current;
    if(illustration(current))current=context.getSharedPreferences("settings",0).getString("reading_text_"+b.id,"");
    Set<String> read=readingIds(b.id,chapters);
    return ReadingNavigation.resume(chapterIds(chapters),current,read,illustrationIds(chapters));
  }
  public int adjacentIndex(List<Chapter> chapters,String current,int direction){
    List<String> ids=chapterIds(chapters);int index=ids.indexOf(current);
    return index<0?-1:ReadingNavigation.adjacent(ids,index,direction,illustrationIds(chapters));
  }
  public int readingStart(Book b,List<Chapter> chapters){int index=resumeIndex(b,chapters);return index<0?0:chapters.get(index).ord;}
  /** A reader opening never backfills old chapters, even for a retained-full book. */
  public static final class BatchPlan {public final List<Chapter> chapters;public final String key;BatchPlan(List<Chapter> c,String k){chapters=c;key=k;}}
  public synchronized BatchPlan readingBatch(Book b,List<Chapter> all){
    List<Chapter> text=new ArrayList<>();for(Chapter ch:all)if(!ignoredForReading(ch)&&!isHidden(b.id,ch.id))text.add(ch);
    int ord=readingStart(b,all),current=0;while(current+1<text.size()&&text.get(current).ord<ord)current++;
    android.content.SharedPreferences prefs=context.getSharedPreferences("settings",0);String prefix="batch_"+b.id+"_",anchor=prefs.getString(prefix+"anchor","");int saved=-1;for(int i=0;i<text.size();i++)if(text.get(i).id.equals(anchor)){saved=i;break;}
    if(anchor.equals("end")&&text.size()==prefs.getInt(prefix+"count",-1))saved=text.size();
    int start=BatchWindow.start(saved,current,text.size()),end=BatchWindow.end(start,text.size());List<Chapter> window=new ArrayList<>(text.subList(Math.min(start,text.size()),end));StringBuilder key=new StringBuilder();for(Chapter ch:window)key.append(ch.id).append('|');String signature=key.toString();
    if(!signature.equals(prefs.getString(prefix+"key",""))){prefs.edit().putString(prefix+"key",signature).putString(prefix+"anchor",start<text.size()?text.get(start).id:"end").putInt(prefix+"count",text.size()).putBoolean(prefix+"done",false).apply();}
    return new BatchPlan(window,signature);
  }
  public boolean batchDone(String book,BatchPlan plan){android.content.SharedPreferences p=context.getSharedPreferences("settings",0);return p.getBoolean("batch_"+book+"_done",false)&&plan.key.equals(p.getString("batch_"+book+"_key",""));}
  public void finishBatch(String book,BatchPlan plan){android.content.SharedPreferences p=context.getSharedPreferences("settings",0);if(plan.key.equals(p.getString("batch_"+book+"_key","")))p.edit().putBoolean("batch_"+book+"_done",true).apply();}
  public boolean needsReadingDownload(String book){if(downloadsSuppressed(book))return false;Book b=book(book);if(b==null||b.dropped)return false;BatchPlan plan=readingBatch(b,chapters(book));if(plan.chapters.isEmpty()||batchDone(book,plan))return false;if(Repository.busy.get()&&context.getSharedPreferences("download_resume",0).contains(book))return false;for(Chapter ch:plan.chapters)if(!readable(ch.id))return true;finishBatch(book,plan);return false;}
  public BookProgress progress(Book b) {
    List<Chapter> chs = chapters(b.id);
    int total = chs.size();
    if (total == 0) return new BookProgress(0, 0, 0, 0, 0);

    int currentOrd = readingStart(b,chs);
    boolean full=isKeepFull(b.id)||isCompleted(b.id);
    boolean toEnd=FetchPolicy.toEnd(b.followed,favorite(b),b.visits>0,full);
    int oldChs = full?0:Math.max(0,currentOrd-FetchPolicy.BEHIND);
    int newChsTotal=0,newChsDownloaded=0;
    for(Chapter ch:chs){if(!FetchPolicy.inDownloadRange(ch.ord,currentOrd,toEnd,full))continue;boolean saved=readable(ch.id);if(!illustration(ch.id)){
      newChsTotal++;
      if(saved)newChsDownloaded++;
    }}

    int pct = newChsTotal==0?100:Math.min(newChsDownloaded==newChsTotal?100:99, Math.max(0, Math.round(newChsDownloaded * 100f / newChsTotal)));
    return new BookProgress(total, oldChs, newChsTotal, newChsDownloaded, pct);
  }

  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT,url TEXT,current TEXT DEFAULT '',pos"
            + " INTEGER DEFAULT 0,fraction REAL DEFAULT 0,stamp INTEGER DEFAULT 0,followed INTEGER"
            + " DEFAULT 0)");
    d.execSQL(
        "CREATE TABLE chapters(id TEXT PRIMARY KEY,book TEXT,title TEXT,url TEXT,ord INTEGER,ready"
            + " INTEGER DEFAULT 0,error TEXT DEFAULT '')");
    d.execSQL("CREATE INDEX chapter_book ON chapters(book,ord)");
    d.execSQL("ALTER TABLE books ADD COLUMN dropped INTEGER DEFAULT 0");
    d.execSQL("ALTER TABLE books ADD COLUMN shelf_rank INTEGER DEFAULT 999999");
    upgrade3(d);
  }

  public void onUpgrade(SQLiteDatabase d, int a, int b) {if(a<2){d.execSQL("ALTER TABLE books ADD COLUMN dropped INTEGER DEFAULT 0");d.execSQL("ALTER TABLE books ADD COLUMN shelf_rank INTEGER DEFAULT 999999");d.execSQL("UPDATE books SET followed=0");d.execSQL("UPDATE chapters SET ready=0");}if(a<3){upgrade3(d);d.execSQL("UPDATE books SET fraction=0");}}

  private void upgrade3(SQLiteDatabase d){d.execSQL("ALTER TABLE books ADD COLUMN visits INTEGER DEFAULT 0");d.execSQL("ALTER TABLE books ADD COLUMN pinned INTEGER DEFAULT 0");d.execSQL("ALTER TABLE books ADD COLUMN shelf_info TEXT DEFAULT ''");d.execSQL("CREATE TABLE read_chapters(id TEXT PRIMARY KEY,stamp INTEGER)");}
  public synchronized void replaceShelf(List<HakoParser.Link> links){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{Set<String> seen=new HashSet<>();for(HakoParser.Link link:links)seen.add(link.id);for(Book old:shelfBooks())if(!seen.contains(old.id))followed(old.id,false,999999);mergeShelf(links,0);d.setTransactionSuccessful();}finally{d.endTransaction();}}
  public synchronized void importHistory(String id,String chapter,long stamp){displayChanged(id,true);Book b=book(id);if(b==null||stamp<=b.stamp)return;ContentValues v=new ContentValues();v.put("current",chapter);v.put("stamp",stamp);v.put("pos",0);v.put("fraction",0);getWritableDatabase().update("books",v,"id=?",new String[]{id});readingRevision.incrementAndGet();}
  public synchronized void selectChapter(String id,String chapter){displayChanged(id,true);Book b=book(id);if(b!=null&&!chapter.equals(b.current)){ContentValues v=new ContentValues();v.put("current",chapter);v.put("pos",0);v.put("fraction",0);getWritableDatabase().update("books",v,"id=?",new String[]{id});readingRevision.incrementAndGet();}}
  public synchronized void visited(String id){getWritableDatabase().execSQL("UPDATE books SET visits=visits+1 WHERE id=?",new Object[]{id});bookMetadataChanged(id);}
  public synchronized void pin(String id,boolean value){Book old=book(id);if(old==null||old.pinned==value)return;getWritableDatabase().execSQL("UPDATE books SET pinned=? WHERE id=?",new Object[]{value?1:0,id});bookMetadataChanged(id);}
  public synchronized void readChapter(String id){Chapter owner=chapterById(id);if(owner!=null)displayChanged(owner.book,true);if(ignoredForReading(id)||wasRead(id))return;ContentValues v=new ContentValues();v.put("id",id);v.put("stamp",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("read_chapters",null,v,SQLiteDatabase.CONFLICT_REPLACE);readingRevision.incrementAndGet();}
  public boolean wasRead(String id){
    Chapter ch=chapterById(id);if(ch==null||ignoredForReading(ch))return false;
    Chapter tip=chapter(ch.book,completedTip(ch.book));if(tip!=null&&ch.ord<=tip.ord)return true;
    try(Cursor c=query("SELECT id FROM read_chapters WHERE id=?",new String[]{id})){return c.moveToFirst();}
  }
  public synchronized void shelfInfo(String id,String info){Book old=book(id);if(old!=null&&Objects.equals(old.shelfInfo,info))return;ContentValues v=new ContentValues();v.put("shelf_info",info);getWritableDatabase().update("books",v,"id=?",new String[]{id});bookMetadataChanged(id);}
  public static class Book {
    public String id, title, url, current;
    public int pos;
    public float fraction;
    public long stamp;
    public boolean followed,dropped;public int shelfRank,visits;public boolean pinned;public String shelfInfo;
  }

  public static class Chapter {
    public String id, book, title, url, error;
    public int ord;
    public boolean ready;
  }

  public synchronized void putBook(HakoParser.Link l) {
    Book old=book(l.id);if(old!=null&&Objects.equals(old.title,l.title)&&Objects.equals(old.url,l.url))return;
    ContentValues v = new ContentValues();
    v.put("id", l.id);
    v.put("title", l.title);
    v.put("url", l.url);
    getWritableDatabase().insertWithOnConflict("books", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    ContentValues update = new ContentValues();
    update.put("title", l.title);
    update.put("url", l.url);
    getWritableDatabase().update("books", update, "id=?", new String[] {l.id});bookMetadataChanged(l.id);
  }

  public synchronized List<Book> books() {
    List<Book> a = new ArrayList<>();
    try (Cursor c =
        query(
                "SELECT id,title,url,current,pos,fraction,stamp,followed,dropped,shelf_rank,visits,pinned,shelf_info FROM books ORDER BY stamp"
                    + " DESC,title",
                null)) {
      while (c.moveToNext()) {
        Book b = new Book();
        b.id = c.getString(0);
        b.title = c.getString(1);
        b.url = c.getString(2);
        b.current = c.getString(3);
        b.pos = c.getInt(4);
        b.fraction = c.getFloat(5);
        b.stamp = c.getLong(6);
        b.followed=c.getInt(7)!=0;b.dropped=c.getInt(8)!=0;b.shelfRank=c.getInt(9);b.visits=c.getInt(10);b.pinned=c.getInt(11)!=0;b.shelfInfo=c.getString(12);
        a.add(b);
      }
    }
    return a;
  }

  public synchronized Book book(String id) {
    try (Cursor c = query("SELECT id,title,url,current,pos,fraction,stamp,followed,dropped,shelf_rank,visits,pinned,shelf_info FROM books WHERE id=?", new String[]{id})) {
      if (c.moveToNext()) {
        Book b = new Book();
        b.id = c.getString(0);
        b.title = c.getString(1);
        b.url = c.getString(2);
        b.current = c.getString(3);
        b.pos = c.getInt(4);
        b.fraction = c.getFloat(5);
        b.stamp = c.getLong(6);
        b.followed = c.getInt(7) != 0;
        b.dropped = c.getInt(8) != 0;
        b.shelfRank = c.getInt(9);
        b.visits = c.getInt(10);
        b.pinned = c.getInt(11) != 0;
        b.shelfInfo = c.getString(12);
        return b;
      }
    } catch (Exception ignored) {}
    return null;
  }

  public synchronized void catalog(String book,List<HakoParser.Link> links){
    List<Chapter> previous=chapters(book);
    List<String> oldIds=new ArrayList<>(),webIds=new ArrayList<>();
    Map<String,HakoParser.Link> web=new HashMap<>();Map<String,Chapter> prior=new HashMap<>();for(Chapter ch:previous)prior.put(ch.id,ch);
    for(Chapter ch:previous)oldIds.add(ch.id);
    for(HakoParser.Link link:links){
      if(!book.equals("demo")&&(!book.equals(HakoParser.storyId(HakoParser.storyUrl(link.url)))||!link.id.equals(HakoParser.chapterId(link.url))))throw new IllegalArgumentException("Mục lục sai truyện");
      webIds.add(link.id);web.put(link.id,link);rememberTitle(link.id,link.title);
    }
    List<String> order=CatalogPolicy.merge(oldIds,webIds);
    Set<String> old=new HashSet<>(oldIds),hidden=new HashSet<>(oldIds);
    hidden.removeAll(webIds);
    int rowsChanged=0;SQLiteDatabase d=getWritableDatabase();
    d.beginTransaction();
    try{
      for(int ord=0;ord<order.size();ord++){
        String id=order.get(ord);
        HakoParser.Link link=web.get(id);Chapter before=prior.get(id);if(before!=null&&before.ord==ord&&(link==null||(Objects.equals(before.title,link.title)&&Objects.equals(before.url,link.url))))continue;rowsChanged++;Diagnostics.count("db.catalog_row_write");
        ContentValues v=new ContentValues();v.put("ord",ord);
        if(link!=null){
          v.put("id",id);v.put("book",book);v.put("title",link.title);v.put("url",link.url);
          d.insertWithOnConflict("chapters",null,v,SQLiteDatabase.CONFLICT_IGNORE);
          v.remove("id");
        }
        d.update("chapters",v,"id=?",new String[]{id});
      }
      d.setTransactionSuccessful();
    }finally{d.endTransaction();}
    if(rowsChanged>0){List<Chapter> removed=chapterCache.remove(book);if(removed!=null)for(Chapter ch:removed)chapterLookup.remove(ch.id);}
    org.json.JSONArray server=new org.json.JSONArray(webIds);boolean catalogChanged=rowsChanged>0||!server.toString().equals(context.getSharedPreferences("settings",0).getString("server_catalog_"+book,""));
    android.content.SharedPreferences.Editor edit=context.getSharedPreferences("settings",0).edit()
      .putStringSet("hidden_chapters_"+book,hidden).putString("server_catalog_"+book,server.toString());
    int added=0;for(String id:webIds)if(!old.contains(id))added++;
    if(!previous.isEmpty()){Set<String> arrivals=new HashSet<>(webIds);arrivals.removeAll(old);edit.putInt("new_arrivals_"+book,added).putStringSet("new_arrival_ids_"+book,arrivals);}
    edit.commit();
    if(catalogChanged){readingRevision.incrementAndGet();displayChanged(book,true);}
    if(!hidden.isEmpty())UpdateReport.checkpoint(context,"Mục lục "+book+": giữ "+hidden.size()+" chương cũ không còn xuất hiện trên web; không xóa cache hoặc tiến độ");
  }
  public boolean isHidden(String book,String chapter){
    return context.getSharedPreferences("settings",0).getStringSet("hidden_chapters_"+book,Collections.emptySet()).contains(chapter);
  }
  public boolean canSubmitServer(String book){
    android.content.SharedPreferences prefs=context.getSharedPreferences("settings",0);
    try{
      org.json.JSONArray serverJson=new org.json.JSONArray(prefs.getString("server_catalog_"+book,"[]"));
      List<String> server=new ArrayList<>();Set<String> seen=new HashSet<>();Map<String,Chapter> known=new HashMap<>();for(Chapter ch:chapters(book))known.put(ch.id,ch);
      for(int i=0;i<serverJson.length();i++){String id=serverJson.getString(i);if(id.isEmpty()||!seen.add(id))return false;Chapter ch=known.get(id);if(ch==null||!book.equals(ch.book))return false;if(!ignoredForReading(ch))server.add(id);}
      return ReadPolicy.canSubmitAtTip(server,queuedTip(book),completedTip(book),!prefs.getBoolean("catalog_failed_"+book,true));
    }catch(Exception e){return false;}
  }

  public synchronized List<Chapter> chapters(String book) {
    List<Chapter> known=chapterCache.get(book);if(known!=null){Diagnostics.count("db.catalog_memory_hit");return new ArrayList<>(known);}
    List<Chapter> a = new ArrayList<>();
    try (Cursor c =
        query(
                "SELECT id,book,title,url,ord,ready,error FROM chapters WHERE book=? ORDER BY ord",
                new String[] {book})) {
      while (c.moveToNext()) {
        Chapter x = new Chapter();
        x.id = c.getString(0);
        x.book = c.getString(1);
        x.title = c.getString(2);rememberTitle(x.id,x.title);
        x.url = c.getString(3);
        x.ord = c.getInt(4);
        x.ready = c.getInt(5)==1;
        x.error = c.getString(6);
        a.add(x);
      }
    }
    cacheChapters(book,a);return new ArrayList<>(a);
  }

  public synchronized Chapter chapter(String book, String id) {
    Chapter known=cachedId(id);if(known!=null){Diagnostics.count("db.chapter_memory_hit");return copyChapter(known);}
    try (Cursor c = query("SELECT id,book,title,url,ord,ready,error FROM chapters WHERE id=?", new String[]{id})) {
      if (c.moveToNext()) {
        Chapter x = new Chapter();
        x.id = c.getString(0); x.book = c.getString(1); x.title = c.getString(2);rememberTitle(x.id,x.title);
        x.url = c.getString(3); x.ord = c.getInt(4);
        x.ready = c.getInt(5)==1;
        x.error = c.getString(6);
        return x;
      }
    } catch (Exception ignored) {}
    return null;
  }

  public static final class Selection {public final Book book;public final Chapter chapter;Selection(Book b,Chapter c){book=b;chapter=c;}}
  public synchronized Selection selectForReading(String id,String requested){
    Book b=book(id);if(b==null)return null;Chapter target=null;
    if(requested==null||requested.isEmpty()){
      target=chapter(id,b.current);if(target!=null&&ignoredForReading(target))target=chapter(id,context.getSharedPreferences("settings",0).getString("reading_text_"+id,""));
      if(target!=null&&ignoredForReading(target))target=null;
      if(target==null){List<Chapter> all=chapters(id);if(all.isEmpty())return null;int index=ReadingNavigation.resume(chapterIds(all),b.current,readingIds(id,all),illustrationIds(all));target=all.get(index<0?0:index);}
    }else target=chapter(id,requested);
    if(target==null)return null;
    if(!ignoredForReading(target)&&!target.id.equals(b.current)){ContentValues values=new ContentValues();values.put("current",target.id);values.put("pos",0);values.put("fraction",0);getWritableDatabase().update("books",values,"id=?",new String[]{id});b.current=target.id;b.pos=0;b.fraction=0;readingRevision.incrementAndGet();displayChanged(id,true);}
    return new Selection(b,target);
  }
  public synchronized void position(String book, String chapter, int pos, float fraction) {OperationTrace.Span trace=OperationTrace.begin("read.persist");try{
    if(ignoredForReading(chapter))return;
    Book previous=book(book);boolean changed=previous!=null&&!chapter.equals(previous.current);
    int savedPos=Math.max(0,pos);float savedFraction=Math.max(0,Math.min(.999999f,fraction));if(previous!=null&&!changed&&previous.pos==savedPos&&Float.compare(previous.fraction,savedFraction)==0)return;
    ContentValues v = new ContentValues();
    v.put("current", chapter);
    v.put("pos", Math.max(0, pos));
    v.put("fraction", Math.max(0, Math.min(.999999f, fraction)));
    v.put("stamp", System.currentTimeMillis());
    getWritableDatabase().update("books", v, "id=?", new String[] {book});
    if(!illustration(chapter))context.getSharedPreferences("settings",0).edit().putString("reading_text_"+book,chapter).apply();
    if(changed){readingRevision.incrementAndGet();displayChanged(book,true);}else {Book updated=previous;if(updated!=null){updated.current=chapter;updated.pos=savedPos;updated.fraction=savedFraction;updated.stamp=v.getAsLong("stamp");LibraryState.positionChanged(updated);TocState.positionChanged(updated);context.sendBroadcast(new Intent(Repository.EVENT).setPackage(context.getPackageName()).putExtra("reading_position",true));}}
  }finally{OperationTrace.end(trace,"closed",0);}}

  public synchronized void state(String id, boolean ready, String error) {
    Chapter owner=chapterById(id);if(owner!=null&&owner.ready==ready&&java.util.Objects.equals(owner.error,error))return;
    ContentValues v = new ContentValues();
    v.put("ready", ready ? 1 : 0);
    v.put("error", error);
    getWritableDatabase().update("chapters", v, "id=?", new String[] {id});updateCachedState(owner,ready,error);if(owner!=null)displayChanged(owner.book,true);
  }

  public synchronized void resetDerived(){chapterCache.clear();chapterLookup.clear();chapterTitles.clear();validatedFiles.clear();validatedImages.clear();imageCounts.clear();imageCountStamps.clear();validationLoaded=false;TocState.resetForImport();}
  public boolean downloadsSuppressed(String book){return context.getSharedPreferences("settings",0).getBoolean("cache_deleted_"+book,false);}
  public void allowDownloads(String book){context.getSharedPreferences("settings",0).edit().remove("cache_deleted_"+book).apply();}
  public void clearDownloaded(String book)throws Exception{OperationTrace.Span trace=OperationTrace.begin("book.clear_files");Repository.contentLock.writeLock().lock();try{
    if(book==null||!book.matches("[a-z0-9-]+"))throw new IllegalArgumentException();
    synchronized(Repository.class){List<Chapter> all;
      synchronized(this){context.getSharedPreferences("settings",0).edit().putBoolean("cache_deleted_"+book,true).commit();Repository.discardQueued(book);context.getSharedPreferences("settings",0).edit().putBoolean("batch_"+book+"_done",false).apply();all=chapters(book);}
      // File deletion can be slow; do not hold the metadata monitor needed by UI.
      for(Chapter ch:all){delete(dir(ch.id));validatedFiles.remove(ch.id);validatedImages.remove(ch.id);imageCounts.remove(ch.id);imageCountStamps.remove(ch.id);}
      synchronized(this){ContentValues values=new ContentValues();values.put("ready",0);values.put("error","");getWritableDatabase().update("chapters",values,"book=?",new String[]{book});List<Chapter> next=new ArrayList<>();for(Chapter ch:all){Chapter changed=copyChapter(ch);changed.ready=false;changed.error="";next.add(changed);}cacheChapters(book,next);displayChanged(book,true);}persistValidation();
    }
  }finally{Repository.contentLock.writeLock().unlock();OperationTrace.end(trace,"complete",0);}}
  public File dir(String id) {
    if (!id.matches("[a-z0-9-]+")) throw new IllegalArgumentException();
    return new File(cache, id);
  }

  public File html(String id) {
    return new File(dir(id), "content.html");
  }

  public static void write(File f, String s) throws IOException {
    f.getParentFile().mkdirs();
    File tmp = new File(f.getPath() + ".tmp");
    try (FileOutputStream out = new FileOutputStream(tmp)) {
      out.write(s.getBytes("UTF-8"));
      out.getFD().sync();
    }
    if (!tmp.renameTo(f)) throw new IOException("Không lưu được nội dung");
  }

  public static String read(File f) throws IOException {
    if("content.html".equals(f.getName()))Diagnostics.count("content.read");
    try (FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      return out.toString("UTF-8");
    }
  }

  public synchronized void prune(Book b) {
    if (b == null) return;
    if (isKeepFull(b.id)) return; // Never prune books selected for full offline
    Chapter current = chapter(b.id, b.current);
    if (current == null) return;
    for (Chapter c : chapters(b.id))
      // Preserve all future/unread chapters; only prune read chapters far in the past
      if (!isHidden(b.id,c.id) && c.ord < current.ord - FetchPolicy.BEHIND && wasRead(c.id)) {
        delete(dir(c.id));
        state(c.id, false, "");
      }
  }

  public synchronized void followed(String id,boolean value,int rank){Book old=book(id);if(old!=null&&old.followed==value&&old.shelfRank==rank)return;ContentValues v=new ContentValues();v.put("followed",value?1:0);v.put("shelf_rank",rank);getWritableDatabase().update("books",v,"id=?",new String[]{id});bookMetadataChanged(id);if(value&&(old==null||!old.followed)&&!completedTip(id).isEmpty())queueIfAllRead(id);}
  public synchronized void dropped(String id,boolean value){Book old=book(id);if(old!=null&&old.dropped==value)return;ContentValues v=new ContentValues();v.put("dropped",value?1:0);getWritableDatabase().update("books",v,"id=?",new String[]{id});bookMetadataChanged(id);}
  // List queries must never read/parse chapter HTML while holding the Store monitor.
  // Content integrity is verified by the worker before queueing and before reuse.
  private boolean cachedChapter(String id,boolean markedReady){File f=html(id);if(!f.isFile()||f.length()==0)return false;String known=validatedFiles.get(id);return contentStamp(id,f).equals(known)||(known==null&&markedReady);}
  private final java.util.concurrent.ConcurrentHashMap<String,Integer> imageCounts=new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.ConcurrentHashMap<String,String> imageCountStamps=new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicBoolean validationSaved=new java.util.concurrent.atomic.AtomicBoolean();
  private boolean validationLoaded;private final Object validationLock=new Object();
  private final java.util.concurrent.ScheduledExecutorService validationWriter=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
  private final java.util.concurrent.ConcurrentHashMap<String,String> validatedFiles=new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.ConcurrentHashMap<String,List<String>> validatedImages=new java.util.concurrent.ConcurrentHashMap<>();
  private void restoreValidation(){synchronized(validationLock){if(validationLoaded)return;validationLoaded=true;try{
    org.json.JSONArray rows=new org.json.JSONArray(new String(new android.util.AtomicFile(new File(context.getFilesDir(),"validation-index.json")).readFully(),java.nio.charset.StandardCharsets.UTF_8));
    for(int i=0;i<Math.min(rows.length(),4096);i++){org.json.JSONObject row=rows.getJSONObject(i);String id=row.getString("id");if(!id.matches("[a-z0-9-]+"))continue;String title=chapterTitles.get(id);if(title!=null&&!title.equals(row.optString("title","")))continue;List<String> names=new ArrayList<>();org.json.JSONArray images=row.getJSONArray("images");for(int n=0;n<images.length();n++){String name=images.getString(n);if(name.matches("image-[a-f0-9]{24}\\.bin"))names.add(name);}validatedImages.put(id,names);validatedFiles.put(id,row.getString("stamp"));}Diagnostics.count("cache.index_restore");
  }catch(Exception ignored){}}}
  private void persistValidation(){if(!validationSaved.compareAndSet(false,true))return;validationWriter.schedule(()->{validationSaved.set(false);try{
    org.json.JSONArray rows=new org.json.JSONArray();for(Map.Entry<String,String> entry:validatedFiles.entrySet()){if(rows.length()>=4096)break;rows.put(new org.json.JSONObject().put("id",entry.getKey()).put("title",chapterTitles.getOrDefault(entry.getKey(),"")).put("stamp",entry.getValue()).put("images",new org.json.JSONArray(validatedImages.getOrDefault(entry.getKey(),Collections.emptyList()))));}
    android.util.AtomicFile file=new android.util.AtomicFile(new File(context.getFilesDir(),"validation-index.json"));FileOutputStream stream=null;try{stream=file.startWrite();stream.write(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(stream);}catch(Exception error){if(stream!=null)file.failWrite(stream);throw error;}Diagnostics.count("cache.index_write");
  }catch(Exception ignored){}},2,java.util.concurrent.TimeUnit.SECONDS);}
  private String contentStamp(String id,File file){
    StringBuilder stamp=new StringBuilder(fileStamp(file));
    List<String> names=validatedImages.get(id);
    if(names!=null)for(String name:names)stamp.append('|').append(name).append(':').append(fileStamp(new File(dir(id),name)));
    return stamp.toString();
  }
  public String displayFingerprint(String id){return contentStamp(id,html(id));}
  public static boolean imageFileReadable(File file){
    if(!file.isFile()||file.length()==0)return false;
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
    options.inJustDecodeBounds=true;
    android.graphics.BitmapFactory.decodeFile(file.getPath(),options);
    return options.outWidth>0&&options.outHeight>0;
  }
  private synchronized void reconcileReady(String id,boolean ready){Chapter known=cachedId(id);if(known!=null&&known.ready!=ready)state(id,ready,ready?"":known.error);}
  public boolean readable(String cid){
    Diagnostics.count("cache.check");long begun=Diagnostics.now();boolean parsed=false;
    try{
      restoreValidation();File f=html(cid);
      if(!f.isFile()||f.length()==0){validatedFiles.remove(cid);validatedImages.remove(cid);reconcileReady(cid,false);return false;}
      String fingerprint=contentStamp(cid,f);
      String known=validatedFiles.get(cid);if(fingerprint.equals(known)){Diagnostics.count("cache.hit");reconcileReady(cid,true);return true;}
      if(("invalid:"+fingerprint).equals(known)){Diagnostics.count("cache.negative_hit");reconcileReady(cid,false);return false;}
      Diagnostics.count("cache.parse");parsed=true;String body=read(f);classify(cid,body);
      if(IllustrationPolicy.skipCachedIllustration(context.getSharedPreferences("settings",0).getInt("chapter_kind_"+cid,0),HakoParser.validContent(body),false))context.getSharedPreferences("settings",0).edit().putString("illustration_cached_"+cid,fileStamp(f)).apply();
      HakoParser.OfflineContent content=HakoParser.offlineContent(body);
      List<String> names=new ArrayList<>();
      for(String src:content.images){String name=HakoParser.offlineImageName(cid,src);if(!name.isEmpty())names.add(name);}
      validatedImages.put(cid,names);
      fingerprint=contentStamp(cid,f);
      boolean ok=content.readable(src->{String name=HakoParser.offlineImageName(cid,src);return !name.isEmpty()&&imageFileReadable(new File(dir(cid),name));});
      ok=ok&&fingerprint.equals(contentStamp(cid,f));
      validatedFiles.put(cid,ok?fingerprint:"invalid:"+fingerprint);persistValidation();reconcileReady(cid,ok);
      return ok;
    }catch(Exception e){validatedFiles.remove(cid);validatedImages.remove(cid);reconcileReady(cid,false);return false;}finally{Diagnostics.elapsed("cache.validation",begun);OperationTrace.cache(parsed,Diagnostics.now()-begun);}
  }
  public synchronized void clearTemporary(String id){Book b=book(id);if(b!=null&&!b.followed&&b.stamp==0&&b.visits==0&&!isKeepFull(id))for(Chapter c:chapters(id)){delete(dir(c.id));state(c.id,false,"");}}
  public void cleanStartup(){
    // Disk validation happens outside Store's monitor and outside SQLite transactions.
    Map<String,String> invalid=new LinkedHashMap<>();
    for(Book b:books())if(!b.id.equals("demo")){
      for(Chapter ch:chapters(b.id)){
        String before=fileStamp(html(ch.id));
        if(!readable(ch.id)&&before.equals(fileStamp(html(ch.id))))invalid.put(ch.id,before);
      }
    }
    List<String> ids=new ArrayList<>(invalid.keySet());
    for(int offset=0;offset<ids.size();offset+=128){
      synchronized(this){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{
          for(int n=offset;n<Math.min(ids.size(),offset+128);n++){
            String id=ids.get(n);
            // Writers use the same monitor. A newer atomic file replacement wins.
            if(!invalid.get(id).equals(fileStamp(html(id))))continue;
            ContentValues v=new ContentValues();v.put("ready",0);
            d.update("chapters",v,"id=?",new String[]{id});chapterCache.clear();chapterLookup.clear();
          }
          d.setTransactionSuccessful();
        }finally{d.endTransaction();}
      }
    }
  }
  private String fileStamp(File f){return f.isFile()?f.length()+":"+f.lastModified():"missing";}


  public synchronized void rebase(String origin){
    origin=HakoParser.originOf(origin);SQLiteDatabase d=getWritableDatabase();
    d.beginTransaction();try{
      d.execSQL("UPDATE books SET url=? || substr(url,instr(substr(url,9),'/')+8) WHERE url LIKE 'https://%/%'",new Object[]{origin});
      d.execSQL("UPDATE chapters SET url=? || substr(url,instr(substr(url,9),'/')+8) WHERE url LIKE 'https://%/%'",new Object[]{origin});
      d.setTransactionSuccessful();
    }finally{d.endTransaction();}
    chapterCache.clear();chapterLookup.clear();
    context.getSharedPreferences("settings",0).edit().putString("origin",origin).remove("pending_origin").commit();
    HakoParser.ORIGIN=origin;TocState.invalidateDisk(context);StoryInfo.invalidateDisk(context);displayChanged("*",false);
  }
  public static void delete(File f) {
    File[] kids = f.listFiles();
    if (kids != null) for (File x : kids) delete(x);
    f.delete();
  }
}
