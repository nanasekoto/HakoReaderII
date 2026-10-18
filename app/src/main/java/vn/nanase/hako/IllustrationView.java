package vn.nanase.hako;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Image gestures are fully contained here; they never reach chapter navigation. */
final class IllustrationView extends ImageView {
    final Matrix transform=new Matrix();
    final ScaleGestureDetector pinch;
    final GestureDetector gestures;
    float scale=1,fit=1,lastX,lastY;
    boolean fitted;int rotation;
    IllustrationView(Context context){
        super(context);setScaleType(ScaleType.MATRIX);setBackgroundColor(Color.BLACK);
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom(d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}});
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onDoubleTap(MotionEvent e){zoom(scale>fit*1.1f?fit/scale:2.5f,e.getX(),e.getY());return true;}});
        setContentDescription("Ảnh minh họa. Chạm đúp hoặc dùng hai ngón tay để phóng to.");
    }
    void zoom(float factor,float x,float y){float next=Math.max(fit,Math.min(fit*5,scale*factor));transform.postScale(next/scale,next/scale,x,y);scale=next;constrain();}
    void constrain(){if(getDrawable()==null)return;RectF r=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());transform.mapRect(r);float dx=r.width()<getWidth()?(getWidth()-r.width())/2-r.left:r.left>0?-r.left:r.right<getWidth()?getWidth()-r.right:0;float dy=r.height()<getHeight()?(getHeight()-r.height())/2-r.top:r.top>0?-r.top:r.bottom<getHeight()?getHeight()-r.bottom:0;transform.postTranslate(dx,dy);setImageMatrix(transform);}
    void rotate(){rotation=(rotation+90)%360;reset();}
    void reset(){if(getDrawable()==null||getWidth()==0||getHeight()==0)return;int w=rotation%180==0?getDrawable().getIntrinsicWidth():getDrawable().getIntrinsicHeight(),h=rotation%180==0?getDrawable().getIntrinsicHeight():getDrawable().getIntrinsicWidth();fit=Math.min((float)getWidth()/w,(float)getHeight()/h);if(w<240&&h<240)fit=Math.min(2f,fit);scale=fit;transform.setRotate(rotation);transform.postScale(fit,fit);constrain();fitted=true;}
    public void setImageBitmap(Bitmap b){super.setImageBitmap(b);fitted=false;post(this::reset);}
    protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(this::reset);}
    public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!pinch.isInProgress()){transform.postTranslate(e.getX()-lastX,e.getY()-lastY);constrain();}lastX=e.getX();lastY=e.getY();return true;}
}
