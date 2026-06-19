package com.brave.tv;

import android.webkit.JavascriptInterface;

public final class TvIntentBridge {
    private final TvIntentLedger ledger;

    public TvIntentBridge(TvIntentLedger ledger) {
        this.ledger = ledger;
    }

    @JavascriptInterface
    public void recordAnchorIntent(String href, long pageTimeMs) {
        ledger.recordJsAnchorIntent(href, pageTimeMs);
    }

    @JavascriptInterface
    public void recordNonAnchorIntent(long pageTimeMs) {
        ledger.recordJsNonAnchorIntent(pageTimeMs);
    }
}
