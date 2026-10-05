package vn.nanase.hako;

/** A chord requires overlapping physical holds, never timing of earlier presses. */
final class VolumeChord {
  static final int TOGGLE = 2;
  private boolean up, down, consumed;

  int event(boolean lower, boolean pressed, boolean released, boolean canceled) {
    if (pressed) {
      if (lower) down = true; else up = true;
      if (up && down && !consumed) { consumed = true; return TOGGLE; }
      return 0;
    }
    if (released) {
      boolean held = lower ? down : up;
      if (lower) down = false; else up = false;
      int result = held && !consumed && !canceled ? (lower ? 1 : -1) : 0;
      if (!up && !down) consumed = false;
      return result;
    }
    return 0;
  }

  void reset() { up = down = consumed = false; }
}
