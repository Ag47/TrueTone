package com.example.android.navigationdrawer;import android.content.Context;import android.media.AudioManager;import android.media.SoundPool;import android.os.Handler;import java.util.HashMap;import java.util.Vector;public class SoundManager {    private SoundPool pSoundPool;    private HashMap<Integer, Integer> pSoundPoolMap;    private AudioManager pAudioManager;    private Context pContext;    private Vector<Integer> pAvailibleSounds = new Vector<Integer>();    private Vector<Integer> pKillSoundQueue = new Vector<Integer>();    private Handler pHandler = new Handler();    public SoundManager() {    }    public void initSounds(Context theContext) {        pContext = theContext;        pSoundPool = new SoundPool(20, AudioManager.STREAM_MUSIC, 0);        pSoundPoolMap = new HashMap<Integer, Integer>();        pAudioManager = (AudioManager) pContext.getSystemService(Context.AUDIO_SERVICE);    }    public void addSound(int Index, int SoundID) {        pAvailibleSounds.add(Index);        pSoundPoolMap.put(Index, pSoundPool.load(pContext, SoundID, 1));    }    public void playSound(int index) {        // dont have a sound for this obj, return.        if (pAvailibleSounds.contains(index)) {            int streamVolume = pAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);            int soundId = pSoundPool.play(pSoundPoolMap.get(index), streamVolume, streamVolume, 1, 0, 1f);            pKillSoundQueue.add(soundId);            // schedule the current sound to stop after set milliseconds//            pHandler.postDelayed(new Runnable() {//                public void run() {//                    if (!pKillSoundQueue.isEmpty()) {//                        pSoundPool.stop(pKillSoundQueue.firstElement());//                    }//                }//            }, 10);        }    }}