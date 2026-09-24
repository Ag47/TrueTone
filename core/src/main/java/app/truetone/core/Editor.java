package app.truetone.core;

import java.util.*;

public final class Editor {
  public Project project;
  private final Deque<Project> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

  public Editor(Project project) {
    this.project = project;
  }

  public void change(Project next) {
    undo.push(project);
    if (undo.size() > 100) undo.removeLast();
    redo.clear();
    project = next;
  }

  public void undo() {
    if (!undo.isEmpty()) {
      redo.push(project);
      project = undo.pop();
    }
  }

  public void redo() {
    if (!redo.isEmpty()) {
      undo.push(project);
      project = redo.pop();
    }
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public void quantize(long grid) {
    if (grid < 1) throw new IllegalArgumentException("Invalid grid");
    List<List<NoteEvent>> tracks = new ArrayList<>();
    for (List<NoteEvent> track : project.tracks) {
      List<NoteEvent> notes = new ArrayList<>();
      for (NoteEvent n : track) {
        long start =
            Math.min(project.lengthTick - 1, Math.round((double) n.startTick / grid) * grid);
        long end =
            Math.min(
                project.lengthTick,
                Math.max(
                    start + 1, Math.round((double) (n.startTick + n.durationTick) / grid) * grid));
        notes.add(new NoteEvent(n.pitch, n.velocity, start, end - start, n.channel));
      }
      tracks.add(notes);
    }
    change(
        new Project(
            project.title,
            project.bpm,
            project.numerator,
            project.denominator,
            project.lengthTick,
            tracks));
  }
}
