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
    check(!HakoParser.isOrigin("https://evilhako.vn/"),"Reject a different registered hostname with Hako suffix");
    check(!HakoParser.isOrigin("https://evildocln.sbs/"),"Reject fake Docln domain prefix");
    check(HakoParser.isOrigin("https://i2.hako.vip/"),"Accept a genuine Hako subdomain");
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
    check(HakoParser.validContent("<p>A</p>"),"Short chapter is valid");
    check(HakoParser.validContent("<p>Access denied! Just a moment...</p>"),"Dialogue is not a challenge");
    check(HakoParser.validContent("<img src='https://i2.hako.vip/image.jpg'>"),"Image-only chapter");
    check(!HakoParser.validContent("<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>"),"Challenge cache rejected");
    check(!HakoParser.validContent("<h1>503 Service Unavailable</h1>"),"HTTP error cache rejected");
    check(!HakoParser.validContent("<script>bad()</script>"),"Script-only cache rejected");
    check(!HakoParser.validContent("<p style='display:none'>hidden</p>"),"Hidden-only cache rejected");

    for(String host:Arrays.asList("i.docln.net","i2.docln.net","i.hako.re","I.DOCLN.NET")){
      check(HakoParser.imageAllowed("https://"+host+"/image.jpg"),"Allow "+host);
      check(HakoParser.imageAllowed("http://"+host+"/image.jpg"),"Legacy HTTP URL "+host);
    }
    check(!HakoParser.imageAllowed("https://i.docln.net.evil.test/image.jpg"),"Reject Docln suffix spoof");
    check(!HakoParser.imageAllowed("https://evilhako.re/image.jpg"),"Reject Hako prefix spoof");
    check(!HakoParser.imageAllowed("file:///image.jpg"),"Reject file source");
    check(!HakoParser.imageAllowed("https://evil@i.docln.net/image.jpg"),"Reject URL credentials");
    check(HakoParser.imageFetchUrl("http://i.docln.net/a.jpg").equals("https://i.docln.net/a.jpg"),"Upgrade legacy image to HTTPS");
    String imageOnly=HakoParser.content("<div id='chapter-content'><img data-src='http://i.docln.net/a.jpg'></div>",base);
    check(imageOnly.contains("http://i.docln.net/a.jpg"),"Preserve HTTP image through sanitizer");
    check(HakoParser.content("<div id='chapter-content'><div id='chapter-c-protected' data-s='x'><img src='https://i.docln.net/a.jpg'></div></div>",base).contains("i.docln.net/a.jpg"),"Protected image-only DOM is valid without text");
    String local="https://offline.hako.invalid/book-1/image-0123456789abcdef01234567.bin";
    check(HakoParser.offlineImageName("book-1",local).equals("image-0123456789abcdef01234567.bin"),"Recognize local image filename");
    check(HakoParser.offlineImageName("book-2",local).isEmpty(),"Reject another chapter cache");
    check(HakoParser.offlineImageName("book-1","https://offline.hako.invalid/book-1/../outside.bin").isEmpty(),"Reject traversal");
    HakoParser.OfflineContent images=HakoParser.offlineContent("<img src='"+local+"'><img src='second'>");
    check(!images.readable(src->src.equals(local)),"Partial image-only chapter is incomplete");
    check(images.readable(src->true),"All image files make image-only chapter readable");
    check(!HakoParser.offlineContent("<img src='https://i.docln.net/a.jpg'>").readable(src->false),"Remote image markup alone is not offline");
    check(HakoParser.offlineContent("<p>Nội dung chữ</p><img src='missing'>").readable(src->false),"Keep text despite missing illustration");
    check(!HakoParser.offlineContent("<p>[Ảnh ngoài máy chủ HAKO — xem trên web]</p>").readable(src->true),"Repair legacy placeholder-only cache");
    check(!HakoParser.offlineContent("<p>[Ảnh chưa tải được — thử tải lại khi có mạng]</p>").readable(src->true),"Missing-image message is not content");
    check(!HakoParser.offlineContent("<p style='display:none'>hidden</p><img src='missing'>").readable(src->false),"Hidden text cannot complete image chapter");
    check(!HakoParser.offlineContent("<h1>503 Service Unavailable</h1>").readable(src->true),"Never accept error page as offline content");
    String shortcut="<section><a href='/truyen/123-fixture/c2-real'>Đọc tiếp</a></section><section class='volume-list'><header>Tập 1</header><a href='/truyen/123-fixture/c1-old'>Chương 1</a><a href='/truyen/123-fixture/c2-real'>Chương 2</a></section>";
    List<HakoParser.Link> filtered=HakoParser.chapters(shortcut,HakoParser.ORIGIN+"/truyen/123-fixture");
    check(filtered.size()==2&&filtered.get(1).title.equals("Tập 1 · Chương 2"),"Resume shortcut cannot steal actual chapter identity or create Other volume");
    check(HakoParser.readingShortcutTitle("Tập khác · Đọc tiếp"),"Recognize cached resume shortcut");
    String discovery="<aside><a href='/truyen/91-popular'>Popular</a></aside><main><article class='thumb-item-flow'><a href='/truyen/92-real'>Real novel</a><a href='/truyen/92-real/c3-new'>Latest chapter</a></article><div id='series-comments'><article class='thumb-item-flow'><a href='/truyen/93-comment'>Comment novel</a></article></div><section class='ranking'><a href='/truyen/94-ranked'>Ranked novel</a></section></main>";
    List<HakoParser.Link> novels=HakoParser.storyList(discovery,HakoParser.ORIGIN);
    check(novels.size()==1&&novels.get(0).id.equals("truyen-92"),"Discovery ignores comment ranking and sidebar story links");
    check(novels.get(0).latestKey.equals("truyen-92-3|Latest chapter"),"Discovery records latest chapter identity without treating it as a novel");
    check(HakoParser.pageIdentity("https://docln.sbs/truyen/123-old/c456-old").equals(HakoParser.pageIdentity("https://docln.net/truyen/123-renamed/c456-new")),"Same chapter remains identifiable through canonical slug/domain redirect");
    check(!HakoParser.pageIdentity("https://docln.sbs/truyen/123-old/c456-old").equals(HakoParser.pageIdentity("https://docln.sbs/truyen/123-old/c457-next")),"Stale different chapter document is rejected");
    check(!HakoParser.pageIdentity("https://docln.sbs/truyen/123-old").equals(HakoParser.pageIdentity("https://docln.sbs/truyen/123-old/c456-ch")),"Story metadata is not a chapter document");
    check(HakoParser.pageIdentity("https://docln.sbs/ke-sach?page=1").equals(HakoParser.pageIdentity("https://docln.sbs/ke-sach?page=2")),"Read-all action can traverse shelf pagination within its request");
    System.out.println("PASS " + passed + " parser assertions");
  }
}

