package app.truetone.core;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Bounded, versioned binary format. No Java serialization or compressed input. */
public final class ProjectCodec {
  public static final int MAX_BYTES = 128 * 1024, QR_MAX_CHARS = 1200;

  private ProjectCodec() {}

  public static byte[] encode(Project p) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    out.writeInt(0x54544e50);
    out.writeByte(Project.VERSION);
    out.writeUTF(p.title);
    out.writeShort(p.bpm);
    out.writeByte(p.numerator);
    out.writeByte(p.denominator);
    out.writeInt((int) p.lengthTick);
    out.writeByte(p.tracks.size());
    for (List<NoteEvent> track : p.tracks) {
      out.writeShort(track.size());
      for (NoteEvent n : track) {
        out.writeByte(n.pitch);
        out.writeByte(n.velocity);
        out.writeByte(n.channel);
        out.writeInt((int) n.startTick);
        out.writeInt((int) n.durationTick);
      }
    }
    out.flush();
    byte[] body = buffer.toByteArray();
    CRC32 crc = new CRC32();
    crc.update(body);
    out.writeInt((int) crc.getValue());
    return buffer.toByteArray();
  }

  public static Project decode(byte[] bytes) throws IOException {
    if (bytes == null || bytes.length < 20 || bytes.length > MAX_BYTES)
      throw new IOException("Invalid project size");
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length - 4);
    DataInputStream checksum =
        new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 4, 4));
    if ((int) crc.getValue() != checksum.readInt())
      throw new IOException("Project checksum failed; ask for a new copy");
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
    if (in.readInt() != 0x54544e50 || in.readUnsignedByte() != Project.VERSION)
      throw new IOException("Unsupported TrueTone project version");
    try {
      String title = in.readUTF();
      int bpm = in.readUnsignedShort();
      int num = in.readUnsignedByte(), den = in.readUnsignedByte();
      long length = in.readInt();
      int trackCount = in.readUnsignedByte(), count = 0;
      if (trackCount < 1 || trackCount > 16) throw new IOException("Invalid track count");
      List<List<NoteEvent>> tracks = new ArrayList<>();
      for (int t = 0; t < trackCount; t++) {
        int n = in.readUnsignedShort();
        count += n;
        if (count > Project.MAX_NOTES) throw new IOException("Too many notes");
        List<NoteEvent> notes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
          int pitch = in.readUnsignedByte(),
              vel = in.readUnsignedByte(),
              ch = in.readUnsignedByte();
          notes.add(new NoteEvent(pitch, vel, in.readInt(), in.readInt(), ch));
        }
        tracks.add(notes);
      }
      if (in.available() != 0) throw new IOException("Unexpected project data");
      return new Project(title, bpm, num, den, length, tracks);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  public static String toQr(Project p) throws IOException {
    String qr = "TRUETONE:1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(encode(p));
    if (qr.length() > QR_MAX_CHARS)
      throw new IOException("This melody is too large for one QR. Share a TrueTone file instead.");
    return qr;
  }

  public static Project fromQr(String text) throws IOException {
    if (text == null || text.length() > QR_MAX_CHARS || !text.startsWith("TRUETONE:1:"))
      throw new IOException("Scan a TrueTone version 1 QR code");
    try {
      return decode(Base64.getUrlDecoder().decode(text.substring(11)));
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid QR encoding", e);
    }
  }

  public static byte[] readBounded(InputStream input, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] block = new byte[4096];
    int n;
    while ((n = input.read(block)) != -1) {
      if (out.size() + n > max) throw new IOException("File exceeds the import size limit");
      out.write(block, 0, n);
    }
    return out.toByteArray();
  }
}
