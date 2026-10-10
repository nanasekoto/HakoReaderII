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
    public String info="", latestKey="";
    public long updatedAt;

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
      if(host.equalsIgnoreCase(originHost))return true;
      for(String site:new String[]{"hako.vn","hako.vip","docln.sbs","docln.net"})if(host.equals(site)||host.endsWith("."+site))return true;
      return false;
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
      if (!id.isEmpty() && seen.add(id)) {Link l=new Link(id,a.text(),u);Element row=a.closest("tr");if(row!=null){Element marker=row.selectFirst(".mark-read[data-unread]");if(marker!=null)l.info=marker.attr("data-unread")+" chương mới";else {Element count=row.selectFirst(".update-status");l.info=count==null?"Chưa rõ số chương mới":count.text();}}if(row!=null){
        Element chapter=row.selectFirst("a[href*='/c']");
        if(chapter!=null&&!chapterId(chapter.absUrl("href")).isEmpty())l.latestKey=chapterId(chapter.absUrl("href"))+"|"+chapter.text().trim();
        Element time=row.selectFirst("time[datetime], [data-timestamp], .timeago[title]");
        if(time!=null){String date=time.hasAttr("datetime")?time.attr("datetime"):time.hasAttr("data-timestamp")?time.attr("data-timestamp"):time.attr("title");l.updatedAt=ShelfPolicy.timestamp(date);if(l.latestKey.isEmpty()&&l.updatedAt>0)l.latestKey="time:"+l.updatedAt;}
      }out.add(l);}
    }
    return out;
  }

  /** Early pagination exit is allowed only when the server explicitly selects update order. */
  public static boolean shelfUpdateOrder(String html){
    Document d=Jsoup.parse(html);
    for(Element e:d.select("select option[selected], .sorting .active, .sort .active, [data-sort][aria-selected=true]")){
      String v=(e.text()+" "+e.attr("value")+" "+e.attr("data-sort")).toLowerCase(Locale.ROOT);
      if((v.contains("cập nhật")||v.contains("updated")||v.contains("latest-update"))&&!v.contains("asc")&&!v.contains("cũ nhất"))return true;
    }
    return false;
  }

  public static List<String> shelfPages(String html, String base) {
    List<String> out = new ArrayList<>();
    Document d = Jsoup.parse(html, base);
    for (Element a : d.select("a[href]")) {
      try {
        URI u = URI.create(a.absUrl("href"));
        if (ShelfPagePolicy.isPage(ORIGIN,u.toString())) out.add(u.toString());
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
    if(errorDocument(d))throw new Exception("Trang lỗi hoặc yêu cầu xác minh — không lưu chương");
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
            .addProtocols("img", "src", "https", "http")
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

  private static boolean errorLabel(String text){
    return text.trim().toLowerCase(Locale.ROOT).matches("(just a moment|attention required|access denied|checking your browser|verify you are human|too many requests|(?:[45][0-9]{2} )?(?:service unavailable|forbidden|not found|internal server error))[.!… ]*");
  }
  private static boolean errorDocument(Document d){
    if(d.selectFirst("#challenge-form,.cf-browser-verification")!=null)return true;
    if(!d.title().isEmpty()&&errorLabel(d.title()))return true;
    Element heading=d.selectFirst("h1");
    return heading!=null&&errorLabel(heading.text())&&d.body().text().trim().equals(heading.text().trim())&&d.selectFirst("img[src]")==null;
  }
  public static boolean validContent(String html){
    if(html==null||html.trim().isEmpty())return false;
    Document d=Jsoup.parse(html);
    if(errorDocument(d))return false;
    d.select("script,style,iframe,form,[hidden]").remove();
    for(Element e:d.select("[style]"))if(e.attr("style").matches("(?is).*(display\\s*:\\s*none|visibility\\s*:\\s*hidden).*"))e.remove();
    if(!d.body().text().trim().isEmpty())return true;
    for(Element img:d.select("img[src]"))if(!img.attr("src").trim().isEmpty())return true;
    return false;
  }


  /** Text tolerates missing illustrations; an image-only chapter needs every image. */
  public static final class OfflineContent {
    public final boolean text;
    public final List<String> images;
    private OfflineContent(boolean text,List<String> images){this.text=text;this.images=images;}
    public boolean readable(java.util.function.Predicate<String> imageAvailable){
      if(text)return true;
      if(images.isEmpty())return false;
      for(String image:images)if(!imageAvailable.test(image))return false;
      return true;
    }
  }
  public static OfflineContent offlineContent(String html){
    List<String> images=new ArrayList<>();
    if(!validContent(html))return new OfflineContent(false,images);
    Document d=Jsoup.parseBodyFragment(html);
    d.select("script,style,iframe,form,[hidden]").remove();
    for(Element el:d.select("[style]"))if(el.attr("style").matches("(?is).*(display\\s*:\\s*none|visibility\\s*:\\s*hidden).*"))el.remove();
    // Old releases replaced failed images with these paragraphs. They are not chapter text.
    for(Element el:d.select("p"))if(el.text().equals("[Ảnh ngoài máy chủ HAKO — xem trên web]")||
        el.text().equals("[Ảnh chưa tải được — thử tải lại khi có mạng]"))el.remove();
    for(Element img:d.select("img[src]"))if(!img.attr("src").trim().isEmpty())images.add(img.attr("src"));
    return new OfflineContent(!d.body().text().trim().isEmpty(),images);
  }
  public static String offlineImageName(String chapterId,String src){
    try{
      URI uri=URI.create(src);
      if(!"https".equals(uri.getScheme())||!"offline.hako.invalid".equals(uri.getHost())||
          uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null)return "";
      String prefix="/"+chapterId+"/",path=uri.getPath();
      if(path==null||!path.startsWith(prefix))return "";
      String name=path.substring(prefix.length());
      return name.matches("image-[a-f0-9]{24}\\.bin")?name:"";
    }catch(Exception e){return "";}
  }

  public static String imageFetchUrl(String url){
    if(!imageAllowed(url))return url;
    return url.regionMatches(true,0,"http:",0,5)?"https:"+url.substring(5):url;
  }

  public static boolean imageAllowed(String url) {
    try {
      URI u = URI.create(url);
      String h = u.getHost();
      if(h != null) h = h.toLowerCase(Locale.ROOT);
      return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))
          && h != null
          && u.getUserInfo() == null
          && (h.equals(URI.create(ORIGIN).getHost().toLowerCase(Locale.ROOT))
              || h.equals("docln.net")
              || h.endsWith(".docln.net")
              || h.equals("hako.re")
              || h.endsWith(".hako.re")
              || h.equals("hako.vip")
              || h.endsWith(".hako.vip")
              || h.equals("hako.vn")
              || h.endsWith(".hako.vn"));
    } catch (Exception e) {
      return false;
    }
  }
}
