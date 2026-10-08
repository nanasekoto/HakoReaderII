package vn.nanase.hako;
public final class CachePolicyTest {
 public static void main(String[] args){
  int requests=0;boolean[] cached=new boolean[1011];
  for(int i=0;i<1008;i++)cached[i]=true;
  for(boolean exists:cached)if(CachePolicy.shouldFetch(exists,false))requests++;
  if(requests!=3)throw new AssertionError("1008/1011 must request exactly 3 chapters");
  // A priority download changes the disk after the queue snapshot was built.
  cached[1008]=true;requests=0;
  for(boolean exists:cached)if(CachePolicy.shouldFetch(exists,false))requests++;
  if(requests!=2)throw new AssertionError("Priority download must not be fetched twice");
  if(CachePolicy.shouldFetch(true,false))throw new AssertionError("Readable text with missing images must be retained");
  if(!CachePolicy.shouldFetch(true,true))throw new AssertionError("Explicit reload must still work");
  if(!CachePolicy.shouldFetch(false,false))throw new AssertionError("Missing/invalid content must be downloaded");
  System.out.println("Cache regression scenarios passed");
 }
}
