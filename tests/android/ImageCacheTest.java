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
      check(store.progress(store.book("demo")).percent==67,"Missing image keeps actual progress below 100");
      Store.write(first,"<html>Access denied</html>");
      check(!Store.imageFileReadable(first),"HTML response is not an image");
      check(!store.readable("demo-1"),"Corrupt image is not offline ready");
      image(first);
      check(store.readable("demo-1"),"Repair only image restores chapter");
      check(store.progress(store.book("demo")).percent==100,"Repair reaches 100 without reloading chapter HTML");
      String original=Store.read(store.html("demo-1"));
      chapters.add(new HakoParser.Link("demo-4","New chapter",""));
      store.catalog("demo",chapters);
      check(store.progress(store.book("demo")).percent==75,"New chapter leaves exactly one missing chapter");
      check(Store.read(store.html("demo-1")).equals(original),"Catalog update preserves image chapter HTML");
      check(!CachePolicy.shouldFetch(store.readable("demo-1"),false),"New chapter does not refetch saved image chapter");
      Store.write(store.html("demo-4"),"<p>[Ảnh ngoài máy chủ HAKO — xem trên web]</p>");
      store.state("demo-4",true,"");
      check(!store.readable("demo-4"),"Old placeholder-only chapter is not falsely complete");
      Store.write(store.html("demo-4"),"<p>Real text</p><img src='https://i.docln.net/missing.jpg'>");
      check(store.readable("demo-4"),"Missing illustration does not discard valid text");
      check(store.progress(store.book("demo")).percent==100,"Text and image chapters share one progress policy");
      result.putString("stream","PASS TOTAL "+checks+" image cache checks\n");
      finish(0,result);
    }catch(Throwable error){
      result.putString("stream","FAIL "+error+"\n");finish(1,result);
    }
  }
}
