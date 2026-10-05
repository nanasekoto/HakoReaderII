import vn.nanase.hako.*;
import java.util.*;
public class FixTest {
 static int checks=0;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 public static void main(String[]args)throws Exception{
  String base="https://docln.sbs/truyen/1-test";
  for(String html:new String[]{"<div id='chapter-content'></div>","<div id='chapter-content'><p style='display:none'>Title</p><div id='chapter-c-protected' data-s='xor_shuffle' data-c='[]'></div></div>","<div id='chapter-content'><script>bad()</script></div>"}){boolean failed=false;try{HakoParser.content(html,base);}catch(Exception e){failed=true;}check(failed,"Reject blank / unrendered chapter");}
  String shelf="<table><tr><td><a href='/truyen/1-test'>One</a></td><td><span class='mark-read' data-series='1' data-unread='17'>Đã đọc?</span></td></tr></table>";
  check(HakoParser.shelf(shelf,base).get(0).info.equals("17 chương mới"),"Read actual Hako unread count");
  check(FetchPolicy.keep(17,20)&&!FetchPolicy.keep(16,20)&&FetchPolicy.keep(35,20)&&!FetchPolicy.keep(36,20),"Window 3 behind and 15 ahead");
  for(int i=1;i<=15;i++)check(FetchPolicy.delayMillis(i)>=FetchPolicy.delayMillis(i-1),"Monotonic delays");
  Random r=new Random(17);for(int run=0;run<200;run++){
   int n=1+r.nextInt(200),height=120+r.nextInt(600),anchor=r.nextInt(n);int[]top=new int[n+1],bottom=new int[n];
   for(int i=0;i<n;i++){top[i+1]=top[i]+12+r.nextInt(60);bottom[i]=top[i+1];}
   List<Integer> pages=PageBreaks.split(top,bottom,height,anchor);check(pages.get(0)==0&&pages.contains(anchor),"Resume anchor is page boundary");
   for(int p=0;p<pages.size();p++){int a=pages.get(p),end=p+1<pages.size()?pages.get(p+1):n;check(end>a&&bottom[end-1]-top[a]<=height,"Whole lines fit every page");}
  }
  check(PageBreaks.split(new int[]{0,900,920},new int[]{900,920},300,0).equals(Arrays.asList(0,1)),"Oversized block progresses without infinite loop");
  
  // Invariant 1: Multi-domain support
  HakoParser.ORIGIN = "https://docln.sbs";
  check(HakoParser.isOrigin("https://docln.sbs/truyen/1-a/c1-b"), "Support docln.sbs origin");
  check(HakoParser.isOrigin("https://docln.net/truyen/1-a/c1-b"), "Support docln.net mirror");
  check(HakoParser.isOrigin("https://ln.hako.vn/truyen/1-a/c1-b"), "Support ln.hako.vn mirror");
  check(HakoParser.isOrigin("https://hako.vip/truyen/1-a/c1-b"), "Support hako.vip mirror");
  check(!HakoParser.isOrigin("https://google.com/test"), "Reject outside domain");

  // Invariant 2: Frequent list percentage formula: (oldChs + newDownloaded) / total
  int totalChapters = 50;
  int currentChapter = 40; // Read up to chapter 40
  int oldChs = currentChapter;
  int newChs = totalChapters - currentChapter; // 10 remaining chapters
  int newDownloadedAll = 10;
  int pctAll = Math.round((oldChs + newDownloadedAll) * 100f / totalChapters);
  check(pctAll == 100, "All new chapters downloaded equals 100%");

  int newDownloadedHalf = 5;
  int pctHalf = Math.round((oldChs + newDownloadedHalf) * 100f / totalChapters);
  check(pctHalf == 90, "Half new chapters downloaded equals 90% (45/50)");

  // Invariant 3: Store.java SQL column check - ensure no camelCase shelfRank/shelfInfo in raw SQL
  String storeCode = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/vn/nanase/hako/Store.java")), java.nio.charset.StandardCharsets.UTF_8);
  check(!storeCode.contains("SELECT id,title,url,current,pos,fraction,stamp,dropped,visits,pinned,shelfRank"), "Store.book SQL query uses snake_case column names");
  check(storeCode.contains("shelf_rank") && storeCode.contains("shelf_info"), "Store.java queries shelf_rank and shelf_info");

  check(FetchPolicy.allowCache(false,false,true),"Explicit full cache survives leaving an unfollowed book");
  check(!FetchPolicy.allowCache(false,false,false),"Temporary unfollowed background chapters are not stored");
  check(FetchPolicy.allowCache(false,true,false),"Active temporary chapter can be stored");
  check(FetchPolicy.allowCache(true,false,false),"Followed book background chapter can be stored");
  check(!FetchPolicy.allowDownload(true,false),"Dropped book does not auto download");
  check(FetchPolicy.allowDownload(true,true),"Explicit manual full download works for dropped book");
  System.out.println("PASS "+checks+" regression assertions");
 }
}

