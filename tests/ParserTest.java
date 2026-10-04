import java.util.*;
import vn.nanase.hako.HakoParser;

public class ParserTest {
  static int passed = 0;

  static void check(boolean b, String msg) {
    if (!b) throw new AssertionError(msg);
    passed++;
  }

  public static void main(String[] args) throws Exception {
    String base = "https://docln.sbs/truyen/26739-test";
    String html =
        "<main><a href='/truyen/26739-test/c645221-chuong-14'>14</a><a"
            + " href='/truyen/26739-test/c645220-chuong-15'>15</a><div id='series-comments'><a"
            + " href='/truyen/26739-test/c1-wrong'>comment</a></div></main>";
    List<HakoParser.Link> ch = HakoParser.chapters(html, base);
    check(ch.size() == 2, "Exclude comment links");
    check(
        ch.get(0).id.endsWith("645221") && ch.get(1).id.endsWith("645220"),
        "Preserve TOC order instead of sorting IDs");
    check(
        HakoParser.chapterId(base + "/c123-x?foo=1#3").equals("truyen-26739-123"),
        "Canonical chapter identifier");
    check(
        HakoParser.storyId("https://evil.example/truyen/26739-test").isEmpty(),
        "Reject foreign host");
    check(
        HakoParser.storyId("https://docln.sbs.evil.test/truyen/26739-x").isEmpty(),
        "Reject suffix spoofing");
    check(HakoParser.normalize("javascript:alert(1)").isEmpty(), "Reject JS URL");
    String sample =
        "<div id='chapter-content'><p style='display: none'>hidden title</p><p id='1'>Nội dung"
            + " <b>đậm</b></p><p><span data-content='&lt;b&gt;Ghi chú&lt;/b&gt;'>1</span></p><img"
            + " src='https://i2.hako.vip/ln/image.jpg' onerror='bad()'><a href='/truyen/9'><img"
            + " src='https://i2.hako.vip/ln/series/chapter-banners/9/x.jpg'></a><script>bad()</script><iframe"
            + " src='https://bad.test'></iframe><p><a href='javascript:bad()'>x</a></p></div>";
    String clean = HakoParser.content(sample, base);
    check(
        clean.contains("Nội dung") && clean.contains("<b>đậm</b>"),
        "Preserve Vietnamese text and formatting");
    check(!clean.contains("hidden title"), "Remove hidden title");
    check(
        clean.contains("<details") && clean.contains("Ghi chú"),
        "Preserve inline translator notes");
    check(!clean.contains("chapter-banners"), "Remove banners only");
    check(clean.contains("/ln/image.jpg"), "Retain story illustrations");
    check(
        !clean.contains("onerror")
            && !clean.contains("<script")
            && !clean.contains("<iframe")
            && !clean.contains("javascript:"),
        "Sanitize executable content");
    check(clean.contains("id=\"1\""), "Preserve paragraph anchors");
    String shelf =
        "<table><tr><td><a href='/truyen/1-one'>One</a></td><td><a"
            + " href='/truyen/1-one/c2-ch'>chapter</a></td></tr></table><a"
            + " href='/truyen/3-not-shelf'>other</a><a href='/ke-sach?page=2'>next</a><a"
            + " href='https://evil.test/ke-sach?page=3'>bad</a>";
    check(HakoParser.shelf(shelf, HakoParser.ORIGIN).size() == 1, "Only story rows from bookshelf");
    check(HakoParser.shelfPages(shelf, HakoParser.ORIGIN).size() == 1, "Safe bookshelf pagination");
    check(HakoParser.imageAllowed("https://i2.hako.vip/ln/test.jpg"), "Allow Hako image CDN");
    check(
        !HakoParser.imageAllowed("https://hako.vip.evil.test/test.jpg"), "Reject image host spoof");
    boolean failed = false;
    try {
      HakoParser.content("<h1>Access denied</h1>", base);
    } catch (Exception e) {
      failed = true;
    }
    check(failed, "Missing content is an error, never stored as chapter");
    System.out.println("PASS " + passed + " parser assertions");
  }
}
