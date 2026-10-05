package vn.nanase.hako;

public final class VolumeChordTest {
  private static void expect(int actual, int expected) {
    if(actual!=expected) throw new AssertionError(actual+" != "+expected);
  }
  public static void main(String[] args) {
    VolumeChord c=new VolumeChord();
    expect(c.event(true,true,false,false),0);
    expect(c.event(true,false,true,false),1);
    expect(c.event(false,true,false,false),0);
    expect(c.event(false,false,true,false),-1);
    for(boolean first:new boolean[]{true,false}) {
      expect(c.event(first,true,false,false),0);
      expect(c.event(!first,true,false,false),VolumeChord.TOGGLE);
      expect(c.event(first,true,false,false),0); // Android auto-repeat
      expect(c.event(!first,false,true,false),0);
      expect(c.event(!first,true,false,false),0); // still part of the same hold
      expect(c.event(first,false,true,false),0);
      expect(c.event(!first,false,true,false),0);
    }
    expect(c.event(true,true,false,false),0);
    expect(c.event(true,false,true,true),0); // canceled key
    expect(c.event(false,true,false,false),0);
    c.reset();
    expect(c.event(false,false,true,false),0); // focus loss consumes dangling release
    System.out.println("PASS volume chord sequences");
  }
}
