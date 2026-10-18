package vn.nanase.hako;
import android.content.Context;
import android.os.Build;
import java.util.Locale;
/** Rendering profile, independent of content source and reading state. */
public final class DeviceProfile {
 public static boolean eink(Context c){return c.getSharedPreferences("settings",0).getBoolean("eink_mode",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("xteink")||Build.MODEL.equalsIgnoreCase("S4"));}
 public static boolean images(Context c){return !eink(c);}
 public static float sp(Context c,float size){return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size,c.getResources().getDisplayMetrics());}
 public static int imageWidth(Context c){return eink(c)?480:Math.min(1600,c.getResources().getDisplayMetrics().widthPixels);}
 public static int imageHeight(Context c){return eink(c)?800:Math.min(4096,c.getResources().getDisplayMetrics().heightPixels);}
}
