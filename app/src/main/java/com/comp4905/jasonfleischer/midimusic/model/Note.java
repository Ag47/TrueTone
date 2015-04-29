package com.comp4905.jasonfleischer.midimusic.model;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

import com.comp4905.jasonfleischer.midimusic.MainActivity;
import com.comp4905.jasonfleischer.midimusic.R;
import com.comp4905.jasonfleischer.midimusic.audio.MidiFile;
import com.comp4905.jasonfleischer.midimusic.audio.SoundManager;
import com.comp4905.jasonfleischer.midimusic.audio.SoundManager.SoundType;
import com.comp4905.jasonfleischer.midimusic.fragments.FragMentManager;
import com.comp4905.jasonfleischer.midimusic.util.HLog;
import com.comp4905.jasonfleischer.midimusic.views.Key;

public class Note implements Serializable {

    private static final long serialVersionUID = -1263171525588010652L;
    private NoteName name;
    private int octave;
    private int midiValue;
    private int soundID;
    private int chordSoundID;
    private int sequenceSoundID;

    public static int DEFAULT_NOTE_VELOCITY;
    private static final int MIN_MIDI_VALUE = 21;
    private static final int MAX_MIDI_VALUE = 108;

    private ArrayList<Finger> fingers = new ArrayList<Finger>();

    public static enum NoteName {
        C("C"), Db("Db/C#"), D("D"), Eb("Eb/D#"), E("E"), F("F"),
        Gb("Gb/F#"), G("G"), Ab("Ab/G#"), A("A"), Bb("Bb/A#"), B("B");

        private String name;

        NoteName(String n) {
            name = n;
        }

        public String toString() {
            return name;
        }

        public static NoteName getNameName(String name) {
            for (NoteName nn : NoteName.values()) {
                if (nn.name.equals(name))
                    return nn;
            }
            return null;


        }
    }

    public static enum NoteDuration {
        Sixteenth(4, MainActivity.getInstance().getResources().getString(R.string.note_very_short)),
        Eighth(8, MainActivity.getInstance().getResources().getString(R.string.note_short)),
        Quarter(16, MainActivity.getInstance().getResources().getString(R.string.note_med)),
        Half(32, MainActivity.getInstance().getResources().getString(R.string.note_long)),
        Whole(64, MainActivity.getInstance().getResources().getString(R.string.note_very_long));

        private int value;
        private String name;

        NoteDuration(int v, String n) {
            value = v;
            name = n;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }
    }

    public Note(int oct, NoteName n, int midiV) {
        if (midiV < MIN_MIDI_VALUE || midiV > MAX_MIDI_VALUE) {
            HLog.e("out of range midi:" + midiValue);
        }
        name = n;
        octave = oct;
        midiValue = midiV;
        soundID = -1;
        updateNoteFile();
    }

    public void playNote() {

        switch (MainActivity.config.playingMode) {
            case CHORD:
                SoundManager.getInstance().playSound(chordSoundID, SoundType.CHORD);
                MainActivity.getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FragMentManager.getInstance().updateNotePressed(name.toString(), octave);
                    }
                });
                break;
            case DRUMS:
                SoundManager.getInstance().playDrumSound(MainActivity.config.midiDrumSounds[name.ordinal()].getSoundID());
                MainActivity.getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FragMentManager.getInstance().updateNotePressed(MainActivity.config.midiDrumSounds[name.ordinal()].getName(), -1);
                    }
                });
                break;
            case SEQUENCE:
                SoundManager.getInstance().playSound(sequenceSoundID, SoundType.SEQUENCE);
                MainActivity.getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FragMentManager.getInstance().updateNotePressed(name.toString(), octave);
                    }
                });
                break;
            case SINGLE_NOTE:
                SoundManager.getInstance().playSound(soundID, SoundType.NOTE);
