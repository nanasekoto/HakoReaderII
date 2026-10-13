package vn.nanase.hako;
/** Text-list indices, never chapter ordinals. Early refill leaves two buffered chapters. */
public final class BatchWindow {
 public static final int SIZE=15,REFILL=13;
 public static int start(int saved,int current,int count){if(count==0)return 0;if(saved<0||saved>count||current<saved-SIZE||current>=saved+SIZE)return Math.max(0,current);if(current>=saved+REFILL)return saved+SIZE;return saved;}
 public static int end(int start,int count){return Math.min(count,start+SIZE);}
}

