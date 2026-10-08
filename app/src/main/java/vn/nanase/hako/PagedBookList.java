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
  private int scrollState=SCROLL_STATE_IDLE;
  private final Runnable alignFirst=new Runnable(){public void run(){
    if(scrollState!=SCROLL_STATE_IDLE||getChildCount()==0||getCount()==0)return;
    View first=getChildAt(0);
    if(first.getTop()!=getPaddingTop())setSelectionFromTop(getFirstVisiblePosition(),0);
  }};
  private final java.util.ArrayDeque<Integer> previous = new java.util.ArrayDeque<>();

  PagedBookList(Context context) {
    super(context);
    setVerticalScrollBarEnabled(false);
    setOverScrollMode(View.OVER_SCROLL_NEVER);
    setOnScrollListener(new OnScrollListener() {
      public void onScroll(AbsListView v, int first, int visible, int total) {}
      public void onScrollStateChanged(AbsListView v, int state) {
        scrollState=state;
        if (state == SCROLL_STATE_IDLE && getChildCount() > 0) {
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
    int available = Math.max(1, View.MeasureSpec.getSize(heightSpec)
        - getPaddingTop() - getPaddingBottom());
    // Two title lines, two metadata lines and card padding at the configured font scale.
    float scaled=getResources().getDisplayMetrics().scaledDensity;
    float density=getResources().getDisplayMetrics().density;
    // Reserve two title lines plus metadata or the actual 38dp control row.
    // Reduce the row count instead of squeezing controls into a shorter card.
    int minimum = compact?(int)Math.ceil(54*scaled):
      (int)Math.ceil(40*scaled+Math.max(15*scaled,controls?38*density:0)+8*density);
    rows = Math.max(1, Math.min(compact?10:6, (available+getDividerHeight())/(minimum+getDividerHeight())));
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

  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
    super.onSizeChanged(w,h,oldw,oldh);
    if(h!=oldh){removeCallbacks(alignFirst);post(alignFirst);}
  }

  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
    super.onLayout(changed,l,t,r,b);
    // ListView may preserve a negative first-row offset when the header/viewport changes.
    // Keep the whole card, including its rounded top border, inside the padded viewport.
    if(scrollState==SCROLL_STATE_IDLE&&getChildCount()>0&&getChildAt(0).getTop()!=getPaddingTop()){
      removeCallbacks(alignFirst);post(alignFirst);
    }
  }

  @Override protected void onDetachedFromWindow(){
    removeCallbacks(alignFirst);super.onDetachedFromWindow();
  }

  int rowHeight(int position) { return base + (position%rows < remainder ? 1 : 0); }

  void turn(int direction) {
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
}
