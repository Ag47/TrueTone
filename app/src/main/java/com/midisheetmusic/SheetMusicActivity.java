/*
 * Copyright (c) 2011-2012 Madhav Vaidyanathan
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package com.midisheetmusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.comp4905.jasonfleischer.midimusic.R;
import com.example.android.navigationdrawer.Contents;
import com.example.android.navigationdrawer.QRCodeEncoder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.ringdroid.ChooseContactActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Handler;
import java.util.zip.CRC32;

/**
 * @class SheetMusicActivity
 * <p/>
 * The SheetMusicActivity is the main activity. The main components are:
 * - MidiPlayer : The buttons and speed bar at the top.
 * - Piano : For highlighting the piano notes during playback.
 * - SheetMusic : For highlighting the sheet music notes during playback.
 */
public class SheetMusicActivity extends Activity {

    public static final String MidiDataID = "MidiDataID";
    public static final String MidiTitleID = "MidiTitleID";
    public static String Path = "Path";
    public static final int settingsRequestCode = 1;

    private MidiPlayer player;   /* The play/stop/rewind toolbar */
    private Piano piano;         /* The piano at the top */
    private SheetMusic sheet;    /* The sheet music */
    private LinearLayout layout; /* THe layout */
    private MidiFile midifile;   /* The midi file to play */
    private MidiOptions options; /* The options for sheet music and sound */
    private long midiCRC;      /* CRC of the midi bytes */
    public String tonePath;
    public String title;
    public static Uri ringtoneURI;
    public static final String ringtoneUriString = "";
    private Bitmap forSaveQR;

    /**
     * Create this SheetMusicActivity.  The Intent should have two parameters:
     * - MidiTitleID: The title of the song (String)
     * - MidiDataID: The raw byte[] data of the midi file.
     */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        ClefSymbol.LoadImages(this);
        TimeSigSymbol.LoadImages(this);
        MidiPlayer.LoadImages(this);

        // Parse the MidiFile from the raw bytes
        byte[] data = this.getIntent().getByteArrayExtra(MidiDataID);
        title = this.getIntent().getStringExtra(MidiTitleID);
        Path = this.getIntent().getStringExtra("fileURI");

        if (this.getIntent().getStringExtra("asdf") != null) {
            ringtoneURI = Uri.parse(this.getIntent().getStringExtra("asdf"));
        }
//        Log.i("paste", ringtoneURI.toString());

        tonePath = "/sdcard/media/audio/ringtones/" + this.getIntent().getStringExtra(Path);


        this.setTitle("MidiSheetMusic: " + title);
        try {
            midifile = new MidiFile(data, title);
            Log.i("data", data.toString());
        } catch (MidiFileException e) {
            Log.e("SheetMusAct", e.toString());
            Log.e("SheetMusAct", "can not assign to midifile");
            this.finish();
            return;
        }