//                SoundManager.getInstance().createAndPlayNoteDynamically(midiValue, DEFAULT_NOTE_VELOCITY);
                MainActivity.getInstance().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FragMentManager.getInstance().updateNotePressed(name.toString(), octave);
                    }
                });
                break;
            default:
                break;
        }
    }

    public static int getMidiValueFrom(NoteName noteName, int octave) {
        return (octave * 12 + noteName.ordinal()) + 12;
    }

    public void updateNoteFile() {

        String fileName = "";
        switch (MainActivity.config.playingMode) {
            case CHORD:

                fileName = midiValue + "_chord.mid";
            /*NDKFunct.writeChordFile(midiValue, MainActivity.config.chordInstrument.getValue(),
						DEFAULT_NOTE_VELOCITY, MainActivity.config.noteDuration.getValue(),
						FileManager.getInstance().EXTERNAL_PATH+fileName, MainActivity.config.tempo.getMpqn()
						,MainActivity.config.chord.getIntervals()); */
                MidiFile.writeChordFile(midiValue, MainActivity.config.chordInstrument.getValue(),
                        DEFAULT_NOTE_VELOCITY, MainActivity.config.noteDuration.getValue(),
                        fileName, MainActivity.config.tempo.getTempoEvent(),
                        MainActivity.config.chord.getIntervals());
                chordSoundID = SoundManager.getInstance().addSoundSoundPool(fileName);
                break;
            case SEQUENCE:
                fileName = midiValue + "_sequence.mid";
			/*NDKFunct.writeSequenceFile(midiValue, MainActivity.config.sequenceInstrument.getValue(),
						DEFAULT_NOTE_VELOCITY, FileManager.getInstance().EXTERNAL_PATH+fileName,
						MainActivity.config.tempo.getMpqn(),
						MainActivity.config.sequence.getSequence());*/

                MidiFile.writeSequenceFile(midiValue, MainActivity.config.sequenceInstrument.getValue(),
                        DEFAULT_NOTE_VELOCITY, fileName,
                        MainActivity.config.sequenceTempo.getTempoEvent(),
                        MainActivity.config.sequence.getSequence());
                sequenceSoundID = SoundManager.getInstance().addSoundSoundPool(fileName);
                break;
            case SINGLE_NOTE:
                fileName = midiValue + ".mid";
                MidiFile.writeSingleNoteFile(midiValue, MainActivity.config.singleNoteInstrument.getValue(),
                        DEFAULT_NOTE_VELOCITY, MainActivity.config.noteDuration.getValue(),
                        fileName, MainActivity.config.tempo.getTempoEvent());
			/*NDKFunct.writeSingleNoteFile(midiValue, MainActivity.config.singleNoteInstrument.getValue(),
						DEFAULT_NOTE_VELOCITY, MainActivity.config.noteDuration.getValue(),
						FileManager.getInstance().EXTERNAL_PATH+fileName, MainActivity.config.tempo.getMpqn());*/
                soundID = SoundManager.getInstance().addSoundSoundPool(fileName);
//                Log.i("SINGLE NOTE","s");
                break;
            default:
                break;
        }

    }

    public NoteName getName() {
        return name;
    }

    public int getOctave() {
        return octave;
    }

    public int getMidiValue() {
        return midiValue;
    }

    public void unLoad() {
        SoundManager.getInstance().unloadFromSoundPool(soundID);
        SoundManager.getInstance().unloadFromSoundPool(sequenceSoundID);
        SoundManager.getInstance().unloadFromSoundPool(chordSoundID);
    }

    public Boolean isPressed() {
        return fingers.size() > 0;
    }

    public void press(Finger finger) {
        this.fingers.add(finger);
//        this.piano.invalidate();
//        if (listener != null) {
//            this.listener.keyPressed(id, Key.ACTION_KEY_DOWN);
//        }
    }

    public void depress(Finger finger) {
        this.fingers.remove(finger);
//        if (!isPressed()) {
//            this.piano.invalidate();
//            if (listener != null) {
//                this.listener.keyPressed(id, Key.ACTION_KEY_UP);
//            }
//        }
    }
}
