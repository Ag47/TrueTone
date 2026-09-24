package app.truetone.core;

import java.io.*;
import java.util.*;

/** SMF type 0/1, PPQ timing. Reject unsupported timing rather than silently change music. */
public final class MidiCodec {
  public static final int MAX_BYTES = 1024 * 1024;

  private MidiCodec() {}

  private static final class Event {
    final long tick;
    final int order;
    final byte[] data;

    Event(long tick, int order, byte... data) {
      this.tick = tick;
      this.order = order;
      this.data = data;
    }
  }

  private static void vlq(DataOutputStream out, long value) throws IOException {
    if (value < 0 || value > 0xfffffff) throw new IOException("MIDI delta out of range");
    int buffer = (int) (value & 127);
    while ((value >>= 7) > 0) {
      buffer <<= 8;
      buffer |= (int) (value & 127) | 128;
    }
    while (true) {
      out.writeByte(buffer);
      if ((buffer & 128) == 0) break;
      buffer >>= 8;
    }
  }

  public static byte[] encode(Project p) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(0x4d546864);
    out.writeInt(6);
    out.writeShort(1);
    out.writeShort(p.tracks.size() + 1);
    out.writeShort(Project.PPQ);
    int tempo = Math.round(60000000f / p.bpm);
    List<Event> meta = new ArrayList<>();
    meta.add(
        new Event(
            0,
            0,
            (byte) 255,
            (byte) 81,
            (byte) 3,
            (byte) (tempo >> 16),
            (byte) (tempo >> 8),
            (byte) tempo));
    meta.add(
        new Event(
            0,
            1,
            (byte) 255,
            (byte) 88,
            (byte) 4,
            (byte) p.numerator,
            (byte) Integer.numberOfTrailingZeros(p.denominator),
            (byte) 24,
            (byte) 8));
    writeTrack(out, meta, p.lengthTick);
    for (List<NoteEvent> notes : p.tracks) {
      List<Event> events = new ArrayList<>();
      Map<Integer, Long> ends = new HashMap<>();
      for (NoteEvent n : notes) {
        int key = n.channel * 128 + n.pitch;
        if (ends.getOrDefault(key, 0L) > n.startTick)
          throw new IOException(
              "Overlapping notes of the same pitch/channel cannot be exported reliably as MIDI."
                  + " Adjust them or share a TrueTone file.");
        ends.put(key, n.startTick + n.durationTick);
        events.add(
            new Event(
                n.startTick, 1, (byte) (0x90 | n.channel), (byte) n.pitch, (byte) n.velocity));
        events.add(
            new Event(
                n.startTick + n.durationTick,
                0,
                (byte) (0x80 | n.channel),
                (byte) n.pitch,
                (byte) 0));
      }
      writeTrack(out, events, p.lengthTick);
    }
    return bytes.toByteArray();
  }

  private static void writeTrack(DataOutputStream out, List<Event> events, long end)
      throws IOException {
    events.sort(Comparator.comparingLong((Event e) -> e.tick).thenComparingInt(e -> e.order));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream track = new DataOutputStream(buffer);
    long tick = 0;
    for (Event event : events) {
      vlq(track, event.tick - tick);
      track.write(event.data);
      tick = event.tick;
    }
    vlq(track, end - tick);
    track.write(new byte[] {(byte) 255, 47, 0});
    out.writeInt(0x4d54726b);
    out.writeInt(buffer.size());
    out.write(buffer.toByteArray());
  }

  private static final class Reader {
    final byte[] bytes;
    int pos;
    final int end;

    Reader(byte[] bytes, int pos, int end) {
      this.bytes = bytes;
      this.pos = pos;
      this.end = end;
    }

    int u8() throws IOException {
      if (pos >= end) throw new EOFException("Truncated MIDI");
      return bytes[pos++] & 255;
    }

    int u16() throws IOException {
      return u8() << 8 | u8();
    }

    long u32() throws IOException {
      return ((long) u16() << 16) | u16();
    }

    long vlq() throws IOException {
      long value = 0;
      for (int i = 0; i < 4; i++) {
        int b = u8();
        value = value << 7 | (b & 127);
        if ((b & 128) == 0) return value;
      }
      throw new IOException("Invalid MIDI variable length number");
    }

    void skip(long n) throws IOException {
      if (n < 0 || n > end - pos) throw new EOFException("Truncated MIDI event");
      pos += (int) n;
    }

    int data() throws IOException {
      int b = u8();
      if (b > 127) throw new IOException("Invalid MIDI data byte");
      return b;
    }
  }

  private static final class On {
    final long tick;
    final int velocity;

    On(long t, int v) {
      tick = t;
      velocity = v;
    }
  }

  public static Project decode(byte[] bytes, String title) throws IOException {
    if (bytes == null || bytes.length < 14 || bytes.length > MAX_BYTES)
      throw new IOException("Invalid MIDI size");
    Reader in = new Reader(bytes, 0, bytes.length);
    if (in.u32() != 0x4d546864) throw new IOException("Choose a MIDI or TrueTone file");
    long header = in.u32();
    if (header < 6 || header > 1024) throw new IOException("Invalid MIDI header");
    int type = in.u16(), count = in.u16(), ppq = in.u16();
    in.skip(header - 6);
    if (type > 1
        || count < 1
        || count > 64
        || (type == 0 && count != 1)
        || ppq == 0
        || (ppq & 0x8000) != 0)
      throw new IOException("Only MIDI type 0/1 with beat-based timing is supported");
    List<List<NoteEvent>> tracks = new ArrayList<>();
    int tempo = 500000, num = 4, den = 4, total = 0;
    boolean tempoSeen = false, meterSeen = false;
    long length = 1;
    for (int t = 0; t < count; t++) {
      if (in.u32() != 0x4d54726b) throw new IOException("Missing MIDI track");
      long size = in.u32();
      if (size > in.end - in.pos) throw new IOException("Truncated MIDI track");
      Reader tr = new Reader(bytes, in.pos, in.pos + (int) size);
      in.skip(size);
      Map<Integer, Deque<On>> active = new HashMap<>();
      List<NoteEvent> notes = new ArrayList<>();
      long tick = 0;
      int running = 0;
      boolean ended = false;
      while (tr.pos < tr.end) {
        tick += tr.vlq();
        long scaled = Math.round(tick * (double) Project.PPQ / ppq);
        if (scaled > Project.MAX_TICKS) throw new IOException("MIDI is longer than 256 beats");
        length = Math.max(length, scaled);
        int status = tr.u8();
        if (status < 128) {
          if (running == 0) throw new IOException("Invalid running status");
          tr.pos--;
          status = running;
        }
        if (status < 0xf0) running = status;
        else running = 0;
        if (status == 255) {
          int kind = tr.u8();
          long n = tr.vlq();
          if (kind == 47) {
            if (n != 0 || tr.pos != tr.end) throw new IOException("Invalid end-of-track");
            ended = true;
            break;
          }
          if (kind == 81) {
            if (n != 3) throw new IOException("Invalid tempo event");
            int next = tr.u8() << 16 | tr.u8() << 8 | tr.u8();
            if (next == 0 || (tick > 0 && next != tempo) || (tempoSeen && next != tempo))
              throw new IOException("Tempo-changing MIDI is not supported yet");
            tempo = next;
            tempoSeen = true;
          } else if (kind == 88) {
            if (n != 4) throw new IOException("Invalid time signature");
            int a = tr.u8(), power = tr.u8();
            tr.skip(2);
            if (power > 4) throw new IOException("Unsupported time signature");
            int b = 1 << power;
            if ((tick > 0 || meterSeen) && (a != num || b != den))
              throw new IOException("Changing meter is not supported");
            num = a;
            den = b;
            meterSeen = true;
          } else tr.skip(n);
        } else if (status == 0xf0 || status == 0xf7) {
          tr.skip(tr.vlq());
        } else if (status >= 0xf0) throw new IOException("Unsupported MIDI system event");
        else {
          int kind = status & 0xf0, channel = status & 15, pitch = tr.data();
          int value = (kind == 0xc0 || kind == 0xd0) ? 0 : tr.data();
          int key = channel * 128 + pitch;
          if (kind == 0x90 && value > 0) {
            if (++total > Project.MAX_NOTES) throw new IOException("MIDI exceeds 4,096 notes");
            active.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(new On(scaled, value));
          } else if (kind == 0x80 || (kind == 0x90 && value == 0)) {
            Deque<On> queue = active.get(key);
            if (queue == null || queue.isEmpty())
              throw new IOException("MIDI has an unmatched note-off");
            On on = queue.removeFirst();
            if (scaled <= on.tick)
              throw new IOException("MIDI note is shorter than supported resolution");
            notes.add(new NoteEvent(pitch, on.velocity, on.tick, scaled - on.tick, channel));
          } else if ((kind == 0xb0 && pitch == 64 && value >= 64) || kind == 0xe0)
            throw new IOException(
                "Sustain or pitch-bend MIDI needs conversion to plain notes first");
        }
      }
      if (!ended) throw new IOException("Missing MIDI end-of-track");
      for (Deque<On> q : active.values())
        if (!q.isEmpty()) throw new IOException("MIDI has an unfinished note");
      if (!notes.isEmpty()) tracks.add(notes);
    }
    if (in.pos != in.end) throw new IOException("Unexpected data after MIDI");
    if (tracks.isEmpty()) tracks.add(Collections.emptyList());
    int bpm = (int) Math.round(60000000.0 / tempo);
    if (bpm < 40 || bpm > 240 || Math.abs(tempo - Math.round(60000000.0 / bpm)) > 1)
      throw new IOException("MIDI tempo must be a whole number from 40 to 240 BPM");
    try {
      return new Project(title, bpm, num, den, length, tracks);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }
}
