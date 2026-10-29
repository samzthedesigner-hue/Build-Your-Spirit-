package com.buildyourspirit.app;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TtsEngine {

    public interface Callbacks { 
        void onStart(); 
        void onDone(); 
        void onWord(int wordIndex); 
    }

    private final Context appContext;
    private final HandlerThread ttsThread;
    private final Handler handler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TextToSpeech textToSpeech;
    private boolean initialized = false;
    private Callbacks activeCb;
    
    private String[] currentWords = new String[0];
    private String cleanedText = "";

    public TtsEngine(Context context) {
        appContext = context.getApplicationContext();
        ttsThread = new HandlerThread("BuildYourSpirit-TTS");
        ttsThread.start();
        handler = new Handler(ttsThread.getLooper());
    }

    public void init(Runnable onReady) {
        handler.post(() -> {
            if (closed.get()) return;

            try {
                textToSpeech = new TextToSpeech(appContext, status -> handler.post(() -> {
                    if (closed.get()) return;

                    if (status != TextToSpeech.SUCCESS) {
                        if (onReady != null) onReady.run();
                        return;
                    }

                    int r = textToSpeech.setLanguage(Locale.UK);
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech.setLanguage(Locale.ENGLISH);
                    }
                    textToSpeech.setSpeechRate(0.85f);
                    textToSpeech.setPitch(0.95f);
                    
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        public void onStart(String id) { 
                            if (activeCb != null) activeCb.onStart(); 
                        }
                        public void onDone(String id) { 
                            if (activeCb != null) activeCb.onDone(); 
                        }
                        public void onError(String id) { 
                            if (activeCb != null) activeCb.onDone(); 
                        }
                        public void onRangeStart(String id, int start, int end, int frame) {
                            int idx = 0, count = 0;
                            for (int i = 0; i < start && i < cleanedText.length(); i++) {
                                if (cleanedText.charAt(i) == ' ') count++;
                            }
                            idx = count;
                            if (idx >= currentWords.length) idx = currentWords.length - 1;
                            if (activeCb != null) activeCb.onWord(idx);
                        }
                    });

                    initialized = true;
                    if (onReady != null) onReady.run();
                }));
            } catch (Exception e) {
                if (onReady != null) onReady.run();
            }
        });
    }

    public void speak(String text, Callbacks cb) {
        if (text == null || text.trim().isEmpty()) return;

        handler.post(() -> {
            if (closed.get() || !initialized || textToSpeech == null) {
                if (cb != null) cb.onDone();
                return;
            }

            activeCb = cb;
            cleanedText = text.replaceAll("[\"'“”‘’.,;:!?()\\[\\]{}<>/\\\\|@#$%^&*+=~`]", " ")
                               .replaceAll("\\s+", " ").trim();
            currentWords = cleanedText.split(" ");

            try {
                int result = textToSpeech.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "confession_" + System.nanoTime());
                if (result == TextToSpeech.ERROR && cb != null) cb.onDone();
            } catch (Exception e) {
                if (cb != null) cb.onDone();
            }
        });
    }

    public void stop() {
        handler.post(() -> {
            if (!closed.get() && textToSpeech != null) {
                textToSpeech.stop();
            }
        });
    }

    public void shutdown() {
        if (!closed.compareAndSet(false, true)) return;

        handler.post(() -> {
            try {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                    textToSpeech.shutdown();
                    textToSpeech = null;
                }
            } catch (Exception ignored) {}
            finally {
                initialized = false;
                ttsThread.quitSafely();
            }
        });
    }
}
