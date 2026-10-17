package vn.nanase.hako;
import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.*;
/** Measured content boundaries shared by detail panels and settings on e-ink. */
final class EinkScrollView extends ScrollView {
 private final ArrayList<Integer> pages=new ArrayList<>();
 private int measuredWidth=-1,measuredHeight=-1,contentHeight=-1,tail;
 private Runnable changed;
 private boolean paddingPending;private float touchY;
 EinkScrollView(Context c){super(c);setSmoothScrollingEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setVerticalScrollBarEnabled(false);}
 void onPageChanged(Runnable callback){changed=callback;}
 private void boundaries(View view,int top,int height,TreeSet<Integer> edges){
  if(view.getVisibility()==GONE)return;edges.add(top);edges.add(top+view.getHeight());if(view.getHeight()<=height)return;
  if(view instanceof TextView){TextView text=(TextView)view;Layout layout=text.getLayout();if(layout!=null)for(int line=1;line<layout.getLineCount();line++)edges.add(top+text.getTotalPaddingTop()+layout.getLineTop(line));}
  else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View child=group.getChildAt(n);boundaries(child,top+child.getTop(),height,edges);}for(int n=0;n+1<group.getChildCount();n++){View heading=group.getChildAt(n),next=group.getChildAt(n+1);if("keep_next".equals(heading.getTag())&&next.getBottom()-heading.getTop()<=height){int first=top+heading.getTop(),end=top+next.getBottom();edges.removeIf(edge->edge>first&&edge<end);}}}
 }
 private void prepare(){if(paddingPending||getChildCount()==0||getHeight()<=0)return;View content=getChildAt(0);int height=getHeight()-getPaddingTop()-getPaddingBottom(),naturalHeight=content.getHeight()-tail;if(height<=0)return;if(measuredWidth==getWidth()&&measuredHeight==height&&contentHeight==naturalHeight)return;measuredWidth=getWidth();measuredHeight=height;contentHeight=naturalHeight;TreeSet<Integer> edges=new TreeSet<>();boundaries(content,0,height,edges);edges.removeIf(edge->edge>naturalHeight);pages.clear();pages.add(0);int start=0;while(start+height<contentHeight){Integer end=edges.floor(start+height);if(end==null||end<=start)end=Math.min(contentHeight,start+height);pages.add(end);start=end;}int extra=Math.max(0,start+height-naturalHeight);if(extra!=tail){int original=content.getPaddingBottom()-tail;tail=extra;paddingPending=true;content.setPadding(content.getPaddingLeft(),content.getPaddingTop(),content.getPaddingRight(),original+tail);}}
 int page(){prepare();int index=Collections.binarySearch(pages,getScrollY());return Math.max(0,index>=0?index:-index-2);}
 int pageCount(){prepare();return Math.max(1,pages.size());}
 void turn(int direction){prepare();if(pages.isEmpty())return;int next=Math.max(0,Math.min(pages.size()-1,page()+direction));scrollTo(0,pages.get(next));if(changed!=null)changed.run();}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);paddingPending=false;prepare();if(this.changed!=null)this.changed.run();}
 // Last page may start after the ordinary scroll maximum so preceding controls
 // are not pulled back into the top of this page by ScrollView's end clamp.
 @Override public void scrollTo(int x,int y){prepare();int max=pages.isEmpty()?0:pages.get(pages.size()-1);super.scrollTo(0,Math.max(0,Math.min(y,max)));}
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){prepare();int next=page()+1;int saved=canvas.save();if(next<pages.size())canvas.clipRect(0,getScrollY(),getWidth(),pages.get(next));super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
 @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)touchY=event.getY();return super.onInterceptTouchEvent(event);}
 @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)touchY=event.getY();if(event.getAction()==android.view.MotionEvent.ACTION_UP&&Math.abs(event.getY()-touchY)>32*getResources().getDisplayMetrics().density)turn(event.getY()<touchY?1:-1);return true;}
 @Override public void fling(int velocity){/* Stable pages on e-ink, no animated fling. */}
}
