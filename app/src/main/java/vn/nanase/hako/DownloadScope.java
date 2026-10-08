package vn.nanase.hako;
/** Shelf changes and pins affect sorting, never grant download eligibility. */
public final class DownloadScope {
 private DownloadScope(){}
 public static boolean eligible(boolean readInApp,boolean keepFull,boolean autoDownload){
  return readInApp||keepFull||autoDownload;
 }
}
