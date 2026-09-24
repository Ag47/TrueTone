package app.truetone.core;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.midi.*;
import org.junit.Test;

public class CoreTest {
  private Project melody(int bpm) {
    return new Project(
        "Moon / 月光",
        bpm,
        3,
        4,
        3840,
        Arrays.asList(
            Arrays.asList(new NoteEvent(60, 30, 0, 480, 0), new NoteEvent(64, 110, 960, 1440, 0)),
            Collections.singletonList(new NoteEvent(72, 90, 240, 2000, 2))));
  }

  @Test
  public void exactProjectAndQrRoundTrip() throws Exception {
    Project p = melody(137), q = ProjectCodec.fromQr(ProjectCodec.toQr(p));
    assertEquals(p.notes(), q.notes());
    assertEquals(p.title, q.title);
    assertEquals(p.bpm, q.bpm);
    assertEquals(p.tracks, q.tracks);
    assertEquals(p.lengthTick, q.lengthTick);
  }

  @Test
  public void midiRoundTripAndIndependentReader() throws Exception {
    for (int bpm : new int[] {40, 73, 120, 137, 240}) {
      Project p = melody(bpm);
      byte[] midi = MidiCodec.encode(p);
      Project q = MidiCodec.decode(midi, p.title);
      assertEquals(p.notes(), q.notes());
      assertEquals(bpm, q.bpm);
      assertEquals(p.numerator, q.numerator);
      assertEquals(p.lengthTick, q.lengthTick);
      Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midi));
      assertEquals(Project.PPQ, seq.getResolution());
      assertEquals(3840, seq.getTickLength());
      long expectedMicros = Math.round(60000000.0 / bpm) * 4;
      assertEquals(expectedMicros, seq.getMicrosecondLength());
    }
  }

  @Test
  public void emptySingleAndStableOverdub() throws Exception {
    Project p = Project.empty().add(new NoteEvent(60, 80, 0, 480, 0));
    p = p.add(new NoteEvent(64, 90, 0, 960, 0));
    p = p.add(new NoteEvent(67, 100, 240, 120, 0));
    assertEquals(3, p.notes().size());
    assertEquals(60, p.notes().get(0).pitch);
    assertEquals(64, p.notes().get(1).pitch);
    assertTrue(MidiCodec.decode(MidiCodec.encode(Project.empty()), "Empty").notes().isEmpty());
    assertEquals(p.notes(), MidiCodec.decode(MidiCodec.encode(p), "Dub").notes());
  }

  @Test
  public void immutableSnapshotsAndUndo() {
    List<NoteEvent> notes = new ArrayList<>();
    Project p = Project.empty().withNotes(notes);
    notes.add(new NoteEvent(60, 90, 0, 240, 0));
    assertTrue(p.notes().isEmpty());
    Editor e = new Editor(p);
    e.change(p.add(notes.get(0)));
    e.undo();
    assertTrue(e.project.notes().isEmpty());
    e.redo();
    assertEquals(1, e.project.notes().size());
    assertThrows(UnsupportedOperationException.class, () -> p.tracks.add(notes));
  }

  @Test
  public void rejectsCorruptionTruncationAndOversize() throws Exception {
    byte[] b = ProjectCodec.encode(melody(100));
    b[10] ^= 3;
    assertThrows(IOException.class, () -> ProjectCodec.decode(b));
    assertThrows(IOException.class, () -> ProjectCodec.fromQr("http://example.com"));
    assertThrows(IOException.class, () -> ProjectCodec.fromQr("TRUETONE:1:!@#"));
    assertThrows(IOException.class, () -> ProjectCodec.fromQr("TRUETONE:1:" + "x".repeat(1300)));
    byte[] midi = MidiCodec.encode(melody(100));
    for (int i = 0; i < midi.length; i++) {
      byte[] truncated = Arrays.copyOf(midi, i);
      assertThrows(IOException.class, () -> MidiCodec.decode(truncated, "Invalid"));
    }
    assertThrows(
        IOException.class,
        () -> ProjectCodec.readBounded(new ByteArrayInputStream(new byte[100]), 99));
  }

  @Test
  public void eventLimitAndQrFallback() throws Exception {
    List<NoteEvent> notes = new ArrayList<>();
    for (int i = 0; i < Project.MAX_NOTES; i++) notes.add(new NoteEvent(60, 90, i * 3, 2, 0));
    Project p = Project.empty().withNotes(notes);
    assertEquals(4096, p.notes().size());
    assertThrows(IllegalArgumentException.class, () -> p.add(new NoteEvent(60, 90, 0, 240, 0)));
    assertThrows(IOException.class, () -> ProjectCodec.toQr(p));
    assertEquals(p.notes(), ProjectCodec.decode(ProjectCodec.encode(p)).notes());
  }

  @Test
  public void atomicStoreReopenAndIndependentFiles() throws Exception {
    File dir = Files.createTempDirectory("truetone-test").toFile();
    ProjectStore s = new ProjectStore(dir);
    s.save("draft", melody(87));
    s.save("one", melody(110));
    s.save("two", Project.empty());
    assertEquals(87, new ProjectStore(dir).load("draft").bpm);
    assertEquals(2, s.ids().size());
    s.save("one", melody(135));
    assertEquals(135, s.load("one").bpm);
    assertThrows(IllegalArgumentException.class, () -> s.save("../bad", Project.empty()));
    assertEquals(3, Objects.requireNonNull(dir.list()).length);
  }

  @Test
  public void frameClockAndWavAgree() throws Exception {
    Project p =
        new Project(
            "Short",
            137,
            4,
            4,
            960,
            Collections.singletonList(
                Collections.singletonList(new NoteEvent(69, 100, 240, 480, 0))));
    Synth synth = new Synth(p);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Synth.writeWav(p, out);
    byte[] wav = out.toByteArray();
    assertEquals(44 + synth.lengthFrames * 2, wav.length);
    short[] samples = new short[(int) synth.lengthFrames];
    synth.fill(samples, false, false);
    for (int i = 0; i < samples.length; i++)
      assertEquals(samples[i], (short) ((wav[44 + 2 * i] & 255) | (wav[45 + 2 * i] << 8)));
    assertEquals(960, p.tickAtNanos(Math.round(60e9 / 137)));
  }

  @Test
  public void quantizeRetainsChannelsAndTracks() {
    Editor e = new Editor(melody(100));
    e.quantize(240);
    assertEquals(2, e.project.tracks.size());
    assertEquals(2, e.project.tracks.get(1).get(0).channel);
    e.undo();
    assertEquals(melody(100).notes(), e.project.notes());
  }

  @Test
  public void legacyFixturesHaveExplicitOutcome() throws Exception {
    for (String name : new String[] {"TrueTone.mid", "Minuet.mid"}) {
      byte[] data = getClass().getResourceAsStream("/legacy/" + name).readAllBytes();
      try {
        Project p = MidiCodec.decode(data, name);
        assertFalse(p.notes().isEmpty());
        assertEquals(p.notes(), MidiCodec.decode(MidiCodec.encode(p), name).notes());
        System.out.println(
            "Legacy " + name + ": " + p.notes().size() + " notes, " + p.bpm + " BPM");
      } catch (IOException ex) {
        System.out.println("Legacy " + name + ": rejected explicitly: " + ex.getMessage());
        // Fixtures must have known supported or unsupported outcomes, not arbitrary parse failures.
        assertEquals(
            "Sustain or pitch-bend MIDI needs conversion to plain notes first", ex.getMessage());
      }
    }
  }

  @Test
  public void midiRejectsAmbiguousOverlappingVoices() {
    Project p =
        Project.empty()
            .add(new NoteEvent(60, 100, 0, 960, 0))
            .add(new NoteEvent(60, 90, 240, 480, 0));
    assertThrows(IOException.class, () -> MidiCodec.encode(p));
  }

  @Test
  public void boundedMalformedInputNeverEscapesParser() {
    Random random = new Random(47);
    for (int i = 0; i < 1000; i++) {
      byte[] bytes = new byte[random.nextInt(1024)];
      random.nextBytes(bytes);
      try {
        MidiCodec.decode(bytes, "Fuzz");
        fail("Random bytes unexpectedly parsed");
      } catch (IOException expected) {
      }
      try {
        ProjectCodec.decode(bytes);
        fail("Random bytes unexpectedly parsed");
      } catch (IOException expected) {
      }
    }
  }

  @Test
  public void loopHasNoFrameDrift() {
    Project p =
        new Project(
            "Loop",
            137,
            4,
            4,
            960,
            Collections.singletonList(Collections.singletonList(new NoteEvent(60, 90, 0, 960, 0))));
    Synth synth = new Synth(p);
    int length = (int) synth.lengthFrames;
    short[] output = new short[length * 5];
    synth.fill(output, true, false);
    for (int i = length; i < output.length; i++) assertEquals(output[i % length], output[i]);
  }

  @Test
  public void polyphonyIsBoundedWithoutDroppingNotes() {
    Project p = Project.empty();
    for (int i = 0; i < Project.MAX_VOICES; i++) p = p.add(new NoteEvent(i, 90, 0, 960, 0));
    final Project full = p;
    assertEquals(32, full.notes().size());
    assertThrows(IllegalArgumentException.class, () -> full.add(new NoteEvent(80, 90, 0, 960, 0)));
  }

  @Test
  public void damagedDraftIsPreservedBeforeNewWork() throws Exception {
    File dir = Files.createTempDirectory("truetone-damaged").toFile();
    ProjectStore s = new ProjectStore(dir);
    byte[] damaged = {1, 2, 3};
    Files.write(new File(dir, "draft.ttn").toPath(), damaged);
    assertThrows(IOException.class, () -> s.load("draft"));
    s.preserveDamagedDraft();
    s.save("draft", melody(100));
    File[] preserved = dir.listFiles((d, n) -> n.endsWith(".damaged"));
    assertEquals(1, preserved.length);
    assertArrayEquals(damaged, Files.readAllBytes(preserved[0].toPath()));
  }
}
