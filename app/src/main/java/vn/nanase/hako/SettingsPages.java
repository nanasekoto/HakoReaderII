package vn.nanase.hako;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Whole rows on e-ink. No partial settings hidden behind the fixed footer. */
public final class SettingsPages extends LinearLayout {
 private final LinearLayout rows;private final FrameLayout viewport;private final TextView count;private int page;private final List<Integer> starts=new ArrayList<>();
 public SettingsPages(Context c,LinearLayout content){super(c);setOrientation(VERTICAL);rows=content;viewport=new FrameLayout(c);viewport.addView(rows,new FrameLayout.LayoutParams(-1,-2));addView(viewport,new LinearLayout.LayoutParams(-1,0,1));LinearLayout controls=new LinearLayout(c);Button previous=new Button(c);previous.setText("← Trước");previous.setOnClickListener(v->turn(-1));controls.addView(previous,new LinearLayout.LayoutParams(0,-2,1));count=new TextView(c);count.setGravity(Gravity.CENTER);controls.addView(count,new LinearLayout.LayoutParams(0,-1,1));Button next=new Button(c);next.setText("Sau →");next.setOnClickListener(v->turn(1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));addView(controls);viewport.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if((r-l)!=(or-ol)||(b-t)!=(ob-ot))post(this::paginate);});}
 private void paginate(){int width=viewport.getWidth(),height=viewport.getHeight();if(width<=0||height<=0)return;starts.clear();starts.add(0);int used=rows.getPaddingTop()+rows.getPaddingBottom();for(int n=0;n<rows.getChildCount();n++){View row=rows.getChildAt(n);row.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-rows.getPaddingLeft()-rows.getPaddingRight()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)row.getLayoutParams();int h=row.getMeasuredHeight()+lp.topMargin+lp.bottomMargin;if(used+h>height&&used>rows.getPaddingTop()+rows.getPaddingBottom()){starts.add(n);used=rows.getPaddingTop()+rows.getPaddingBottom();}used+=h;}page=Math.min(page,starts.size()-1);show();}
 private void show(){if(starts.isEmpty())return;int first=starts.get(page),end=page+1<starts.size()?starts.get(page+1):rows.getChildCount();for(int n=0;n<rows.getChildCount();n++)rows.getChildAt(n).setVisibility(n>=first&&n<end?VISIBLE:GONE);count.setText((page+1)+" / "+starts.size());}
 public void turn(int dir){if(starts.isEmpty())paginate();page=Math.max(0,Math.min(starts.size()-1,page+dir));show();}
}
