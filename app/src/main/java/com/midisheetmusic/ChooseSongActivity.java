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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

//import com.comp4905.jasonfleischer.midimusic.R;

import com.comp4905.jasonfleischer.midimusic.MainActivity;
import com.comp4905.jasonfleischer.midimusic.R;
import com.comp4905.jasonfleischer.midimusic.audio.SoundManager;
import com.example.android.navigationdrawer.AboutUs;
import com.example.android.navigationdrawer.PlanetAdapter;
import com.example.android.navigationdrawer.QRCode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @class ScanMidiFiles
 * The ScanMidiFiles class is used to scan for midi files
 * on a background thread.
 */
class ScanMidiFiles extends AsyncTask<Integer, Integer, ArrayList<FileUri>> {
    private ArrayList<FileUri> songlist;
    private File rootdir;
    private ChooseSongActivity activity;

    public ScanMidiFiles() {

    }

    public void setActivity(ChooseSongActivity activity) {
        this.activity = activity;
    }

    @Override
    protected void onPreExecute() {
        songlist = new ArrayList<FileUri>();
        try {
            rootdir = Environment.getExternalStorageDirectory();
//            rootdir = new File("/");
            Toast message = Toast.makeText(activity, "Scanning " + rootdir.getAbsolutePath() + " for MIDI files", Toast.LENGTH_SHORT);
            message.show();
        } catch (Exception e) {
        }
    }

    @Override
    protected ArrayList<FileUri> doInBackground(Integer... params) {
        if (rootdir == null) {
            return songlist;
        }
        try {
            loadMidiFilesFromDirectory(rootdir, 1);
        } catch (Exception e) {
        }
        return songlist;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
    }

    @Override
    protected void onPostExecute(ArrayList<FileUri> result) {
        ChooseSongActivity act = activity;
        this.activity = null;
        act.scanDone(songlist);
//        Toast message = Toast.makeText(act, "Found " + songlist.size() + " MIDI files", Toast.LENGTH_SHORT);
//        message.show();
    }

    @Override
    protected void onCancelled() {
        this.activity = null;
    }

    /* Given a directory, add MIDI files (ending in .mid) to the songlist.
     * If the directory contains subdirectories, call this method recursively.
     */
    private void loadMidiFilesFromDirectory(File dir, int depth) throws IOException {
        if (isCancelled()) {
            return;
        }
        if (depth > 10) {
            return;
        }
        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            if (isCancelled()) {
                return;
            }
            if (file.getName().endsWith(".mid") || file.getName().endsWith(".MID") ||
                    file.getName().endsWith(".midi")) {
                FileUri song = new FileUri(file.getAbsolutePath());
                songlist.add(song);
            }
        }
        for (File file : files) {
            if (isCancelled()) {
                return;
            }
            try {
                if (file.isDirectory()) {
                    loadMidiFilesFromDirectory(file, depth + 1);
                }
            } catch (Exception e) {
            }
        }
    }
}


/**
 * @class ChooseSongActivity
 * The ChooseSongActivity class is used to display a list of
 * songs to choose from.  The list is created from the songs
 * shipped with MidiSheetMusic (in the assets directory), and
 * also by searching for midi files in the internal/external
 * device storage.
 * <p/>
 * When a song is chosen, this calls the SheetMusicAcitivty, passing
 * the raw midi byte[] data as a parameter in the Intent.
 */
public class ChooseSongActivity extends ListActivity implements PlanetAdapter.OnItemClickListener, TextWatcher {

    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mPlanetTitles;

    /**
     * The complete list of midi files
     */
    ArrayList<FileUri> songlist;

    /**
     * Textbox to filter the songs by name
     */
    EditText filterText;

    /**
     * Task to scan for midi files
     */
    ScanMidiFiles scanner;

    IconArrayAdapter<FileUri> adapter;

    /* When this activity changes orientation, save the songlist,
     * so we don't have to re-scan for midi songs.
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return songlist;
    }

    @Override
    public void onCreate(Bundle state) {

        mPlanetTitles = getResources().getStringArray(R.array.planets_array);
        mDrawerList = (RecyclerView) findViewById(R.id.left_drawer);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        super.onCreate(state);
        setContentView(R.layout.choose_song);
        getActionBar().setTitle("TrueTone: Library");

        scanForSongs();
        
        /* If we're restarting from an orientation change,
         * load the saved song list.
         */
        songlist = (ArrayList<FileUri>) getLastNonConfigurationInstance();
        if (songlist != null) {
            adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
            this.setListAdapter(adapter);
        }

