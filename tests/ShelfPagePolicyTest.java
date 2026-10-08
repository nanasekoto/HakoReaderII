package vn.nanase.hako;
public final class ShelfPagePolicyTest {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  String o="https://docln.sbs";
  check(ShelfPagePolicy.isPage(o,o+"/ke-sach?page=2"),"simple");
  check(ShelfPagePolicy.isPage(o,o+"/ke-sach?sort=updated&page=2"),"sort before");
  check(ShelfPagePolicy.isPage(o,o+"/ke-sach?page=3&filter=unread"),"filter after");
  check(!ShelfPagePolicy.isPage(o,"https://other.test/ke-sach?page=2"),"foreign");
  check(!ShelfPagePolicy.isPage(o,o+"/truyen?page=2"),"wrong path");
  check(!ShelfPagePolicy.isPage(o,o+"/ke-sach?notpage=2"),"wrong parameter");
  check(!ShelfPagePolicy.isPage(o,o+"/ke-sach?page=0"),"zero");
  check(!ShelfPagePolicy.isPage(o,"http://docln.sbs/ke-sach?page=2"),"http");
  System.out.println("ShelfPagePolicyTest passed");
 }
}
