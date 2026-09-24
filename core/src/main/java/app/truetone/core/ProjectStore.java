package app.truetone.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Write, fsync, atomic rename; failures leave the previous project intact. */
public final class ProjectStore {
  private final File directory;

  public ProjectStore(File directory) throws IOException {
    this.directory = directory;
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot open project library");
  }

  private File path(String id) {
    if (!id.matches("[a-zA-Z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid project id");
    return new File(directory, id + ".ttn");
  }

  public synchronized void save(String id, Project project) throws IOException {
    byte[] bytes = ProjectCodec.encode(project);
    File target = path(id), temp = new File(directory, id + ".tmp");
    try {
      try (FileOutputStream stream = new FileOutputStream(temp)) {
        stream.write(bytes);
        stream.getFD().sync();
      }
      Files.move(
          temp.toPath(),
          target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp.toPath());
    }
  }

  public synchronized Project load(String id) throws IOException {
    try (InputStream in = new FileInputStream(path(id))) {
      return ProjectCodec.decode(ProjectCodec.readBounded(in, ProjectCodec.MAX_BYTES));
    }
  }

  public synchronized List<String> ids() {
    File[] files =
        directory.listFiles((d, name) -> name.endsWith(".ttn") && !name.equals("draft.ttn"));
    List<String> ids = new ArrayList<>();
    if (files != null) {
      Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
      for (File f : files) ids.add(f.getName().substring(0, f.getName().length() - 4));
    }
    return ids;
  }

  public synchronized void preserveDamagedDraft() throws IOException {
    if (hasDraft())
      Files.move(
          path("draft").toPath(),
          new File(directory, "draft-" + UUID.randomUUID() + ".damaged").toPath(),
          StandardCopyOption.ATOMIC_MOVE);
  }

  public boolean hasDraft() {
    return path("draft").exists();
  }
}