        // Initialize the settings (MidiOptions).
        // If previous settings have been saved, used those
        options = new MidiOptions(midifile);
        CRC32 crc = new CRC32();
        crc.update(data);
        midiCRC = crc.getValue();
        SharedPreferences settings = getPreferences(0);
        options.scrollVert = settings.getBoolean("scrollVert", false);
        options.shade1Color = settings.getInt("shade1Color", options.shade1Color);
        options.shade2Color = settings.getInt("shade2Color", options.shade2Color);
        String json = settings.getString("" + midiCRC, null);
        MidiOptions savedOptions = MidiOptions.fromJson(json);
        if (savedOptions != null) {
            options.merge(savedOptions);
        }
        createView();
        createSheetMusic(options);
    }

    /* Create the MidiPlayer and Piano views */
    void createView() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        player = new MidiPlayer(this);
        piano = new Piano(this);
        layout.addView(player);
        layout.addView(piano);
        setContentView(layout);
        player.SetPiano(piano);
        layout.requestLayout();
    }

    /**
     * Create the SheetMusic view with the given options
     */
    private void createSheetMusic(MidiOptions options) {
        if (sheet != null) {
            layout.removeView(sheet);
        }
        if (!options.showPiano) {
            piano.setVisibility(View.GONE);
        } else {
            piano.setVisibility(View.VISIBLE);
        }
        sheet = new SheetMusic(this);
        sheet.init(midifile, options);
        sheet.setPlayer(player);
        layout.addView(sheet);
        piano.SetMidiFile(midifile, options, player);
        piano.SetShadeColors(options.shade1Color, options.shade2Color);
        player.SetMidiFile(midifile, options, sheet);
        layout.requestLayout();
        sheet.callOnDraw();
    }


    /**
     * Always display this activity in landscape mode.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * When the menu button is pressed, initialize the menus.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (player != null) {
            player.Pause();
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sheet_menu, menu);
        return true;
    }

    /**
     * Callback when a menu item is selected.
     * - Choose Song : Choose a new song
     * - Song Settings : Adjust the sheet music and sound options
     * - Save As Images: Save the sheet music as PNG images
     * - Help : Display the HTML help screen
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.choose_song:
                chooseSong();
                return true;
            case R.id.song_settings:
                changeSettings();
                return true;
            case R.id.save_images:
                showSaveImagesDialog();
                return true;
            // generate QR Code
            case R.id.help:
//                showHelp();
                generateQRCode();
                return true;
            case R.id.setDefaultTone:
                RingtoneManager.setActualDefaultRingtoneUri(SheetMusicActivity.this, RingtoneManager.TYPE_RINGTONE, ringtoneURI);
                Context context = getApplicationContext();
                try {
                    Log.i("set", ringtoneURI.toString());
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
                CharSequence text = "Ringtone Saved!";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                return true;
            case R.id.rename:
                showRenameDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * To choose a new song, simply finish this activity.
     * The previous activity is always the ChooseSongActivity.
     */
    private void chooseSong() {
//        this.finish();
        Intent intent = new Intent(this, ChooseContactActivity.class);
        startActivity(intent);
    }

    /**
     * To change the sheet music options, start the SettingsActivity.
     * Pass the current MidiOptions as a parameter to the Intent.
     * Also pass the 'default' MidiOptions as a parameter to the Intent.
     * When the SettingsActivity has finished, the onActivityResult()
     * method will be called.
     */
    private void changeSettings() {
        MidiOptions defaultOptions = new MidiOptions(midifile);
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.settingsID, options);
        intent.putExtra(SettingsActivity.defaultSettingsID, defaultOptions);
        startActivityForResult(intent, settingsRequestCode);
    }


    /* Show the "Save As Images" dialog */
    private void showSaveImagesDialog() {
        LayoutInflater inflator = LayoutInflater.from(this);
        final View dialogView = inflator.inflate(R.layout.save_images_dialog, null);
        final EditText filenameView = (EditText) dialogView.findViewById(R.id.save_images_filename);
        filenameView.setText(midifile.getFileName().replace("_", " "));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.save_images_str);
        builder.setView(dialogView);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
                saveAsImages(filenameView.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /* Show the "show rename" dialog */
    private void showRenameDialog() {
        LayoutInflater inflator = LayoutInflater.from(this);
        final View dialogView = inflator.inflate(R.layout.rename_dialog, null);
        final EditText filenameView = (EditText) dialogView.findViewById(R.id.rename_filename);
        filenameView.setText(midifile.getFileName().replace("_", " "));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename");
        builder.setView(dialogView);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
                File from = new File("/sdcard/Ringtones/", midifile.getFileName() + ".mid");
                File to = new File("/sdcard/Ringtones/", filenameView.getText().toString() + ".mid");
                from.renameTo(to);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface builder, int whichButton) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    /* Save the current sheet music as PNG images. */
    private void saveAsImages(String name) {
        String filename = name;
        try {
            filename = URLEncoder.encode(name, "utf-8");
        } catch (UnsupportedEncodingException e) {
        }
        if (!options.scrollVert) {
            options.scrollVert = true;
            createSheetMusic(options);
        }
        try {
            int numpages = sheet.GetTotalPages();
            for (int page = 1; page <= numpages; page++) {
                Bitmap image = Bitmap.createBitmap(SheetMusic.PageWidth + 40, SheetMusic.PageHeight + 40, Bitmap.Config.ARGB_8888);
                Canvas imageCanvas = new Canvas(image);
                sheet.DrawPage(imageCanvas, page);
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/MidiSheetMusic");
                File file = new File(path, "" + filename + page + ".png");
                path.mkdirs();
                OutputStream stream = new FileOutputStream(file);
                image.compress(Bitmap.CompressFormat.PNG, 0, stream);
                image = null;
                stream.close();

                // Inform the media scanner about the file
                MediaScannerConnection.scanFile(this, new String[]{file.toString()}, null, null);
            }
        } catch (IOException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Error saving image to file " + Environment.DIRECTORY_PICTURES + "/MidiSheetMusic/" + filename + ".png");
            builder.setCancelable(false);
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }


    /**
     * Show the HTML help screen.
     */
    private void showHelp() {
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
    }

    /*
    * genereate QR Code for sharing
    */
    private void generateQRCode() {
        String qrInputText = MidiFile.readString;

        try {
            //Find screen size
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            Point point = new Point();
            display.getSize(point);
            int width = point.x;
            int height = point.y;
            int smallerDimension = width < height ? width : height;
            smallerDimension = smallerDimension * 3 / 4;
            //Encode with a QR Code image

            QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(qrInputText,
                    null,
                    Contents.Type.TEXT,
                    BarcodeFormat.QR_CODE.toString(),
                    smallerDimension);

            Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
            // Get screen size
            Display display1 = this.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display1.getSize(size);

            // Get target image size
            Bitmap bitmap1 = qrCodeEncoder.encodeAsBitmap();
            int bitmapHeight = bitmap1.getHeight();
            int bitmapWidth = bitmap1.getWidth();

            // Create resized bitmap image
            BitmapDrawable resizedBitmap = new BitmapDrawable(this.getResources(), Bitmap.createScaledBitmap(bitmap, bitmapWidth, bitmapHeight, false));

            // Create dialog
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.thumbnail);

            ImageView image = (ImageView) dialog.findViewById(R.id.imageview);

            // !!! Do here setBackground() instead of setImageDrawable() !!! //
            image.setBackground(resizedBitmap);
            forSaveQR = resizedBitmap.getBitmap();

            // Without this line there is a very small border around the image (1px)
            // In my opinion it looks much better without it, so the choice is up to you.
            dialog.getWindow().setBackgroundDrawable(null);
            dialog.show();


            try {
                Bitmap image1 = forSaveQR;
                Canvas imageCanvas = new Canvas(image1);
                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/MidiSheetMusic");
                File file = new File(path, "" + "savedQR" + ".png");
                path.mkdirs();
                OutputStream stream = new FileOutputStream(file);
                image1.compress(Bitmap.CompressFormat.PNG, 0, stream);
                image1 = null;
                stream.close();
                // Inform the media scanner about the file
                MediaScannerConnection.scanFile(this, new String[]{file.toString()}, null, null);
            } catch (IOException e) {
            }
            // TODO upload
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    /**
     * This is the callback when the SettingsActivity is finished.
     * Get the modified MidiOptions (passed as a parameter in the Intent).
     * Save the MidiOptions.  The key is the CRC checksum of the midi data,
     * and the value is a JSON dump of the MidiOptions.
     * Finally, re-create the SheetMusic View with the new options.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != settingsRequestCode) {
            return;
        }
        options = (MidiOptions)
                intent.getSerializableExtra(SettingsActivity.settingsID);

        // Check whether the default instruments have changed.
        for (int i = 0; i < options.instruments.length; i++) {
            if (options.instruments[i] !=
                    midifile.getTracks().get(i).getInstrument()) {
                options.useDefaultInstruments = false;
            }
        }
        // Save the options. 
        SharedPreferences settings = getPreferences(0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("scrollVert", options.scrollVert);
        editor.putInt("shade1Color", options.shade1Color);
        editor.putInt("shade2Color", options.shade2Color);
        String json = options.toJson();
        if (json != null) {
            editor.putString("" + midiCRC, json);
        }
        editor.commit();

        // Recreate the sheet music with the new options
        createSheetMusic(options);
    }

    /**
     * When this activity resumes, redraw all the views
     */
    @Override
    protected void onResume() {
        super.onResume();
        layout.requestLayout();
        player.invalidate();
        piano.invalidate();
        if (sheet != null) {
            sheet.invalidate();
        }
        layout.requestLayout();
    }

    /**
     * When this activity pauses, stop the music
     */
    @Override
    protected void onPause() {
        if (player != null) {
            player.Pause();
        }
        super.onPause();
    }
}