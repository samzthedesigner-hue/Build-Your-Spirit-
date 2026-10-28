package com.buildyourspirit.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
    private TextView lyrics;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        
        // Request Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }

        lyrics = findViewById(R.id.lyrics);
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_play).setOnClickListener(v -> send(PlaybackService.ACTION_PLAY));
        findViewById(R.id.btn_pause).setOnClickListener(v -> send(PlaybackService.ACTION_PAUSE));
        findViewById(R.id.btn_next).setOnClickListener(v -> send(PlaybackService.ACTION_NEXT));
        findViewById(R.id.btn_back).setOnClickListener(v -> send(PlaybackService.ACTION_BACK));
        Update.maybeCheck(this);
        ticker();
    }

    private void ticker() {
        h.postDelayed(new Runnable() {
            public void run() { render(); h.postDelayed(this, 200); }
        }, 200);
    }

    private void render() {
        long curId = Data.Prefs.currentConfessionId(this);
        List<Data.Confession> list = Data.all(this);
        if (list.isEmpty()) { lyrics.setText("No confessions yet. Add one in Settings."); return; }
        Data.Confession current = null;
        for (Data.Confession c : list) if (c.id == curId) { current = c; break; }
        if (current == null) current = list.get(0);
        String text = current.body == null ? "" : current.body;
        String cleaned = text.replaceAll("[\"'“”‘’.,;:!?()\\[\\]{}<>/\\\\|@#$%^&*+=~`]", " ")
                             .replaceAll("\\s+", " ").trim();
        String[] words = cleaned.split(" ");
        int active = Data.Prefs.activeIndex(this);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < words.length; i++) {
            int start = sb.length();
            sb.append(words[i]);
            if (i == active) sb.setSpan(new BackgroundColorSpan(0xFFBBDEFB), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(" ");
        }
        lyrics.setText(sb);
    }

    private void send(String a) {
        try {
            Intent i = new Intent(this, PlaybackService.class).setAction(a);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            // Prevents crash if the app is in the background and can't start the service
            e.printStackTrace();
        }
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
