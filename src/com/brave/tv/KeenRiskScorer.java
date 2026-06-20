package com.brave.tv;

import android.net.Uri;
import android.util.Log;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-origin risk scoring engine.
 *
 * Accumulates behaviour signals from native hooks and JS prelude reports,
 * then returns a threat level that governs how strictly the firewall treats
 * navigation requests from that origin.
 *
 * Threat levels:
 *   0–3  NORMAL   — allow, force noopener on cross-site
 *   4–6  WATCH    — allow visible links, block hidden actions
 *   7–10 RESTRICT — block popups, ask for external navigation
 *   11+  STRICT   — block all non-same-origin actions
 */
public final class KeenRiskScorer {
    private static final String TAG = "KeenFirewall";

    public static final int LEVEL_NORMAL   = 0;
    public static final int LEVEL_WATCH    = 4;
    public static final int LEVEL_RESTRICT = 7;
    public static final int LEVEL_STRICT   = 11;

    // Signal weights
    private static final int SCORE_GLOBAL_CLICK_LISTENER    = 2;
    private static final int SCORE_WINDOW_OPEN_NO_VISIBLE   = 3;
    private static final int SCORE_ABOUT_BLANK_TRAMPOLINE   = 4;
    private static final int SCORE_HIDDEN_BLANK_ANCHOR      = 3;
    private static final int SCORE_INTENT_ESCAPE            = 4;
    private static final int SCORE_OPENER_REWRITE           = 4;
    private static final int SCORE_PUSHSTATE_SPAM           = 2;
    private static final int SCORE_BACK_TRAP                = 3;
    private static final int SCORE_CROSS_SITE_POPUP_HIDDEN  = 3;
    private static final int SCORE_RAPID_POPUP_VELOCITY     = 2;

    /** Per-origin accumulated scores. Keyed by root domain. */
    private static final Map<String, OriginState> sOrigins = new HashMap<>();

    /** Maximum number of tracked origins before pruning oldest. */
    private static final int MAX_ORIGINS = 100;

    private KeenRiskScorer() {}

    // ---- Signal recording ----

    public static void signalGlobalClickListener(String origin) {
        addScore(origin, SCORE_GLOBAL_CLICK_LISTENER, "global-click-listener");
    }

    public static void signalWindowOpenNoVisible(String origin) {
        addScore(origin, SCORE_WINDOW_OPEN_NO_VISIBLE, "window-open-no-visible");
    }

    public static void signalAboutBlankTrampoline(String origin) {
        addScore(origin, SCORE_ABOUT_BLANK_TRAMPOLINE, "about-blank-trampoline");
    }

    public static void signalHiddenBlankAnchor(String origin) {
        addScore(origin, SCORE_HIDDEN_BLANK_ANCHOR, "hidden-blank-anchor");
    }

    public static void signalIntentEscape(String origin) {
        addScore(origin, SCORE_INTENT_ESCAPE, "intent-escape");
    }

    public static void signalOpenerRewrite(String origin) {
        addScore(origin, SCORE_OPENER_REWRITE, "opener-rewrite");
    }

    public static void signalPushStateSpam(String origin) {
        addScore(origin, SCORE_PUSHSTATE_SPAM, "pushstate-spam");
    }

    public static void signalBackTrap(String origin) {
        addScore(origin, SCORE_BACK_TRAP, "back-trap");
    }

    public static void signalCrossSitePopupHidden(String origin) {
        addScore(origin, SCORE_CROSS_SITE_POPUP_HIDDEN, "cross-site-popup-hidden");
    }

    public static void signalRapidPopupVelocity(String origin) {
        addScore(origin, SCORE_RAPID_POPUP_VELOCITY, "rapid-popup-velocity");
    }

    // ---- Query ----

    /** Returns the accumulated risk score for an origin. */
    public static int getScore(String origin) {
        String root = rootDomain(origin);
        OriginState state = sOrigins.get(root);
        return state != null ? state.score : 0;
    }

    /** Returns whether the origin is at or above the given threat level. */
    public static boolean isAtLevel(String origin, int level) {
        return getScore(origin) >= level;
    }

    /** Returns the human-readable threat level name. */
    public static String getLevelName(String origin) {
        int score = getScore(origin);
        if (score >= LEVEL_STRICT)   return "STRICT";
        if (score >= LEVEL_RESTRICT) return "RESTRICT";
        if (score >= LEVEL_WATCH)    return "WATCH";
        return "NORMAL";
    }

    /** Resets score for an origin (e.g. user explicitly trusts it). */
    public static void resetOrigin(String origin) {
        sOrigins.remove(rootDomain(origin));
    }

    /** Decays all scores by half. Called periodically or on session boundaries. */
    public static void decayAll() {
        for (OriginState state : sOrigins.values()) {
            state.score = state.score / 2;
        }
    }

    // ---- Internal ----

    private static void addScore(String origin, int points, String signal) {
        String root = rootDomain(origin);
        if (root.isEmpty()) return;

        OriginState state = sOrigins.get(root);
        if (state == null) {
            if (sOrigins.size() >= MAX_ORIGINS) {
                pruneOldest();
            }
            state = new OriginState();
            sOrigins.put(root, state);
        }
        state.score += points;
        state.lastSignalTime = System.currentTimeMillis();
        state.signalCount++;

        Log.d(TAG, "RiskScore: origin=" + root
                + " signal=" + signal
                + " +" + points
                + " total=" + state.score
                + " level=" + getLevelName(origin));
    }

    private static void pruneOldest() {
        String oldest = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, OriginState> entry : sOrigins.entrySet()) {
            if (entry.getValue().lastSignalTime < oldestTime) {
                oldestTime = entry.getValue().lastSignalTime;
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            sOrigins.remove(oldest);
        }
    }

    /** Extracts root domain from a URL or host string. */
    static String rootDomain(String urlOrHost) {
        if (urlOrHost == null) return "";
        try {
            String host = urlOrHost;
            if (urlOrHost.contains("://")) {
                Uri uri = Uri.parse(urlOrHost);
                host = uri.getHost();
            }
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT).trim();
            if (host.startsWith("www.")) host = host.substring(4);
            String[] parts = host.split("\\.");
            if (parts.length < 2) return host;
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        } catch (Exception e) {
            return "";
        }
    }

    private static final class OriginState {
        int score;
        long lastSignalTime;
        int signalCount;
    }
}
