package vn.nanase.hako;
public final class CachePolicy {
 private CachePolicy(){}
 public static boolean shouldFetch(boolean readable,boolean force){return force||!readable;}
}
