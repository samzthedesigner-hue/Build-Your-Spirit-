package com.buildyourspirit.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Data {
    public static class Confession {
        public long id; public String title, body; public int orderIndex;
        public Long windowStart, windowEnd; public boolean enabled = true;
    }

    private static SQLiteOpenHelper helper;
    private static synchronized SQLiteOpenHelper h(Context c) {
        if (helper == null) helper = new SQLiteOpenHelper(c, "bys.db", null, 1) {
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE confessions(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT,body TEXT,order_index INTEGER,window_start INTEGER," +
                    "window_end INTEGER,enabled INTEGER DEFAULT 1)");
            }
            public void onUpgrade(SQLiteDatabase db, int o, int n) {}
        };
        return helper;
    }

    public static long insert(Context c, Confession x) {
        x.id = h(c).getWritableDatabase().insert("confessions", null, vals(x)); return x.id;
    }
    public static void update(Context c, Confession x) {
        h(c).getWritableDatabase().update("confessions", vals(x), "id=?",
            new String[]{String.valueOf(x.id)});
    }
    public static void delete(Context c, long id) {
        h(c).getWritableDatabase().delete("confessions", "id=?", new String[]{String.valueOf(id)});
    }
    public static List<Confession> all(Context c) {
        List<Confession> out = new ArrayList<>();
        Cursor cur = h(c).getReadableDatabase().query("confessions", null, null, null, null, null, "order_index");
        while (cur.moveToNext()) out.add(from(cur));
        cur.close(); return out;
    }
    private static ContentValues vals(Confession x) {
        ContentValues v = new ContentValues();
        v.put("title", x.title); v.put("body", x.body); v.put("order_index", x.orderIndex);
        v.put("window_start", x.windowStart); v.put("window_end", x.windowEnd);
        v.put("enabled", x.enabled ? 1 : 0); return v;
    }
    private static Confession from(Cursor c) {
        Confession x = new Confession();
        x.id = c.getLong(0); x.title = c.getString(1); x.body = c.getString(2);
        x.orderIndex = c.getInt(3);
        x.windowStart = c.isNull(4) ? null : c.getLong(4);
        x.windowEnd = c.isNull(5) ? null : c.getLong(5);
        x.enabled = c.getInt(6) == 1; return x;
    }

    public static class Prefs {
        private static SharedPreferences sp(Context c) {
            return c.getSharedPreferences("bys", Context.MODE_PRIVATE);
        }
        public static String musicTrack(Context c) { return sp(c).getString("music", "instrumental"); }
        public static void setMusicTrack(Context c, String t) { sp(c).edit().putString("music", t).apply(); }
        public static long lastCheck(Context c) { return sp(c).getLong("lastCheck", 0); }
        public static void setLastCheck(Context c, long t) { sp(c).edit().putLong("lastCheck", t).apply(); }
        public static long updateSeen(Context c) { return sp(c).getLong("updateSeen", 0); }
        public static void setUpdateSeen(Context c, long t) { sp(c).edit().putLong("updateSeen", t).apply(); }
        public static String updateTag(Context c) { return sp(c).getString("updateTag", null); }
        public static void setUpdateTag(Context c, String t) { sp(c).edit().putString("updateTag", t).apply(); }
        public static int activeIndex(Context c) { return sp(c).getInt("activeWord", -1); }
        public static void setActiveIndex(Context c, int i) { sp(c).edit().putInt("activeWord", i).apply(); }
        public static long currentConfessionId(Context c) { return sp(c).getLong("curConf", -1); }
        public static void setCurrentConfessionId(Context c, long id) { sp(c).edit().putLong("curConf", id).apply(); }
    }
  }
