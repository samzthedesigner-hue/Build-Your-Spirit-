package com.buildyourspirit.app;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

public class MusicPlayer {
    private MediaPlayer mp;
    private final Context ctx;
    private static final float VOL = 0.22f;
    
    public MusicPlayer(Context c) { ctx = c.getApplicationContext(); }

    public void start() {
        stop();
        String uriStr = Data.Prefs.musicDeviceUri(ctx);
        if (uriStr == null) return; // User skipped music, play voice only
        
        try {
            mp = new MediaPlayer();
            mp.setDataSource(ctx, Uri.parse(uriStr));
            mp.setLooping(true);
            mp.setVolume(VOL, VOL);
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            stop(); // Fail safely
        }
    }
    
    public void duck() { if (mp != null) { try { mp.setVolume(0.06f, 0.06f); } catch (Exception e) {} } }
    public void unduck() { if (mp != null) { try { mp.setVolume(VOL, VOL); } catch (Exception e) {} } }
    
    public void stop() {
        if (mp != null) { 
            try { mp.stop(); mp.release(); } catch (Exception ignored) {} 
            mp = null; 
        }
    }
}
