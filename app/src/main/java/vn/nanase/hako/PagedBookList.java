package vn.nanase.hako;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Whole measured rows; blank tail prevents ListView from backfilling the final page. */
final class PagedBookList extends ListView {
 private final ListPaging measuredPaging=new ListPaging();
 private final View tail;
 private Runnable before,after,changed;
 private float downY;
 private int knownCount=-1;
 PagedBookList(Context context){super(context);setVerticalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);
  if(DeviceProfile.eink(context)){tail=new View(context);tail.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);tail.setLayoutParams(new AbsListView.LayoutParams(-1,1));addFooterView(tail,null,false);}else tail=null;
 }
 void cardsWithControls(boolean value){requestLayout();}
 void compact(){requestLayout();}
 int rowHeight(int position){return -2;}
 int contentCount(){return Math.max(0,getCount()-(tail==null?0:1));}
 boolean hasPreviousPage(){return getFirstVisiblePosition()>0||(getChildCount()>0&&getChildAt(0).getTop()<getPaddingTop());}
 boolean hasNextPage(){int last=Math.min(contentCount()-1,getLastVisiblePosition());if(last<0)return false;View row=getChildAt(last-getFirstVisiblePosition());return last<contentCount()-1||(row!=null&&row.getBottom()>getHeight()-getPaddingBottom());}
 void atEdges(Runnable before,Runnable after){this.before=before;this.after=after;}
 void onPageChanged(Runnable callback){changed=callback;}
 String pageLabel(){int count=contentCount();if(count==0)return "0 truyện";int first=getFirstVisiblePosition(),last=first;for(int n=0;n<getChildCount()&&first+n<count;n++)if(getChildAt(n).getBottom()<=getHeight()-getPaddingBottom())last=first+n;return "Trang "+measuredPaging.page(this)+" · "+(first+1)+"–"+(last+1)+" / "+count+(!hasNextPage()?" · Cuối":"");}
 void turn(int direction){if(contentCount()==0)return;if(direction<0&&!hasPreviousPage()){if(before!=null)before.run();return;}if(direction>0&&!hasNextPage()){if(after!=null)after.run();return;}measuredPaging.turn(this,direction);}
 void fitCard(View view){if(!DeviceProfile.eink(getContext()))return;if(view instanceof TextView){TextView text=(TextView)view;text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);text.setEllipsize(null);}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)fitCard(group.getChildAt(n));}}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(tail!=null&&h>0)tail.setLayoutParams(new AbsListView.LayoutParams(-1,Math.max(1,h-getPaddingTop()-getPaddingBottom())));}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);if(contentCount()!=knownCount){knownCount=contentCount();measuredPaging.reset(this);}if(this.changed!=null)this.changed.run();}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){if(tail==null)return super.onInterceptTouchEvent(event);if(event.getAction()==MotionEvent.ACTION_DOWN)downY=event.getY();return event.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(event.getY()-downY)>32*getResources().getDisplayMetrics().density;}
 @Override public boolean onTouchEvent(MotionEvent event){if(tail==null)return super.onTouchEvent(event);if(event.getAction()==MotionEvent.ACTION_DOWN)downY=event.getY();if(event.getAction()==MotionEvent.ACTION_UP){float distance=event.getY()-downY;if(Math.abs(distance)>32*getResources().getDisplayMetrics().density){turn(distance<0?1:-1);return true;}}if(event.getAction()==MotionEvent.ACTION_MOVE)return true;return super.onTouchEvent(event);}
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){int saved=canvas.save();if(tail!=null&&getChildCount()>0){int limit=getHeight()-getPaddingBottom();View last=getChildAt(getChildCount()-1);if(last.getBottom()>limit){if(getChildCount()>1)limit=last.getTop();else{java.util.TreeSet<Integer> edges=new java.util.TreeSet<>();EinkScrollView.boundaries(last,last.getTop(),limit,edges);Integer edge=edges.floor(limit);if(edge!=null&&edge>getPaddingTop())limit=edge;}}canvas.clipRect(0,getPaddingTop(),getWidth(),limit);}super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
}
