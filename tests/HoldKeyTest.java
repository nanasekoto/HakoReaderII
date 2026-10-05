package vn.nanase.hako;

public final class HoldKeyTest {
  static void check(boolean value){if(!value)throw new AssertionError();}
  public static void main(String[] args){
    HoldKey k=new HoldKey();
    check(k.down());check(!k.down());check(k.up(false));
    check(!k.up(false));check(!k.hold());
    check(k.down());check(k.hold());check(!k.hold());check(!k.down());check(!k.up(false));
    check(k.down());check(!k.up(true));
    check(k.down());k.reset();check(!k.hold());check(!k.up(false));
    check(k.down());check(k.hold());check(!k.up(false));
    System.out.println("PASS single-key hold, repeat, release and cancellation checks");
  }
}
