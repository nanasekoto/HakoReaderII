package vn.nanase.hako;
import java.util.*;
/** Line geometry only. The saved line is a page boundary, preventing resume drift. */
public final class PageBreaks {
 public static List<Integer> split(int[] top,int[] bottom,int height,int anchor){
  if(top.length!=bottom.length+1||height<=0)throw new IllegalArgumentException();
  ArrayList<Integer> result=new ArrayList<>();int first=0;
  while(first<bottom.length){result.add(first);int next=first;
   while(next<bottom.length&&bottom[next]-top[first]<=height){if(next==anchor&&next>first)break;next++;}
   first=Math.max(first+1,next);
  }return result;
 }
}
