package vn.nanase.hako;
public final class ReadingRangeTest {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  check(!FetchPolicy.toEnd(false,false,false),"unsaved limited");
  check(FetchPolicy.toEnd(true,false,false),"shelf to end");
  check(FetchPolicy.toEnd(false,true,false),"favorite to end");
  check(FetchPolicy.toEnd(false,false,true),"full");
  int limited=0,saved=0,full=0;
  for(int ord=0;ord<1011;ord++){
   if(FetchPolicy.inDownloadRange(ord,10,false,false))limited++;
   if(FetchPolicy.inDownloadRange(ord,10,true,false))saved++;
   if(FetchPolicy.inDownloadRange(ord,10,true,true))full++;
  }
  check(limited==19,"3 previous + current + 15 next");
  check(saved==1004,"3 previous + current through end");
  check(full==1011,"full retains past");
  check(!FetchPolicy.inDownloadRange(6,10,true,false),"past outside three");
  check(FetchPolicy.inDownloadRange(7,10,true,false),"third previous");
  check(FetchPolicy.inDownloadRange(25,10,false,false),"fifteenth next");
  check(!FetchPolicy.inDownloadRange(26,10,false,false),"sixteenth next excluded");
  check(FetchPolicy.inDownloadRange(1010,10,true,false),"saved final chapter");
  check(FetchPolicy.inDownloadRange(0,0,false,false),"first current");
  System.out.println("ReadingRangeTest passed");
 }
}
