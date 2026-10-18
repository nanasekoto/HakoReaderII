package vn.nanase.hako;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.*;

/** Separate launcher and sandbox. Never packaged in the Pocket release. */
public final class FixtureLabActivity extends Activity {
 final ExecutorService worker=Executors.newSingleThreadExecutor();TextView state;
 public void onCreate(Bundle saved){super.onCreate(saved);FixtureLibrary.requireLab(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);int pad=Math.round(16*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);TextView title=new TextView(this);title.setText("HAKO KIỂM THỬ");title.setTextSize(22);box.addView(title);state=new TextView(this);state.setText("Kho riêng. Không có quyền Internet; không đăng nhập hoặc gửi dữ liệu thật. Xóa/gỡ bản này không ảnh hưởng Pocket.");state.setTextSize(16);box.addView(state);add(box,"Tạo mẫu S4",()->seed(true));add(box,"Tạo mẫu K60",()->seed(false));add(box,"Mở ứng dụng kiểm thử",()->startActivity(new Intent(this,MainActivity.class)));add(box,"Xóa dữ liệu kiểm thử",()->new AlertDialog.Builder(this).setTitle("Xóa kho kiểm thử?").setMessage("Chỉ xóa dữ liệu Hako Kiểm thử. Pocket thật được giữ nguyên.").setPositiveButton("Xóa dữ liệu kiểm thử",(d,w)->{FixtureLibrary.requireLab(this);((ActivityManager)getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();}).setNegativeButton("Hủy",null).show());ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);}
 void add(LinearLayout box,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());box.addView(b);}
 void seed(boolean eink){state.setText("Đang tạo dữ liệu mẫu…");worker.execute(()->{try{FixtureLibrary.seed(this,eink);runOnUiThread(()->{state.setText("Đã tạo bốn truyện mẫu. Có thể mở app để kiểm tra hoặc xóa kho thử.");startActivity(new Intent(this,MainActivity.class));});}catch(Exception e){runOnUiThread(()->state.setText("Không tạo được mẫu: "+e.getMessage()));}});}
 protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