        mTitle = mDrawerTitle = getTitle();
        mPlanetTitles = getResources().getStringArray(R.array.planets_array);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (RecyclerView) findViewById(R.id.left_drawer);

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // improve performance by indicating the list if fixed size.
        mDrawerList.setHasFixedSize(true);
        mDrawerList.setLayoutManager(new LinearLayoutManager(this));

        // set up the drawer's list view with items and click listener
        mDrawerList.setAdapter(new PlanetAdapter(mPlanetTitles, this));
        // enable ActionBar app icon to behave as action to toggle nav drawer
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
        ) {
            public void onDrawerClosed(View view) {
                getActionBar().setTitle(mTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle(mDrawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        if (state == null) {
            selectItem(2);
        }

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (songlist == null || songlist.size() == 0) {
            songlist = new ArrayList<FileUri>();
            loadAssetMidiFiles();
            loadMidiFilesFromProvider(MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
            loadMidiFilesFromProvider(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);

            // Sort the songlist by name
            Collections.sort(songlist, songlist.get(0));

            // Remove duplicates
            ArrayList<FileUri> origlist = songlist;
            songlist = new ArrayList<FileUri>();
            String prevname = "";
            for (FileUri file : origlist) {
                if (!file.toString().equals(prevname)) {
                    songlist.add(file);
                }
                prevname = file.toString();
            }

            adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
            this.setListAdapter(adapter);
        }
        filterText = (EditText) findViewById(R.id.name_filter);
        filterText.addTextChangedListener(this);
        filterText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        SharedPreferences settings = getPreferences(0);
        boolean showedBrowseMenu = settings.getBoolean("showedBrowseMenu", false);
        if (!showedBrowseMenu) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("showedBrowseMenu", true);
            editor.commit();
//            Toast message = Toast.makeText(this, "To search for additional MIDI files, use the Menu", Toast.LENGTH_LONG);
//            message.show();

        }
//        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(SoundManager.lastSaved)));
//        Log.i("refreshed","content uri");
    }

    /**
     * Scan the SD card for midi songs.  Since this is a lengthy
     * operation, perform the scan in a background thread.
     */
    public void scanForSongs() {
        if (scanner != null) {
            return;
        }
        scanner = new ScanMidiFiles();
        scanner.setActivity(this);
        scanner.execute(0);
    }

    public void scanDone(ArrayList<FileUri> newfiles) {
        if (songlist == null || newfiles == null) {
            return;
        }
        for (FileUri file : newfiles) {
            songlist.add(file);
        }
        // Sort the songlist by name
        Collections.sort(songlist, songlist.get(0));

        // Remove duplicates
        ArrayList<FileUri> origlist = songlist;
        songlist = new ArrayList<FileUri>();
        String prevname = "";
        for (FileUri file : origlist) {
            if (!file.toString().equals(prevname)) {
                songlist.add(file);
            }
            prevname = file.toString();
        }

        adapter = new IconArrayAdapter<FileUri>(this, android.R.layout.simple_list_item_1, songlist);
        this.setListAdapter(adapter);
        scanner = null;
    }

    /**
     * Load all the sample midi songs from the assets directory into songlist.
     * Look for files ending with ".mid"
     */
    void loadAssetMidiFiles() {
        try {
            AssetManager assets = this.getResources().getAssets();
            String[] files = assets.list("");
            for (String path : files) {
                if (path.endsWith(".mid")) {
                    FileUri file = new FileUri(assets, path, path);
                    songlist.add(file);
                }
            }
        } catch (IOException e) {
        }
    }


    /**
     * Look for midi files (with mime-type audio/midi) in the
     * internal/external storage. Add them to the songlist.
     */
    private void loadMidiFilesFromProvider(Uri content_uri) {
        ContentResolver resolver = getContentResolver();
        String columns[] = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.MIME_TYPE
        };
        String selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE '%mid%'";
        Cursor cursor = resolver.query(content_uri, columns, selection, null, null);

        if (cursor == null) {
            return;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }

        do {
            int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            long id = cursor.getLong(idColumn);
            String title = cursor.getString(titleColumn);
            String mime = cursor.getString(mimeColumn);

            if (mime.endsWith("/midi") || mime.endsWith("/mid")) {
                Uri uri = Uri.withAppendedPath(content_uri, "" + id);
                FileUri file = new FileUri(resolver, uri, title);
                songlist.add(file);
            }
        } while (cursor.moveToNext());
        cursor.close();
    }

    /**
     * When a song is clicked on, start a SheetMusicActivity.
     * Read the raw byte[] data of the midi file.
     * Pass the raw byte[] data as a parameter in the Intent.
     * Pass the midi file Title as a parameter in the Intent.
     */
    @Override
    protected void onListItemClick(ListView parent, View view, int position, long id) {
        super.onListItemClick(parent, view, position, id);
        if (scanner != null) {
            scanner.cancel(true);
            scanner = null;
        }

        FileUri file = (FileUri) this.getListAdapter().getItem(position);
        Log.i("selected file path", file.toString());

        byte[] data = file.getData();
        Log.i("data", data.toString());
        if (data == null) {
            showErrorDialog("Error(data==null): Unable to open song: " + file.toString());
            Log.e("midifile error", "Error(data==null): Unable to open song: " + file.toString());
            return;
        }
        if (data.length <= 6) {
            showErrorDialog("Error(data.leg4nth <= 6): Unable to open song: " + file.toString());
            Log.e("midifile error", "Error(data.legnth <= 6): Unable to open song: " + file.toString());
            return;
        }
        if (!hasMidiHeader(data)) {
            showErrorDialog("Error(Has no Midi Header(data)): Unable to open song: " + file.toString());
            Log.e("midifile error", "Error(Has no Midi Header(data)): Unable to open song: " + file.toString());
            return;
        }

        Intent intent = new Intent(this, SheetMusicActivity.class);

        intent.putExtra(SheetMusicActivity.MidiDataID, data);
        intent.putExtra(SheetMusicActivity.MidiTitleID, file.toString());
        intent.putExtra(SheetMusicActivity.Path, file.toString());
        Uri uri = file.uri;
        if (uri != null) {
            intent.putExtra("asdf", uri.toString());
        }
        startActivity(intent);
    }


    /**
     * As text is entered in the filter box, filter the list of
     * midi songs to display.
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        adapter.getFilter().filter(s);
    }

    @Override
    public void afterTextChanged(Editable s) {
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }


    /**
     * Return true if the data starts with the header MTrk
     */
    boolean hasMidiHeader(byte[] data) {
        String s;
        try {
            s = new String(data, 0, 4, "US-ASCII");
            if (s.equals("MThd"))
                return true;
            else
                return false;
        } catch (UnsupportedEncodingException e) {
            Log.e("ChooseSong error", "has no Midi Header");
            return false;
        }
    }

    /**
     * Start the FileBrowser activity, which is used to select a midi file
     */
    void browseForSongs() {
        Intent intent = new Intent(this, FileBrowserActivity.class);
        startActivity(intent);
    }

    /**
     * When the menu button is pressed, initialize the menus.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.choose_song_menu, menu);
        return true;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }

    /**
     * Callback when a menu item is selected.
     * - Scan by QR: Scan the QR Code to create a new midi file
     * - Scan for Midi Files : Scan the SD card for midi files
     * - Browse Midi Files : Let the user browser for midi files
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.scan_qr:
                Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                startActivityForResult(intent, 0);    //Barcode Scanner to scan for us
                return true;
            case R.id.scan_files:
                scanForSongs();
                return true;
            case R.id.browse_files:
                browseForSongs();
                return true;
            case R.id.ringtone_manage:
//                check Ringtone List
                Intent intent1 = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent1.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_RINGTONE);
                intent1.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone");

                // for existing ringtone
                Uri urie = RingtoneManager.getActualDefaultRingtoneUri(
                        getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
                intent1.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, urie);

                startActivityForResult(intent1, 5);
            default:
                return super.onOptionsItemSelected(item);
        }


    }

    public void scanQR(View v) {
        // check Ringtone List
//        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
//        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
//                RingtoneManager.TYPE_RINGTONE);
//        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone");
//
//        // for existing ringtone
//        Uri urie = RingtoneManager.getActualDefaultRingtoneUri(
//                getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
//        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, urie);
//
//        startActivityForResult(intent, 5);


        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        startActivityForResult(intent, 0);    //Barcode Scanner to scan for us
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {

                Log.i("Result", intent.getStringExtra("SCAN_RESULT"));

                byte[] bytes = hexStringToByteArray(intent.getStringExtra("SCAN_RESULT"));

                String mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
                Log.i("QR Saved Path:", mFileName);
//                String mFileName = "/sdcard/media/audio/ringtones/";
                FileOutputStream outputStream;
                try {
//                    outputStream = new FileOutputStream(new File(mFileName, "Recent.mid"));
                    outputStream = new FileOutputStream(new File("/sdcard/Ringtones", "Recent.mid"));
                    outputStream.write(bytes);
                    MediaScannerConnection.scanFile(MainActivity.getInstance().getApplicationContext(), new String[]{"sdcard/Ringtones/Recent.mid"}, null, null);
                    outputStream.close();
                    Log.i("file", "Saved as Recent.mid");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                MediaScannerConnection.scanFile(MainActivity.getInstance().getApplicationContext(), new String[]{"sdcard/Ringtones/Recent.mid"}, null, null);
                intent = new Intent(this, SheetMusicActivity.class);
//                String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "Recent.mid";
                String filePath = "/sdcard/Ringtones/" + "Recent.mid";
                File file = new File("/sdcard/Ringtones", "Recent.mid");


                Log.i("fileAbs", file.getAbsolutePath().toString());
                FileUri song = new FileUri("/sdcard/Ringtones/Recent.mid");
//                MidiFileReader midiread = new MidiFileReader(song.getData());
                Uri contentUri = Uri.fromFile(file);
                Intent mediaScanIntent = new Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(contentUri);
                this.sendBroadcast(mediaScanIntent);
                MediaScannerConnection.scanFile(MainActivity.getInstance().getApplicationContext(), new String[]{"sdcard/Ringtones/Recent.mid"}, null, null);
//                MediaScannerConnection msc;
//                msc.scanFile("sdcard/Ringtones/Recent.mid", null);
                intent.putExtra(SheetMusicActivity.MidiDataID, bytes/*byte array*/);
                intent.putExtra(SheetMusicActivity.MidiTitleID, "Recent");
                Uri uri = song.uri;
                try {
                    Log.i("uriS", uri.toString());

                    intent.putExtra("asdf", uri.toString());
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                }
                startActivity(intent);

            } else if (resultCode == RESULT_CANCELED) {
            }
        }
    }

    /**
     * Show an error dialog with the given message
     */
    void showErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Fragment that appears in the "content_frame", shows a planet
     */
    public static class PlanetFragment extends Fragment {
        public static final String ARG_PLANET_NUMBER = "planet_number";

        public PlanetFragment() {
            // Empty constructor required for fragment subclasses
        }

        public static Fragment newInstance(int position) {
            Fragment fragment = new PlanetFragment();
            Bundle args = new Bundle();
            args.putInt(PlanetFragment.ARG_PLANET_NUMBER, position);
            fragment.setArguments(args);
            return fragment;
        }
    }

    private void selectItem(int position) {
        // update the main content by replacing fragments
        Fragment fragment = PlanetFragment.newInstance(position);

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.content_frame, fragment);
        ft.commit();

        // update selected item title, then close the drawer

        setTitle(mPlanetTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
    }


    public void onOption(View v) {

    }

    @SuppressLint("NewApi")
    public static final void recreateActivityCompat(final Activity a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            a.recreate();
        } else {
            final Intent intent = a.getIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            a.finish();
            a.overridePendingTransition(0, 0);
            a.startActivity(intent);
            a.overridePendingTransition(0, 0);
        }
    }


    /* The click listener for RecyclerView in the navigation drawer */
    @Override
    public void onClick(View view, int position) {
        selectItem(position);
        Intent intent;
        switch (position) {
            case 0:
                intent = new Intent(this, com.comp4905.jasonfleischer.midimusic.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
//                recreateActivityCompat(com.comp4905.jasonfleischer.midimusic.MainActivity.getInstance());
//                Intent mStartActivity = new Intent(this.getApplicationContext(), com.comp4905.jasonfleischer.midimusic.MainActivity.class);
//                int mPendingIntentId = 123456;
//                PendingIntent mPendingIntent = PendingIntent.getActivity(this.getApplicationContext(), mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
//                AlarmManager mgr = (AlarmManager) this.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
//                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
//                System.exit(0);
                break;
            case 1:
                intent = new Intent(this, ChooseSongActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 2:
                intent = new Intent(this, AboutUs.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 3:
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://202.125.255.1/finaltruetonev1/index.php/user/tutorial"));
                this.startActivity(intent);
        }
    }
}