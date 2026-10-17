package vn.nanase.hako;
import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** The foremost dialog owns page keys, never the chapter underneath. */
final class ReaderDialogBuilder extends AlertDialog.Builder {
 private final ListPaging paging=new ListPaging();
 ReaderDialogBuilder(Context c){super(c);}
 @Override public AlertDialog show(){AlertDialog dialog=super.show();if(DeviceProfile.eink(getContext())){replaceScrolls(dialog.getWindow().getDecorView());dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_VOLUME_UP&&key!=KeyEvent.KEYCODE_VOLUME_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){int direction=key==KeyEvent.KEYCODE_VOLUME_DOWN?1:-1;ListView list=dialog.getListView();if(list!=null)paging.turn(list,direction);else turn(dialog.getWindow().getDecorView(),direction);}return true;});}return dialog;}
 private void replaceScrolls(View view){if(!(view instanceof ViewGroup))return;ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View child=group.getChildAt(n);if(child instanceof ScrollView&&!(child instanceof EinkScrollView)){ScrollView old=(ScrollView)child;if(old.getChildCount()==0)continue;View content=old.getChildAt(0);old.removeView(content);EinkScrollView replacement=new EinkScrollView(getContext());replacement.setPadding(old.getPaddingLeft(),old.getPaddingTop(),old.getPaddingRight(),old.getPaddingBottom());replacement.addView(content);ViewGroup.LayoutParams lp=old.getLayoutParams();group.removeViewAt(n);group.addView(replacement,n,lp);}else replaceScrolls(child);}}
 private boolean turn(View view,int direction){if(view instanceof EinkScrollView){((EinkScrollView)view).turn(direction);return true;}if(view instanceof ScrollView){ScrollView scroll=(ScrollView)view;scroll.setSmoothScrollingEnabled(false);scroll.pageScroll(direction>0?View.FOCUS_DOWN:View.FOCUS_UP);return true;}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)if(turn(group.getChildAt(n),direction))return true;}return false;}
}
