package com.comp4905.jasonfleischer.midimusic.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.sql.Time;

import com.comp4905.jasonfleischer.midimusic.MainActivity;
import com.comp4905.jasonfleischer.midimusic.model.Note;
import com.comp4905.jasonfleischer.midimusic.model.Track;
import com.comp4905.jasonfleischer.midimusic.util.FileManager;
import com.comp4905.jasonfleischer.midimusic.util.HLog;
import com.comp4905.jasonfleischer.midimusic.views.RecordingPane;
import com.leff.midi.event.MidiEvent;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

// TODO set onSensor change the DEFAULT_NOTE_VELOCITY in this java

public class SoundManager {

    public static File lastSaved;

    public String toHex(String arg) {
        return String.format("%x", new BigInteger(1, arg.getBytes(/*YOUR_CHARSET?*/)));
    }

    private static final SoundManager instance = new SoundManager();
    private static SoundPool metronomePool, soundPool, drumSoundPool, chordSoundPool, dynamicSoundPool, sequenceSoundPool;
    MediaPlayer mediaPlayer;
    private Timer timer, metronomeTimer;

    private int lastSequenceId;

    public static boolean isPlayingMetronome = false;
    public static boolean isMetronomeSpeakState = false;

    static private HashMap<String, Integer> metronomeSoundMap;

    private Vector<Integer> pKillSoundQueue = new Vector<Integer>();

    public static int[] savedVel = new int[1000];
    public static int saveno = 0;

    public static ArrayList<Integer> publicIds;
    public static ArrayList<Long> publicTimes;

    public enum SoundType {
        NOTE, CHORD, SEQUENCE, DRUM;
    }

    private SoundManager() {

        soundPool = new SoundPool(35, AudioManager.STREAM_MUSIC, 0);
        metronomePool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        drumSoundPool = new SoundPool(25, AudioManager.STREAM_MUSIC, 0);

        //chordSoundPool = new SoundPool(15, AudioManager.STREAM_DTMF, 0);
        sequenceSoundPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);

		/*MediaPlayer mediaPlayer = new MediaPlayer();
        // permission modify_audio_setting
		EnvironmentalReverb eReverb = new EnvironmentalReverb(0, mediaPlayer.getAudioSessionId());
		mediaPlayer.attachAuxEffect(eReverb.getId());
		mediaPlayer.setAuxEffectSendLevel(1.0f);
		//eReverb.setPreset(PresetReverb.PRESET_LARGEHALL);
		eReverb.setDecayHFRatio((short) 1000);
	       eReverb.setDecayTime(1000);
	       eReverb.setDensity((short) 1000);
	       eReverb.setDiffusion((short) 1000);
	       eReverb.setReverbLevel((short) -1000);
	       eReverb.setReverbDelay((short) 50);

	    eReverb.setEnabled(true);

		PresetReverb pReverb  = new PresetReverb(1,0);
		mediaPlayer.attachAuxEffect(pReverb.getId());
		mediaPlayer.setAuxEffectSendLevel(1.0f);
		pReverb.setPreset(PresetReverb.PRESET_LARGEHALL);
	    pReverb.setEnabled(true);*/


