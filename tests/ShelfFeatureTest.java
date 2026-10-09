import vn.nanase.hako.*;
import java.util.*;
public class ShelfFeatureTest {
  static int n;
  static void check(boolean value){n++;if(!value)throw new AssertionError("case "+n);}
  public static void main(String[] args){
    check(ShelfPolicy.unchanged("c1|Chapter 1","c1|Chapter 1"));
    check(!ShelfPolicy.unchanged("c2|Chapter 2","c1|Chapter 1"));
    check(!ShelfPolicy.unchanged("c1|Edited","c1|Original"));
    check(!ShelfPolicy.unchanged("",""));
    check(!ShelfPolicy.unchanged("c1",""));
    long epoch=ShelfPolicy.timestamp("2026-10-07T05:00:00Z");check(epoch>0);
    check(epoch==ShelfPolicy.timestamp("2026-10-07T12:00:00+07:00"));
    check(ShelfPolicy.timestamp("not a date")==0);
    check(ShelfPolicy.timestamp("1791349200")>0);
    HakoParser.Link a=new HakoParser.Link("truyen-1","A","https://docln.sbs/truyen/1-a"),b=new HakoParser.Link("truyen-2","B","https://docln.sbs/truyen/2-b");
    a.updatedAt=epoch;b.updatedAt=epoch-1000;
    check(ShelfPolicy.oldPage(Arrays.asList(a,b),epoch,true));
    check(!ShelfPolicy.oldPage(Arrays.asList(a,b),epoch,false));
    check(!ShelfPolicy.oldPage(Arrays.asList(a,b),epoch-1001,true));
    check(!ShelfPolicy.oldPage(Arrays.asList(b,a),epoch,true));
    check(!ShelfPolicy.oldPage(Collections.emptyList(),epoch,true));
    b.updatedAt=0;check(!ShelfPolicy.oldPage(Arrays.asList(a,b),epoch,true));
    String row="<table><tr><td><a href='/truyen/1-a'>A</a></td><td><a href='/truyen/1-a/c123-chapter'>Chapter 123</a><time datetime='2026-10-07T05:00:00Z'></time><span class='mark-read' data-unread='8'></span></td></tr></table>";
    List<HakoParser.Link> links=HakoParser.shelf(row,"https://docln.sbs/ke-sach");
    check(links.size()==1);check(links.get(0).latestKey.endsWith("|Chapter 123"));check(links.get(0).updatedAt==epoch);check(links.get(0).info.equals("8 chương mới"));
    check(HakoParser.shelfUpdateOrder("<select><option selected value='updated_desc'>Mới cập nhật</option></select>"));
    check(!HakoParser.shelfUpdateOrder("<select><option value='updated_desc'>Mới cập nhật</option><option selected>Tên truyện</option></select>"));
    check(!HakoParser.shelfUpdateOrder("<select><option selected value='updated_asc'>Cập nhật cũ nhất</option></select>"));
    System.out.println(n+" focused shelf cases passed");
  }
}
