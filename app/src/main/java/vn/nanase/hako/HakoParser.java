package vn.nanase.hako;

import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.safety.Safelist;

/** Pure parser: never executes site JavaScript, never changes remote reading state. */
public final class HakoParser {
  public static volatile String ORIGIN = "https://docln.sbs";
  private static final Pattern STORY = Pattern.compile("^/(truyen|ai-dich|sang-tac)/(\\d+)[^/]*$");
  private static final Pattern CHAPTER =
      Pattern.compile("^/(truyen|ai-dich|sang-tac)/(\\d+)[^/]*/c(\\+?\\d+)[^/]*$");

  public static class Link {
    public String id, title, url;
    public String info="";

    public Link(String i, String t, String u) {
      id = i;
      title = t;
      url = u;
    }
  }

  public static boolean isOrigin(String url) {
    try {
      URI u = URI.create(url);
      if (!"https".equals(u.getScheme()) && !"http".equals(u.getScheme())) return false;
      String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
      String originHost = URI.create(ORIGIN).getHost() == null ? "" : URI.create(ORIGIN).getHost().toLowerCase();
      return host.equalsIgnoreCase(originHost) || host.endsWith("hako.vn") || host.endsWith("hako.vip") || host.endsWith("docln.sbs") || host.endsWith("docln.net");
    } catch (Exception e) {
      return false;
    }
  }

  public static String normalize(String input) {
    try {
      URI u = URI.create(input.startsWith("/") ? ORIGIN + input : input);
      if (!"https".equals(u.getScheme())
          || !URI.create(ORIGIN).getHost().equalsIgnoreCase(u.getHost())
          || (u.getPort() != -1 && u.getPort() != 443)) return "";
      return ORIGIN + u.getPath();
    } catch (Exception e) {
      return "";
    }
  }

