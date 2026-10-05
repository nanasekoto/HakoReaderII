package vn.nanase.hako;

import android.view.View;
import android.widget.ListView;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

/** Page through the measured viewport; never skip a partially visible row. */
final class ListPaging {
  private final WeakHashMap<ListView, ArrayDeque<Integer>> previous = new WeakHashMap<>();
  void turn(ListView list, int direction) {
    if (list.getCount() == 0 || list.getChildCount() == 0) return;
    int first = list.getFirstVisiblePosition();
    ArrayDeque<Integer> history = previous.get(list);
    if (history == null) { history = new ArrayDeque<>(); previous.put(list, history); }
    int target;
    if (direction > 0) {
      int last = list.getLastVisiblePosition();
      View bottom = list.getChildAt(list.getChildCount() - 1);
      target = bottom.getBottom() > list.getHeight() - list.getPaddingBottom() ? last : last + 1;
      if (target >= list.getCount()) return;
      if (target <= first) { list.scrollListBy(Math.max(1, list.getHeight() / 2)); return; }
      history.push(first);
    } else {
      while (!history.isEmpty() && history.peek() >= first) history.pop();
      target = history.isEmpty() ? Math.max(0, first - Math.max(1, list.getChildCount() - 1)) : history.pop();
    }
    list.setSelectionFromTop(target, list.getPaddingTop());
  }
}
