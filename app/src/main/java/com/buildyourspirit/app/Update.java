package com.buildyourspirit.app;

import android.app.*;
import android.content.*;
import android.net.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;

public class Update {
    private static final long CHECK = 60L*24*60*60*1000;
    private static final long FORCE = 7L*24*60*60*1000;
    private static final String API =
        "https://api.github.com/repos/samzthedesigner-hue/Build-Your-Spirit/releases/latest";
    private static final String APK =
        "https://github.com/samzthedesigner-hue/Build-Your-Spirit/releases/latest/download/app-debug.apk";

    public static void maybeCheck(Context c) {
        prompt(c);
        if (System.currentTimeMillis() - Data.Prefs.lastCheck(c) < CHECK) return;
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) return;
        new Thread(() -> check(c)).start();
    }

    private static void check(Context c) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(API).openConnection();
            h.setRequestProperty("Accept","application/vnd.github+json");
            h.setConnectTimeout(8000); h.setReadTimeout(8000);
            if (h.getResponseCode()!=200) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream()))) {
                String l; while ((l=r.readLine())!=null) sb.append(l);
            }
            String body = sb.toString();
            Matcher mt = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (!mt.find()) return;
            String tag = mt.group(1);
            boolean hasApk = Pattern.compile("\"name\"\\s*:\\s*\"[^\"]*\\.apk\"").matcher(body).find();
            if (!hasApk) return;
            if (tag.equals(Data.Prefs.updateTag(c))) { prompt(c); return; }
            Data.Prefs.setUpdateTag(c, tag);
            Data.Prefs.setUpdateSeen(c, System.currentTimeMillis());
            Data.Prefs.setLastCheck(c, System.currentTimeMillis());
            prompt(c);
        } catch (Exception ignored) {}
    }

    private static void prompt(Context c) {
        long seen = Data.Prefs.updateSeen(c);
        if (seen == 0) return;
        String tag = Data.Prefs.updateTag(c);
        if (tag == null) return;
        boolean forced = System.currentTimeMillis() - seen >= FORCE;
        AlertDialog.Builder b = new AlertDialog.Builder(c)
            .setTitle(forced ? "Update required" : "Update available: " + tag)
            .setMessage(forced
                ? "Your 7-day update window has ended. Please install " + tag + " to continue."
                : "A new version of Build Your Spirit is available. Install within 7 days.")
            .setPositiveButton(forced ? "Update Now" : "Update", (d,w)->download(c));
        if (!forced) b.setNegativeButton("Later", null);
        else b.setCancelable(false);
        b.show();
    }

    private static void download(Context c) {
        try {
            DownloadManager dm = c.getSystemService(DownloadManager.class);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(APK));
            r.setDestinationInExternalFilesDir(c, android.os.Environment.DIRECTORY_DOWNLOADS,
                "BuildYourSpirit-update.apk");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            dm.enqueue(r);
        } catch (Exception ignored) {}
    }
}
