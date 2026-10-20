package vn.nanase.hako;

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/** Sizes cards from the actual allotted viewport, before ListView lays out rows. */
final class PagedBookList extends ListView {
  void cardsWithControls(boolean value){requestLayout();}
  void compact(){requestLayout();}
  private final ListPaging measuredPaging=new ListPaging();
  void fitCard(View view){if(!DeviceProfile.eink(getContext()))return;if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);text.setEllipsize(null);}else if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)fitCard(group.getChildAt(n));}}

  PagedBookList(Context context) {
    super(context);
    setVerticalScrollBarEnabled(false);
    setOverScrollMode(View.OVER_SCROLL_NEVER);
  }
  int rowHeight(int position){return -2;}
  private Runnable before,after;
  void atEdges(Runnable before,Runnable after){this.before=before;this.after=after;}
  void turn(int direction){if(getChildCount()>0){if(direction<0&&before!=null&&getFirstVisiblePosition()==0&&getChildAt(0).getTop()>=getPaddingTop()){before.run();return;}if(direction>0&&after!=null&&getLastVisiblePosition()==getCount()-1&&getChildAt(getChildCount()-1).getBottom()<=getHeight()-getPaddingBottom()){after.run();return;}}measuredPaging.turn(this,direction);}
  @Override protected void dispatchDraw(android.graphics.Canvas canvas){int saved=canvas.save();if(DeviceProfile.eink(getContext())&&getChildCount()>0){int limit=getHeight()-getPaddingBottom();View last=getChildAt(getChildCount()-1);if(last.getBottom()>limit){if(getChildCount()>1)limit=last.getTop();else{java.util.TreeSet<Integer> edges=new java.util.TreeSet<>();EinkScrollView.boundaries(last,last.getTop(),limit,edges);Integer edge=edges.floor(limit);if(edge!=null&&edge>getPaddingTop())limit=edge;}}canvas.clipRect(0,getPaddingTop(),getWidth(),limit);}super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
}
