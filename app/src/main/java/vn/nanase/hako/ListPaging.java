package vn.nanase.hako;
import android.view.View;
import android.widget.ListView;
import java.util.*;
/** Whole measured rows, with exact offset history for a row taller than a page. */
final class ListPaging {
 private final WeakHashMap<ListView,ArrayDeque<int[]>> previous=new WeakHashMap<>();
 int page(ListView list){ArrayDeque<int[]> history=previous.get(list);return history==null?1:history.size()+1;}
 void reset(ListView list){previous.remove(list);}
 void turn(ListView list,int direction){if(list.getCount()==0||list.getChildCount()==0)return;int first=list.getFirstVisiblePosition(),padding=list.getPaddingTop(),offset=padding-list.getChildAt(0).getTop(),height=list.getHeight()-padding-list.getPaddingBottom();ArrayDeque<int[]> history=previous.get(list);if(history==null){history=new ArrayDeque<>();previous.put(list,history);}
  if(direction<0){while(!history.isEmpty()&&(history.peek()[0]>first||history.peek()[0]==first&&history.peek()[1]>=offset))history.pop();if(!history.isEmpty()){int[] point=history.pop();list.setSelectionFromTop(point[0],padding-point[1]);return;}if(offset>0){list.setSelectionFromTop(first,padding);return;}list.setSelectionFromTop(Math.max(0,first-Math.max(1,list.getChildCount()-1)),padding);return;}
  int count=list instanceof PagedBookList?((PagedBookList)list).contentCount():list.getCount();int last=Math.min(count-1,list.getLastVisiblePosition());View bottom=list.getChildAt(last-first);if(bottom==null)return;int target=bottom.getBottom()>list.getHeight()-list.getPaddingBottom()?last:last+1;if(target>=count)return;history.push(new int[]{first,offset});
  if(target<=first){View row=list.getChildAt(0);TreeSet<Integer> edges=new TreeSet<>();EinkScrollView.boundaries(row,0,height,edges);Integer stop=edges.floor(offset+height);int next=stop==null||stop<=offset?offset+height:stop;list.setSelectionFromTop(first,padding-next);}else list.setSelectionFromTop(target,padding);
 }
}
