package com.brave.tv;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

public final class TvPopupFeedback {
    private long lastBlockMessageAtMs = 0;
    private int blockCount = 0;
    private static final long DEDUPLICATION_WINDOW_MS = 1500;

    public synchronized void showBlocked(WebView webView, String message) {
        showBlocked(message);
    }

    public synchronized void showBlocked(String message) {
        long now = System.currentTimeMillis();
        if (now - lastBlockMessageAtMs <= DEDUPLICATION_WINDOW_MS) {
            blockCount++;
        } else {
            blockCount = 1;
        }
        lastBlockMessageAtMs = now;

        final String finalMessage;
        if (blockCount > 1) {
            finalMessage = blockCount + " popups blocked";
        } else {
            finalMessage = message;
        }

        // Run on UI thread
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            Activity activity = getActiveActivity();
            if (activity != null) {
                View root = activity.findViewById(android.R.id.content);
                if (root != null) {
                    try {
                        Class<?> snackbarClass = Class.forName("com.google.android.material.snackbar.Snackbar");
                        java.lang.reflect.Method makeMethod = snackbarClass.getMethod("make", View.class, CharSequence.class, int.class);
                        java.lang.reflect.Method showMethod = snackbarClass.getMethod("show");
                        Object snackbarInstance = makeMethod.invoke(null, root, finalMessage, 0); // 0 is LENGTH_SHORT
                        showMethod.invoke(snackbarInstance);
                        return;
                    } catch (Exception e) {
                        // Fallback to Toast
                    }
                }
            }
            Context context = getAppContext();
            if (context != null) {
                Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static Activity getActiveActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            java.util.Map<?, ?> activities = (java.util.Map<?, ?>) activitiesField.get(activityThread);
            for (Object activityRecord : activities.values()) {
                Class<?> activityRecordClass = activityRecord.getClass();
                java.lang.reflect.Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(activityRecord)) {
                    java.lang.reflect.Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    return (Activity) activityField.get(activityRecord);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static Context getAppContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplicationMethod = activityThreadClass.getMethod("currentApplication");
            return (Context) currentApplicationMethod.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
