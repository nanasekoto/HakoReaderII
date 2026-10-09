package vn.nanase.hako;
public final class DownloadScopeTest {
 public static void main(String[] args){
  int requested=0;
  // 2,000 unseen shelf books may have updates/pins/imported web timestamps.
  for(int i=0;i<2000;i++)if(DownloadScope.eligible(false,false,false))requested++;
  if(requested!=0)throw new AssertionError("Unseen shelf books must not download");
  if(!DownloadScope.eligible(true,false,false))throw new AssertionError("Local reading must qualify from first visit");
  if(!DownloadScope.eligible(false,true,false))throw new AssertionError("Manual full download must qualify");
  if(!DownloadScope.eligible(false,false,true))throw new AssertionError("Explicit auto-download must qualify");
  // New eligibility filtering must not change remaining-chapter cache policy.
  int missing=0;for(int i=0;i<1011;i++)if(DownloadScope.eligible(true,false,false)&&CachePolicy.shouldFetch(i<1008,false))missing++;
  if(missing!=3)throw new AssertionError("Read book 1008/1011 must request three missing chapters");
  System.out.println("Download scope regression passed");
 }
}
