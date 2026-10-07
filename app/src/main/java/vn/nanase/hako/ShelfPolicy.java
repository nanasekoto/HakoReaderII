package vn.nanase.hako;
import java.util.*;
import java.text.*;
/** Conservative shelf shortcuts: absent metadata always requires a normal refresh. */
public final class ShelfPolicy {
  public static boolean unchanged(String current,String catalogSnapshot){
    return current!=null&&!current.isEmpty()&&current.equals(catalogSnapshot);
  }
  public static long timestamp(String value){
    if(value==null||value.isEmpty())return 0;
    if(value.matches("\\d{10}|\\d{13}")){try{long n=Long.parseLong(value);return value.length()==10?n*1000:n;}catch(Exception ignored){}}
    for(String pattern:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd HH:mm:ss"})try{
      SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));if(pattern.endsWith("'Z'"))f.setTimeZone(TimeZone.getTimeZone("UTC"));ParsePosition pos=new ParsePosition(0);Date d=f.parse(value,pos);if(d!=null&&pos.getIndex()==value.length())return d.getTime();
    }catch(Exception ignored){}
    return 0;
  }
  public static boolean oldPage(List<HakoParser.Link> rows,long watermark,boolean updateOrder){
    if(!updateOrder||watermark<=0||rows.isEmpty())return false;
    long previous=Long.MAX_VALUE;
    for(HakoParser.Link row:rows){if(row.updatedAt<=0||row.updatedAt>previous)return false;previous=row.updatedAt;}
    // The page may start with new rows. Once its tail is strictly older than
    // the previous full snapshot, later rows in descending order cannot be new.
    return previous<watermark;
  }
}
