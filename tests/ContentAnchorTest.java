package vn.nanase.hako;
import java.util.*;
public final class ContentAnchorTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  List<String> phone=Arrays.asList("\ufffc","Đoạn mở đầu kể lại chuyến đi.","\ufffc","Đây là đoạn cần tiếp tục trên thiết bị còn lại.","Hết.");
  List<String> s4=Arrays.asList(phone.get(1),phone.get(3),phone.get(4));
  ContentAnchor.Capture saved=ContentAnchor.capture(phone,3,.3f);
  ContentAnchor.Position restored=ContentAnchor.resolve(s4,3,saved.textParagraph,saved.fraction,saved.quote);
  check(restored.paragraph==1,"Phone image blocks must not shift S4 text anchors");
  saved=ContentAnchor.capture(s4,1,.999f);restored=ContentAnchor.resolve(phone,1,saved.textParagraph,saved.fraction,saved.quote);
  check(restored.paragraph==3&&Math.abs(restored.fraction-.999f)<.001,"Short/empty end quote uses logical paragraph and keeps fraction");
  saved=ContentAnchor.capture(s4,2,.5f);restored=ContentAnchor.resolve(phone,2,saved.textParagraph,saved.fraction,saved.quote);
  check(restored.paragraph==4,"Short paragraphs remain positionable");
  List<String> repeated=Arrays.asList("Cùng một câu được lặp lại trong truyện.","Giữa hai lần.","Cùng một câu được lặp lại trong truyện.");
  saved=ContentAnchor.capture(repeated,2,0);restored=ContentAnchor.resolve(repeated,2,saved.textParagraph,0,saved.quote);
  check(restored.paragraph==2,"Repeated text does not jump to its first appearance");
  List<String> emoji=Arrays.asList("Nguyễn 😀\u00a0  mở sách, rồi tiếp tục cuộc hành trình qua khu rừng.");
  saved=ContentAnchor.capture(emoji,0,.12f);restored=ContentAnchor.resolve(emoji,0,saved.textParagraph,saved.fraction,saved.quote);
  check(restored.paragraph==0&&restored.fraction>=0&&restored.fraction<1,"Unicode/whitespace anchors remain bounded");
  restored=ContentAnchor.resolve(Collections.emptyList(),3,1,.2f,"Một neo chưa có file");
  check(restored.paragraph==3,"Absent cache preserves the locator for deferred resolution");
  System.out.println("PASS logical anchors: images, reverse direction, paragraph end, short text, repeated text, Unicode, absent cache");
 }
}
