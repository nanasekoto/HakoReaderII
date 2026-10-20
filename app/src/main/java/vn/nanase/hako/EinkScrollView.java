package vn.nanase.hako;
import android.content.Context;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Stable pages over natural content; blank tail lives outside the content. */
final class EinkScrollView extends ScrollView {
 private final ArrayList<Integer> pages=new ArrayList<>();
 private FrameLayout frame;private View content;
 private int measuredWidth=-1,measuredHeight=-1,contentHeight=-1;
 private Runnable changed;private boolean layingOut;private float touchY;
 EinkScrollView(Context c){super(c);setSmoothScrollingEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setVerticalScrollBarEnabled(false);frame=new FrameLayout(c);super.addView(frame,new ScrollView.LayoutParams(-1,-2));}
 @Override public void addView(View view){addView(view,new ViewGroup.LayoutParams(-1,-2));}
 @Override public void addView(View view,ViewGroup.LayoutParams params){if(frame==null||view==frame){super.addView(view,params);return;}if(content!=null)throw new IllegalStateException("Only one content view");content=view;frame.addView(view,new FrameLayout.LayoutParams(params.width,params.height));}
 @Override public void removeView(View view){if(view==content){frame.removeView(view);content=null;pages.clear();contentHeight=-1;}else super.removeView(view);}
 void onPageChanged(Runnable callback){changed=callback;}
 static void boundaries(View view,int top,int height,TreeSet<Integer> edges){
  if(view.getVisibility()==GONE)return;edges.add(top);edges.add(top+view.getHeight());if(view.getHeight()<=height)return;
  if(view instanceof TextView){TextView text=(TextView)view;Layout layout=text.getLayout();if(layout!=null)for(int line=1;line<layout.getLineCount();line++)edges.add(top+text.getTotalPaddingTop()+layout.getLineTop(line));}
  else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View child=group.getChildAt(n);boundaries(child,top+child.getTop(),height,edges);}for(int n=0;n+1<group.getChildCount();n++){View heading=group.getChildAt(n),next=group.getChildAt(n+1);if("keep_next".equals(heading.getTag())&&next.getBottom()-heading.getTop()<=height){int first=top+heading.getTop(),end=top+next.getBottom();edges.removeIf(edge->edge>first&&edge<end);}}}
 }

 private void prepare(){
  if(layingOut||content==null||getHeight()<=0)return;
  int height=getHeight()-getPaddingTop()-getPaddingBottom(),naturalHeight=content.getHeight();
  if(height<=0||content.getWidth()<=0)return;
  if(measuredWidth==getWidth()&&measuredHeight==height&&contentHeight==naturalHeight)return;
  measuredWidth=getWidth();measuredHeight=height;contentHeight=naturalHeight;
  TreeSet<Integer> edges=new TreeSet<>(),breaks=new TreeSet<>();boundaries(content,0,height,edges);if(content instanceof ViewGroup){ViewGroup group=(ViewGroup)content;for(int n=0;n<group.getChildCount();n++){View child=group.getChildAt(n);if("page_start".equals(child.getTag())){breaks.add(child.getTop());edges.add(child.getTop());}}}pages.clear();pages.add(0);int start=0;
  while(start+height<naturalHeight||breaks.higher(start)!=null){Integer end=edges.floor(start+height);Integer forced=breaks.higher(start);if(forced!=null&&forced<=start+height)end=forced;if(end==null||end<=start)end=Math.min(naturalHeight,start+height);pages.add(end);start=end;}
  int minimum=Math.max(naturalHeight,start+height);if(frame.getMinimumHeight()!=minimum)frame.setMinimumHeight(minimum);
 }
 int page(){prepare();int index=Collections.binarySearch(pages,getScrollY());return Math.min(Math.max(0,pages.size()-1),Math.max(0,index>=0?index:-index-2));}
 int pageCount(){prepare();return Math.max(1,pages.size());}
 void turn(int direction){prepare();if(pages.isEmpty())return;int next=Math.max(0,Math.min(pages.size()-1,page()+direction));scrollTo(0,pages.get(next));if(changed!=null)changed.run();}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layingOut=true;super.onLayout(changed,l,t,r,b);layingOut=false;prepare();if(this.changed!=null)this.changed.run();}
 @Override public void scrollTo(int x,int y){if(layingOut){super.scrollTo(0,y);return;}prepare();int max=pages.isEmpty()?0:pages.get(pages.size()-1);super.scrollTo(0,Math.max(0,Math.min(y,max)));}
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){prepare();int next=page()+1,saved=canvas.save();if(next<pages.size())canvas.clipRect(0,getScrollY(),getWidth(),pages.get(next));super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN)touchY=event.getY();return super.onInterceptTouchEvent(event);}
 @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN)touchY=event.getY();if(event.getAction()==MotionEvent.ACTION_UP&&Math.abs(event.getY()-touchY)>32*getResources().getDisplayMetrics().density)turn(event.getY()<touchY?1:-1);return true;}
 @Override public void fling(int velocity){}
}
