package vn.nanase.hako;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
/** Content evidence is authoritative; titles are only navigation hints. */
public final class IllustrationPolicy {
  public static boolean hint(String title){
    String t=title==null?"":title.trim().toLowerCase(Locale.ROOT);
    return t.matches("^(minh họa|minh hoạ|illustrations?|color illustrations?|colour illustrations?|tranh minh họa)(\\b|[ :：\\-]).*");
  }
  public static int kind(String html){
    HakoParser.OfflineContent c=HakoParser.offlineContent(html);
    if(!c.text&&!c.images.isEmpty())return 2;
    return c.text?1:0;
  }
  public static boolean shouldAttempt(boolean illustration,boolean attempted,boolean force){
    return force||!illustration||!attempted;
  }
  public static boolean shouldYield(String current,String pending){
    return pending!=null&&!pending.isEmpty()&&!pending.equals(current);
  }
}
