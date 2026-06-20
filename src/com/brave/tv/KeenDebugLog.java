package com.brave.tv;

import android.util.Log;

/**
 * Ring buffer storing the last 50 firewall decisions.
 * Each entry records what Keen blocked, allowed, or escalated and why.
 * Queryable via: adb logcat -s KeenFirewall
 */
public final class KeenDebugLog {
    private static final String TAG = "KeenFirewall";
    private static final int CAPACITY = 50;

    private static final String[] timestamps = new String[CAPACITY];
    private static final String[] origins = new String[CAPACITY];
    private static final String[] actions = new String[CAPACITY];
    private static final String[] decisions = new String[CAPACITY];
    private static final String[] reasons = new String[CAPACITY];
    private static final int[] riskScores = new int[CAPACITY];
    private static int head = 0;
    private static int count = 0;

    private KeenDebugLog() {}

    public static void record(String origin, String action, String decision, String reason, int riskScore) {
        String ts = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
        timestamps[head] = ts;
        origins[head] = origin != null ? origin : "";
        actions[head] = action != null ? action : "";
        decisions[head] = decision != null ? decision : "";
        reasons[head] = reason != null ? reason : "";
        riskScores[head] = riskScore;

        Log.i(TAG, decision + " | " + action
                + " | origin=" + origin
                + " | reason=" + reason
                + " | risk=" + riskScore);

        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    /** Convenience methods for common decisions. */
    public static void allowed(String origin, String action, String reason, int riskScore) {
        record(origin, action, "ALLOW", reason, riskScore);
    }

    public static void blocked(String origin, String action, String reason, int riskScore) {
        record(origin, action, "BLOCK", reason, riskScore);
    }

    public static void redirected(String origin, String action, String reason, int riskScore) {
        record(origin, action, "REDIRECT_CURRENT_TAB", reason, riskScore);
    }

    /** Returns the total number of events recorded (may exceed CAPACITY). */
    public static int getCount() {
        return count;
    }

    /** Dumps the last N entries to logcat. */
    public static void dumpRecent(int n) {
        int total = Math.min(n, count);
        int start = (head - total + CAPACITY) % CAPACITY;
        for (int i = 0; i < total; i++) {
            int idx = (start + i) % CAPACITY;
            Log.i(TAG, "[" + timestamps[idx] + "] "
                    + decisions[idx] + " | " + actions[idx]
                    + " | origin=" + origins[idx]
                    + " | reason=" + reasons[idx]
                    + " | risk=" + riskScores[idx]);
        }
    }
}
