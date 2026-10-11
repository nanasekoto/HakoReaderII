package vn.nanase.hako;
import java.text.Normalizer;
import java.util.Locale;
import org.jsoup.Jsoup;
/** Titles suggest priority; extracted content establishes certainty. Unknown never means read. */
public final class IllustrationPolicy {
  public static final int UNKNOWN=0,TEXT=1,ILLUSTRATION=2,CANDIDATE=3;
  public static boolean hint(String title){
    String t=Normalizer.normalize(title==null?"":title,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
    return t.matches("(?s).*(\\bminh hoa\\b|\\billustrations?\\b|\\bcolor illustrations?\\b|\\bcolour illustrations?\\b|\\banh mau\\b|\\bfanarts?\\b|\\bartbook\\b).*");
  }
  public static int kind(String html){return kind("",html);}
  public static int kind(String title,String html){
    HakoParser.OfflineContent c=HakoParser.offlineContent(html);
    if(!c.text&&!c.images.isEmpty())return ILLUSTRATION;
    if(!c.text)return UNKNOWN;
    // A short caption may be fiction: candidate affects priority, never remote read-all.
    String text=Jsoup.parseBodyFragment(html).body().text().trim();
    if(!c.images.isEmpty()&&hint(title)&&text.split("\\s+").length<=24)return CANDIDATE;
    return TEXT;
  }
  public static boolean skipCachedIllustration(int kind,boolean validMarkup,boolean force){
    return !force&&validMarkup&&kind==ILLUSTRATION;
  }
  public static boolean shouldAttempt(boolean illustration,boolean attempted,boolean force){return force||!illustration||!attempted;}
  public static boolean shouldYield(String current,String pending){return pending!=null&&!pending.isEmpty()&&!pending.equals(current);}
}
