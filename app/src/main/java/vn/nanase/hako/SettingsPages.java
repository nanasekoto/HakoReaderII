package vn.nanase.hako;
import android.content.Context;
import android.view.Gravity;
import android.widget.*;
/** Whole controls and line-aligned continuation for oversized text. */
public final class SettingsPages extends LinearLayout {
 private final EinkScrollView viewport;
 public SettingsPages(Context c,LinearLayout rows){super(c);setOrientation(VERTICAL);viewport=new EinkScrollView(c);viewport.addView(rows);addView(viewport,new LinearLayout.LayoutParams(-1,0,1));LinearLayout controls=new LinearLayout(c);Button previous=new Button(c);previous.setText("← Trước");previous.setOnClickListener(v->turn(-1));controls.addView(previous,new LinearLayout.LayoutParams(0,-2,1));TextView count=new TextView(c);count.setGravity(Gravity.CENTER);controls.addView(count,new LinearLayout.LayoutParams(0,-1,1));Button next=new Button(c);next.setText("Sau →");next.setOnClickListener(v->turn(1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));addView(controls);viewport.onPageChanged(()->{int page=viewport.page(),total=viewport.pageCount();count.setText((page+1)+" / "+total);previous.setEnabled(page>0);next.setEnabled(page+1<total);});}
 public void turn(int direction){viewport.turn(direction);}
}
