package app.truetone;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, temporary single-file grants; never exposes the project library. */
public final class ShareProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (uri.getPathSegments().size() != 1
        || name == null
        || !name.matches("[a-f0-9-]+\\.(ttn|mid|wav|png)"))
      throw new FileNotFoundException("Invalid share");
    File f = new File(new File(getContext().getCacheDir(), "shares"), name);
    if (!f.isFile()) throw new FileNotFoundException("Share has expired");
    return f;
  }

  @Override
  public String getType(Uri uri) {
    String name = uri.getLastPathSegment();
    if (name == null) return "application/octet-stream";
    if (name.endsWith(".mid")) return "audio/midi";
    if (name.endsWith(".wav")) return "audio/wav";
    if (name.endsWith(".png")) return "image/png";
    return "application/octet-stream";
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read-only share");
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    try {
      File f = file(uri);
      String[] columns =
          projection == null
              ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
              : projection;
      MatrixCursor cursor = new MatrixCursor(columns);
      Object[] row = new Object[columns.length];
      for (int i = 0; i < columns.length; i++) {
        if (OpenableColumns.DISPLAY_NAME.equals(columns[i]))
          row[i] = "TrueTone." + f.getName().substring(f.getName().lastIndexOf('.') + 1);
        else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = f.length();
      }
      cursor.addRow(row);
      return cursor;
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
