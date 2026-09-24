package app.truetone;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
  private Camera camera;
  private SurfaceView surface;
  private final ExecutorService decoder = Executors.newSingleThreadExecutor();
  private volatile boolean busy, done;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 64, 24, 48);
    layout.setBackgroundColor(0xff101511);
    TextView title = new TextView(this);
    title.setText("Scan a TrueTone QR\nKeep the whole square in view.");
    title.setTextSize(22);
    title.setTextColor(0xfff2f5e9);
    layout.addView(title);
    surface = new SurfaceView(this);
    layout.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
    Button cancel = new Button(this);
    cancel.setText("Cancel");
    cancel.setOnClickListener(v -> finish());
    layout.addView(cancel);
    setContentView(layout);
    surface.getHolder().addCallback(this);
  }

  private void open(SurfaceHolder holder) {
    if (camera != null || done) return;
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      fail("Camera permission is needed to scan. You can also import a QR image.");
      return;
    }
    try {
      camera = Camera.open();
      Camera.Parameters params = camera.getParameters();
      Camera.Size chosen = params.getSupportedPreviewSizes().get(0);
      for (Camera.Size s : params.getSupportedPreviewSizes())
        if (s.width <= 1280 && s.width >= 640) chosen = s;
      params.setPreviewSize(chosen.width, chosen.height);
      if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      camera.setParameters(params);
      camera.setDisplayOrientation(90);
      camera.setPreviewDisplay(holder);
      final int width = chosen.width, height = chosen.height;
      camera.setPreviewCallback(
          (data, c) -> {
            if (busy || done || decoder.isShutdown()) return;
            busy = true;
            byte[] copy = data.clone();
            decoder.execute(
                () -> {
                  try {
                    LuminanceSource source =
                        new PlanarYUVLuminanceSource(
                            copy, width, height, 0, 0, width, height, false);
                    String result =
                        QrImages.read(new BinaryBitmap(new HybridBinarizer(source)), false);
                    done = true;
                    runOnUiThread(
                        () -> {
                          setResult(RESULT_OK, new Intent().putExtra("qr", result));
                          finish();
                        });
                  } catch (ReaderException ignored) {
                  } finally {
                    busy = false;
                  }
                });
          });
      camera.startPreview();
    } catch (Exception ex) {
      fail("Camera is unavailable. Try importing a QR image instead.");
    }
  }

  private void fail(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    open(holder);
  }

  @Override
  public void surfaceChanged(SurfaceHolder h, int format, int w, int height) {}

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    release();
  }

  private void release() {
    if (camera != null) {
      camera.setPreviewCallback(null);
      camera.stopPreview();
      camera.release();
      camera = null;
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    release();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (surface != null && surface.getHolder().getSurface().isValid()) open(surface.getHolder());
  }

  @Override
  protected void onDestroy() {
    done = true;
    release();
    decoder.shutdown();
    super.onDestroy();
  }
}
