package com.buildyourspirit.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.List;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY="p", ACTION_PAUSE="pa", ACTION_STOP="s",
        ACTION_NEXT="n", ACTION_BACK="b";
    private static final String CH="bys"; private static final int ID=42;

    private TtsEngine tts; private MusicPlayer music;
    private List<Data.Confession> list; private int idx=0;
    private boolean paused=false; private long windowEndAt=Long.MAX_VALUE;

    @Override public void onCreate() {
        super.onCreate();
        music = new MusicPlayer(this);
        tts = new TtsEngine(this);
        NotificationChannel ch = new NotificationChannel(CH,"Playback",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        String a = i != null ? i.getAction() : ACTION_PLAY;
        if (ACTION_STOP.equals(a)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(a)) { paused=true; tts.stop(); music.duck(); return START_STICKY; }
        if (ACTION_PLAY.equals(a)) {
            if (paused) { paused=false; music.unduck(); readCurrent(); }
            else begin();
            return START_STICKY;
        }
        if (ACTION_NEXT.equals(a)) { nextConfession(); return START_STICKY; }
        if (ACTION_BACK.equals(a)) { readCurrent(); return START_STICKY; }
        begin(); return START_STICKY;
    }

    private void begin() {
        startForeground(ID, notif());
        list = Data.all(this);
        if (list.isEmpty()) { stopSelf(); return; }
        idx=0; paused=false;
        Data.Confession c = list.get(0);
        windowEndAt = c.windowEnd != null ? c.windowEnd : Long.MAX_VALUE;
        Data.Prefs.setCurrentConfessionId(this, c.id);
        music.start(); readCurrent();
    }

    private void readCurrent() {
        if (list==null || idx>=list.size()) { stopSelf(); return; }
        final Data.Confession c = list.get(idx);
        Data.Prefs.setCurrentConfessionId(this, c.id);
        tts.speak(c.body, new TtsEngine.Callbacks() {
            public void onStart() { music.duck(); }
            public void onWord(int wi) { Data.Prefs.setActiveIndex(PlaybackService.this, wi); }
            public void onDone() {
                music.unduck();
                if (paused) return;
                if (System.currentTimeMillis() >= windowEndAt) {
                    Data.Prefs.setActiveIndex(PlaybackService.this, -1);
                    stopSelf(); return;
                }
                readCurrent();
            }
        });
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
        stopSelf();
    }

    private Notification notif() {
        Intent o = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,o,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH)
            .setContentTitle("Build Your Spirit").setContentText("Confession in progress")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Back", act(ACTION_BACK)).build())
            .addAction(new Notification.Action.Builder(null, paused?"Play":"Pause",
                act(paused?ACTION_PLAY:ACTION_PAUSE)).build())
            .addAction(new Notification.Action.Builder(null,"Next", act(ACTION_NEXT)).build())
            .build();
    }
    private PendingIntent act(String a) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        return PendingIntent.getService(this,a.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onDestroy() {
        if (tts!=null) tts.shutdown();
        if (music!=null) music.stop();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
          }
