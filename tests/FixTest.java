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
  System.out.println("PASS "+checks+" regression assertions");
 }
}
