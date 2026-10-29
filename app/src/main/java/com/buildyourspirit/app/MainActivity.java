package com.buildyourspirit.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class MainActivity extends Activity {
    private TextView lyrics;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        
        // 1. SET THE CRASH CATCHER IMMEDIATELY
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    String stackTrace = sw.toString();

                    // Save error to a file so we can read it on next launch
                    File file = new File(getFilesDir(), "crash_log.txt");
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(stackTrace.getBytes());
                    fos.close();
                } catch (Exception ignored) {}

                // Kill the app
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        setContentView(R.layout.activity_main);

        // 2. CHECK IF WE CRASHED LAST TIME AND SHOW THE ERROR
        File crashFile = new File(getFilesDir(), "crash_log.txt");
        if (crashFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(crashFile);
                byte[] data = new byte[(int) crashFile.length()];
                fis.read(data);
                fis.close();
                String error = new String(data);
                crashFile.delete(); // Delete so it doesn't show again

                new AlertDialog.Builder(this)
                        .setTitle("Crash Cause Found!")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .setCancelable(false)
                        .show();
            } catch (Exception ignored) {}
        }

        // 3. PERMISSIONS & UI SETUP
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
        try {
            long curId = Data.Prefs.currentConfessionId(this);
            List<Data.Confession> list = Data.all(this);
            
            if (list.isEmpty()) { 
                lyrics.setText("Tap Settings to add confessions."); 
                return; 
            }
            
            Data.Confession current = null;
            for (Data.Confession c : list) if (c.id == curId) { current = c; break; }
            if (current == null) current = list.get(0);
            
            String text = current.body == null ? "" : current.body;
            String cleaned = text.replaceAll("[\"'“”‘’.,;:!?()\\[\\]{}<>/\\\\|@#$%^&*+=~`]", " ")
                                 .replaceAll("\\s+", " ").trim();
            String[] words = cleaned.split(" ");
            
            int active = Data.Prefs.activeIndex(this);
            
            if (active >= 0 && active < words.length) {
                lyrics.setText(words[active]);
            } else {
                lyrics.setText("Press Play");
            }
        } catch (Exception e) {
            lyrics.setText("Waiting for text...");
        }
    }

    private void send(String a) {
        try {
            Intent i = new Intent(this, PlaybackService.class).setAction(a);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (final Exception e) {
            // Catch immediate start failures and show on screen
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Play Error")
                    .setMessage(e.toString())
                    .setPositiveButton("OK", null)
                    .show();
            });
        }
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
