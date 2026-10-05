package vn.nanase.hako;

/** One hold action; repeats and release after holding cannot turn a page. */
final class HoldKey {
  private boolean pressed, consumed;
  boolean down(){if(pressed)return false;pressed=true;consumed=false;return true;}
  boolean hold(){if(!pressed||consumed)return false;consumed=true;return true;}
  boolean up(boolean canceled){boolean turn=pressed&&!consumed&&!canceled;reset();return turn;}
  void reset(){pressed=consumed=false;}
}
