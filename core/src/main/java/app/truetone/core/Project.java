package app.truetone.core;

import java.util.*;

/** Immutable, engine-independent source for editing, playback, persistence and sharing. */
public final class Project {
  public static final int VERSION = 1, PPQ = 960, MAX_NOTES = 4096, MAX_VOICES = 32;
  public static final long MAX_TICKS = PPQ * 256L;
  public final String title;
  public final int bpm, numerator, denominator;
  public final long lengthTick;
  public final List<List<NoteEvent>> tracks;

  public Project(
      String title,
      int bpm,
      int numerator,
      int denominator,
      long lengthTick,
      List<List<NoteEvent>> tracks) {
    if (title == null
        || title.trim().isEmpty()
        || title.length() > 80
        || title.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Use a title of 1–80 characters");
    if (bpm < 40
        || bpm > 240
        || numerator < 1
        || numerator > 12
        || !(denominator == 2 || denominator == 4 || denominator == 8 || denominator == 16)
        || lengthTick < 1
        || lengthTick > MAX_TICKS
        || tracks == null
        || tracks.isEmpty()
        || tracks.size() > 16)
      throw new IllegalArgumentException("Unsupported tempo, meter, length or track count");
    this.title = title.trim();
    this.bpm = bpm;
    this.numerator = numerator;
    this.denominator = denominator;
    this.lengthTick = lengthTick;
    int count = 0;
    List<List<NoteEvent>> copy = new ArrayList<>();
    for (List<NoteEvent> track : tracks) {
      List<NoteEvent> sorted = new ArrayList<>(track);
      for (NoteEvent n : sorted) {
        if (n == null || n.startTick + n.durationTick > lengthTick)
          throw new IllegalArgumentException("Note exceeds project length");
      }
      count += sorted.size();
      if (count > MAX_NOTES)
        throw new IllegalArgumentException("This project has reached the 4,096-note limit");
      sorted.sort(Comparator.comparingLong(n -> n.startTick)); // stable; overdubs retain order
      copy.add(Collections.unmodifiableList(sorted));
    }
    List<long[]> boundaries = new ArrayList<>();
    for (List<NoteEvent> track : copy)
      for (NoteEvent note : track) {
        boundaries.add(new long[] {note.startTick, 1});
        boundaries.add(new long[] {note.startTick + note.durationTick, -1});
      }
    boundaries.sort(
        Comparator.comparingLong((long[] edge) -> edge[0]).thenComparingLong(edge -> edge[1]));
    int voices = 0;
    for (long[] edge : boundaries) {
      voices += edge[1];
      if (voices > MAX_VOICES)
        throw new IllegalArgumentException("A melody supports up to 32 simultaneous notes");
    }
    this.tracks = Collections.unmodifiableList(copy);
  }

  public static Project empty() {
    return new Project(
        "Untitled melody", 100, 4, 4, PPQ * 16, Collections.singletonList(Collections.emptyList()));
  }

  public List<NoteEvent> notes() {
    List<NoteEvent> all = new ArrayList<>();
    for (List<NoteEvent> t : tracks) all.addAll(t);
    all.sort(Comparator.comparingLong(n -> n.startTick));
    return Collections.unmodifiableList(all);
  }

  public Project withNotes(List<NoteEvent> notes) {
    return new Project(
        title, bpm, numerator, denominator, lengthTick, Collections.singletonList(notes));
  }

  public Project add(NoteEvent note) {
    List<List<NoteEvent>> copy = new ArrayList<>(tracks);
    List<NoteEvent> first = new ArrayList<>(copy.get(0));
    first.add(note);
    copy.set(0, first);
    return new Project(title, bpm, numerator, denominator, lengthTick, copy);
  }

  public Project settings(String name, int tempo) {
    return new Project(name, tempo, numerator, denominator, lengthTick, tracks);
  }

  public long tickAtNanos(long nanos) {
    return Math.max(0, Math.round(nanos / 1e9 * bpm * PPQ / 60.0));
  }

  public long frameAtTick(long tick, int sampleRate) {
    return Math.round(tick * 60.0 * sampleRate / (bpm * PPQ));
  }
}
