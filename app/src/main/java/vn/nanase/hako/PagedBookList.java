package vn.nanase.hako;

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/** Sizes cards from the actual allotted viewport, before ListView lays out rows. */
final class PagedBookList extends ListView {
  private boolean compact=false,controls=false;
  void cardsWithControls(boolean value){controls=value;requestLayout();}
  void compact(){compact=true;requestLayout();}
  private int rows = 6, base = 1, remainder;
  private int scrollState=OnScrollListener.SCROLL_STATE_IDLE;
  private final java.util.ArrayDeque<Integer> previous = new java.util.ArrayDeque<>();
  private final ListPaging measuredPaging=new ListPaging();
  void fitCard(View view){if(!DeviceProfile.eink(getContext()))return;if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);text.setEllipsize(null);}else if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)fitCard(group.getChildAt(n));}}

  PagedBookList(Context context) {
    super(context);
    setVerticalScrollBarEnabled(false);
    setOverScrollMode(View.OVER_SCROLL_NEVER);
    setOnScrollListener(new OnScrollListener() {
      public void onScroll(AbsListView v, int first, int visible, int total) {}
      public void onScrollStateChanged(AbsListView v, int state) {
        scrollState=state;
        if (!DeviceProfile.eink(getContext()) && state == OnScrollListener.SCROLL_STATE_IDLE && getChildCount() > 0) {
          View first = getChildAt(0);
          if (first.getTop() != getPaddingTop()) {
            int target = getFirstVisiblePosition();
            if (getPaddingTop()-first.getTop() > first.getHeight()/2) target++;
            setSelectionFromTop(Math.min(target, Math.max(0,getCount()-rows)), 0);
          }
        }
      }
    });
  }

  @Override protected void onMeasure(int widthSpec, int heightSpec) {
    if(DeviceProfile.eink(getContext())){super.onMeasure(widthSpec,heightSpec);return;}
    int available = Math.max(1, View.MeasureSpec.getSize(heightSpec)
        - getPaddingTop() - getPaddingBottom());
    // Two title lines, two metadata lines and card padding at the configured font scale.
    float scaled=getResources().getDisplayMetrics().scaledDensity;
    float density=getResources().getDisplayMetrics().density;
    // Reserve two title lines plus metadata or the actual 38dp control row.
    // Reduce the row count instead of squeezing controls into a shorter card.
    int minimum = compact?(int)Math.ceil(54*scaled):
      (int)Math.ceil(40*scaled+Math.max(15*scaled,controls?38*density:0)+8*density);
    int maximum=DeviceProfile.eink(getContext())?(compact?10:6):12;
    rows = Math.max(1, Math.min(maximum, (available+getDividerHeight())/(minimum+getDividerHeight())));
    int content = Math.max(rows, available-(rows-1)*getDividerHeight());
    base = content/rows;
    remainder = content%rows;
    for (int i=0;i<getChildCount();i++) {
      View child=getChildAt(i);
      int height=rowHeight(getFirstVisiblePosition()+i);
      if (child.getLayoutParams().height!=height) {
        child.getLayoutParams().height=height; child.requestLayout();
      }
    }
    super.onMeasure(widthSpec,heightSpec);
  }

  int rowHeight(int position) { return DeviceProfile.eink(getContext())?-2:base + (position%rows < remainder ? 1 : 0); }

  void turn(int direction) {
    if(DeviceProfile.eink(getContext())){measuredPaging.turn(this,direction);return;}
    if (getCount()==0) return;
    int first=getFirstVisiblePosition();
    int target=direction>0 ? Math.min(Math.max(0,getCount()-rows),first+rows)
        : Math.max(0,first-rows);
    if (direction>0 && target>first) previous.push(first);
    else if (direction<0) {
      while (!previous.isEmpty() && previous.peek()>=first) previous.pop();
      if (!previous.isEmpty()) target=previous.pop();
    }
    setSelectionFromTop(target,0);
  }
  @Override protected void dispatchDraw(android.graphics.Canvas canvas){int saved=canvas.save();if(DeviceProfile.eink(getContext())&&getChildCount()>0){int limit=getHeight()-getPaddingBottom();View last=getChildAt(getChildCount()-1);if(last.getBottom()>limit){if(getChildCount()>1)limit=last.getTop();else{java.util.TreeSet<Integer> edges=new java.util.TreeSet<>();EinkScrollView.boundaries(last,last.getTop(),limit,edges);Integer edge=edges.floor(limit);if(edge!=null&&edge>getPaddingTop())limit=edge;}}canvas.clipRect(0,getPaddingTop(),getWidth(),limit);}super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
}
