package vn.nanase.hako;
public final class DownloadLedgerTest {
 public static void main(String[] args){
  DownloadLedger l=new DownloadLedger();
  for(int pass=0;pass<2;pass++)for(int i=1008;i<1011;i++)l.add("chapter-"+i);
  if(l.total()!=3)throw new AssertionError("Repeated 1008/1011 refresh must count only 3 missing chapters");
  l.complete("chapter-1008");l.complete("chapter-1008");l.complete("outside-queue");
  if(l.done()!=1)throw new AssertionError("Priority and normal loop must count completion once");
  if(DownloadLedger.percent(0,0)!=0||DownloadLedger.percent(24,25)!=96||DownloadLedger.percent(25,25)!=100||DownloadLedger.percent(199,200)!=99)throw new AssertionError("Only a completed queue reaches 100 percent");
  l.clear();l.add("new");if(l.total()!=1||l.done()!=0)throw new AssertionError("Refresh must reset ledger");
  System.out.println("Unique queue regression passed");
 }
}

