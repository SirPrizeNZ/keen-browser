package com.brave.tv;

import java.util.Locale;

public final class TvIntentLedger {
    private long lastNativeActionAtMs;
    private long lastJsAnchorAtMs;
    private String lastJsAnchorHref;
    private long lastJsNonAnchorAtMs;
    private static final long INTENT_WINDOW_MS = 1000;

    public void recordNativeAction() {
        lastNativeActionAtMs = System.currentTimeMillis();
    }

    public void recordJsAnchorIntent(String href, long pageTimeMs) {
        lastJsAnchorAtMs = System.currentTimeMillis();
        lastJsAnchorHref = href;
    }

    public void recordJsNonAnchorIntent(long pageTimeMs) {
        lastJsNonAnchorAtMs = System.currentTimeMillis();
    }

    public boolean hasRecentNativeAction() {
        return System.currentTimeMillis() - lastNativeActionAtMs <= INTENT_WINDOW_MS;
    }

    public boolean wasRecentNativeAnchorIntent() {
        return hasRecentNativeAction()
                && lastJsAnchorHref != null
                && System.currentTimeMillis() - lastJsAnchorAtMs <= INTENT_WINDOW_MS;
    }

    public boolean recentJsAnchorMatches(String targetUrl) {
        if (targetUrl == null || lastJsAnchorHref == null) return false;
        if (System.currentTimeMillis() - lastJsAnchorAtMs > INTENT_WINDOW_MS) return false;

        return normaliseUrl(lastJsAnchorHref).equals(normaliseUrl(targetUrl)) ||
               normaliseUrl(targetUrl).startsWith(normaliseUrl(lastJsAnchorHref));
    }

    public boolean recentNonAnchorIntent() {
        return System.currentTimeMillis() - lastJsNonAnchorAtMs <= INTENT_WINDOW_MS;
    }

    public String getLastJsAnchorHref() {
        return lastJsAnchorHref;
    }

    public String normaliseUrl(String url) {
        if (url == null) return "";
        String clean = url.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("https://")) {
            clean = clean.substring(8);
        } else if (clean.startsWith("http://")) {
            clean = clean.substring(7);
        }
        if (clean.startsWith("www.")) {
            clean = clean.substring(4);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