        //mAudioManager = (AudioManager) MainActivity.getInstance().getSystemService(MainActivity.getInstance().AUDIO_SERVICE);
        //streamVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        //streamVolume = streamVolume / mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public static SoundManager getInstance() {
        return instance;
    }


    public void createAndPlayNoteDynamically(int midiValue, int velocity) {
        if (dynamicSoundPool == null) {
            dynamicSoundPool = new SoundPool(88, AudioManager.STREAM_MUSIC, 0);
        }
        String fileName = "dyn_" + midiValue + ".mid";
        MidiFile.writeSingleNoteFile(midiValue, 1, velocity, 1, fileName, MainActivity.config.tempo.getTempoEvent());
        final int id = dynamicSoundPool.load(FileManager.getInstance().EXTERNAL_PATH + fileName, 1);
        dynamicSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                dynamicSoundPool.play(id, 1, 1, 0, 0, 1);
            }
        });
    }

    //KEY POOL

    public int addSoundSoundPool(String fileName) {
        return soundPool.load(FileManager.getInstance().INTERNAL_PATH + fileName, 1);
    }

    public void playSound(int soundId, SoundType st) {
        float vol = (float) Note.DEFAULT_NOTE_VELOCITY / 127.0f;
        soundPool.play(soundId, vol, vol, 0, 0, 1);

        if (RecordingPane.isRecording) {
            RecordingPane.masterTrack.add(st, System.nanoTime(), soundId);
            savedVel[saveno] = Note.DEFAULT_NOTE_VELOCITY;
            saveno++;
        }
    }

	/*public void playSingleNoteSound(int soundId){
        soundPool.play(soundId, 1, 1, 0, 0, 1);
		if(RecordingPane.isRecording){
			RecordingPane.masterTrack.add(SoundType.NOTE, System.nanoTime(), soundId);
		}
	}
	public void playChordSound(int soundId){
		soundPool.play(soundId, 1, 1, 0, 0, 1);
		if(RecordingPane.isRecording){
			RecordingPane.masterTrack.add(SoundType.CHORD, System.nanoTime(), soundId);
		}
	}
	public void playSequenceSound(int soundId){
		soundPool.play(soundId, 1, 1, 0, 0, 1);
		if(RecordingPane.isRecording){
			RecordingPane.masterTrack.add(SoundType.SEQUENCE, System.nanoTime(), soundId);
		}
	}*/


    public void unloadFromSoundPool(int soundID) {
        soundPool.unload(soundID);
    }

    //DRUM POOL

    public int addSoundToDrumSoundPool(String fileName) {
        return drumSoundPool.load(FileManager.getInstance().getAFD("drums/" + fileName), 1);
    }

    public void playDrumSound(int soundId) {
        drumSoundPool.play(soundId, 0.5f, 0.5f, 0, 0, 1);
        if (RecordingPane.isRecording) {
            RecordingPane.masterTrack.add(SoundType.DRUM, System.nanoTime(), soundId);
        }
    }

    public void unloadDrumPool(int soundIDs) {
        drumSoundPool.unload(soundIDs);
    }

    public long playCountIn() {
        final long tempoTime = MainActivity.config.tempo.getMS();
        stopMetronome();
        metronomeTimer = new Timer();

        metronomePool.play(metronomeSoundMap.get("four.mp3"), 1, 1, 0, 0, 1);
        String[] fileNames = new String[]{"three.mp3", "two.mp3", "one.mp3"};
        for (int i = 0; i < 3; i++) {
            metronomeTimer.schedule(new MetronomeTimerTimer(fileNames[i]), tempoTime * (i + 1));
        }
        return tempoTime * 4;
    }

    //Track
    public void playTrack(final Track t, final boolean repeat) {
        stopTimer(timer);
        timer = new Timer();

        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                ArrayList<Integer> ids = t.getSoundIds();
                publicIds = null;
                publicIds = t.getSoundIds();
                ArrayList<Long> times = t.getTimes();
                publicTimes = null;
                publicTimes = t.getTimes();
                ArrayList<SoundType> st = t.getSoundTypes();

                // Original Format of midi
                // the Header of savedMidi is default and preset.
                for (int i = 0; i < ids.size(); i++) {
                    if (st.get(i) == SoundType.DRUM)
                        timer.schedule(new TrackTimerTimer(ids.get(i), drumSoundPool), times.get(i));
                    else {
                        timer.schedule(new TrackTimerTimer(ids.get(i), soundPool), times.get(i));
                        Log.i("note", Integer.toString(ids.get(i)));
                    }
                }

                if (!repeat) {
                    timer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            RecordingPane.stopDub();
                        }
                    }, t.getDelayBeforeNextLoop());
                }

            }
        };

        if (t == null) {
            HLog.e("Track is empty");
            return;
        }

        if (repeat)
            timer.scheduleAtFixedRate(tt, 0, t.getDelayBeforeNextLoop());
        else
            timer.schedule(tt, 0);
    }

    public void saveTrack(final Track t, final boolean repeat) {
        // save as midi file
        String mFileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/media/audio/ringtones";
        Log.i("File Saving Path", mFileName);
//                FileOutputStream newRuleStream;
//
//                try {
//                    newRuleStream = new FileOutputStream(new File(mFileName, "NewRuleMIDI.txt"));
//                    newRuleStream.write(newRuleMidi.getBytes());
//                    newRuleStream.close();
//                    Log.i("file", "Saved as NewRuleMIDI.txt");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }

        // trying leff library
        // Create midi file example
        // 1. Create some MidiTracks
        com.leff.midi.MidiTrack tempoTrack = new com.leff.midi.MidiTrack();
        com.leff.midi.MidiTrack noteTrack = new com.leff.midi.MidiTrack();

        // 2. Add events to the tracks
        // Track 0 is the tempo map
        com.leff.midi.event.meta.TimeSignature ts = new com.leff.midi.event.meta.TimeSignature();
        ts.setTimeSignature(4, 4, com.leff.midi.event.meta.TimeSignature.DEFAULT_METER, com.leff.midi.event.meta.TimeSignature.DEFAULT_DIVISION);

        com.leff.midi.event.meta.Tempo tempo = new com.leff.midi.event.meta.Tempo();
        tempo.setBpm(120);

        tempoTrack.insertEvent(ts);
        tempoTrack.insertEvent(tempo);

        // Track 1 will have some notes in it
        for (int i = 0; i < publicIds.size(); i++) {
            int channel = 0;
            // adjustment of pitch
            int pitch = publicIds.get(i) + 20;
            while (pitch > 127)
                pitch -= 88;
            int velocity = savedVel[i];
//            long tick = i * 480;
            long tick = publicTimes.get(i) + 200;
            long duration = 240;
            noteTrack.insertNote(channel, pitch, velocity, tick, duration);
        }

        // 3. Create a MidiFile with the tracks we created
        List<com.leff.midi.MidiTrack> tracks = new ArrayList<com.leff.midi.MidiTrack>();
        tracks.add(tempoTrack);
        tracks.add(noteTrack);

        com.leff.midi.MidiFile midi = new com.leff.midi.MidiFile(com.leff.midi.MidiFile.DEFAULT_RESOLUTION, (ArrayList<com.leff.midi.MidiTrack>) tracks);

        // 4. Write the MIDI data to a file

        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd:MM:yyyy HH:mm:ss");
        String strDate = sdf.format(c.getTime());
//        File output = new File(mFileName, strDate + ".mid");
//        File nonroot = new File(Environment.getExternalStorageDirectory(), strDate + ".mid");
        Log.i("checkPath", mFileName.toString());
        File outputForMI = new File("/sdcard/Ringtones", strDate + ".mid");
        try {
//            midi.writeToFile(output);
            midi.writeToFile(outputForMI);
            lastSaved = outputForMI; // should be useless
//            midi.writeToFile(nonroot);
            Log.i("File Saved", "Saved as " + strDate + ".mid");
            MediaScannerConnection.scanFile(MainActivity.getInstance().getApplicationContext(), new String[]{outputForMI.toString()}, null, null);
        } catch (IOException e) {
            System.err.println(e);
        }


//        FileInputStream fis = null;
//        try {
//            fis = new FileInputStream(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/ANewTone.mid"));
//            com.midisheetmusic.MidiFile.readString = toHex(Integer.toString(fis.read()));
////                    midi.
//            Log.i("instantRead", com.midisheetmusic.MidiFile.readString);
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    public void stopTrack() {
        stopTimer(timer);
        stopTimer(metronomeTimer);
    }

    private class TrackTimerTimer extends TimerTask {
        private int soundId;
        private SoundPool sundPool;

        private TrackTimerTimer(Integer integer, SoundPool sp) {
            soundId = integer;
            sundPool = sp;
        }

        @Override
        public void run() {
            sundPool.play(soundId, 1, 1, 0, 0, 1);
        }
    }

    private void stopTimer(Timer timer) {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;
        }
    }

    //CHORD POOL

	/*public void playChordPlayerSound(int soundId) {
        chordSoundPool.play(soundId, 1, 1, 0, 0, 1);
	}
	public void unloadChordSound(int soundId){
		chordSoundPool.unload(soundId);
	}
	public int addChordSoundPool(String fileName) {
		return chordSoundPool.load(FileManager.getInstance().EXTERNAL_PATH+fileName, 1);
	}*/

    //SEQUENCE

    public void playSequence(final boolean loop) {
        int midiValue = 0;
        int velocity = 0;
        int instrument = 0;
        String fileName = "dyn_" + midiValue + ".mid";
//        MidiFile.writeNoteFile(midiValue, velocity, fileName, MainActivity.config.tempo.getTempoEvent());
//        MidiFile.writeSingleNoteFile(midiValue,instrument, velocity, fileName, MainActivity.config.tempo.getTempoEvent());
        lastSequenceId = sequenceSoundPool.load(FileManager.getInstance().INTERNAL_PATH + "sequence.mid", 1);
        sequenceSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                sequenceSoundPool.play(lastSequenceId, 1, 1, 0, (loop ? -1 : 0), 1);
            }
        });

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(FileManager.getInstance().EXTERNAL_PATH + "sequence.mid");
            mediaPlayer.setLooping(loop);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            HLog.e("Media Player Failure");
            e.printStackTrace();
        }
    }

    public void stopLoop() {
        //sequenceSoundPool.
        sequenceSoundPool.stop(lastSequenceId);
        //sequenceSoundPool.setLoop(lastSequence, 0);
        //mediaPlayer.reset();
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // Metronome

    private class MetronomeTimerTimer extends TimerTask {
        private String fileName;

        public MetronomeTimerTimer(String string) {
            fileName = string;
        }

        @Override
        public void run() {
            metronomePool.play(metronomeSoundMap.get(fileName), 1, 1, 0, 0, 1);
        }
    }

    public void initMetronome() { // onCreate
        metronomeSoundMap = new HashMap<String, Integer>();
        for (String fileName : FileManager.getInstance().getMetronomeSoundsFromAssets()) {
            metronomeSoundMap.put(fileName, metronomePool.load(FileManager.getInstance().getAFD("metronome/" + fileName), 1));
        }
    }

    public void unloadMetronome() { // onDestroy
        for (String fileName : FileManager.getInstance().getMetronomeSoundsFromAssets()) {
            metronomePool.unload(metronomeSoundMap.get(fileName));
        }
        stopMetronome();
    }

    public void stopMetronome() {
        isPlayingMetronome = false;
        stopTimer(metronomeTimer);
        ;
    }

    public void startMetronome(final int accent, final int indexOfSpokenOption) {
        // accent == 0->none, 1->"2" ,2->"3" or 3->"4"
        // indexOfSpokenOption 0-> -, 1-> &, 2-> e & a

        final long time = MainActivity.config.tempo.getMS();
        final long halfTime = time / 2;
        final long quarterTime = time / 4;
        TimerTask tt;
        if (isMetronomeSpeakState) {
            tt = new TimerTask() {
                @Override
                public void run() {

                    metronomePool.play(metronomeSoundMap.get("one.mp3"), 1, 1, 0, 0, 1);
                    if (indexOfSpokenOption == 1) {
                        metronomeTimer.schedule(new MetronomeTimerTimer("n.mp3"), halfTime);
                    }
                    if (indexOfSpokenOption == 2) {
                        metronomeTimer.schedule(new MetronomeTimerTimer("e.mp3"), quarterTime);
                        metronomeTimer.schedule(new MetronomeTimerTimer("n.mp3"), halfTime);
                        metronomeTimer.schedule(new MetronomeTimerTimer("a.mp3"), halfTime + quarterTime);
                    }
                    String[] fileNames = new String[]{"two.mp3", "three.mp3", "four.mp3"};
                    for (int i = 0; i < accent; i++) {
                        long timeSurplus = time * (i + 1);
                        metronomeTimer.schedule(new MetronomeTimerTimer(fileNames[i]), timeSurplus);
                        if (indexOfSpokenOption == 1) {
                            metronomeTimer.schedule(new MetronomeTimerTimer("n.mp3"), timeSurplus + halfTime);
                        }
                        if (indexOfSpokenOption == 2) {
                            metronomeTimer.schedule(new MetronomeTimerTimer("e.mp3"), timeSurplus + quarterTime);
                            metronomeTimer.schedule(new MetronomeTimerTimer("n.mp3"), timeSurplus + halfTime);
                            metronomeTimer.schedule(new MetronomeTimerTimer("a.mp3"), timeSurplus + halfTime + quarterTime);
                        }
                    }
                }
            };
        } else {
            tt = new TimerTask() {
                @Override
                public void run() {
                    metronomePool.play(metronomeSoundMap.get("Low.wav"), 1, 1, 0, 0, 1);
                    for (int i = 0; i < accent; i++) {
                        metronomeTimer.schedule(new MetronomeTimerTimer("High.wav"), time * (i + 1));
                    }
                }
            };
        }
        metronomeTimer = new Timer();
        isPlayingMetronome = true;
        metronomeTimer.schedule(tt, 0, time * (accent + 1));
    }

	/*public static void release(){
        soundPool.release();
		soundPool = null;
		metronomePool.release();
		metronomePool = null;
		drumSoundPool.release();

		drumSoundPool = null;
		sequenceSoundPool.release();
		sequenceSoundPool = null;
	}*/
}
