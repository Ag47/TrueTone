package com.comp4905.jasonfleischer.midimusic.model;

import com.comp4905.jasonfleischer.midimusic.views.Key;

import java.util.ArrayList;

public class Finger {
    private ArrayList<Key> keys = new ArrayList<Key>();
    
    public Boolean isPressing(Key key){
        return this.keys.contains(key);
    }

    public void press(Key key){
        
        if(this.isPressing(key))
        {
            return;
        }
        
//        key.onPress();
        this.keys.add(key);
    }
    
    public void lift(){
        for(Key key : keys){
//            key.onRelease();
        }
        keys.clear();
    }

}
