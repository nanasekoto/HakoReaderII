package vn.nanase.hako;
/** Pure scheduling policy; delays are a courtesy, not a promise of server acceptance. */
public final class FetchPolicy {
 public static final int AHEAD=15, BEHIND=3;
 public static long delayMillis(int distance) {
  if(distance<=0)return 2000;
  if(distance<=2)return 4000;
  if(distance<=5)return 12000;
  if(distance<=10)return 30000;
  return 60000;
 }
 public static boolean allowCache(boolean followed,boolean active,boolean full){return followed||active||full;}
 public static boolean allowDownload(boolean dropped,boolean manual){return !dropped||manual;}
 public static boolean keep(int ordinal,int current){return ordinal>=Math.max(0,current-BEHIND)&&ordinal<=current+AHEAD;}
}

