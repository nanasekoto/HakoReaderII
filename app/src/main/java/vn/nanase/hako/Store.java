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

  public final File cache;
  private final Context context;

  private Store(Context c) {
    super(c, "hako.db", null, 3);
    this.context = c.getApplicationContext();
    cache = new File(c.getFilesDir(), "chapters");
    cache.mkdirs();
  }

  public boolean isKeepFull(String id) {
    return context.getSharedPreferences("settings", 0).getBoolean("keep_full_" + id, false);
  }

  public void setKeepFull(String id, boolean keep) {
    context.getSharedPreferences("settings", 0).edit().putBoolean("keep_full_" + id, keep).apply();
  }

  public boolean isCompleted(String id){return context.getSharedPreferences("settings",0).getBoolean("completed_"+id,false);}
  public void setCompleted(String id,boolean value){context.getSharedPreferences("settings",0).edit().putBoolean("completed_"+id,value).apply();if(value)setKeepFull(id,true);}
  public int newArrivals(String id){return context.getSharedPreferences("settings",0).getInt("new_arrivals_"+id,0);}
  public synchronized void mergeShelf(List<HakoParser.Link> links){mergeShelf(links,0);}
  public synchronized void mergeShelf(List<HakoParser.Link> links,int firstRank){int rank=firstRank;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(HakoParser.Link l:links){putBook(l);followed(l.id,true,rank++);shelfInfo(l.id,l.info);}d.setTransactionSuccessful();}finally{d.endTransaction();}}
  public synchronized void queueIfAllRead(String id){if(!chapters(id).isEmpty()&&unread(id)==0){if(!canSubmit(id))queueCaughtUp(id);}}

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
  public boolean syncTarget(Book b){return !b.id.equals("demo")&&!b.dropped&&DownloadScope.eligible(b.visits>0,isKeepFull(b.id),context.getSharedPreferences("settings",0).getBoolean("auto_dl_"+b.id,false));}
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
  public int compareShelf(Book a,Book b){
    int n=Boolean.compare(hasShelfUpdate(b),hasShelfUpdate(a));if(n!=0)return n;
    n=Long.compare(shelfUpdatedAt(b),shelfUpdatedAt(a));if(n!=0)return n;
    n=Integer.compare(a.shelfRank,b.shelfRank);return n!=0?n:a.title.compareToIgnoreCase(b.title);
  }
  public boolean completedOffline(Book b){if(!isCompleted(b.id))return false;android.content.SharedPreferences p=context.getSharedPreferences("settings",0);String current=p.getString("shelf_key_"+b.id,""),known=p.getString("catalog_shelf_key_"+b.id,"");if(p.getBoolean("shelf_changed_"+b.id,false)||(!current.isEmpty()&&!current.equals(known)))return false;List<Chapter> all=chapters(b.id);if(all.isEmpty())return false;for(Chapter ch:all)if(!ch.ready)return false;return true;}
  public int unread(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM chapters c LEFT JOIN read_chapters r ON r.id=c.id WHERE c.book=? AND r.id IS NULL",new String[]{id})){return c.moveToFirst()?c.getInt(0):0;}}
  public boolean queued(String id){return context.getSharedPreferences("read_queue",0).contains(id);}
  public synchronized void queueCaughtUp(String id){
    if(chapters(id).isEmpty()||unread(id)>0)throw new IllegalStateException("Chỉ đồng bộ đọc hết sau khi đã đọc hết các chương offline");
    context.getSharedPreferences("settings",0).edit().putBoolean("read_baseline_"+id,true).commit();
    org.json.JSONArray ids=new org.json.JSONArray();
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(Chapter c:chapters(id)){ids.put(c.id);}d.setTransactionSuccessful();}finally{d.endTransaction();}
    if(ids.length()==0)throw new IllegalStateException("Chưa có mục lục để xác nhận đã đọc");
    if(!context.getSharedPreferences("read_queue",0).edit().putString(id,ids.toString()).commit())throw new IllegalStateException("Không lưu được hàng chờ");
  }
  public Set<String> queuedBooks(){return new HashSet<>(context.getSharedPreferences("read_queue",0).getAll().keySet());}
  public synchronized boolean canSubmit(String id){
    if(!queued(id)||chapters(id).isEmpty()||unread(id)>0)return false;
    try{org.json.JSONArray a=new org.json.JSONArray(context.getSharedPreferences("read_queue",0).getString(id,"[]"));Set<String> snapshot=new HashSet<>();for(int i=0;i<a.length();i++)snapshot.add(a.getString(i));for(Chapter c:chapters(id))if(!snapshot.contains(c.id))return false;return true;}catch(Exception e){return false;}
  }
  public void extendQueueIfCaughtUp(String id){if(queued(id)&&!canSubmit(id)&&unread(id)==0)queueCaughtUp(id);}
  public void clearQueue(String id){context.getSharedPreferences("read_queue",0).edit().remove(id).commit();}
  public int totalOfflinePercent(){int total=0,done=0;boolean unknown=false;for(Book b:books())if(syncTarget(b)){BookProgress p=progress(b);if(p.totalChs==0)unknown=true;total+=p.newChsTotal;done+=p.newChsDownloaded;}return unknown?0:total==0?0:Math.min(done==total&&context.getSharedPreferences("settings",0).getBoolean("sync_verified",false)?100:99,Math.round(done*100f/total));}
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

  public int readingStart(Book b,List<Chapter> chapters){if(b.current!=null&&!b.current.isEmpty())for(Chapter ch:chapters)if(ch.id.equals(b.current))return ch.ord;int last=0;for(Chapter ch:chapters)if(wasRead(ch.id))last=Math.max(last,ch.ord);return last;}
  public BookProgress progress(Book b) {
    List<Chapter> chs = chapters(b.id);
    int total = chs.size();
    if (total == 0) return new BookProgress(0, 0, 0, 0, 0);

    int currentOrd = readingStart(b,chs);
    boolean full=isKeepFull(b.id)||isCompleted(b.id);
    boolean toEnd=FetchPolicy.toEnd(b.followed,favorite(b),full);
    int oldChs = full?0:Math.max(0,currentOrd-FetchPolicy.BEHIND);
    int newChsTotal=0,newChsDownloaded=0;
    for(Chapter ch:chs)if(FetchPolicy.inDownloadRange(ch.ord,currentOrd,toEnd,full)){
      newChsTotal++;
      if(ch.ready)newChsDownloaded++;
    }

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
  public synchronized void replaceShelf(List<HakoParser.Link> links){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.execSQL("UPDATE books SET followed=0,shelf_rank=999999");int rank=0;for(HakoParser.Link l:links){putBook(l);followed(l.id,true,rank++);shelfInfo(l.id,l.info);}d.setTransactionSuccessful();}finally{d.endTransaction();}}
  public synchronized void importHistory(String id,String chapter,long stamp){Book b=book(id);if(b==null||stamp<=b.stamp)return;ContentValues v=new ContentValues();v.put("current",chapter);v.put("stamp",stamp);v.put("pos",0);v.put("fraction",0);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
  public synchronized void selectChapter(String id,String chapter){Book b=book(id);if(b!=null&&!chapter.equals(b.current)){ContentValues v=new ContentValues();v.put("current",chapter);v.put("pos",0);v.put("fraction",0);getWritableDatabase().update("books",v,"id=?",new String[]{id});}}
  public synchronized void visited(String id){getWritableDatabase().execSQL("UPDATE books SET visits=visits+1 WHERE id=?",new Object[]{id});}
  public synchronized void pin(String id,boolean value){getWritableDatabase().execSQL("UPDATE books SET pinned=? WHERE id=?",new Object[]{value?1:0,id});}
  public synchronized void readChapter(String id){if(wasRead(id))return;ContentValues v=new ContentValues();v.put("id",id);v.put("stamp",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("read_chapters",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
  public boolean wasRead(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM read_chapters WHERE id=?",new String[]{id})){return c.moveToFirst();}}
  public synchronized void shelfInfo(String id,String info){ContentValues v=new ContentValues();v.put("shelf_info",info);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
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
    ContentValues v = new ContentValues();
    v.put("id", l.id);
    v.put("title", l.title);
    v.put("url", l.url);
    getWritableDatabase().insertWithOnConflict("books", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    ContentValues update = new ContentValues();
    update.put("title", l.title);
    update.put("url", l.url);
    getWritableDatabase().update("books", update, "id=?", new String[] {l.id});
  }

  public synchronized List<Book> books() {
    List<Book> a = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
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
    try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,url,current,pos,fraction,stamp,followed,dropped,shelf_rank,visits,pinned,shelf_info FROM books WHERE id=?", new String[]{id})) {
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

  public synchronized void catalog(String book, List<HakoParser.Link> links) {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      List<Chapter> previous=chapters(book);Set<String> oldIds=new HashSet<>();for(Chapter old:previous)oldIds.add(old.id);
      int added=0;for(HakoParser.Link l:links)if(!oldIds.contains(l.id))added++;
      Set<String> valid=new HashSet<>();for(HakoParser.Link l:links)valid.add(l.id);
      // A partial or mismatched web catalog must never erase existing offline chapters.
      for(Chapter old:previous)if(!valid.contains(old.id))throw new IllegalStateException("Mục lục mới thiếu chương đã biết. Đã giữ nguyên dữ liệu offline; chưa cập nhật mục lục.");
      int i = 0;
      for (HakoParser.Link l : links) {
        ContentValues v = new ContentValues();
        v.put("id", l.id);
        v.put("book", book);
        v.put("title", l.title);
        v.put("url", l.url);
        v.put("ord", i++);
        d.insertWithOnConflict("chapters", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        v.remove("id");
        d.update("chapters", v, "id=?", new String[] {l.id});
      }
      d.setTransactionSuccessful();
      if(!previous.isEmpty())context.getSharedPreferences("settings",0).edit().putInt("new_arrivals_"+book,added).apply();
    } finally {
      d.endTransaction();
    }
  }

  public synchronized List<Chapter> chapters(String book) {
    List<Chapter> a = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,book,title,url,ord,ready,error FROM chapters WHERE book=? ORDER BY ord",
                new String[] {book})) {
      while (c.moveToNext()) {
        Chapter x = new Chapter();
        x.id = c.getString(0);
        x.book = c.getString(1);
        x.title = c.getString(2);
        x.url = c.getString(3);
        x.ord = c.getInt(4);
        x.ready = cachedChapter(x.id,c.getInt(5)==1);
        x.error = c.getString(6);
        a.add(x);
      }
    }
    return a;
  }

  public synchronized Chapter chapter(String book, String id) {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,title,url,ord,ready,error FROM chapters WHERE id=?", new String[]{id})) {
      if (c.moveToNext()) {
        Chapter x = new Chapter();
        x.id = c.getString(0); x.book = c.getString(1); x.title = c.getString(2);
        x.url = c.getString(3); x.ord = c.getInt(4);
        x.ready = cachedChapter(x.id,c.getInt(5)==1);
        x.error = c.getString(6);
        return x;
      }
    } catch (Exception ignored) {}
    return null;
  }

  public synchronized void position(String book, String chapter, int pos, float fraction) {
    ContentValues v = new ContentValues();
    v.put("current", chapter);
    v.put("pos", Math.max(0, pos));
    v.put("fraction", Math.max(0, Math.min(.999999f, fraction)));
    v.put("stamp", System.currentTimeMillis());
    getWritableDatabase().update("books", v, "id=?", new String[] {book});
  }

  public synchronized void state(String id, boolean ready, String error) {
    ContentValues v = new ContentValues();
    v.put("ready", ready ? 1 : 0);
    v.put("error", error);
    getWritableDatabase().update("chapters", v, "id=?", new String[] {id});
  }

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
      if (c.ord < current.ord - FetchPolicy.BEHIND && wasRead(c.id)) {
        delete(dir(c.id));
        state(c.id, false, "");
      }
  }

  public synchronized void followed(String id,boolean value,int rank){ContentValues v=new ContentValues();v.put("followed",value?1:0);v.put("shelf_rank",rank);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
  public synchronized void dropped(String id,boolean value){ContentValues v=new ContentValues();v.put("dropped",value?1:0);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
  // List queries must never read/parse chapter HTML while holding the Store monitor.
  // Content integrity is verified by the worker before queueing and before reuse.
  private boolean cachedChapter(String id,boolean markedReady){File f=html(id);return f.isFile()&&f.length()>0;}
  private final java.util.concurrent.ConcurrentHashMap<String,String> validatedFiles=new java.util.concurrent.ConcurrentHashMap<>();
  public boolean readable(String cid){try{File f=html(cid);if(!f.isFile()||f.length()==0){validatedFiles.remove(cid);return false;}String fingerprint=f.length()+":"+f.lastModified();if(fingerprint.equals(validatedFiles.get(cid)))return true;boolean ok=HakoParser.validContent(read(f));if(ok)validatedFiles.put(cid,fingerprint);else validatedFiles.remove(cid);return ok;}catch(Exception e){validatedFiles.remove(cid);return false;}}
  public synchronized void clearTemporary(String id){Book b=book(id);if(b!=null&&!b.followed&&b.stamp==0&&b.visits==0&&!isKeepFull(id))for(Chapter c:chapters(id)){delete(dir(c.id));state(c.id,false,"");}}
  public void cleanStartup(){for(Book b:books())if(!b.id.equals("demo")){if(!b.followed&&!b.id.equals(Repository.activeBook))clearTemporary(b.id);for(Chapter c:chapters(b.id))if(!html(c.id).isFile()||html(c.id).length()==0){state(c.id,false,"");}}}

  public synchronized void rebase(String origin){for(Book b:books()){ContentValues v=new ContentValues();try{v.put("url",origin+java.net.URI.create(b.url).getPath());getWritableDatabase().update("books",v,"id=?",new String[]{b.id});for(Chapter ch:chapters(b.id)){v.clear();v.put("url",origin+java.net.URI.create(ch.url).getPath());getWritableDatabase().update("chapters",v,"id=?",new String[]{ch.id});}}catch(Exception ignored){}}}
  public static void delete(File f) {
    File[] kids = f.listFiles();
    if (kids != null) for (File x : kids) delete(x);
    f.delete();
  }
}
