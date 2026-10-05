package vn.nanase.hako;

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/** Sizes cards from the actual allotted viewport, before ListView lays out rows. */
final class PagedBookList extends ListView {
  private int rows = 5, base = 1, remainder;

  PagedBookList(Context context) {
    super(context);
    setVerticalScrollBarEnabled(false);
    setOnScrollListener(new OnScrollListener() {
      public void onScroll(AbsListView v, int first, int visible, int total) {}
      public void onScrollStateChanged(AbsListView v, int state) {
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
    int minimum = (int)Math.ceil(74 * getResources().getDisplayMetrics().scaledDensity);
    rows = Math.max(1, Math.min(5, (available+getDividerHeight())/(minimum+getDividerHeight())));
    int content = Math.max(rows, available-(rows-1)*getDividerHeight());
    base = content/rows;
    remainder = content%rows;
    for (int i=0;i<getChildCount();i++) {
      View child=getChildAt(i);
      child.getLayoutParams().height=rowHeight(getFirstVisiblePosition()+i);
    }
    super.onMeasure(widthSpec,heightSpec);
  }

  int rowHeight(int position) { return base + (position%rows < remainder ? 1 : 0); }

  void turn(int direction) {
    if (getCount()==0) return;
    int first=getFirstVisiblePosition();
    int target=direction>0 ? Math.min(Math.max(0,getCount()-rows),first+rows)
        : Math.max(0,first-rows);
    setSelectionFromTop(target,0);
  }
}
