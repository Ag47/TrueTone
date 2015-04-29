/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.navigationdrawer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.comp4905.jasonfleischer.midimusic.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.midisheetmusic.ChooseSongActivity;
import com.midisheetmusic.FileUri;
import com.midisheetmusic.MidiFile;
import com.midisheetmusic.MidiFileReader;
import com.midisheetmusic.MidiSheetMusicActivity;
import com.midisheetmusic.SheetMusic;
import com.midisheetmusic.SheetMusicActivity;
import com.ringdroid.RingdroidSelectActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

//import info.androidhive.loginandregistration.LoginActivity;

/**
 * This example illustrates a common usage of the DrawerLayout widget
 * in the Android support library.
 * <p/>
 * <p>When a navigation (left) drawer is present, the host activity should detect presses of
 * the action bar's Up affordance as a signal to open and close the navigation drawer. The
 * ActionBarDrawerToggle facilitates this behavior.
 * Items within the drawer should fall into one of two categories:</p>
 * <p/>
 * <ul>
 * <li><strong>View switches</strong>. A view switch follows the same basic policies as
 * list or tab navigation in that a view switch does not create navigation history.
 * This pattern should only be used at the root activity of a task, leaving some form
 * of Up navigation active for activities further down the navigation hierarchy.</li>
 * <li><strong>Selective Up</strong>. The drawer allows the user to choose an alternate
 * parent for Up navigation. This allows a user to jump across an app's navigation
 * hierarchy at will. The application should treat this as it treats Up navigation from
 * a different task, replacing the current task stack using TaskStackBuilder or similar.
 * This is the only form of navigation drawer that should be used outside of the root
 * activity of a task.</li>
 * </ul>
 * <p/>
 * <p>Right side drawers should be used for actions, not navigation. This follows the pattern
 * established by the Action Bar that navigation should be to the left and actions to the right.
 * An action should be an operation performed on the current contents of the window,
 * for example enabling or disabling a data overlay on top of the current content.</p>
 */
public class QRCode extends Activity implements PlanetAdapter.OnItemClickListener {
    private static final String LOG_TAG = "QR";
    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mPlanetTitles;

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();


    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qrcode);

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

        if (savedInstanceState == null) {
            selectItem(2);
        }

        HandleClick hc = new HandleClick();
        findViewById(R.id.butQR).setOnClickListener(hc);

    }

    private class HandleClick implements OnClickListener {
        public void onClick(View arg0) {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            switch (arg0.getId()) {
                case R.id.butQR:
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                    break;
            }
            startActivityForResult(intent, 0);    //Barcode Scanner to scan for us
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {

                Log.i("Result", intent.getStringExtra("SCAN_RESULT"));

                byte[] bytes = hexStringToByteArray(intent.getStringExtra("SCAN_RESULT"));

                String mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
                FileOutputStream outputStream;
                try {
                    outputStream = new FileOutputStream(new File(mFileName, "aQRresult.mid"));
                    outputStream.write(bytes);
                    outputStream.close();
                    Log.i("file", "Saved as aQRresult.mid");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // TODO what? forgot...
                intent = new Intent(this, SheetMusicActivity.class);
                String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "aQRresult.mid";
                File file = new File(filePath);
                FileUri song = new FileUri(file.getAbsolutePath());
                MidiFileReader midiread = new MidiFileReader(song.getData());

                // Pass byte[] raw midi data to SheetMusicActivity View.
//                String string = intent.getStringExtra("SCAN_RESULT");
                Log.i("check", "run here");
//                Log.i("string", string);
//                String[] temp = string.split(" ");
//
//                byte[] bytess = hexStringToByteArray(keith);
//                int index = 0;
//                for (String item : temp)
//                {
//                    bytes[index] = Byte.parseByte(item);
//                    index++;
//                }
//                bytess = string.getBytes();

                intent.putExtra(SheetMusicActivity.MidiDataID, bytes/*byte array*/);
//                String print = new String(song.getData());
//                Log.i("print3", print);
//                intent.putExtra(SheetMusicActivity.MidiTitleID, MidiFile.getReadString());
                intent.putExtra(SheetMusicActivity.MidiTitleID, "QRmidi");
//                Log.i("here", "here");

                startActivity(intent);

            } else if (resultCode == RESULT_CANCELED) {
            }
        }


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.navigation_drawer, menu);
        return true;
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle action buttons
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* The click listener for RecyclerView in the navigation drawer */
    @Override
    public void onClick(View view, int position) {
        selectItem(position);
        Intent intent;
        switch (position) {
//            case 0:
//                intent = new Intent(this, MainActivity.class);
//                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                this.startActivity(intent);
//                break;
            case 0:
                intent = new Intent(this, com.comp4905.jasonfleischer.midimusic.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 1:
                intent = new Intent(this, QRCode.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 2:
                intent = new Intent(this, RingdroidSelectActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 3:
                intent = new Intent(this, ChooseSongActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
            case 4:
                intent = new Intent(this, AboutUs.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                this.startActivity(intent);
                break;
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

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
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

    public void onQR(View v) {
        switch (v.getId()) {
            case R.id.button1:
                String qrInputText = MidiFile.readString;

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
                try {
                    Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
                    ImageView myImage = (ImageView) findViewById(R.id.imageView1);
                    myImage.setImageBitmap(bitmap);



                    // Get screen size
                    Display display1 = this.getWindowManager().getDefaultDisplay();
                    Point size = new Point();
                    display1.getSize(size);
                    int screenWidth = size.x;
                    int screenHeight = size.y;

// Get target image size
                    Bitmap bitmap1 = qrCodeEncoder.encodeAsBitmap();
                    int bitmapHeight = bitmap1.getHeight();
                    int bitmapWidth = bitmap1.getWidth();

// Scale the image down to fit perfectly into the screen
// The value (250 in this case) must be adjusted for phone/tables displays
                    while(bitmapHeight > (screenHeight - 250) || bitmapWidth > (screenWidth - 250)) {
                        bitmapHeight = bitmapHeight / 2;
                        bitmapWidth = bitmapWidth / 2;
                    }

// Create resized bitmap image
                    BitmapDrawable resizedBitmap = new BitmapDrawable(this.getResources(), Bitmap.createScaledBitmap(bitmap, bitmapWidth, bitmapHeight, false));

// Create dialog
                    Dialog dialog = new Dialog(this);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.thumbnail);


                    ImageView image = (ImageView) dialog.findViewById(R.id.imageview);

// !!! Do here setBackground() instead of setImageDrawable() !!! //
                    image.setBackground(resizedBitmap);

// Without this line there is a very small border around the image (1px)
// In my opinion it looks much better without it, so the choice is up to you.
                    dialog.getWindow().setBackgroundDrawable(null);
                    dialog.show();

                } catch (WriterException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}