package app.truetone.core;

import java.io.*;
import java.util.*;

/** Same deterministic instrument and frame schedule for preview and WAV export. */
public final class Synth {
  public static final int RATE = 48000;
  private static final double[] PHASE_STEP = new double[128];

  static {
    for (int p = 0; p < 128; p++)
      PHASE_STEP[p] = 2 * Math.PI * 440 * Math.pow(2, (p - 69) / 12.0) / RATE;
  }

  private final Project project;
  private final List<NoteEvent> notes;
  private final List<Voice> active = new ArrayList<>();
  private int next;
  private long frame;
  public final long lengthFrames;

  public Synth(Project project) {
    this.project = project;
    notes = project.notes();
    lengthFrames = project.frameAtTick(project.lengthTick, RATE);
  }

  private static final class Voice {
    final NoteEvent n;
    final long start, end;

    Voice(NoteEvent n, long start, long end) {
      this.n = n;
      this.start = start;
      this.end = end;
    }
  }

  public static double tone(int pitch, int velocity, long age, long duration) {
    double t = age / (double) RATE, remaining = (duration - age) / (double) RATE;
    if (age < 0 || remaining <= 0) return 0;
    double envelope =
        Math.min(1, t / .005) * Math.min(1, remaining / .025) * (.65 + .35 * Math.exp(-t * 3));
    double phase = PHASE_STEP[pitch] * age;
    return (Math.sin(phase) + .22 * Math.sin(phase * 2) + .08 * Math.sin(phase * 3))
        * envelope
        * velocity
        / 127.0
        * .18;
  }

  public synchronized void reset() {
    next = 0;
    frame = 0;
    active.clear();
  }

  public synchronized void fill(short[] samples, boolean loop, boolean metronome) {
    for (int i = 0; i < samples.length; i++) {
      if (frame >= lengthFrames) {
        if (loop) reset();
        else {
          samples[i] = 0;
          continue;
        }
      }
      while (next < notes.size() && project.frameAtTick(notes.get(next).startTick, RATE) <= frame) {
        NoteEvent n = notes.get(next++);
        active.add(
            new Voice(
                n,
                project.frameAtTick(n.startTick, RATE),
                project.frameAtTick(n.startTick + n.durationTick, RATE)));
      }
      double value = 0;
      for (Iterator<Voice> it = active.iterator(); it.hasNext(); ) {
        Voice v = it.next();
        if (frame >= v.end) it.remove();
        else value += tone(v.n.pitch, v.n.velocity, frame - v.start, v.end - v.start);
      }
      if (metronome) {
        // Absolute beat index; no rounded-interval accumulation.
        long beat = (long) Math.floor(frame * project.bpm / (60.0 * RATE));
        long age = frame - project.frameAtTick(beat * Project.PPQ, RATE);
        if (age >= 0 && age < RATE / 40)
          value += .18 * Math.sin(age * 2 * Math.PI * 1400 / RATE) * (1 - age / (RATE / 40.0));
      }
      samples[i] = (short) Math.round(Math.max(-1, Math.min(1, value)) * 32767);
      frame++;
    }
  }

  public synchronized boolean finished() {
    return frame >= lengthFrames;
  }

  public static void writeWav(Project p, OutputStream output) throws IOException {
    Synth synth = new Synth(p);
    DataOutputStream out = new DataOutputStream(output);
    long size = synth.lengthFrames * 2;
    out.writeBytes("RIFF");
    le32(out, size + 36);
    out.writeBytes("WAVEfmt ");
    le32(out, 16);
    le16(out, 1);
    le16(out, 1);
    le32(out, RATE);
    le32(out, RATE * 2);
    le16(out, 2);
    le16(out, 16);
    out.writeBytes("data");
    le32(out, size);
    short[] block = new short[512];
    long remaining = synth.lengthFrames;
    while (remaining > 0) {
      synth.fill(block, false, false);
      int n = (int) Math.min(block.length, remaining);
      for (int i = 0; i < n; i++) le16(out, block[i]);
      remaining -= n;
    }
    out.flush();
  }

  private static void le16(DataOutputStream out, int v) throws IOException {
    out.writeByte(v);
    out.writeByte(v >> 8);
  }

  private static void le32(DataOutputStream out, long v) throws IOException {
    le16(out, (int) v);
    le16(out, (int) (v >> 16));
  }
}
