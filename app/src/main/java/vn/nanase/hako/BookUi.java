package vn.nanase.hako;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Profile-specific visual primitives. No database, network or reading policy. */
final class BookUi {
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static TextView text(Context c,String value,int size,boolean strong){
  TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(25,28,30));
  t.setTypeface(Typeface.create("sans-serif",strong?Typeface.BOLD:Typeface.NORMAL));
  t.setPadding(0,dp(c,2),0,dp(c,2));t.setLineSpacing(dp(c,2),1f);return t;
 }
 static void gap(LinearLayout parent,int size){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),size)));}
 static void divider(LinearLayout parent){Context c=parent.getContext();View line=new View(c);line.setBackgroundColor(DeviceProfile.eink(c)?Color.BLACK:Color.rgb(226,230,231));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,1);lp.setMargins(0,dp(c,10),0,dp(c,10));parent.addView(line,lp);}
 static Flow flow(Context c){return new Flow(c,dp(c,8),dp(c,6));}
 static void tag(Flow flow,String label){if(label==null||label.isEmpty())return;Context c=flow.getContext();TextView t=text(c,label,13,false);t.setPadding(dp(c,7),dp(c,3),dp(c,7),dp(c,3));GradientDrawable bg=new GradientDrawable();bg.setColor(DeviceProfile.eink(c)?Color.WHITE:Color.rgb(240,244,243));bg.setCornerRadius(dp(c,DeviceProfile.eink(c)?4:6));if(DeviceProfile.eink(c))bg.setStroke(1,Color.BLACK);t.setBackground(bg);flow.addView(t);}
 static void statistic(Flow flow,String label,String value){if(value==null||value.isEmpty()||value.equals("Chưa rõ"))return;Context c=flow.getContext();LinearLayout item=new LinearLayout(c);item.setOrientation(1);item.setPadding(0,dp(c,3),dp(c,8),dp(c,5));item.addView(text(c,label,13,false));item.addView(text(c,value,DeviceProfile.eink(c)?14:15,true));int width=dp(c,DeviceProfile.eink(c)?140:112);float font=c.getResources().getConfiguration().fontScale;item.setMinimumWidth(Math.round(width*Math.min(1.6f,font)));flow.addView(item);}
 /** Natural children wrap as units. Oversized labels measure to the available width. */
 static final class Flow extends ViewGroup {
  final int horizontal,vertical;
  Flow(Context c,int h,int v){super(c);horizontal=h;vertical=v;}
  protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws),available=Math.max(1,width-getPaddingLeft()-getPaddingRight());int x=0,y=0,row=0;
   for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getVisibility()==GONE)continue;v.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int w=v.getMeasuredWidth(),h=v.getMeasuredHeight();if(x>0&&x+w>available){x=0;y+=row+vertical;row=0;}x+=w+horizontal;row=Math.max(row,h);}
   setMeasuredDimension(resolveSize(width,ws),resolveSize(y+row+getPaddingTop()+getPaddingBottom(),hs));}
  protected void onLayout(boolean changed,int l,int t,int r,int b){int x=getPaddingLeft(),y=getPaddingTop(),row=0,right=getWidth()-getPaddingRight();for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getVisibility()==GONE)continue;int w=v.getMeasuredWidth(),h=v.getMeasuredHeight();if(x>getPaddingLeft()&&x+w>right){x=getPaddingLeft();y+=row+vertical;row=0;}v.layout(x,y,x+w,y+h);x+=w+horizontal;row=Math.max(row,h);}}
  protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-2,-2);}
 }
}
