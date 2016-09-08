package com.comp4905.jasonfleischer.midimusic.util;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

import com.comp4905.jasonfleischer.midimusic.MainActivity;
import com.comp4905.jasonfleischer.midimusic.MidiMusicConfig;
import com.comp4905.jasonfleischer.midimusic.model.Instrument;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Locale;

public class FileManager {

    private static final FileManager instance = new FileManager();
    //	public final String EXTERNAL_PATH =  "/sdcard/MidiMusic/";
    public final String EXTERNAL_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    public final String INTERNAL_PATH = MainActivity.getInstance().getFilesDir().getPath();
    private final AssetManager assets;
    private final String SAVED_CONFIG_FILENAME = "MidiMusic.ser";


    private FileManager() {
        assets = MainActivity.getInstance().getAssets();
        File path = new File(INTERNAL_PATH);
        if (!path.exists()) {
            path.mkdirs();
        }
        Log.i("Saved Config FILE PATH Internal Path", INTERNAL_PATH);
        Log.i("External Path", EXTERNAL_PATH);
    }

    public static FileManager getInstance() {
        return instance;
    }

    public void loadInstrumentsFromAssets(ArrayList<Instrument> instruments) {

        try {
            InputStream is = assets.open("instruments_" + getLanguageSuffix() + ".txt");
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                String[] tok = line.split(",");
                instruments.add(new Instrument(Integer.valueOf(tok[0]), tok[1],
                        Integer.valueOf(tok[2]), Integer.valueOf(tok[3])));
            }
            is.close();
            isr.close();
            br.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String getLanguageSuffix() {
        String lang = Locale.getDefault().getLanguage();
        if (lang.equals("zh") || lang.equals("fr") || lang.equals("es"))
            return lang;
        else
            return "en";
    }

    public String[] getMetronomeSoundsFromAssets() {
        try {
            return assets.list("metronome");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String[] getDrumFileNames() {
        try {
            return assets.list("drums");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public AssetFileDescriptor getAFD(String fileName) {
        try {
            return assets.openFd(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasMusicConfigFile() {
        return (new File(INTERNAL_PATH + SAVED_CONFIG_FILENAME)).exists();
    }

    public void writeMidiMusicConfig(final MidiMusicConfig object) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(INTERNAL_PATH + SAVED_CONFIG_FILENAME));
                    try {
                        out.writeObject(object);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = bos.toByteArray();
                        out.write(buf);
                    } finally {
                        out.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public MidiMusicConfig readMidiMusicConfig() {
        try {
            FileInputStream in = new FileInputStream(INTERNAL_PATH + SAVED_CONFIG_FILENAME);
            ObjectInputStream reader = new ObjectInputStream(in);
            MidiMusicConfig result = (MidiMusicConfig) reader.readObject();
            reader.close();
            in.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            HLog.e("Problem reading saved configurations");
            return new MidiMusicConfig();
        }
    }
}
