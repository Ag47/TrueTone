package app.truetone;

import android.content.Context;
import android.media.*;
import android.os.Process;
import app.truetone.core.*;
import java.util.concurrent.ConcurrentHashMap;

final class AudioEngine implements AutoCloseable {
  private final AudioManager manager;
  private final AudioFocusRequest focus;
  private final ConcurrentHashMap<Integer, Live> live = new ConcurrentHashMap<>();
  private volatile boolean running = true, loop, metronome;
  private volatile Synth synth;
  private volatile long frame;
  private final Thread thread;
  private volatile AudioTrack output;
  private volatile String failure;

  private static final class Live {
    final int pitch, velocity;
    final long start;

    Live(int pitch, int velocity, long start) {
      this.pitch = pitch;
      this.velocity = velocity;
      this.start = start;
    }
  }

  AudioEngine(Context context, Runnable interrupted) {
    manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    AudioAttributes attributes =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(
                change -> {
                  if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    stop();
                    interrupted.run();
                  }
                })
            .build();
    thread = new Thread(() -> render(attributes), "TrueTone-audio");
    thread.start();
  }

  boolean acquire() {
    return failure == null
        && manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  void play(Project p, boolean repeat, boolean click) {
    loop = repeat;
    metronome = click;
    synth = new Synth(p);
  }

  boolean playing() {
    Synth s = synth;
    return s != null && (loop || !s.finished());
  }

  void noteOn(int id, int pitch, int velocity) {
    if (live.size() < 16) live.put(id, new Live(pitch, velocity, frame));
  }

  void noteOff(int id) {
    live.remove(id);
  }

  void stop() {
    synth = null;
    live.clear();
    manager.abandonAudioFocusRequest(focus);
  }

  String failure() {
    return failure;
  }

  private void render(AudioAttributes attributes) {
    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
    AudioTrack track = null;
    try {
      int min =
          AudioTrack.getMinBufferSize(
              Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
      track =
          new AudioTrack.Builder()
              .setAudioAttributes(attributes)
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setSampleRate(Synth.RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .build())
              .setTransferMode(AudioTrack.MODE_STREAM)
              .setBufferSizeInBytes(Math.max(2048, min))
              .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
              .build();
      output = track;
      track.play();
      short[] buffer = new short[256];
      while (running) {
        Synth current = synth;
        if (current == null) java.util.Arrays.fill(buffer, (short) 0);
        else current.fill(buffer, loop, metronome);
        for (int i = 0; i < buffer.length; i++) {
          double value = buffer[i] / 32767.0;
          for (Live n : live.values())
            value += Synth.tone(n.pitch, n.velocity, frame + i - n.start, Long.MAX_VALUE / 4);
          buffer[i] = (short) (Math.max(-1, Math.min(1, value)) * 32767);
        }
        int written = 0;
        while (running && written < buffer.length) {
          int n = track.write(buffer, written, buffer.length - written, AudioTrack.WRITE_BLOCKING);
          if (n <= 0) throw new IllegalStateException("Audio output interrupted (" + n + ")");
          written += n;
        }
        frame += buffer.length;
        if (current != null && current == synth && !loop && current.finished() && live.isEmpty()) {
          synth = null;
          manager.abandonAudioFocusRequest(focus);
        }
      }
    } catch (RuntimeException ex) {
      failure = "Audio output is unavailable. Reopen the app to retry.";
    } finally {
      output = null;
      if (track != null) {
        track.stop();
        track.release();
      }
    }
  }

  @Override
  public void close() {
    running = false;
    stop(); /* Audio thread owns and releases its track. */
  }
}
