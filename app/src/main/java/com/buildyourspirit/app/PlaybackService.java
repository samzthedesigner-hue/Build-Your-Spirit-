package com.buildyourspirit.app;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import java.util.List;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY="p", ACTION_PAUSE="pa", ACTION_STOP="s",
        ACTION_NEXT="n", ACTION_BACK="b";
    private static final String CH="bys"; private static final int ID=42;

    private HandlerThread workerThread;
    private Handler worker;
    private Handler mainHandler;

    private TtsEngine tts; 
    private MediaPlayer music;
    
    private List<Data.Confession> list; 
    private int idx=0;
    private boolean paused=false; 
    private long windowEndAt=Long.MAX_VALUE;
    private boolean foregroundStarted = false;
    private boolean destroyed = false;

    @Override public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        workerThread = new HandlerThread("BuildYourSpirit-Playback");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH,"Playback",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        startForegroundSafely();
        
        // Initialize TTS on the worker thread
        worker.post(() -> {
            tts = new TtsEngine(this);
            tts.init(() -> {});
        });
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        if (i == null) return START_NOT_STICKY;
        String a = i.getAction();

        if (ACTION_STOP.equals(a)) { stopPlayback(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(a)) { 
            paused=true; 
            if (tts != null) tts.stop(); 
            duckMusic(); 
            return START_STICKY; 
        }
        if (ACTION_PLAY.equals(a)) {
            if (paused) { paused=false; unduckMusic(); readCurrent(); }
            else beginPlayback();
            return START_STICKY;
        }
        if (ACTION_NEXT.equals(a)) { nextConfession(); return START_STICKY; }
        if (ACTION_BACK.equals(a)) { readCurrent(); return START_STICKY; }
        
        beginPlayback(); 
        return START_STICKY;
    }

    private void startForegroundSafely() {
        if (foregroundStarted) return;
        Notification n = notif();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, n);
        }
        foregroundStarted = true;
    }

    private void beginPlayback() {
        worker.post(() -> {
            if (destroyed) return;
            list = Data.all(this);
            if (list.isEmpty()) { stopPlayback(); return; }
            
            idx=0; paused=false;
            Data.Confession c = list.get(0);
            windowEndAt = c.windowEnd != null ? c.windowEnd : Long.MAX_VALUE;
            Data.Prefs.setCurrentConfessionId(this, c.id);
            
            prepareMusic();
            readCurrent();
        });
    }

    private void readCurrent() {
        if (destroyed || list==null || idx>=list.size()) { stopPlayback(); return; }
        final Data.Confession c = list.get(idx);
        Data.Prefs.setCurrentConfessionId(this, c.id);
        
        if (tts != null) {
            tts.speak(c.body, new TtsEngine.Callbacks() {
                public void onStart() { duckMusic(); }
                public void onWord(int wi) { Data.Prefs.setActiveIndex(PlaybackService.this, wi); }
                public void onDone() {
                    unduckMusic();
                    if (paused || destroyed) return;
                    if (System.currentTimeMillis() >= windowEndAt) {
                        Data.Prefs.setActiveIndex(PlaybackService.this, -1);
                        stopPlayback(); 
                        return;
                    }
                    readCurrent();
                }
            });
        }
    }

    private void prepareMusic() {
        String uriStr = Data.Prefs.musicDeviceUri(this);
        if (uriStr == null) return; // User skipped music
        
        try {
            music = new MediaPlayer();
            music.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            
            music.setDataSource(getApplicationContext(), Uri.parse(uriStr));
            music.setLooping(true);
            music.setVolume(0.22f, 0.22f);
            
            music.setOnPreparedListener(mp -> { if (!destroyed) mp.start(); });
            music.setOnErrorListener((mp, what, extra) -> { releaseMusic(); return true; });
            music.prepareAsync();
        } catch (Exception e) {
            releaseMusic();
        }
    }

    private void duckMusic() { if (music != null) { try { music.setVolume(0.06f, 0.06f); } catch (Exception e) {} } }
    private void unduckMusic() { if (music != null) { try { music.setVolume(0.22f, 0.22f); } catch (Exception e) {} } }
    
    private void releaseMusic() {
        if (music != null) {
            try { music.reset(); music.release(); } catch (Exception ignored) {}
            music = null;
        }
    }

    private void nextConfession() {
        if (list==null) return;
        long now = System.currentTimeMillis();
        for (int k = idx+1; k < list.size(); k++) {
            Data.Confession c = list.get(k);
            if (c.windowStart != null && now >= c.windowStart && (c.windowEnd==null || now <= c.windowEnd)) {
                idx=k; windowEndAt = c.windowEnd != null ? c.windowEnd : Long.MAX_VALUE;
                readCurrent(); return;
            }
            if (c.windowStart == null) {
                idx=k; windowEndAt = Long.MAX_VALUE; readCurrent(); return;
            }
        }
        stopPlayback();
    }

    private Notification notif() {
        Intent o = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,o,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b = new Notification.Builder(this, CH);
        else b = new Notification.Builder(this);

        return b.setContentTitle("Build Your Spirit").setContentText("Confession in progress")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) 
            .setContentIntent(pi).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Back", act(ACTION_BACK)).build())
            .addAction(new Notification.Action.Builder(null, paused?"Play":"Pause", act(paused?ACTION_PLAY:ACTION_PAUSE)).build())
            .addAction(new Notification.Action.Builder(null,"Next", act(ACTION_NEXT)).build())
            .build();
    }
    
    private PendingIntent act(String a) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        return PendingIntent.getService(this,a.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private void stopPlayback() {
        worker.post(() -> {
            if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
            releaseMusic();
            mainHandler.post(() -> stopSelf());
        });
    }

    @Override public void onDestroy() {
        destroyed = true;
        if (tts != null) { tts.shutdown(); }
        releaseMusic();
        if (workerThread != null) workerThread.quitSafely();
        if (foregroundStarted) stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
