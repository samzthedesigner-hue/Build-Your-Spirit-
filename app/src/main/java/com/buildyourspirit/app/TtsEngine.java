package com.buildyourspirit.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

public class TtsEngine {
    public interface Callbacks { void onStart(); void onDone(); void onWord(int wordIndex); }

    private TextToSpeech tts;
    private boolean ready = false;
    private Callbacks activeCb;
    private String[] currentWords = new String[0];
    private String cleaned = "";

    public TtsEngine(Context c) {
        tts = new TextToSpeech(c.getApplicationContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                int r = tts.setLanguage(Locale.UK);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.ENGLISH);
                }
                tts.setSpeechRate(0.85f);
                tts.setPitch(0.95f);
            }
        });
    }

    public boolean isReady() { return ready; }

    public void speak(String text, Callbacks cb) {
        if (!ready || tts == null) { if (cb != null) cb.onDone(); return; }
        activeCb = cb;
        cleaned = text.replaceAll("[\"'“”‘’.,;:!?()\\[\\]{}<>/\\\\|@#$%^&*+=~`]", " ")
                       .replaceAll("\\s+", " ").trim();
        currentWords = cleaned.split(" ");

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String id) { if (activeCb != null) activeCb.onStart(); }
            public void onDone(String id) { if (activeCb != null) activeCb.onDone(); }
            public void onError(String id) { if (activeCb != null) activeCb.onDone(); }
            public void onRangeStart(String id, int start, int end, int frame) {
                int idx = 0, count = 0;
                for (int i = 0; i < start && i < cleaned.length(); i++) {
                    if (cleaned.charAt(i) == ' ') count++;
                }
                idx = count;
                if (idx >= currentWords.length) idx = currentWords.length - 1;
                if (activeCb != null) activeCb.onWord(idx);
            }
        });
        tts.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "u");
    }

    public void stop() { if (tts != null) tts.stop(); }
    public void shutdown() { if (tts != null) { tts.stop(); tts.shutdown(); } }
}
