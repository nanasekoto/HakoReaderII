package vn.nanase.hako.tests;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.*;
import java.util.*;
import vn.nanase.hako.*;

/** Focused offline cache checks: real Android image decoder + SQLite + existing file reuse. */
public final class ImageCacheTest extends Instrumentation {
  private int checks;
  private void check(boolean value,String label){
    checks++;
    if(!value)throw new AssertionError(label);
    Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);
  }
  private void image(File file)throws Exception{
    file.getParentFile().mkdirs();
    Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
    try(FileOutputStream out=new FileOutputStream(file)){
      if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG fixture");
    }finally{bitmap.recycle();}
  }
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){
    Bundle result=new Bundle();
    try{
      Diagnostics.start(getTargetContext());Store store=Store.get(getTargetContext());
      store.putBook(new HakoParser.Link("demo","Image cache fixture",""));
      List<HakoParser.Link> chapters=new ArrayList<>();
      for(int n=1;n<=3;n++)chapters.add(new HakoParser.Link("demo-"+n,"Chapter "+n,""));
      store.catalog("demo",chapters);
      store.setKeepFull("demo",true);
      String name="image-0123456789abcdef01234567.bin";
      for(int n=1;n<=3;n++){
        String id="demo-"+n;image(new File(store.dir(id),name));
        Store.write(store.html(id),"<img src='https://offline.hako.invalid/"+id+"/"+name+"'>");
        store.state(id,false,"Legacy flag");
      }
      check(store.progress(store.book("demo")).percent==100,"Image-only chapters with local PNGs reach 100 even with old ready=0");
      check(store.readable("demo-1"),"Cache is readable before image removal");
      File first=new File(store.dir("demo-1"),name);
      check(first.delete(),"Remove one fixture image");
      check(!store.readable("demo-1"),"Cached validation detects removed image");
      long parses=Diagnostics.payload().getJSONObject("counts").optLong("cache.parse");for(int repeat=0;repeat<12;repeat++)store.readable("demo-1");check(parses>0&&Diagnostics.payload().getJSONObject("counts").optLong("cache.parse")==parses,"Unchanged invalid cache reuses negative result without repeated HTML parse");

      check(!store.autoIllustration(store.chapter("demo","demo-1")),"Legacy saved image markup is excluded from automatic retry");
      String cachedHtml=Store.read(store.html("demo-1"));long cachedTime=store.html("demo-1").lastModified();
      Repository.download(getTargetContext(),store.chapter("demo","demo-1"));
      check(cachedHtml.equals(Store.read(store.html("demo-1")))&&cachedTime==store.html("demo-1").lastModified(),"Download itself rechecks legacy cache and performs no page fetch or overwrite");
      check(store.progress(store.book("demo")).percent==100,"Missing illustration does not reduce text progress");
      check(store.missingImages("demo-1")==1,"Missing image is reported separately");
      Store.write(first,"<html>Access denied</html>");
      check(!Store.imageFileReadable(first),"HTML response is not an image");
      check(!store.readable("demo-1"),"Corrupt image is not offline ready");
      image(first);
      check(store.readable("demo-1"),"Repair only image restores chapter");
      check(store.progress(store.book("demo")).percent==100,"Repair reaches 100 without reloading chapter HTML");
      String original=Store.read(store.html("demo-1"));
      chapters.add(new HakoParser.Link("demo-4","New chapter",""));
      store.catalog("demo",chapters);
      check(store.progress(store.book("demo")).newChsTotal==1&&store.progress(store.book("demo")).newChsDownloaded==0,"Only new text chapter contributes to text progress");
      check(Store.read(store.html("demo-1")).equals(original),"Catalog update preserves image chapter HTML");
      check(!CachePolicy.shouldFetch(store.readable("demo-1"),false),"New chapter does not refetch saved image chapter");
      Store.write(store.html("demo-4"),"<p>[Ảnh ngoài máy chủ HAKO — xem trên web]</p>");
      store.state("demo-4",true,"");
      check(!store.readable("demo-4"),"Old placeholder-only chapter is not falsely complete");
      Store.write(store.html("demo-4"),"<p>Real text</p><img src='https://i.docln.net/missing.jpg'>");
      check(store.readable("demo-4"),"Missing illustration does not discard valid text");
      check(store.progress(store.book("demo")).percent==100,"Text and image chapters share one progress policy");
      store.readChapter("demo-4");
      boolean blocked=false;try{store.queueCaughtUp("demo");}catch(IllegalStateException expected){blocked=true;}
      check(!blocked,"Ignored illustrations need no separate consent for read-all");
      store.queueCaughtUp("demo",true);
      check(!store.wasRead("demo-1"),"Ignored illustrations keep no fabricated local reading");
      getTargetContext().getSharedPreferences("settings",0).edit().putBoolean("catalog_failed_demo",false).commit();
      check(!store.queued("demo")&&!store.canSubmitServer("demo"),"Local demo never creates a remote read-all request");
      chapters.add(new HakoParser.Link("demo-5","Unread text chapter",""));
      store.catalog("demo",chapters);
      check(!store.canSubmitServer("demo"),"New unread chapter blocks old read-all snapshot");
      Bitmap large=Bitmap.createBitmap(1600,2400,Bitmap.Config.ARGB_8888);
      ByteArrayOutputStream source=new ByteArrayOutputStream();large.compress(Bitmap.CompressFormat.PNG,100,source);large.recycle();
      ByteArrayOutputStream resized=new ByteArrayOutputStream();Repository.writeSmallImage(source.toByteArray(),resized);
      android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
      android.graphics.BitmapFactory.decodeByteArray(resized.toByteArray(),0,resized.size(),bounds);
      check(bounds.outWidth==480&&bounds.outHeight==720,"Image resize preserves aspect at 480 pixels wide");
      check(resized.size()<source.size(),"Image conversion reduces fixture storage");
      Store.Book active=new Store.Book(),waiting=new Store.Book();active.id="active";waiting.id="waiting";
      ArrayList<Store.Book> queue=new ArrayList<>();queue.add(waiting);queue.add(active);Repository.activeBook="active";
      java.lang.reflect.Method promote=Repository.class.getDeclaredMethod("promoteActive",List.class);promote.setAccessible(true);promote.invoke(null,queue);
      check(queue.get(0)==active&&queue.get(1)==waiting,"Actual coordinator promotes active book without losing waiting book");
      HakoParser.Link sa=new HakoParser.Link("shelf-a","A",""),sb=new HakoParser.Link("shelf-b","B",""),sc=new HakoParser.Link("shelf-c","C","");
      store.mergeShelf(Arrays.asList(sa,sb,sc));
      store.mergeShelf(Collections.singletonList(sc));
      check(store.book(sc.id).shelfRank==0&&store.book(sa.id).shelfRank==1&&store.book(sb.id).shelfRank==2,"Partial shelf prefix preserves unique tail ranks");
      store.mergeShelf(Collections.singletonList(sb),1);
      check(store.book(sc.id).shelfRank==0&&store.book(sb.id).shelfRank==1&&store.book(sa.id).shelfRank==2,"Next shelf page preserves observed prefix");
      check(store.compareShelf(store.book(sb.id),store.book(sa.id))<0,"Shelf follows server rank independently of download priority");
      NativeReader reader=new NativeReader(getTargetContext(),store.dir("demo-1"),new NativeReader.Listener(){
        public void position(int p,float f,int page,int count){}public void boundary(int d){}public void toolbar(){}public void dismissToolbar(){}
      });
      java.lang.reflect.Method imageMethod=NativeReader.class.getDeclaredMethod("image",String.class);imageMethod.setAccessible(true);
      android.graphics.drawable.Drawable drawable=(android.graphics.drawable.Drawable)imageMethod.invoke(reader,"https://offline.hako.invalid/demo-1/"+name);
      java.lang.reflect.Field decoded=NativeReader.class.getDeclaredField("activeImage");decoded.setAccessible(true);
      check(drawable!=null&&decoded.get(reader)==null,"Reader prepares image layout without decoding every bitmap");
      Bitmap screen=Bitmap.createBitmap(480,800,Bitmap.Config.RGB_565);drawable.draw(new android.graphics.Canvas(screen));screen.recycle();
      check(decoded.get(reader)!=null,"Reader decodes image only when drawn");
      chapters.add(new HakoParser.Link("demo-6","Minh họa · Ellie",""));
      store.catalog("demo",chapters);Store.write(store.html("demo-6"),"<p>Ellie</p><img src='https://i.docln.net/missing.jpg'>");store.readable("demo-6");
      check(getTargetContext().getSharedPreferences("settings",0).getInt("chapter_kind_demo-6",0)==IllustrationPolicy.CANDIDATE,"Caption and illustration section produce candidate rather than false certainty");
      check(!store.illustration("demo-6")&&store.illustrationHint(store.chapter("demo","demo-6")),"Candidate affects queue priority but does not bypass read-all");
      result.putString("stream","PASS TOTAL "+checks+" image cache checks\n");
      finish(0,result);
    }catch(Throwable error){
      result.putString("stream","FAIL "+error+"\n");finish(1,result);
    }
  }
}


