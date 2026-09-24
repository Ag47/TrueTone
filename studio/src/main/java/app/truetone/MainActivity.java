package app.truetone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.truetone.core.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements SensorEventListener {
  static final int BG = 0xff101511,
      PANEL = 0xff1c241e,
      TEXT = 0xfff0f3e7,
      MUTED = 0xffa4b3a5,
      ACCENT = 0xffc8f58a;
  private static final int OPEN = 10, IMAGE = 11, SCAN = 12, EXPORT = 13, CAMERA = 14;
  private ProjectStore store;
  private Editor editor;
  private AudioEngine audio;
  private LinearLayout root, body;
  private TextView status, transport;
  private RollView roll;
  private String tab = "Create", exportFormat;
  private Project exportSnapshot, importPreview, takeBefore;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean recording, metronome, scale = true, tilt, storageBlocked;
  private long recordStart;
  private final Map<Integer, Held> held = new HashMap<>();
  private SensorManager sensors;
  private Sensor gravity;
  private float neutral, filtered, sensitivity = 1;
  private int velocity = 90;
  private long grid = 240, pageStart;
  private final Runnable stopTake = () -> finishTake(false);
  private final BroadcastReceiver noisy =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          finishTake(false);
          stopAudio();
          message("Audio route changed. Tap Play when ready.");
        }
      };

  private static final class Held {
    final int pitch, velocity;
    final long start;

    Held(int pitch, int velocity, long start) {
      this.pitch = pitch;
      this.velocity = velocity;
      this.start = start;
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
    gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
    if (gravity == null) gravity = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    try {
      store = new ProjectStore(new File(getFilesDir(), "projects"));
      editor = new Editor(store.hasDraft() ? store.load("draft") : Project.empty());
    } catch (IOException e) {
      editor = new Editor(Project.empty());
      try {
        if (store != null) store.preserveDamagedDraft();
      } catch (IOException archiveFailure) {
        storageBlocked = true;
      }
      new AlertDialog.Builder(this)
          .setTitle("Draft could not be opened")
          .setMessage(e.getMessage() + ". The existing file has been kept.")
          .setPositiveButton("OK", null)
          .show();
    }
    if (state != null) {
      tab = state.getString("tab", "Create");
      exportFormat = state.getString("format");
      try {
        if (state.getByteArray("export") != null)
          exportSnapshot = ProjectCodec.decode(state.getByteArray("export"));
        if (state.getByteArray("preview") != null)
          importPreview = ProjectCodec.decode(state.getByteArray("preview"));
      } catch (IOException e) {
        message(e.getMessage());
      }
    }
    render();
    if (importPreview != null) preview(importPreview);
  }

  @Override
  protected void onResume() {
    super.onResume();
    audio =
        new AudioEngine(
            this,
            () -> {
              finishTake(false);
              message("Playback stopped because another app needs audio");
            });
    IntentFilter filter = new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(noisy, filter);
    if (tilt && gravity != null)
      sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
  }

  @Override
  protected void onPause() {
    finishTake(false);
    releaseKeys();
    if (audio != null) {
      audio.close();
      audio = null;
    }
    sensors.unregisterListener(this);
    unregisterReceiver(noisy);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    io.shutdown();
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    out.putString("tab", tab);
    out.putString("format", exportFormat);
    try {
      if (exportSnapshot != null) out.putByteArray("export", ProjectCodec.encode(exportSnapshot));
      if (importPreview != null) out.putByteArray("preview", ProjectCodec.encode(importPreview));
    } catch (IOException e) {
      message(e.getMessage());
    }
    super.onSaveInstanceState(out);
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  private Button button(String label, Runnable click) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(TEXT);
    b.setTextSize(14);
    b.setMinHeight(dp(48));
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
    b.setOnClickListener(
        v -> {
          try {
            click.run();
          } catch (IllegalArgumentException e) {
            message(e.getMessage());
          }
        });
    return b;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    l.setOrientation(LinearLayout.HORIZONTAL);
    return l;
  }

  private void equal(LinearLayout row, View view) {
    row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private LinearLayout card() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(16), dp(12), dp(16), dp(12));
    box.setBackground(shape(PANEL, 18));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(8), 0, dp(8));
    body.addView(box, lp);
    return box;
  }

  private void render() {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    root.setPadding(dp(20), dp(12), dp(20), dp(8));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(
                dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, dp(8) + bars.bottom);
          } else v.setPadding(dp(20), dp(12), dp(20), dp(8));
          return insets;
        });
    LinearLayout brand = row();
    TextView logo = text("truetone", 28, ACCENT);
    logo.setTypeface(null, android.graphics.Typeface.BOLD);
    equal(brand, logo);
    brand.addView(text("YOUR LITTLE MELODY", 10, MUTED));
    root.addView(brand);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (tab.equals("Library")) library();
    else if (tab.equals("Import")) imports();
    else creator();
    status =
        text(
            recording
                ? "Recording · hold a key, then release"
                : "Saved on this device · no account needed",
            12,
            MUTED);
    root.addView(status);
    LinearLayout nav = row();
    for (String name : new String[] {"Library", "Create", "Import"}) {
      Button b =
          button(
              name,
              () -> {
                finishTake(false);
                stopAudio();
                tab = name;
                render();
              });
      if (name.equals(tab)) b.setTextColor(ACCENT);
      equal(nav, b);
    }
    root.addView(nav);
    setContentView(root);
  }

  private void creator() {
    if (recording) {
      body.addView(text("Your moment.\nMake it sing.", 30, TEXT));
      body.addView(text("Recording from beat 1 · hold keys to shape each note", 15, ACCENT));
      LinearLayout controls = row();
      equal(controls, button("Stop & keep", () -> finishTake(false)));
      equal(controls, button("Cancel take", () -> finishTake(true)));
      body.addView(controls);
      keyboard();
      return;
    }
    body.addView(text("Make something\nonly you could make.", 30, TEXT));
    LinearLayout details = row();
    TextView title = text(editor.project.title, 20, TEXT);
    equal(details, title);
    details.addView(button("Edit details", this::settings));
    body.addView(details);
    body.addView(
        text(
            editor.project.bpm
                + " BPM  ·  "
                + editor.project.numerator
                + "/"
                + editor.project.denominator
                + "  ·  "
                + editor.project.notes().size()
                + " notes",
            13,
            MUTED));
    LinearLayout actions = row();
    equal(actions, button("Templates", this::templates));
    equal(actions, button("Save copy", this::saveCopy));
    equal(actions, button("Share", this::shareMenu));
    body.addView(actions);
    LinearLayout player = card();
    player.addView(text("01 / COMPOSE", 11, ACCENT));
    LinearLayout controls = row();
    Button play =
        button(
            "Play loop",
            () -> {
              if (recording) {
                message("Finish the take first");
                return;
              }
              play(editor.project, true, metronome);
            });
    equal(controls, play);
    equal(
        controls,
        button(
            "Stop",
            () -> {
              finishTake(false);
              stopAudio();
            }));
    equal(controls, button("Record", this::startTake));
    player.addView(controls);
    transport = text("Tap the grid to draw. Tap a note to edit.", 12, MUTED);
    player.addView(transport);
    LinearLayout pages = row();
    equal(
        pages,
        button(
            "‹ Earlier",
            () -> {
              pageStart = Math.max(0, pageStart - Project.PPQ * 8);
              roll.invalidate();
              updateRange();
            }));
    equal(
        pages,
        button(
            "Later ›",
            () -> {
              pageStart =
                  Math.min(
                      Math.max(0, editor.project.lengthTick - Project.PPQ * 8),
                      pageStart + Project.PPQ * 8);
              roll.invalidate();
              updateRange();
            }));
    player.addView(pages);
    roll = new RollView(this);
    player.addView(roll, new LinearLayout.LayoutParams(-1, dp(230)));
    updateRange();
    LinearLayout edit = row();
    Button undo =
        button(
            "Undo",
            () -> {
              if (editable()) {
                editor.undo();
                persist();
                render();
              }
            });
    undo.setEnabled(editor.canUndo() && !recording);
    equal(edit, undo);
    Button redo =
        button(
            "Redo",
            () -> {
              if (editable()) {
                editor.redo();
                persist();
                render();
              }
            });
    redo.setEnabled(editor.canRedo() && !recording);
    equal(edit, redo);
    equal(
        edit,
        button(
            "Quantize ¼",
            () -> {
              if (editable()) {
                editor.quantize(grid);
                persist();
                render();
              }
            }));
    player.addView(edit);
    player.addView(button("All notes · add or edit", this::noteList));
    keyboard();
    body.addView(
        text(
            "A small idea is enough. Start with a few notes, leave some space, then listen.",
            14,
            MUTED));
  }

  private void keyboard() {
    LinearLayout keys = card();
    keys.addView(text("02 / PLAY IT YOUR WAY", 11, ACCENT));
    LinearLayout modes = row();
    Switch scaleToggle = new Switch(this);
    scaleToggle.setText("C major assist");
    scaleToggle.setTextColor(TEXT);
    scaleToggle.setChecked(scale);
    scaleToggle.setEnabled(!recording);
    scaleToggle.setOnCheckedChangeListener(
        (b, checked) -> {
          releaseKeys();
          scale = checked;
          render();
        });
    equal(modes, scaleToggle);
    Switch click = new Switch(this);
    click.setText("Click");
    click.setTextColor(TEXT);
    click.setChecked(metronome);
    click.setEnabled(!recording);
    click.setOnCheckedChangeListener((b, c) -> metronome = c);
    modes.addView(click);
    keys.addView(modes);
    keys.addView(
        text("Hold keys while recording. Timing and touch duration are captured.", 12, MUTED));
    LinearLayout keyboard = row();
    int[] pitches =
        scale
            ? new int[] {60, 62, 64, 65, 67, 69, 71, 72}
            : new int[] {60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71};
    for (int pitch : pitches) {
      Button key = new PianoKey(pitch);
      key.setTextSize(11);
      key.setPadding(0, 0, 0, 0);
      key.setTextColor(BG);
      key.setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(
              pitch % 12 == 1
                      || pitch % 12 == 3
                      || pitch % 12 == 6
                      || pitch % 12 == 8
                      || pitch % 12 == 10
                  ? 0xffa8baa9
                  : 0xffe3edcf));
      keyboard.addView(key, new LinearLayout.LayoutParams(0, dp(90), 1));
    }
    keys.addView(keyboard);
    keys.addView(
        button("Expression · " + (tilt ? "tilt" : "fixed velocity " + velocity), this::expression));
    if (recording) keys.addView(button("Cancel this take", () -> finishTake(true)));
  }

  private void updateRange() {
    if (transport != null)
      transport.setText(
          recording
              ? "● Recording · Stop to keep, Cancel to discard"
              : "Beats "
                  + (pageStart / Project.PPQ + 1)
                  + "–"
                  + Math.min(editor.project.lengthTick / Project.PPQ, pageStart / Project.PPQ + 8)
                  + " · tap to draw / edit");
  }

  private boolean editable() {
    if (recording) {
      message("Finish or cancel this take first");
      return false;
    }
    stopAudio();
    return true;
  }

  private void change(Project p) {
    editor.change(p);
    persist();
    render();
  }

  private boolean persist() {
    try {
      if (store == null || storageBlocked)
        throw new IOException("Project storage unavailable; export a file to preserve your work");
      store.save("draft", editor.project);
      return true;
    } catch (IOException e) {
      message("Draft was not saved: " + e.getMessage());
      return false;
    }
  }

  private void settings() {
    if (!editable()) return;
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(20), 0, dp(20), 0);
    EditText title = new EditText(this);
    title.setSingleLine();
    title.setText(editor.project.title);
    title.setHint("Melody title");
    fields.addView(title);
    EditText bpm = new EditText(this);
    bpm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    bpm.setText("" + editor.project.bpm);
    bpm.setHint("40–240 BPM");
    fields.addView(bpm);
    EditText beats = new EditText(this);
    beats.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    beats.setText("" + (editor.project.lengthTick / Project.PPQ));
    beats.setHint("Length: 1–256 quarter-note beats");
    fields.addView(beats);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Melody details")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        Project p = editor.project;
                        change(
                            new Project(
                                title.getText().toString(),
                                Integer.parseInt(bpm.getText().toString()),
                                p.numerator,
                                p.denominator,
                                Long.parseLong(beats.getText().toString()) * Project.PPQ,
                                p.tracks));
                        dialog.dismiss();
                      } catch (IllegalArgumentException e) {
                        message(
                            "Use 40–240 BPM and 1–256 beats; notes must fit inside the length.");
                      }
                    }));
    dialog.show();
  }

  private void templates() {
    if (!editable()) return;
    new AlertDialog.Builder(this)
        .setTitle("Start with a spark")
        .setItems(
            new String[] {
              "Blank · your idea", "Moonlight · gentle steps", "Daybreak · a bright little loop"
            },
            (d, index) -> {
              Project p = Project.empty();
              if (index > 0) {
                int[] pitches =
                    index == 1
                        ? new int[] {60, 64, 67, 71, 69, 67, 64, 62}
                        : new int[] {60, 67, 69, 67, 64, 65, 62, 60};
                for (int i = 0; i < pitches.length; i++)
                  p =
                      p.add(
                          new NoteEvent(
                              pitches[i],
                              80 + i % 3 * 12,
                              i * Project.PPQ,
                              Project.PPQ * 3 / 4,
                              0));
                p = p.settings(index == 1 ? "Moonlight" : "Daybreak", index == 1 ? 88 : 124);
              }
              Project next = p;
              new AlertDialog.Builder(this)
                  .setTitle("Start a new melody?")
                  .setMessage("Your current draft will be saved as a library copy first.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Start",
                      (a, b) -> {
                        if (saveCopyInternal()) {
                          editor = new Editor(next);
                          pageStart = 0;
                          persist();
                          render();
                        }
                      })
                  .show();
            })
        .show();
  }

  private boolean saveCopyInternal() {
    try {
      if (store == null) throw new IOException("Storage unavailable");
      store.save(UUID.randomUUID().toString(), editor.project);
      return true;
    } catch (IOException e) {
      message("Could not save: " + e.getMessage());
      return false;
    }
  }

  private void saveCopy() {
    if (!editable()) return;
    if (saveCopyInternal()) message("Saved a copy in your library");
  }

  private void library() {
    body.addView(text("Your ideas,\nkept close.", 32, TEXT));
    body.addView(
        text("Every melody starts somewhere. Revisit one or make a new beginning.", 14, MUTED));
    LinearLayout draft = card();
    draft.addView(text("CURRENT DRAFT", 11, ACCENT));
    draft.addView(text(editor.project.title, 22, TEXT));
    draft.addView(
        button(
            "Continue creating →",
            () -> {
              tab = "Create";
              render();
            }));
    if (store == null) return;
    List<String> ids = store.ids();
    if (ids.isEmpty())
      body.addView(
          text(
              "Your saved melodies will appear here. Open Create and choose Save copy.",
              16,
              MUTED));
    for (String id : ids) {
      try {
        Project p = store.load(id);
        LinearLayout card = card();
        card.addView(text(p.title, 20, TEXT));
        card.addView(text(p.notes().size() + " notes · " + p.bpm + " BPM", 13, MUTED));
        LinearLayout actions = row();
        equal(actions, button("Listen", () -> play(p, false, false)));
        equal(
            actions,
            button(
                "Open",
                () -> {
                  if (saveCopyInternal()) {
                    editor = new Editor(p);
                    pageStart = 0;
                    persist();
                    tab = "Create";
                    render();
                  }
                }));
        card.addView(actions);
      } catch (IOException e) {
        body.addView(text("A saved project could not be read. Its file has been kept.", 14, MUTED));
      }
    }
  }

  private void imports() {
    body.addView(text("A melody travels.\nAn idea grows.", 32, TEXT));
    body.addView(text("Bring in a friend's melody, listen, then make it your own.", 15, MUTED));
    LinearLayout qr = card();
    qr.addView(text("01 / OFFLINE QR", 11, ACCENT));
    qr.addView(text("A little square.\nA whole new starting point.", 23, TEXT));
    qr.addView(
        button(
            "Scan with camera",
            () -> {
              if (checkSelfPermission(Manifest.permission.CAMERA)
                  == PackageManager.PERMISSION_GRANTED) launchScan();
              else requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA);
            }));
    qr.addView(button("Import QR image", () -> pick("image/*", IMAGE)));
    qr.addView(button("Paste QR text", this::pasteQr));
    LinearLayout files = card();
    files.addView(text("02 / FROM A FILE", 11, ACCENT));
    files.addView(text("TrueTone or MIDI", 23, TEXT));
    files.addView(
        text(
            "TrueTone files preserve all project details. MIDI brings in notes, tempo and meter;"
                + " instrument programs are played with TrueTone's own sound.",
            14,
            MUTED));
    files.addView(button("Choose a file", () -> pick("*/*", OPEN)));
    body.addView(
        text(
            "Imports always open in a preview. Nothing is added to your library until you confirm.",
            13,
            MUTED));
  }

  private void pick(String mime, int code) {
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE),
        code);
  }

  private void launchScan() {
    startActivityForResult(new Intent(this, ScanActivity.class), SCAN);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] result) {
    super.onRequestPermissionsResult(code, permissions, result);
    if (code == CAMERA) {
      if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) launchScan();
      else message("Camera access was declined. Use Import QR image or paste QR text.");
    }
  }

  private void pasteQr() {
    EditText input = new EditText(this);
    input.setHint("TRUETONE:1:…");
    input.setFilters(
        new android.text.InputFilter[] {
          new android.text.InputFilter.LengthFilter(ProjectCodec.QR_MAX_CHARS + 1)
        });
    new AlertDialog.Builder(this)
        .setTitle("Paste a melody QR")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Preview",
            (d, w) -> {
              try {
                preview(ProjectCodec.fromQr(input.getText().toString().trim()));
              } catch (IOException e) {
                message(e.getMessage());
              }
            })
        .show();
  }

  private void preview(Project p) {
    stopAudio();
    importPreview = p;
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(p.title)
            .setMessage(
                p.notes().size()
                    + " notes · "
                    + p.bpm
                    + " BPM\n"
                    + "Listen before adding this melody. A checksum detects damage; it does not"
                    + " verify who made it.")
            .setNegativeButton(
                "Cancel",
                (d, w) -> {
                  stopAudio();
                  importPreview = null;
                })
            .setNeutralButton("Listen", null)
            .setPositiveButton("Keep & edit", null)
            .create();
    dialog.setOnShowListener(
        d -> {
          dialog.getButton(-3).setOnClickListener(v -> play(p, false, false));
          dialog
              .getButton(-1)
              .setOnClickListener(
                  v -> {
                    try {
                      if (store == null) throw new IOException("Storage unavailable");
                      if (!saveCopyInternal()) return;
                      store.save(UUID.randomUUID().toString(), p);
                      editor = new Editor(p);
                      persist();
                      stopAudio();
                      importPreview = null;
                      pageStart = 0;
                      tab = "Create";
                      dialog.dismiss();
                      render();
                    } catch (IOException e) {
                      message(e.getMessage());
                    }
                  });
        });
    dialog.setOnCancelListener(
        d -> {
          stopAudio();
          importPreview = null;
        });
    dialog.show();
  }

  private void shareMenu() {
    if (!editable()) return;
    new AlertDialog.Builder(this)
        .setTitle("Share your melody")
        .setItems(
            new String[] {
              "Listen to actual output",
              "Offline QR",
              "TrueTone · editable backup",
              "MIDI · editable notes",
              "WAV · audio to listen to"
            },
            (d, index) -> {
              if (index == 0) {
                play(editor.project, false, false);
                return;
              }
              if (index == 1) {
                showQr();
                return;
              }
              exportFormat = index == 2 ? "ttn" : index == 3 ? "mid" : "wav";
              exportSnapshot = editor.project;
              new AlertDialog.Builder(this)
                  .setTitle("Export " + exportFormat.toUpperCase(Locale.ROOT))
                  .setMessage(
                      "Preview and WAV use the same TrueTone sound. MIDI keeps musical timing;"
                          + " other players may use a different instrument.")
                  .setNeutralButton("Listen", (a, b) -> play(exportSnapshot, false, false))
                  .setNegativeButton("Save file", (a, b) -> saveExport())
                  .setPositiveButton("Share file", (a, b) -> shareExport())
                  .show();
            })
        .show();
  }

  private String mime() {
    return "mid".equals(exportFormat)
        ? "audio/midi"
        : "wav".equals(exportFormat) ? "audio/wav" : "application/octet-stream";
  }

  private void saveExport() {
    // DocumentsUI chooses an extension from the platform's MIME map. audio/midi
    // maps to .rtx on some Android builds, even when the supplied title ends .mid.
    String documentMime =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(exportFormat);
    if (documentMime == null) documentMime = "application/octet-stream";
    startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(documentMime)
            .putExtra(Intent.EXTRA_TITLE, "TrueTone." + exportFormat),
        EXPORT);
  }

  private void writeExport(Project p, String format, OutputStream out) throws IOException {
    if (format.equals("wav")) Synth.writeWav(p, out);
    else out.write(format.equals("mid") ? MidiCodec.encode(p) : ProjectCodec.encode(p));
  }

  private void shareExport() {
    Project p = exportSnapshot;
    String format = exportFormat;
    String type = mime();
    message("Preparing " + format.toUpperCase(Locale.ROOT) + "…");
    io.execute(
        () -> {
          try {
            File f = shareFile(format);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
              writeExport(p, format, out);
            }
            runOnUiThread(() -> send(f, type));
          } catch (IOException e) {
            runOnUiThread(() -> message("Export failed: " + e.getMessage()));
          }
        });
  }

  private File shareFile(String extension) throws IOException {
    File dir = new File(getCacheDir(), "shares");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot prepare share");
    File[] old = dir.listFiles();
    if (old != null)
      for (File f : old)
        if (System.currentTimeMillis() - f.lastModified() > 86400000L)
          java.nio.file.Files.deleteIfExists(f.toPath());
    return new File(dir, UUID.randomUUID() + "." + extension);
  }

  private void send(File f, String type) {
    if (isFinishing() || isDestroyed()) return;
    Uri uri = Uri.parse("content://" + getPackageName() + ".share/" + f.getName());
    Intent send =
        new Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    send.setClipData(ClipData.newRawUri("TrueTone melody", uri));
    startActivity(Intent.createChooser(send, "Share your melody"));
  }

  private void showQr() {
    try {
      String payload = ProjectCodec.toQr(editor.project);
      Bitmap bitmap = QrImages.encode(payload);
      ImageView image = new ImageView(this);
      image.setImageBitmap(bitmap);
      image.setAdjustViewBounds(true);
      image.setPadding(dp(12), dp(12), dp(12), dp(12));
      new AlertDialog.Builder(this)
          .setTitle("Scan. Listen. Make it yours.")
          .setView(image)
          .setNegativeButton("Close", null)
          .setPositiveButton(
              "Share QR image",
              (d, w) -> {
                try {
                  File file = shareFile("png");
                  try (OutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                  }
                  send(file, "image/png");
                } catch (IOException e) {
                  message(e.getMessage());
                }
              })
          .show();
    } catch (IOException | WriterException e) {
      new AlertDialog.Builder(this)
          .setTitle("Use a file for this melody")
          .setMessage(e.getMessage())
          .setPositiveButton(
              "Share TrueTone file",
              (d, w) -> {
                exportFormat = "ttn";
                exportSnapshot = editor.project;
                shareExport();
              })
          .setNegativeButton("Close", null)
          .show();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    if (request == SCAN) {
      try {
        preview(ProjectCodec.fromQr(data.getStringExtra("qr")));
      } catch (IOException e) {
        message(e.getMessage());
      }
      return;
    }
    Uri uri = data.getData();
    if (uri == null) return;
    Project snapshot = exportSnapshot;
    String format = exportFormat;
    io.execute(
        () -> {
          try {
            if (request == EXPORT) {
              if (snapshot == null || format == null)
                throw new IOException("Export expired; choose Share again");
              try (OutputStream raw = getContentResolver().openOutputStream(uri, "wt")) {
                if (raw == null) throw new IOException("Cannot open destination");
                try (OutputStream out = new BufferedOutputStream(raw)) {
                  writeExport(snapshot, format, out);
                }
              }
              runOnUiThread(() -> message("Export saved"));
              return;
            }
            Project imported;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IOException("Cannot open file");
              byte[] bytes =
                  ProjectCodec.readBounded(
                      in, request == IMAGE ? 8 * 1024 * 1024 : MidiCodec.MAX_BYTES);
              if (request == IMAGE) imported = ProjectCodec.fromQr(decodeImage(bytes));
              else if (bytes.length >= 4
                  && bytes[0] == 'M'
                  && bytes[1] == 'T'
                  && bytes[2] == 'h'
                  && bytes[3] == 'd') imported = MidiCodec.decode(bytes, "Imported melody");
              else imported = ProjectCodec.decode(bytes);
            }
            runOnUiThread(
                () -> {
                  if (!isDestroyed()) preview(imported);
                });
          } catch (Exception e) {
            runOnUiThread(() -> message("Could not open or save: " + e.getMessage()));
          }
        });
  }

  private String decodeImage(byte[] bytes) throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (options.outWidth < 1 || options.outHeight < 1)
      throw new IOException("Unsupported QR image");
    options.inJustDecodeBounds = false;
    options.inSampleSize = 1;
    while (options.outWidth / options.inSampleSize > 2048
        || options.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) throw new IOException("Could not decode image");
    int w = bitmap.getWidth(), h = bitmap.getHeight();
    int[] pixels = new int[w * h];
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
    bitmap.recycle();
    try {
      return QrImages.read(
          new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels))), true);
    } catch (ReaderException e) {
      throw new IOException("No readable QR found in this image");
    }
  }

  private void play(Project p, boolean loop, boolean click) {
    if (audio == null || !audio.acquire()) {
      message("Audio is busy or unavailable. Try again when other audio has stopped.");
      return;
    }
    audio.play(p, loop, click);
    message(loop ? "Playing loop · tap Stop to finish" : "Playing preview");
  }

  private void stopAudio() {
    if (audio != null) audio.stop();
  }

  private void startTake() {
    if (recording) return;
    if (audio == null || !audio.acquire()) {
      message("Audio is unavailable");
      return;
    }
    held.clear();
    takeBefore = editor.project;
    recordStart = System.nanoTime();
    recording = true;
    audio.play(takeBefore, false, metronome);
    handler.postDelayed(
        stopTake,
        Math.round(editor.project.lengthTick * 60000.0 / (editor.project.bpm * Project.PPQ)));
    render();
  }

  private void keyDown(int pitch) {
    if (audio == null) return;
    if (!recording && !audio.acquire()) return;
    int vel = currentVelocity();
    audio.noteOn(pitch, pitch, vel);
    if (recording)
      held.put(
          pitch, new Held(pitch, vel, editor.project.tickAtNanos(System.nanoTime() - recordStart)));
  }

  private void keyUp(int pitch) {
    if (audio != null) audio.noteOff(pitch);
    Held n = held.remove(pitch);
    if (!recording || n == null) return;
    long end =
        Math.min(
            editor.project.lengthTick, editor.project.tickAtNanos(System.nanoTime() - recordStart));
    if (end <= n.start) return;
    try {
      editor.project =
          editor.project.add(new NoteEvent(n.pitch, n.velocity, n.start, end - n.start, 0));
      persist();
      if (roll != null) roll.invalidate();
    } catch (IllegalArgumentException e) {
      message(e.getMessage());
      finishTake(false);
    }
  }

  private void releaseKeys() {
    for (Integer pitch : new ArrayList<>(held.keySet())) keyUp(pitch);
    held.clear();
  }

  private void finishTake(boolean cancel) {
    if (!recording) return;
    handler.removeCallbacks(stopTake);
    releaseKeys();
    recording = false;
    stopAudio();
    Project take = editor.project;
    editor.project = takeBefore;
    if (!cancel) editor.change(take);
    takeBefore = null;
    persist();
    render();
  }

  private int currentVelocity() {
    return tilt
        ? Math.max(1, Math.min(127, Math.round(velocity + (filtered - neutral) * sensitivity * 65)))
        : velocity;
  }

  private void expression() {
    if (!editable()) return;
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(dp(20), 0, dp(20), 0);
    Switch enabled = new Switch(this);
    enabled.setText(gravity == null ? "Tilt unavailable on this device" : "Tilt expression");
    enabled.setChecked(tilt);
    enabled.setEnabled(gravity != null);
    layout.addView(enabled);
    layout.addView(text("Base velocity · soft to strong", 15, MUTED));
    SeekBar vel = new SeekBar(this);
    vel.setMax(126);
    vel.setProgress(velocity - 1);
    layout.addView(vel);
    layout.addView(text("Tilt sensitivity", 15, MUTED));
    SeekBar sense = new SeekBar(this);
    sense.setMax(150);
    sense.setProgress(Math.round(sensitivity * 100) - 50);
    layout.addView(sense);
    layout.addView(
        button(
            "Calibrate neutral angle",
            () -> {
              neutral = filtered;
              message("Neutral angle set");
            }));
    new AlertDialog.Builder(this)
        .setTitle("Play with expression")
        .setView(layout)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Apply",
            (d, w) -> {
              tilt = enabled.isChecked();
              velocity = vel.getProgress() + 1;
              sensitivity = (sense.getProgress() + 50) / 100f;
              if (tilt && gravity != null)
                sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
              else sensors.unregisterListener(this);
              render();
            })
        .show();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    float angle =
        (float)
            Math.atan2(
                event.values[0],
                Math.sqrt(event.values[1] * event.values[1] + event.values[2] * event.values[2]));
    filtered = filtered * .85f + angle * .15f;
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private String noteName(int pitch) {
    return new String[] {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"}
        [pitch % 12];
  }

  private void message(String message) {
    if (isDestroyed()) return;
    if (status != null) status.setText(message);
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void editNote(NoteEvent note) {
    if (!editable()) return;
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(20), 0, dp(20), 0);
    EditText pitch = new EditText(this);
    pitch.setInputType(2);
    pitch.setText("" + note.pitch);
    pitch.setHint("MIDI pitch 0–127");
    fields.addView(text("Pitch (MIDI 0–127)", 14, MUTED));
    fields.addView(pitch);
    EditText start = new EditText(this);
    start.setInputType(8194);
    start.setText(Double.toString(note.startBeat()));
    fields.addView(text("Start beat (zero-based)", 14, MUTED));
    fields.addView(start);
    EditText duration = new EditText(this);
    duration.setInputType(8194);
    duration.setText("" + note.durationBeats());
    fields.addView(text("Duration in quarter-note beats", 14, MUTED));
    fields.addView(duration);
    EditText strength = new EditText(this);
    strength.setInputType(2);
    strength.setText("" + note.velocity);
    fields.addView(text("Velocity 1–127", 14, MUTED));
    fields.addView(strength);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Shape this note")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete", (d, w) -> replaceNote(note, null))
            .setPositiveButton("Apply", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        double beats = Double.parseDouble(duration.getText().toString());
                        if (!Double.isFinite(beats)) throw new IllegalArgumentException();
                        double startBeat = Double.parseDouble(start.getText().toString());
                        if (!Double.isFinite(startBeat)) throw new IllegalArgumentException();
                        replaceNote(
                            note,
                            new NoteEvent(
                                Integer.parseInt(pitch.getText().toString()),
                                Integer.parseInt(strength.getText().toString()),
                                Math.round(startBeat * Project.PPQ),
                                Math.round(beats * Project.PPQ),
                                note.channel));
                        dialog.dismiss();
                      } catch (IllegalArgumentException e) {
                        message(
                            "Check pitch, velocity and duration; the note must fit in your"
                                + " melody.");
                      }
                    }));
    dialog.show();
  }

  private void replaceNote(NoteEvent old, NoteEvent next) {
    List<List<NoteEvent>> tracks = new ArrayList<>();
    for (List<NoteEvent> original : editor.project.tracks) {
      List<NoteEvent> list = new ArrayList<>();
      for (NoteEvent n : original) {
        if (n == old) {
          if (next != null) list.add(next);
        } else list.add(n);
      }
      tracks.add(list);
    }
    Project p = editor.project;
    change(new Project(p.title, p.bpm, p.numerator, p.denominator, p.lengthTick, tracks));
  }

  private void noteList() {
    if (!editable()) return;
    List<NoteEvent> notes = editor.project.notes();
    String[] labels = new String[notes.size() + 1];
    labels[0] = "+ Add a note";
    for (int i = 0; i < notes.size(); i++) {
      NoteEvent n = notes.get(i);
      labels[i] =
          noteName(n.pitch)
              + (n.pitch / 12 - 1)
              + " · beat "
              + (n.startBeat() + 1)
              + " · "
              + n.durationBeats()
              + " beats";
    }
    new AlertDialog.Builder(this)
        .setTitle("Every note in your melody")
        .setItems(
            labels,
            (d, index) -> {
              if (index == 0) {
                NoteEvent note =
                    new NoteEvent(
                        60,
                        currentVelocity(),
                        pageStart,
                        Math.min(grid, editor.project.lengthTick - pageStart),
                        0);
                change(editor.project.add(note));
                editNote(note);
              } else editNote(notes.get(index - 1));
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private final class PianoKey extends Button {
    private final int pitch;
    private boolean fromTouch;

    PianoKey(int pitch) {
      super(MainActivity.this);
      this.pitch = pitch;
      setText(noteName(pitch));
      setAllCaps(false);
      setMinHeight(dp(48));
      setContentDescription("Play " + noteName(pitch) + (pitch / 12 - 1));
      setOnClickListener(
          v -> {
            if (!fromTouch) {
              keyDown(pitch);
              handler.postDelayed(() -> keyUp(pitch), 250);
            }
          });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        keyDown(pitch);
        setAlpha(.55f);
        return true;
      }
      if (event.getActionMasked() == MotionEvent.ACTION_UP
          || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
        keyUp(pitch);
        setAlpha(1);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
          fromTouch = true;
          performClick();
          fromTouch = false;
        }
        return true;
      }
      return true;
    }

    @Override
    public boolean performClick() {
      return super.performClick();
    }
  }

  private final class RollView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RollView(Context context) {
      super(context);
      setContentDescription(
          "Melody grid. Tap to add or edit a note. Use keyboard to record notes.");
      setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float left = dp(26), w = getWidth() - left, cellH = getHeight() / 13f;
      paint.setTextSize(dp(9));
      for (int row = 0; row < 13; row++) {
        int pitch = 72 - row;
        paint.setColor(row % 2 == 0 ? 0xff27332a : 0xff222c25);
        canvas.drawRect(left, row * cellH, getWidth(), (row + 1) * cellH, paint);
        paint.setColor(MUTED);
        canvas.drawText(noteName(pitch), 0, (row + .7f) * cellH, paint);
      }
      for (int i = 0; i <= 32; i++) {
        paint.setColor(i % 4 == 0 ? 0xff4a5b4b : 0xff303d32);
        float x = left + w * i / 32;
        canvas.drawLine(x, 0, x, getHeight(), paint);
      }
      for (NoteEvent n : editor.project.notes()) {
        if (n.pitch < 60 || n.pitch > 72) continue;
        float x = left + w * (n.startTick - pageStart) / (Project.PPQ * 8f),
            end = left + w * (n.startTick + n.durationTick - pageStart) / (Project.PPQ * 8f);
        if (end < left || x > getWidth()) continue;
        float y = (72 - n.pitch) * cellH;
        paint.setColor(ACCENT);
        paint.setAlpha(100 + n.velocity);
        canvas.drawRoundRect(
            Math.max(left, x) + 1,
            y + 2,
            Math.min(getWidth(), end) - 1,
            y + cellH - 2,
            dp(3),
            dp(3),
            paint);
        paint.setAlpha(255);
      }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (event.getAction() != MotionEvent.ACTION_UP) return true;
      performClick();
      if (!editable()) return true;
      float left = dp(26);
      if (event.getX() < left) return true;
      int pitch = 72 - Math.min(12, Math.max(0, (int) (event.getY() * 13 / getHeight())));
      long raw = pageStart + (long) ((event.getX() - left) / (getWidth() - left) * Project.PPQ * 8);
      long tick = (raw / grid) * grid;
      for (NoteEvent n : editor.project.notes())
        if (n.pitch == pitch && raw >= n.startTick && raw < n.startTick + n.durationTick) {
          editNote(n);
          return true;
        }
      if (scale && !Arrays.asList(0, 2, 4, 5, 7, 9, 11).contains(pitch % 12)) {
        message("C major assist: choose a natural note or turn assist off");
        return true;
      }
      if (tick < editor.project.lengthTick)
        change(
            editor.project.add(
                new NoteEvent(
                    pitch,
                    currentVelocity(),
                    tick,
                    Math.min(grid, editor.project.lengthTick - tick),
                    0)));
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
