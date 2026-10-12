package vn.nanase.hako;

import android.content.*;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Durable display metadata. No chapter HTML or image decoding belongs here. */
public final class LibraryState {
 private static volatile UiLibrarySnapshot current;
 private static final ConcurrentHashMap<String,Boolean> dirty=new ConcurrentHashMap<>();
 private static final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor();
 private static final AtomicBoolean scheduled=new AtomicBoolean();
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"library-state.json"));}
 public static UiLibrarySnapshot peek(){return current;}
 public static UiLibrarySnapshot restore(Context c){
  if(current!=null)return current;
  try{current=decode(new String(file(c).readFully(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
  return current;
 }
 public static void changed(Context ctx,String id,boolean recount){
  dirty.merge(id,recount,(a,b)->a||b);
  if(scheduled.compareAndSet(false,true)){
   Context app=ctx.getApplicationContext();
   writer.schedule(()->{scheduled.set(false);try{refresh(app);app.sendBroadcast(new Intent(Repository.EVENT).setPackage(app.getPackageName()).putExtra("snapshot_changed",true));}catch(Exception e){android.util.Log.w("LibraryState","Keep previous display state",e);}},150,TimeUnit.MILLISECONDS);
  }
 }
 public static synchronized UiLibrarySnapshot refresh(Context c)throws Exception{
  try{return refreshInternal(c);}catch(Exception error){dirty.put("*",true);throw error;}
 }
 private static UiLibrarySnapshot refreshInternal(Context c)throws Exception{
  restore(c);Store store=Store.get(c);SharedPreferences prefs=c.getSharedPreferences("settings",0);
  Map<String,Boolean> changes=new HashMap<>();
  for(Map.Entry<String,Boolean> e:dirty.entrySet())if(dirty.remove(e.getKey(),e.getValue()))changes.put(e.getKey(),e.getValue());
  if(current==null){current=UiLibrarySnapshot.load(store,prefs);}
  else if(!changes.isEmpty()){
   Map<String,Store.Book> books=new LinkedHashMap<>();for(Store.Book b:current.books)books.put(b.id,b);
   Map<String,UiLibrarySnapshot.Summary> summaries=new HashMap<>(current.summaries);
   if(changes.containsKey("*")){
    boolean recount=changes.get("*");
    books.clear();for(Store.Book b:store.books()){books.put(b.id,b);if(recount||!summaries.containsKey(b.id))changes.put(b.id,true);}
    summaries.keySet().retainAll(books.keySet());changes.remove("*");
   }
   for(Map.Entry<String,Boolean> change:changes.entrySet()){
    Store.Book b=store.book(change.getKey());if(b==null){books.remove(change.getKey());summaries.remove(change.getKey());continue;}
    books.put(b.id,b);
    if(change.getValue()||!summaries.containsKey(b.id))summaries.put(b.id,UiLibrarySnapshot.summarize(store,prefs,b));
    else {
     UiLibrarySnapshot.Summary summary=readObject(writeObject(summaries.get(b.id)),new UiLibrarySnapshot.Summary());
     Store.Chapter chapter=store.chapter(b.id,b.current);
     summary.currentTitle=chapter==null?"":chapter.title;summary.currentRead=chapter!=null&&store.wasRead(chapter.id);
     summary.updatedAt=prefs.getLong("shelf_updated_at_"+b.id,0);
     String now=prefs.getString("shelf_key_"+b.id,""),known=prefs.getString("catalog_shelf_key_"+b.id,"");
     boolean changed=prefs.getBoolean("shelf_changed_"+b.id,false)||(!now.isEmpty()&&!now.equals(known));
     summary.updated=(b.followed&&changed)||(summary.newCount>0&&summary.stored<summary.total);
     summary.completedOffline=store.isCompleted(b.id)&&summary.total>0&&summary.stored==summary.total&&!changed;
     summary.full=store.isKeepFull(b.id);
     summaries.put(b.id,summary);
    }
   }
   current=new UiLibrarySnapshot(new ArrayList<>(books.values()),summaries,0,prefs.getLong("lastShelfSync",0),store.readingRevision());
  }else return current;
  AtomicFile target=file(c);FileOutputStream output=null;
  try{output=target.startWrite();output.write(encode(current).getBytes(java.nio.charset.StandardCharsets.UTF_8));target.finishWrite(output);}
  catch(Exception error){if(output!=null)target.failWrite(output);dirty.put("*",true);throw error;}
  return current;
 }
 private static JSONObject writeObject(Object value)throws Exception{
  JSONObject json=new JSONObject();for(Field field:value.getClass().getFields())json.put(field.getName(),field.get(value));return json;
 }
 private static <T>T readObject(JSONObject json,T value)throws Exception{
  for(Field field:value.getClass().getFields()){
   String key=field.getName();if(!json.has(key))continue;Class<?> type=field.getType();
   if(type==String.class)field.set(value,json.optString(key,""));else if(type==int.class)field.setInt(value,json.optInt(key));
   else if(type==long.class)field.setLong(value,json.optLong(key));else if(type==float.class)field.setFloat(value,(float)json.optDouble(key));
   else if(type==boolean.class)field.setBoolean(value,json.optBoolean(key));
  }return value;
 }
 public static String encode(UiLibrarySnapshot state)throws Exception{
  JSONObject root=new JSONObject();root.put("version",1).put("lastShelfSync",state.lastShelfSync);
  JSONArray records=new JSONArray(),recent=new JSONArray(),favorites=new JSONArray(),shelf=new JSONArray();
  for(Store.Book b:state.books){records.put(new JSONObject().put("book",writeObject(b)).put("summary",writeObject(state.summary(b))));
   if(b.stamp>0)recent.put(b.id);if(b.pinned||b.visits>=3)favorites.put(b.id);if(b.followed)shelf.put(b.id);
  }
  return root.put("books",records).put("recent",recent).put("favorites",favorites).put("shelf",shelf).toString();
 }
 public static UiLibrarySnapshot decode(String content)throws Exception{
  JSONObject root=new JSONObject(content);if(root.getInt("version")!=1)throw new IOException("Unknown display state");
  JSONArray records=root.getJSONArray("books");List<Store.Book> books=new ArrayList<>();Map<String,UiLibrarySnapshot.Summary> summaries=new HashMap<>();
  for(int i=0;i<records.length();i++){JSONObject record=records.getJSONObject(i);Store.Book b=readObject(record.getJSONObject("book"),new Store.Book());
   if(b.id==null||b.id.isEmpty()||summaries.containsKey(b.id))throw new IOException("Invalid display record");
   books.add(b);summaries.put(b.id,readObject(record.getJSONObject("summary"),new UiLibrarySnapshot.Summary()));
  }
  return new UiLibrarySnapshot(books,summaries,0,root.optLong("lastShelfSync"),0);
 }
}
