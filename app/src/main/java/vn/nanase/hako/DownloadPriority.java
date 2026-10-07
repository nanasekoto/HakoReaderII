package vn.nanase.hako;
/** Pins and full-download choices do not imply frequent reading. */
public final class DownloadPriority {
  public static int rank(boolean updated,int visits,boolean completed){
    if(updated)return 0;
    if(visits>=3)return 1;
    return completed?3:2;
  }
}
