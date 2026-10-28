package com.buildyourspirit.app;

import android.app.*;
import android.content.*;

public class Scheduler {
    public static void scheduleAll(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        for (Data.Confession c : Data.all(ctx)) {
            if (c.windowStart==null || c.windowEnd==null) continue;
            if (c.windowEnd < System.currentTimeMillis()) continue;
            set(am, ctx, c.id, c.windowStart, true);
            set(am, ctx, c.id, c.windowEnd, false);
        }
    }
    private static void set(AlarmManager am, Context c, long id, long at, boolean start) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("id", id).putExtra("start", start);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int)(id*10+(start?1:0)), i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent i) {
            boolean start = i.getBooleanExtra("start", false);
            Intent s = new Intent(c, PlaybackService.class).setAction(start?PlaybackService.ACTION_PLAY:PlaybackService.ACTION_STOP);
            if (start) c.startForegroundService(s); else c.startService(s);
        }
    }
    public static class BootReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent i) { if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) scheduleAll(c); }
    }
}
