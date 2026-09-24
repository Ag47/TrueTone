package app.truetone;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.truetone.core.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FlowTest {
  private Instrumentation instrumentation;
  private MainActivity activity;
  private Context context;

  @Before
  public void launch() throws Exception {
    instrumentation = InstrumentationRegistry.getInstrumentation();
    context = instrumentation.getTargetContext();
    new ProjectStore(new File(context.getFilesDir(), "projects")).save("draft", Project.empty());
    activity =
        (MainActivity)
            instrumentation.startActivitySync(
                new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    instrumentation.waitForIdleSync();
  }

  @After
  public void close() {
    instrumentation.runOnMainSync(
        () -> {
          if (activity != null) activity.finish();
        });
    instrumentation.waitForIdleSync();
  }

  private Object field(String name) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(activity);
  }

  private void call(String name, Class<?>[] types, Object... args) {
    instrumentation.runOnMainSync(
        () -> {
          try {
            Method m = MainActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            m.invoke(activity, args);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    instrumentation.waitForIdleSync();
  }

  private void click(String label) {
    instrumentation.runOnMainSync(
        () -> {
          View v = find(activity.getWindow().getDecorView(), label);
          assertNotNull("Missing control: " + label, v);
          v.performClick();
        });
    instrumentation.waitForIdleSync();
  }

  private View find(View root, String label) {
    if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return root;
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        View v = find(((ViewGroup) root).getChildAt(i), label);
        if (v != null) return v;
      }
    return null;
  }

  @Test
  public void createSaveUndoReopen() throws Exception {
    Project p =
        Project.empty().add(new NoteEvent(64, 102, 240, 1440, 0)).settings("Device melody", 137);
    call("change", new Class[] {Project.class}, p);
    click("Save copy");
    click("Undo");
    assertTrue(((Editor) field("editor")).project.notes().isEmpty());
    click("Redo");
    assertEquals(p.notes(), ((Editor) field("editor")).project.notes());
    Project restored = new ProjectStore(new File(context.getFilesDir(), "projects")).load("draft");
    assertEquals(137, restored.bpm);
    assertEquals(p.notes(), restored.notes());
    instrumentation.runOnMainSync(
        () -> {
          if (activity != null) activity.finish();
        });
    instrumentation.waitForIdleSync();
    activity =
        (MainActivity)
            instrumentation.startActivitySync(
                new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    instrumentation.waitForIdleSync();
    assertEquals(p.notes(), ((Editor) field("editor")).project.notes());
    click("Library");
    click("Import");
    click("Create");
  }

  @Test
  public void recordDurationAndCancelTake() throws Exception {
    call("startTake", new Class[] {});
    call("keyDown", new Class[] {int.class}, 60);
    Thread.sleep(220);
    call("keyUp", new Class[] {int.class}, 60);
    call("finishTake", new Class[] {boolean.class}, false);
    Project recorded = ((Editor) field("editor")).project;
    assertEquals(1, recorded.notes().size());
    assertTrue(recorded.notes().get(0).durationTick > 200);
    call("startTake", new Class[] {});
    call("keyDown", new Class[] {int.class}, 64);
    Thread.sleep(150);
    call("keyUp", new Class[] {int.class}, 64);
    call("finishTake", new Class[] {boolean.class}, true);
    assertEquals(recorded.notes(), ((Editor) field("editor")).project.notes());
  }

  @Test
  public void importPreviewDoesNotWrite() throws Exception {
    ProjectStore store = new ProjectStore(new File(context.getFilesDir(), "projects"));
    int before = store.ids().size();
    Project incoming = Project.empty().add(new NoteEvent(72, 95, 960, 480, 2));
    call(
        "onActivityResult",
        new Class[] {int.class, int.class, Intent.class},
        12,
        Activity.RESULT_OK,
        new Intent().putExtra("qr", ProjectCodec.toQr(incoming)));
    assertEquals(before, store.ids().size());
    assertTrue(store.load("draft").notes().isEmpty());
    assertEquals(incoming.notes(), ((Project) field("importPreview")).notes());
  }

  private void dialogClick(String label) {
    android.view.accessibility.AccessibilityNodeInfo root =
        instrumentation.getUiAutomation().getRootInActiveWindow();
    assertNotNull(root);
    List<android.view.accessibility.AccessibilityNodeInfo> nodes =
        root.findAccessibilityNodeInfosByText(label);
    assertFalse("Missing dialog action " + label, nodes.isEmpty());
    assertTrue(
        nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
    instrumentation.waitForIdleSync();
  }

  @Test
  public void importConfirmationAndCancel() throws Exception {
    ProjectStore store = new ProjectStore(new File(context.getFilesDir(), "projects"));
    int before = store.ids().size();
    Project incoming = Project.empty().add(new NoteEvent(67, 95, 240, 1440, 0));
    call("preview", new Class[] {Project.class}, incoming);
    dialogClick("Keep & edit");
    assertEquals(incoming.notes(), store.load("draft").notes());
    assertEquals(before + 2, store.ids().size());
    call("preview", new Class[] {Project.class}, Project.empty());
    dialogClick("Cancel");
    assertEquals(before + 2, store.ids().size());
    assertEquals(incoming.notes(), store.load("draft").notes());
  }

  @Test
  public void displayedQrDecodesFromScreenshot() throws Exception {
    Project p = Project.empty().add(new NoteEvent(65, 87, 120, 960, 0));
    call("change", new Class[] {Project.class}, p);
    call("showQr", new Class[] {});
    Bitmap screen = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(screen);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    screen.compress(Bitmap.CompressFormat.PNG, 100, bytes);
    Method decode = MainActivity.class.getDeclaredMethod("decodeImage", byte[].class);
    decode.setAccessible(true);
    String payload = (String) decode.invoke(activity, (Object) bytes.toByteArray());
    assertEquals(p.notes(), ProjectCodec.fromQr(payload).notes());
    dialogClick("Close");
  }

  @Test
  public void exportProviderIsReadOnlyAndBounded() throws Exception {
    ShareProvider provider = new ShareProvider();
    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
    info.authority = context.getPackageName() + ".share";
    provider.attachInfo(context, info);
    assertThrows(
        FileNotFoundException.class,
        () ->
            provider.openFile(
                android.net.Uri.parse("content://" + info.authority + "/../projects/draft.ttn"),
                "r"));
    assertThrows(
        FileNotFoundException.class,
        () ->
            provider.openFile(
                android.net.Uri.parse("content://" + info.authority + "/abc.ttn"), "w"));
  }

  @Test
  public void denseExportedQrRegression() throws Exception {
    byte[] fixture;
    try (InputStream in = instrumentation.getContext().getAssets().open("moonlight-qr.png")) {
      fixture = ProjectCodec.readBounded(in, 1024 * 1024);
    }
    Method decode = MainActivity.class.getDeclaredMethod("decodeImage", byte[].class);
    decode.setAccessible(true);
    String payload = (String) decode.invoke(activity, (Object) fixture);
    Project p = ProjectCodec.fromQr(payload);
    assertEquals(8, p.notes().size());
    Bitmap generated = QrImages.encode(payload);
    int w = generated.getWidth(), h = generated.getHeight();
    int[] pixels = new int[w * h];
    generated.getPixels(pixels, 0, w, 0, 0, w, h);
    assertEquals(
        payload,
        QrImages.read(
            new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.RGBLuminanceSource(w, h, pixels))),
            false));
  }
}
