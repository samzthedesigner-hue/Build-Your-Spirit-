package com.buildyourspirit.app;

import android.app.*;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import java.util.Calendar;

public class SettingsActivity extends Activity {
    private LinearLayout list;
    private boolean readonly = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        list = findViewById(R.id.confession_list);
        
        findViewById(R.id.btn_add).setOnClickListener(v -> edit(null));
        findViewById(R.id.btn_readonly).setOnClickListener(v -> {
            readonly = !readonly;
            Toast.makeText(this, readonly ? "Read-only ON" : "Editing ON", Toast.LENGTH_SHORT).show();
            reload();
        });

        findViewById(R.id.btn_download_prayer).setOnClickListener(v -> openUrl("https://pixabay.com/music/search/prayer%20instrumental/"));
        findViewById(R.id.btn_download_worship).setOnClickListener(v -> openUrl("https://pixabay.com/music/search/worship%20instrumental/"));
        findViewById(R.id.btn_download_tongues).setOnClickListener(v -> openUrl("https://pixabay.com/music/search/praying%20in%20tongues/"));

        findViewById(R.id.btn_pick_music).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            startActivityForResult(intent, 202);
        });

        findViewById(R.id.btn_auto_recent).setOnClickListener(v -> autoLoadRecentMusic());
        
        reload();
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show();
        }
    }

    private void autoLoadRecentMusic() {
        try {
            long oneDayAgo = (System.currentTimeMillis() / 1000) - 86400;
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_ADDED
            };
            String selection = MediaStore.Audio.Media.DATE_ADDED + " > ?";
            String[] args = { String.valueOf(oneDayAgo) };
            String sort = MediaStore.Audio.Media.DATE_ADDED + " DESC";

            Cursor cursor = getContentResolver().query(collection, projection, selection, args, sort);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                cursor.close();

                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                Data.Prefs.setMusicDeviceUri(this, contentUri.toString());
                Data.Prefs.setMusicTrack(this, "device");
                Toast.makeText(this, "Loaded: " + name, Toast.LENGTH_LONG).show();
            } else {
                if (cursor != null) cursor.close();
                Toast.makeText(this, "No recent MP3 found. Download one first, then try again.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not scan music library", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 202 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (Exception ignored) {}
                Data.Prefs.setMusicDeviceUri(this, uri.toString());
                Data.Prefs.setMusicTrack(this, "device");
                Toast.makeText(this, "Background music set!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void reload() {
        list.removeAllViews();
        for (Data.Confession c : Data.all(this)) {
            Button btn = new Button(this);
            btn.setText(c.title + (c.windowStart != null ? "  ⏰" : ""));
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF172A45));
            btn.setTextColor(0xFFFFFFFF);
            btn.setOnClickListener(v -> { if (readonly) view(c); else edit(c); });
            list.addView(btn);
        }
    }

    private void edit(Data.Confession c) {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#0A192F")); // Dark background for dialog
        
        LinearLayout ll = new LinearLayout(this); 
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(32, 32, 32, 32);
        sc.addView(ll);
        
        EditText t = new EditText(this); 
        t.setHint("Title"); 
        t.setTextColor(Color.WHITE); 
        t.setHintTextColor(Color.GRAY);
        
        EditText body = new EditText(this); 
        body.setHint("Confessions / affirmations..."); 
        body.setMinLines(6); 
        body.setTextColor(Color.WHITE); 
        body.setHintTextColor(Color.GRAY);
        
        if (c != null) { t.setText(c.title); body.setText(c.body); }
        ll.addView(t); 
        ll.addView(body);

        final long[] window = { c != null && c.windowStart != null ? c.windowStart : 0,
                                c != null && c.windowEnd != null ? c.windowEnd : 0 };
                                
        Button pickStart = new Button(this); 
        pickStart.setText("Set window start (optional)"); 
        pickStart.setTextColor(Color.WHITE);
        pickStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF172A45));
        
        Button pickEnd = new Button(this); 
        pickEnd.setText("Set window end (optional)"); 
        pickEnd.setTextColor(Color.WHITE);
        pickEnd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF172A45));
        
        pickStart.setOnClickListener(v -> pickDateTime(window[0], ms -> window[0] = ms));
        pickEnd.setOnClickListener(v -> pickDateTime(window[1], ms -> window[1] = ms));
        ll.addView(pickStart); 
        ll.addView(pickEnd);

        // Use a dark themed AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(c == null ? "New Confession" : "Edit Confession")
            .setView(sc)
            .setPositiveButton("Save", (d, w) -> {
                Data.Confession x = c != null ? c : new Data.Confession();
                x.title = t.getText().toString();
                x.body = body.getText().toString();
                x.windowStart = window[0] == 0 ? null : window[0];
                x.windowEnd = window[1] == 0 ? null : window[1];
                if (c == null) Data.insert(this, x); else Data.update(this, x);
                Scheduler.scheduleAll(this);
                reload();
            })
            .setNeutralButton("Delete", (d, w) -> {
                if (c != null) { Data.delete(this, c.id); Scheduler.scheduleAll(this); reload(); }
            })
            .setNegativeButton("Cancel", null).create();
            
        dialog.show();
    }

    interface MsPick { void onPick(long ms); }
    private void pickDateTime(long initial, MsPick pick) {
        Calendar cal = Calendar.getInstance();
        if (initial > 0) cal.setTimeInMillis(initial);
        new DatePickerDialog(this, (dp, y, m, d) -> {
            Calendar c2 = Calendar.getInstance(); c2.set(y, m, d);
            new TimePickerDialog(this, (tp, hh, mm) -> {
                c2.set(Calendar.HOUR_OF_DAY, hh); c2.set(Calendar.MINUTE, mm); c2.set(Calendar.SECOND, 0);
                pick.onPick(c2.getTimeInMillis());
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void view(Data.Confession c) {
        new AlertDialog.Builder(this)
            .setTitle(c.title)
            .setMessage(c.body)
            .setPositiveButton("Close", null)
            .show();
    }
}
