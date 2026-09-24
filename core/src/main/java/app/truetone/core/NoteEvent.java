package app.truetone.core;

import java.util.Objects;

/** Musical time is integer ticks at Project.PPQ ticks per quarter-note beat. */
public final class NoteEvent {
  public final int pitch, velocity, channel;
  public final long startTick, durationTick;

  public NoteEvent(int pitch, int velocity, long startTick, long durationTick, int channel) {
    if (pitch < 0
        || pitch > 127
        || velocity < 1
        || velocity > 127
        || channel < 0
        || channel > 15
        || startTick < 0
        || durationTick < 1
        || startTick > Project.MAX_TICKS - durationTick)
      throw new IllegalArgumentException(
          "Note is outside the supported pitch, velocity or 256-beat range");
    this.pitch = pitch;
    this.velocity = velocity;
    this.startTick = startTick;
    this.durationTick = durationTick;
    this.channel = channel;
  }

  public double startBeat() {
    return (double) startTick / Project.PPQ;
  }

  public double durationBeats() {
    return (double) durationTick / Project.PPQ;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NoteEvent)) return false;
    NoteEvent n = (NoteEvent) o;
    return pitch == n.pitch
        && velocity == n.velocity
        && channel == n.channel
        && startTick == n.startTick
        && durationTick == n.durationTick;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pitch, velocity, channel, startTick, durationTick);
  }
}
