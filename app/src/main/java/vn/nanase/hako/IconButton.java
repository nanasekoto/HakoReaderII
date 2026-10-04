package vn.nanase.hako;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Small monochrome vectors, large touch targets. */
public final class IconButton extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind;
 public IconButton(Context c,int k,String label,Runnable action){super(c);kind=k;setContentDescription(label);setTooltipText(label);setFocusable(true);setClickable(true);setOnClickListener(v->action.run());}
 protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight())*.52f;c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);p.setColor(Color.BLACK);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);
  if(kind==0){Path q=new Path();q.moveTo(2,11);q.lineTo(12,2);q.lineTo(22,11);q.moveTo(5,9);q.lineTo(5,22);q.lineTo(19,22);q.lineTo(19,9);c.drawPath(q,p);}
  else if(kind==1){for(int y=5;y<=19;y+=7){c.drawLine(3,y,5,y,p);c.drawLine(9,y,22,y,p);}}
  else if(kind==2){Path q=new Path();q.moveTo(5,3);q.lineTo(19,3);q.lineTo(19,22);q.lineTo(12,17);q.lineTo(5,22);q.close();c.drawPath(q,p);c.drawLine(9,9,15,9,p);c.drawLine(12,6,12,12,p);}
  else if(kind==3){p.setStyle(Paint.Style.FILL);p.setTextSize(18);p.setTypeface(Typeface.SERIF);c.drawText("Aa",0,19,p);}
  else if(kind==6){c.drawLine(12,1,12,12,p);c.drawArc(3,4,21,23,-55,290,false,p);}
  else if(kind==7){c.drawCircle(12,12,9,p);c.drawLine(12,5,12,12,p);c.drawLine(12,12,17,15,p);}
  else if(kind==8){c.drawArc(3,3,21,21,40,280,false,p);c.drawLine(19,2,21,8,p);c.drawLine(21,8,15,7,p);}
  else if(kind==9){Path q=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?10:4.5f;float x=12+r*(float)Math.cos(a),y=12+r*(float)Math.sin(a);if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}q.close();c.drawPath(q,p);}
  else if(kind==10){c.drawCircle(10,10,7,p);c.drawLine(15,15,22,22,p);}
  else if(kind==11){c.drawCircle(12,7,4,p);c.drawArc(3,12,21,28,180,180,false,p);}
  else if(kind==12){Path q=new Path();q.moveTo(7,3);q.lineTo(21,12);q.lineTo(7,21);q.close();c.drawPath(q,p);}
  else if(kind==13){for(int y=5;y<=19;y+=7)c.drawLine(2,y,22,y,p);c.drawCircle(7,5,2,p);c.drawCircle(17,12,2,p);c.drawCircle(10,19,2,p);}
  else if(kind==14){c.drawOval(1,7,15,17,p);c.drawOval(9,7,23,17,p);}
  else {for(int x=4;x<=20;x+=8)c.drawCircle(x,12,1.5f,p);}c.restore();
 }
}
