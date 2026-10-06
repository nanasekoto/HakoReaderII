package vn.nanase.hako;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.Color;import android.widget.*;
import org.mozilla.geckoview.*;import org.json.*;
/** Human login uses the same persistent profile as the worker, outside reader RAM. */
public final class GeckoBrowserActivity extends Activity {
 private GeckoSession session;private String url="",history="[]";private boolean back;private TextView status;
 public void onCreate(Bundle b){super.onCreate(b);GeckoHost.browsers++;GeckoHost.active();HakoParser.ORIGIN=getIntent().getStringExtra("origin");
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);
  LinearLayout tools=new LinearLayout(this);root.addView(tools);
  Button close=new Button(this);close.setText("Về app");close.setOnClickListener(v->done(false));tools.addView(close,new LinearLayout.LayoutParams(0,-2,1));
  Button shelf=new Button(this);shelf.setText("Tủ sách");shelf.setOnClickListener(v->session.loadUri(HakoParser.ORIGIN+"/ke-sach"));tools.addView(shelf,new LinearLayout.LayoutParams(0,-2,1));
  Button read=new Button(this);read.setText("Đọc offline");read.setOnClickListener(v->done(true));tools.addView(read,new LinearLayout.LayoutParams(0,-2,1));
  status=new TextView(this);status.setText("Gecko Lite · Xác minh trực tiếp · Không tự tải lại");root.addView(status);
  GeckoView view=new GeckoView(this);root.addView(view,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  session=new GeckoSession();session.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
   public void onCanGoBack(GeckoSession s,boolean value){back=value;}
   public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s,LoadRequest req){
    if(!req.uri.startsWith("https://"))return GeckoResult.fromValue(AllowOrDeny.DENY);
    url=req.uri;return GeckoResult.fromValue(AllowOrDeny.ALLOW);
   }
  });
  session.setProgressDelegate(new GeckoSession.ProgressDelegate(){public void onPageStop(GeckoSession s,boolean success){status.setText(success?"Gecko Lite · Phiên được giữ trong app":"Chưa tải được trang · Kiểm tra mạng");}});
  GeckoRuntime runtime;try{runtime=GeckoHost.get(this);}catch(Exception e){finish();return;}session.open(runtime);view.setSession(session);
  runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/gecko-bridge/","hako-lite@nanase.vn").accept(ext->{
   if(isFinishing()||isDestroyed())return;session.getWebExtensionController().setMessageDelegate(ext,new WebExtension.MessageDelegate(){public GeckoResult<Object> onMessage(String app,Object message,WebExtension.MessageSender sender){
    if(sender.session==session&&HakoParser.isOrigin(sender.url)&&message instanceof JSONObject){JSONObject m=(JSONObject)message;if(m.has("history")){String h=m.optString("history","[]");if(h.length()<200000)history=h;}}
    return null;
   }},"hako");session.loadUri(getIntent().getStringExtra("url"));
  },e->{status.setText("Không khởi tạo được Gecko");});
 }
 private void done(boolean read){setResult(RESULT_OK,new Intent().putExtra("url",url).putExtra("history",history).putExtra("read",read));finish();}
 public void onBackPressed(){if(back)session.goBack();else done(false);}
 protected void onPause(){if(session!=null&&session.isOpen())session.setActive(false);super.onPause();}
 protected void onResume(){super.onResume();if(session!=null&&session.isOpen())session.setActive(true);}
 protected void onDestroy(){if(session!=null)session.close();GeckoHost.browsers--;GeckoHost.idle();super.onDestroy();}
}
