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
    check(IllustrationPolicy.hint("Tập 01: Khởi đầu · Minh họa"));
    check(IllustrationPolicy.hint("Minh họa · Ellie"));
    check(IllustrationPolicy.hint("Minh họa các tập · Vol 16"));
    check(IllustrationPolicy.hint("WN · Vol 1 Illustrations"));
    check(IllustrationPolicy.hint("〖Volume 12 Light Novel〗Minh Họa"));
    check(IllustrationPolicy.hint("Minh hoạ - Fanart · Nhân vật"));
    check(!IllustrationPolicy.hint("Chương 1 · Một hình ảnh đáng nhớ"));
    check(IllustrationPolicy.kind("Minh họa · Ellie","<p>Ellie</p><img src='https://i.docln.net/a.jpg'>")==IllustrationPolicy.CANDIDATE);
    check(IllustrationPolicy.kind("Tập 1 · Chương 1","<p>Đây là chương truyện ngắn.</p><img src='https://i.docln.net/a.jpg'>")==IllustrationPolicy.TEXT);
    check(IllustrationPolicy.kind("Minh họa · Thông báo","<p>Thông báo dịch.</p>")==IllustrationPolicy.TEXT);
    check(IllustrationPolicy.kind("Minh họa","")==IllustrationPolicy.UNKNOWN);
    check(IllustrationPolicy.skipCachedIllustration(2,true,false));
    check(!IllustrationPolicy.skipCachedIllustration(2,false,false));
    check(!IllustrationPolicy.skipCachedIllustration(2,true,true));
    check(!IllustrationPolicy.skipCachedIllustration(3,true,false));
    System.out.println("PASS 26 illustration and queue policy checks");
  }
}
