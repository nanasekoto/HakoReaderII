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

  public boolean favorite(Book b){return b.pinned||isKeepFull(b.id)||b.visits>=3;}
  public boolean syncTarget(Book b){return !b.id.equals("demo")&&!b.dropped&&(b.stamp>0||favorite(b));}
  public int unread(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM chapters c LEFT JOIN read_chapters r ON r.id=c.id WHERE c.book=? AND r.id IS NULL",new String[]{id})){return c.moveToFirst()?c.getInt(0):0;}}
  public boolean queued(String id){return context.getSharedPreferences("read_queue",0).contains(id);}
  public synchronized void queueCaughtUp(String id){
    org.json.JSONArray ids=new org.json.JSONArray();
    SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(Chapter c:chapters(id)){readChapter(c.id);ids.put(c.id);}d.setTransactionSuccessful();}finally{d.endTransaction();}
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

  public BookProgress progress(Book b) {
    List<Chapter> chs = chapters(b.id);
    int total = chs.size();
    if (total == 0) return new BookProgress(0, 0, 0, 0, 0);

    int currentOrd = 0;
    if (b.current != null && !b.current.isEmpty()) {
      for (int i = 0; i < chs.size(); i++) {
        if (chs.get(i).id.equals(b.current)) {
          currentOrd = chs.get(i).ord;
          break;
        }
      }
    } else {
      for (int i = 0; i < chs.size(); i++) {
        if (wasRead(chs.get(i).id)) currentOrd = i;
      }
    }

    if(isKeepFull(b.id))currentOrd=0;
    int oldChs = Math.max(0, currentOrd);
    int newChsTotal = Math.max(0, total - currentOrd);
    int newChsDownloaded = 0;
    for (int i = currentOrd; i < total; i++) {
      if (chs.get(i).ready) newChsDownloaded++;
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
      Set<String> valid=new HashSet<>();for(HakoParser.Link l:links)valid.add(l.id);for(Chapter old:chapters(book))if(!valid.contains(old.id)){d.delete("chapters","id=?",new String[]{old.id});delete(dir(old.id));}
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
        x.ready = c.getInt(5) == 1 && html(x.id).isFile();
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
        x.ready = c.getInt(5) == 1 && html(x.id).isFile();
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
      if (c.ord < current.ord - FetchPolicy.BEHIND) {
        delete(dir(c.id));
        state(c.id, false, "");
      }
  }

  public synchronized void followed(String id,boolean value,int rank){ContentValues v=new ContentValues();v.put("followed",value?1:0);v.put("shelf_rank",rank);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
  public synchronized void dropped(String id,boolean value){ContentValues v=new ContentValues();v.put("dropped",value?1:0);getWritableDatabase().update("books",v,"id=?",new String[]{id});}
  public boolean readable(String cid){try{return html(cid).isFile()&&HakoParser.validContent(read(html(cid)));}catch(Exception e){return false;}}
  public synchronized void clearTemporary(String id){Book b=book(id);if(b!=null&&!b.followed&&b.stamp==0&&b.visits==0&&!isKeepFull(id))for(Chapter c:chapters(id)){delete(dir(c.id));state(c.id,false,"");}}
  public void cleanStartup(){for(Book b:books())if(!b.id.equals("demo")){if(!b.followed&&!b.id.equals(Repository.activeBook))clearTemporary(b.id);else for(Chapter c:chapters(b.id))if(!readable(c.id)){delete(dir(c.id));state(c.id,false,"");}}}
  public synchronized void rebase(String origin){for(Book b:books()){ContentValues v=new ContentValues();try{v.put("url",origin+java.net.URI.create(b.url).getPath());getWritableDatabase().update("books",v,"id=?",new String[]{b.id});for(Chapter ch:chapters(b.id)){v.clear();v.put("url",origin+java.net.URI.create(ch.url).getPath());getWritableDatabase().update("chapters",v,"id=?",new String[]{ch.id});}}catch(Exception ignored){}}}
  public static void delete(File f) {
    File[] kids = f.listFiles();
    if (kids != null) for (File x : kids) delete(x);
    f.delete();
  }
}

