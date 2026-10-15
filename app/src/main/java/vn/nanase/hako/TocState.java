package vn.nanase.hako;
import android.content.*;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Per-book rebuildable chapter metadata. Never reads HTML or decodes images. */
public final class TocState {
 public static final class Entry {
  public final Store.Book book;public final List<Store.Chapter> chapters;public final Set<String> read;public final long generation;
  Entry(Store.Book b,List<Store.Chapter> c,Set<String> r){book=b;chapters=Collections.unmodifiableList(c);read=Collections.unmodifiableSet(r);generation=System.nanoTime();}
 }
 private static final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(8,.75f,true);
 private static final Map<String,List<Store.Chapter>> baseChapters=new HashMap<>();
 private static final java.util.concurrent.ConcurrentHashMap<String,Store.Book> positions=new java.util.concurrent.ConcurrentHashMap<>();
 public static synchronized void metadataChanged(Store.Book book){if(book!=null&&entries.containsKey(book.id))positions.put(book.id,book);}
 public static void positionChanged(Store.Book book){positions.put(book.id,book);}
 private static final Map<String,Entry> diskEntries=new HashMap<>();private static final Map<String,String> diskGenerations=new HashMap<>();
 private static final Set<String> dirty=new HashSet<>(),scheduled=new HashSet<>();
 private static final Object persistLock=new Object();
 private static final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor();
 private static synchronized void remember(String id,Entry e){entries.put(id,e);int total=0;for(Entry entry:entries.values())total+=entry.chapters.size();while(entries.size()>4||(total>16000&&entries.size()>1)){String first=entries.keySet().iterator().next();total-=entries.remove(first).chapters.size();diskEntries.remove(first);diskGenerations.remove(first);baseChapters.remove(first);positions.remove(first);}}
 public static void invalidateDisk(Context c){synchronized(persistLock){resetForImport();Store.delete(new File(c.getFilesDir(),"toc-state"));}}
 public static synchronized void resetForImport(){entries.clear();diskEntries.clear();diskGenerations.clear();dirty.clear();positions.clear();baseChapters.clear();}
 public static synchronized Entry peek(String id){if(dirty.contains(id))return null;Entry entry=entries.get(id);Store.Book latest=positions.get(id);return entry==null?null:latest==null?entry:new Entry(latest,entry.chapters,entry.read);}
 public static void changed(Context c,String id){
  if(id==null||id.isEmpty())return;positions.remove(id);if(id.equals("*")){synchronized(TocState.class){dirty.addAll(entries.keySet());}return;}
  Context app=c.getApplicationContext();synchronized(TocState.class){dirty.add(id);if(!scheduled.add(id))return;}
  writer.schedule(()->{try{boolean needed;synchronized(TocState.class){needed=dirty.contains(id);}if(needed)refresh(app,id);}catch(Exception error){Diagnostics.failure("toc.refresh",error);}finally{boolean again;synchronized(TocState.class){scheduled.remove(id);again=dirty.contains(id);}if(again)changed(app,id);}},200,TimeUnit.MILLISECONDS);
 }
 public static Entry refresh(Context c,String id)throws Exception{OperationTrace.Span trace=OperationTrace.begin("toc.refresh");try{
  long begun=Diagnostics.now();Store store=Store.get(c);Entry result;
  // All writes that dirty this entry hold Store's monitor; publish while still owning that monitor.
  synchronized(store){Store.Book b=store.book(id);if(b==null){synchronized(TocState.class){dirty.remove(id);entries.remove(id);}return null;}List<Store.Chapter> chapters=store.chapters(id);result=new Entry(b,chapters,store.readingIds(id,chapters));synchronized(TocState.class){dirty.remove(id);remember(id,result);}}
  persist(c,id,result);Diagnostics.count("toc.refresh");Diagnostics.elapsed("toc.prepare",begun);return result;
 }finally{OperationTrace.end(trace,"closed",0);}}
 public static Entry load(Context c,String id)throws Exception{
  Entry warm=peek(id);if(warm!=null){Diagnostics.count("toc.memory_hit");return warm;}
  synchronized(TocState.class){if(dirty.contains(id))return null;}
  try{Entry saved=readSaved(c,id);Store.Book latest=Store.get(c).book(id);if(latest!=null)saved=new Entry(latest,saved.chapters,saved.read);remember(id,saved);Diagnostics.count("toc.disk_hit");return saved;}catch(Exception missing){return null;}
 }
 private static AtomicFile file(Context c,String id){if(!id.matches("[a-z0-9-]+"))throw new IllegalArgumentException();return new AtomicFile(new File(new File(c.getFilesDir(),"toc-state"),id+".json"));}
 static JSONObject object(Object value)throws Exception{JSONObject out=new JSONObject();for(Field f:value.getClass().getFields())if(!Modifier.isStatic(f.getModifiers()))out.put(f.getName(),f.get(value));return out;}
 static <T>T object(JSONObject value,T out)throws Exception{for(Field f:out.getClass().getFields()){if(Modifier.isStatic(f.getModifiers())||!value.has(f.getName()))continue;Object item=value.get(f.getName());Class<?> type=f.getType();if(type==int.class)f.setInt(out,((Number)item).intValue());else if(type==long.class)f.setLong(out,((Number)item).longValue());else if(type==float.class)f.setFloat(out,((Number)item).floatValue());else if(type==boolean.class)f.setBoolean(out,(Boolean)item);else if(type==String.class)f.set(out,value.optString(f.getName(),""));}return out;}
 public static Entry readSaved(Context c,String id)throws Exception{
  AtomicFile base=file(c,id);if(base.getBaseFile().length()>8*1024*1024)throw new IOException("TOC snapshot too large");JSONObject json=new JSONObject(new String(base.readFully(),java.nio.charset.StandardCharsets.UTF_8));if(json.optInt("schema")!=1)throw new IOException("Invalid TOC state");
  try{JSONObject delta=new JSONObject(new String(new AtomicFile(new File(base.getBaseFile().getPath()+".progress")).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(!json.optString("generation").isEmpty()&&json.optString("generation").equals(delta.optString("generation"))){json.put("book",delta.getJSONObject("book"));json.put("read",delta.getJSONArray("read"));}}catch(Exception ignored){}
  Store.Book b=object(json.getJSONObject("book"),new Store.Book());if(!id.equals(b.id))throw new IOException("Wrong TOC book");List<Store.Chapter> chapters=new ArrayList<>();JSONArray rows=json.getJSONArray("chapters");for(int i=0;i<rows.length();i++){Store.Chapter ch=object(rows.getJSONObject(i),new Store.Chapter());if(!id.equals(ch.book))throw new IOException("Wrong TOC chapter");chapters.add(ch);}try{JSONObject state=new JSONObject(new String(new AtomicFile(new File(base.getBaseFile()+".states")).readFully(),java.nio.charset.StandardCharsets.UTF_8));if(json.optString("generation").equals(state.optString("generation"))){Map<String,JSONObject> changes=new HashMap<>();JSONArray values=state.getJSONArray("rows");for(int n=0;n<values.length();n++)changes.put(values.getJSONObject(n).getString("id"),values.getJSONObject(n));for(Store.Chapter ch:chapters){JSONObject change=changes.get(ch.id);if(change!=null){ch.ready=change.getBoolean("ready");ch.error=change.optString("error","");}}}}catch(Exception ignored){}Set<String> read=new HashSet<>();JSONArray ids=json.getJSONArray("read");for(int i=0;i<ids.length();i++)read.add(ids.getString(i));return new Entry(b,chapters,read);
 }
 private static boolean structure(List<Store.Chapter> a,List<Store.Chapter> b){if(a==null||a.size()!=b.size())return false;for(int n=0;n<a.size();n++){Store.Chapter x=a.get(n),y=b.get(n);if(x.ord!=y.ord||!Objects.equals(x.id,y.id)||!Objects.equals(x.book,y.book)||!Objects.equals(x.title,y.title)||!Objects.equals(x.url,y.url))return false;}return true;}
 private static boolean states(List<Store.Chapter> a,List<Store.Chapter> b){if(a==null||a.size()!=b.size())return false;for(int n=0;n<a.size();n++){Store.Chapter x=a.get(n),y=b.get(n);if(!Objects.equals(x.id,y.id)||x.ready!=y.ready||!Objects.equals(x.error,y.error))return false;}return true;}
 private static boolean bookSame(Store.Book a,Store.Book b)throws Exception{if(a==null)return false;for(Field f:Store.Book.class.getFields())if(!Objects.equals(f.get(a),f.get(b)))return false;return true;}
 private static void write(AtomicFile target,JSONObject data)throws Exception{FileOutputStream out=null;try{out=target.startWrite();out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));target.finishWrite(out);}catch(Exception error){if(out!=null)target.failWrite(out);throw error;}}
 private static void persist(Context c,String id,Entry e)throws Exception{synchronized(persistLock){synchronized(TocState.class){if(entries.get(id)!=e)return;}Entry prior;String generation;synchronized(TocState.class){prior=diskEntries.get(id);generation=diskGenerations.get(id);}boolean same=prior!=null&&structure(prior.chapters,e.chapters);boolean stateSame=same&&states(prior.chapters,e.chapters),progressSame=same&&bookSame(prior.book,e.book)&&prior.read.equals(e.read);if(same&&stateSame&&progressSame){Diagnostics.count("toc.noop_write_skipped");return;}
  AtomicFile base=file(c,id);if(!same||generation==null){generation=UUID.randomUUID().toString();JSONObject json=new JSONObject().put("schema",1).put("generation",generation).put("book",object(e.book)).put("read",new JSONArray(e.read));JSONArray rows=new JSONArray();for(Store.Chapter chapter:e.chapters)rows.put(object(chapter));json.put("chapters",rows);write(base,json);new AtomicFile(new File(base.getBaseFile()+".states")).delete();new AtomicFile(new File(base.getBaseFile()+".progress")).delete();baseChapters.put(id,e.chapters);Diagnostics.count("toc.base_write");}
  else {if(!progressSame){write(new AtomicFile(new File(base.getBaseFile()+".progress")),new JSONObject().put("generation",generation).put("book",object(e.book)).put("read",new JSONArray(e.read)));Diagnostics.count("toc.progress_write");}if(!stateSame){List<Store.Chapter> original=baseChapters.get(id);if(original==null){original=prior.chapters;baseChapters.put(id,original);}JSONArray rows=new JSONArray();for(int n=0;n<e.chapters.size();n++){Store.Chapter ch=e.chapters.get(n),old=original.get(n);if(ch.ready!=old.ready||!Objects.equals(ch.error,old.error))rows.put(new JSONObject().put("id",ch.id).put("ready",ch.ready).put("error",ch.error));}write(new AtomicFile(new File(base.getBaseFile()+".states")),new JSONObject().put("generation",generation).put("rows",rows));Diagnostics.count("toc.state_write");}}
  synchronized(TocState.class){if(entries.containsKey(id)){diskEntries.put(id,e);diskGenerations.put(id,generation);}}Diagnostics.count("toc.persist");}}
}
