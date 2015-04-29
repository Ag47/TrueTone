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

package com.example.android.navigationdrawer;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.comp4905.jasonfleischer.midimusic.R;

/**
 * @class MidiSheetMusicActivity
 * This is the launch activity for MidiSheetMusic.
 * It simply displays the splash screen, and a button to choose a song.
 */
public class PlayMidi extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadImages();
        setContentView(R.layout.activity_play_midi);
        Button button = (Button) findViewById(R.id.choose_song);
        button.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        chooseSong();
                    }
                }
        );
    }

    /**
     * Start the ChooseSongActivity when the "Choose Song" button is clicked
     */
    private void chooseSong() {
//        Intent intent = new Intent(this, ChooseSongActivity.class);
//        startActivity(intent);
    }

    /**
     * Load all the resource images
     */
    private void loadImages() {
//        ClefSymbol.LoadImages(this);
//        TimeSigSymbol.LoadImages(this);
//        MidiPlayer.LoadImages(this);
    }

    /**
     * Always use landscape mode for this activity.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}

