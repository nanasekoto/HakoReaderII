package vn.nanase.hako;
import android.content.Context;
import android.os.Build;
import java.util.Locale;
/** Rendering profile, independent of content source and reading state. */
public final class DeviceProfile {
 public static boolean eink(Context c){return c.getSharedPreferences("settings",0).getBoolean("eink_mode",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("xteink")||Build.MODEL.equalsIgnoreCase("S4"));}
 public static int imageWidth(Context c){return eink(c)?480:Math.min(1600,c.getResources().getDisplayMetrics().widthPixels);}
 public static int imageHeight(Context c){return eink(c)?800:Math.min(4096,c.getResources().getDisplayMetrics().heightPixels);}
}
