package vn.nanase.hako;
import java.net.URI;
/** Accept a page parameter even when Hako appends sorting/filter parameters. */
public final class ShelfPagePolicy {
 private ShelfPagePolicy(){}
 public static boolean isPage(String origin,String url){
  try{
   URI site=URI.create(origin),u=URI.create(url);
   if(!site.getHost().equalsIgnoreCase(u.getHost())||!site.getScheme().equalsIgnoreCase(u.getScheme())||!"/ke-sach".equals(u.getPath())||u.getRawQuery()==null)return false;
   for(String item:u.getRawQuery().split("&"))if(item.matches("page=[1-9][0-9]*"))return true;
  }catch(Exception ignored){}
  return false;
 }
}
