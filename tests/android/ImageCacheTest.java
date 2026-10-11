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
      Store store=Store.get(getTargetContext());
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
      check(blocked,"Read-all requires explicit consent for unseen illustrations");
      store.queueCaughtUp("demo",true);
      check(!store.wasRead("demo-1"),"Consent does not fabricate local illustration reading");
      check(store.canSubmitServer("demo"),"Fresh matching catalog permits explicitly accepted illustrations");
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
      result.putString("stream","PASS TOTAL "+checks+" image cache checks\n");
      finish(0,result);
    }catch(Throwable error){
      result.putString("stream","FAIL "+error+"\n");finish(1,result);
    }
  }
}
