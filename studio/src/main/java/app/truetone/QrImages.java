package app.truetone;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.*;

/** Check the encoded symbol with the detector, and handle clean exported PNGs separately. */
final class QrImages {
  private QrImages() {}

  static String read(BinaryBitmap image, boolean allowPure) throws ReaderException {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.TRY_HARDER, true);
    try {
      return new MultiFormatReader().decode(image, hints).getText();
    } catch (ReaderException first) {
      if (!allowPure) throw first;
      hints.put(DecodeHintType.PURE_BARCODE, true);
      return new MultiFormatReader().decode(image, hints).getText();
    }
  }

  static Bitmap encode(String payload) throws WriterException {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.MARGIN, 4);
    for (int mask = -1; mask < 8; mask++) {
      if (mask >= 0) hints.put(EncodeHintType.QR_MASK_PATTERN, mask);
      BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 900, 900, hints);
      int[] pixels = new int[900 * 900];
      for (int y = 0; y < 900; y++)
        for (int x = 0; x < 900; x++)
          pixels[y * 900 + x] = matrix.get(x, y) ? 0xff000000 : 0xffffffff;
      try {
        String actual =
            read(
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(900, 900, pixels))),
                false);
        if (payload.equals(actual))
          return Bitmap.createBitmap(pixels, 900, 900, Bitmap.Config.ARGB_8888);
      } catch (ReaderException ignored) {
        /* Try another standard QR mask. */
      }
    }
    throw new WriterException(
        "Could not create a reliably readable QR. Share a TrueTone file instead.");
  }
}
