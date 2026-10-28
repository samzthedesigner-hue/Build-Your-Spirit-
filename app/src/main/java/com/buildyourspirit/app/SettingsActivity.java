package com.buildyourspirit.app;

import android.app.*;
import android.os.Bundle;
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
        findViewById(R.id.btn_music).setOnClickListener(v -> music());
        findViewById(R.id.btn_readonly).setOnClickListener(v -> {
            readonly = !readonly;
            Toast.makeText(this, readonly ? "Read-only ON" : "Editing ON", Toast.LENGTH_SHORT).show();
            reload();
        });
        reload();
    }

    private void reload() {
        list.removeAllViews();
        for (Data.Confession c : Data.all(this)) {
            Button btn = new Button(this);
            btn.setText(c.title + (c.windowStart != null ? "  ⏰" : ""));
            btn.setOnClickListener(v -> { if (readonly) view(c); else edit(c); });
            list.addView(btn);
        }
    }

    private void edit(Data.Confession c) {
        ScrollView sc = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL);
        sc.addView(ll);
        EditText t = new EditText(this); t.setHint("Title");
        EditText body = new EditText(this); body.setHint("Confessions / affirmations..."); body.setMinLines(6);
        if (c != null) { t.setText(c.title); body.setText(c.body); }
        ll.addView(t); ll.addView(body);

        final long[] window = { c != null && c.windowStart != null ? c.windowStart : 0,
                                c != null && c.windowEnd != null ? c.windowEnd : 0 };
        Button pickStart = new Button(this); pickStart.setText("Set window start (optional)");
        Button pickEnd = new Button(this); pickEnd.setText("Set window end (optional)");
        pickStart.setOnClickListener(v -> pickDateTime(window[0], ms -> window[0] = ms));
        pickEnd.setOnClickListener(v -> pickDateTime(window[1], ms -> window[1] = ms));
        ll.addView(pickStart); ll.addView(pickEnd);

        new AlertDialog.Builder(this).setTitle(c == null ? "New Confession" : "Edit Confession")
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
            .setNegativeButton("Cancel", null).show();
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
        new AlertDialog.Builder(this).setTitle(c.title).setMessage(c.body).setPositiveButton("Close", null).show();
    }

    private void music() {
        new AlertDialog.Builder(this).setTitle("Music")
            .setItems(new String[]{"Prayer + instrumental","Instrumental only"},
                (d, w) -> Data.Prefs.setMusicTrack(this, w == 0 ? "prayer" : "instrumental")).show();
    }
  }
