package com.buildyourspirit.app;

import android.content.Context;
import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {
    private MediaPlayer mp;
    private final Context ctx;
    private static final float VOL = 0.22f;
    
    public MusicPlayer(Context c) { ctx = c.getApplicationContext(); }

    public void start() {
        stop();
        try {
            List<String> mp3Files = new ArrayList<>();
            String resPath = "res"; 
            String[] rootFiles = ctx.getAssets().list(resPath);
            
            if (rootFiles != null) {
                for (String f : rootFiles) {
                    if (f.toLowerCase().endsWith(".mp3")) {
                        mp3Files.add(resPath + "/" + f);
                    }
                }
            }

            if (mp3Files.isEmpty()) return;

            String trackPref = Data.Prefs.musicTrack(ctx);
            String chosenAsset;
            if ("prayer".equals(trackPref) && mp3Files.size() > 0) {
                chosenAsset = mp3Files.get(0); 
            } else if (mp3Files.size() > 1) {
                chosenAsset = mp3Files.get(1); 
            } else {
                chosenAsset = mp3Files.get(0); 
            }

            File cacheFile = new File(ctx.getCacheDir(), "current_track.mp3");
            try (InputStream in = ctx.getAssets().open(chosenAsset);
                 FileOutputStream out = new FileOutputStream(cacheFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            mp = new MediaPlayer();
            mp.setDataSource(cacheFile.getAbsolutePath());
            mp.setLooping(true);
            mp.setVolume(VOL, VOL);
            mp.prepare();
            mp.start();
            
        } catch (Exception ignored) {}
    }
    
    public void duck() { if (mp != null) mp.setVolume(0.06f, 0.06f); }
    public void unduck() { if (mp != null) mp.setVolume(VOL, VOL); }
    
    public void stop() {
        if (mp != null) { try { mp.stop(); mp.release(); } catch (Exception ignored) {} mp = null; }
    }
}
