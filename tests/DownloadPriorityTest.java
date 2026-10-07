package vn.nanase.hako;
public class DownloadPriorityTest {
 public static void main(String[] args) {
  if(DownloadPriority.rank(true,0,true)!=0)throw new AssertionError("New chapters first even for completed books");
  if(DownloadPriority.rank(false,8,true)!=1)throw new AssertionError("Frequent completed books second");
  if(DownloadPriority.rank(false,0,false)!=2)throw new AssertionError("Ordinary unfinished book");
  if(DownloadPriority.rank(false,1,true)!=3)throw new AssertionError("Rarely read completed book last");
  System.out.println("4 priority scenarios passed");
 }
}
