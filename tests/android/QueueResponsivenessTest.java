package vn.nanase.hako.tests;
import android.app.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import vn.nanase.hako.*;

/** Reproduce the exact monitor held by the synchronized chapter downloader. */
public final class QueueResponsivenessTest extends Instrumentation {
 int checks;
 void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 Object invoke(Method method,Object...args)throws Exception{final Object[] result={null};runOnMainSync(()->{try{result[0]=method.invoke(null,args);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();CountDownLatch release=new CountDownLatch(1);Thread holder=null;try{
  Method queue=Repository.class.getDeclaredMethod("queueBook",String.class,int.class),take=Repository.class.getDeclaredMethod("takeBook"),peek=Repository.class.getDeclaredMethod("peekBook");queue.setAccessible(true);take.setAccessible(true);peek.setAccessible(true);
  CountDownLatch entered=new CountDownLatch(1);holder=new Thread(()->{synchronized(Repository.class){entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(Exception ignored){}}});holder.start();entered.await(2,TimeUnit.SECONDS);
  long begun=SystemClock.elapsedRealtime();invoke(queue,"queue-fixture",Repository.MODE_BOOK_ALL);check(SystemClock.elapsedRealtime()-begun<1000,"Main-thread service queue handoff remains responsive while downloader holds Repository.class");
  invoke(queue,"queue-fixture",Repository.MODE_READER_NEXT);Object request=invoke(peek);Field book=request.getClass().getDeclaredField("book"),mode=request.getClass().getDeclaredField("mode");book.setAccessible(true);mode.setAccessible(true);check(book.get(request).equals("queue-fixture")&&mode.getInt(request)==Repository.MODE_BOOK_ALL,"Reader handoff preserves already queued full-download request");
  begun=SystemClock.elapsedRealtime();Object taken=invoke(take);check(SystemClock.elapsedRealtime()-begun<1000&&mode.getInt(taken)==Repository.MODE_BOOK_ALL,"Taking request does not wait on downloader monitor and keeps matching mode");
  check(book.get(invoke(peek)).equals(""),"Taking request atomically clears pending book");
  result.putString("stream","PASS TOTAL "+checks+" queue responsiveness checks\n");finish(0,result);
 }catch(Throwable error){result.putString("stream","FAIL "+error+"\n");finish(1,result);}finally{release.countDown();if(holder!=null)try{holder.join(1000);}catch(Exception ignored){}}}
}