  public static String storyId(String u) {
    try {
      Matcher m = STORY.matcher(URI.create(normalize(u)).getPath());
      return m.matches() ? m.group(1) + "-" + m.group(2) : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String chapterId(String u) {
    try {
      Matcher m = CHAPTER.matcher(URI.create(normalize(u)).getPath());
      return m.matches() ? m.group(1) + "-" + m.group(2) + "-" + m.group(3) : "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String storyUrl(String u) {
    u = normalize(u);
    return chapterId(u).isEmpty() ? u : u.substring(0, u.lastIndexOf('/'));
  }

    public static List<Link> storyList(String html, String base) {
    Document d = Jsoup.parse(html, base);
    List<Link> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Element a : d.select("a[href]")) {
      String href = a.attr("href");
      String id = storyId(href);
      if (id.isEmpty()) continue;
      String title = a.text().trim();
      if (title.length() < 2) continue;
      if (!seen.add(id)) continue;
      String abs = a.absUrl("href");
      Link l = new Link(id, title, abs);
      Element container = a.closest(".thumb-item-flow, .search-item, tr, .row, .col-12, .sect-item");
      if (container != null) {
        Element ch = container.selectFirst("a[href*='/c']");
        if (ch != null && !ch.text().trim().isEmpty() && !ch.equals(a)) {
          l.info = ch.text().trim();
        }
      }
      out.add(l);
    }
    return out;
  }

  public static List<Link> shelf(String html, String base) {
    Document d = Jsoup.parse(html, base);
    List<Link> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Element a : d.select("table a[href]")) {
      String u = normalize(a.absUrl("href")), id = storyId(u);
      if (!id.isEmpty() && seen.add(id)) {Link l=new Link(id,a.text(),u);Element row=a.closest("tr");if(row!=null){Element marker=row.selectFirst(".mark-read[data-unread]");if(marker!=null)l.info=marker.attr("data-unread")+" chương mới";else {Element count=row.selectFirst(".update-status");l.info=count==null?"Chưa rõ số chương mới":count.text();}}out.add(l);}
    }
    return out;
  }

  public static List<String> shelfPages(String html, String base) {
    List<String> out = new ArrayList<>();
    Document d = Jsoup.parse(html, base);
    for (Element a : d.select("a[href]")) {
      try {
        URI u = URI.create(a.absUrl("href"));
        if (URI.create(ORIGIN).getHost().equals(u.getHost())
            && "/ke-sach".equals(u.getPath())
            && u.getQuery() != null
            && u.getQuery().matches("page=\\d+")) out.add(u.toString());
      } catch (Exception ignored) {
      }
    }
    return out;
  }

  public static List<Link> chapters(String html, String base) {
    Document d = Jsoup.parse(html, base);
    d.select("#series-comments,#chapter-comments,.comment-section,nav,.navbar").remove();
    Element scope = d.selectFirst("#chapters");
    if (scope == null) scope = d;
    LinkedHashMap<String, Link> out = new LinkedHashMap<>();
    String sid = storyId(storyUrl(base));
    org.jsoup.select.Elements volSections = scope.select(".volume-list, .basic-section, section");
    if (!volSections.isEmpty()) {
      for (Element sec : volSections) {
        Element vt = sec.selectFirst(".sect-title, .volume-title, .volume-header, header span, header");
        String vName = vt != null ? vt.text().trim() : "";
        for (Element a : sec.select("a[href]")) {
          String u = normalize(a.absUrl("href")), id = chapterId(u);
          if (!id.isEmpty() && storyId(storyUrl(u)).equals(sid) && !out.containsKey(id)) {
            String chTitle = a.text().isEmpty() ? "Chương" : a.text();
            String fullTitle = vName.isEmpty() ? chTitle : (vName + " · " + chTitle);
            out.put(id, new Link(id, fullTitle, u));
          }
        }
      }
    }
    if (out.isEmpty()) {
      for (Element a : scope.select("a[href]")) {
        String u = normalize(a.absUrl("href")), id = chapterId(u);
        if (!id.isEmpty() && storyId(storyUrl(u)).equals(sid) && !out.containsKey(id))
          out.put(id, new Link(id, a.text().isEmpty() ? "Chương" : a.text(), u));
      }
    }
    return new ArrayList<>(out.values());
  }

  public static String title(String html, String base) {
    Document d = Jsoup.parse(html, base);
    Element e = d.selectFirst(".series-name,.series-title");
    return e != null
        ? e.text()
        : d.title().replaceAll(" - Cổng Light Novel.*$", "").replaceFirst("^Đọc ", "");
  }

  public static String content(String html, String base) throws Exception {
    Document d = Jsoup.parse(html, base);
    Element c = d.selectFirst("#chapter-content");
    if (c == null)
      throw new Exception(
          "Không tìm thấy nội dung chương. Có thể cần đăng nhập hoặc HAKO đã đổi cấu trúc.");
    if(c.selectFirst("#chapter-c-protected[data-s]")!=null && c.selectFirst("#chapter-c-protected[data-s]").text().trim().isEmpty()) throw new Exception("Nội dung chưa được trang hiển thị");
    c.select("script,style,iframe,form,input,button,video,audio,object,embed").remove();
    for (Element e : new ArrayList<Element>(c.getAllElements())) {
      if (e == c) continue;
      String style = e.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
      if (style.contains("display:none")) {
        e.remove();
        continue;
      }
      for (Attribute a : new ArrayList<Attribute>(e.attributes().asList()))
        if (a.getKey().startsWith("on")) e.removeAttr(a.getKey());
    }
    for (Element img : new ArrayList<Element>(c.select("img"))) {
      String src = img.absUrl("data-src");
      if (src.isEmpty()) src = img.absUrl("src");
      if (src.contains("chapter-banners/") || src.contains("/series/covers/")) {
        Element p = img.parent();
        img.remove();
        if (p != null && p.tagName().equals("a") && p.text().isEmpty()) p.remove();
        continue;
      }
      img.attr("src", src);
      img.removeAttr("srcset");
    }
    // Preserve common inline translator notes without retaining executable markup.
    for (Element e : new ArrayList<Element>(c.select("[data-content],[data-note],[title]"))) {
      String note =
          e.hasAttr("data-content")
              ? e.attr("data-content")
              : e.hasAttr("data-note") ? e.attr("data-note") : e.attr("title");
      if (note.trim().isEmpty() || e.tagName().equals("img")) continue;
      Element details = new Element("details").addClass("translator-note");
      details
          .appendElement("summary")
          .text("✎ " + (e.text().length() < 12 ? e.text() : "Chú thích"));
      details.appendElement("div").text(Jsoup.parseBodyFragment(note).text());
      e.replaceWith(details);
    }
    for (Element a : c.select("a[href]")) {
      String href = a.attr("href");
      if (!href.startsWith("#")) a.attr("href", a.absUrl("href"));
    }
    Safelist safe =
        new Safelist()
            .addTags(
                "p",
                "br",
                "div",
                "span",
                "b",
                "strong",
                "i",
                "em",
                "u",
                "s",
                "blockquote",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "ul",
                "ol",
                "li",
                "img",
                "a",
                "sup",
                "sub",
                "ruby",
                "rt",
                "rp",
                "hr",
                "table",
                "tbody",
                "tr",
                "td",
                "th",
                "details",
                "summary")
            .addAttributes(":all", "id", "class")
            .addAttributes("img", "src", "alt")
            .addAttributes("a", "href")
            .addProtocols("img", "src", "https")
            .addProtocols("a", "href", "https", "#")
            .preserveRelativeLinks(true);
    String clean =
        Jsoup.clean(c.html(), base, safe, new Document.OutputSettings().prettyPrint(false));
    Document result = Jsoup.parseBodyFragment(clean);
    int n = 0;
    for (Element e : result.body().children()) e.attr("data-pos", "" + (n++));
    if(!validContent(result.body().html())) throw new Exception("Nội dung rỗng — không lưu chương");
    return result.body().html();
  }

  public static boolean validContent(String html) { Document d=Jsoup.parseBodyFragment(html); return !d.text().trim().isEmpty() || d.selectFirst("img[src]")!=null; }

  public static boolean imageAllowed(String url) {
    try {
      URI u = URI.create(url);
      String h = u.getHost();
      return "https".equals(u.getScheme())
          && h != null
          && (h.equals(URI.create(ORIGIN).getHost())
              || h.equals("hako.vip")
              || h.endsWith(".hako.vip")
              || h.equals("hako.vn")
              || h.endsWith(".hako.vn"));
    } catch (Exception e) {
      return false;
    }
  }
}
