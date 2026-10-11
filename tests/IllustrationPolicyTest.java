package vn.nanase.hako;
public class IllustrationPolicyTest {
  static void check(boolean v){if(!v)throw new AssertionError();}
  public static void main(String[] args){
    check(IllustrationPolicy.hint("Minh họa tập 1"));
    check(!IllustrationPolicy.hint("Chương 1: Một hình ảnh đáng nhớ"));
    check(IllustrationPolicy.kind("<img src='https://i.docln.net/a.jpg'>")==2);
    check(IllustrationPolicy.kind("<p>Câu chuyện có chữ thật.</p><img src='https://i.docln.net/a.jpg'>")==1);
    check(IllustrationPolicy.kind("<p>[Ảnh ngoài máy chủ HAKO — xem trên web]</p>")==0);
    check(IllustrationPolicy.shouldYield("a","b"));
    check(!IllustrationPolicy.shouldYield("a","a"));
    check(!IllustrationPolicy.shouldYield("a",""));
    check(!IllustrationPolicy.shouldAttempt(true,true,false));
    check(IllustrationPolicy.shouldAttempt(true,true,true));
    check(IllustrationPolicy.shouldAttempt(false,true,false));
    System.out.println("PASS 11 illustration and queue policy checks");
  }
}
