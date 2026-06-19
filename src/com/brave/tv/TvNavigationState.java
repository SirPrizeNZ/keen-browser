package com.brave.tv;

public class TvNavigationState {
    public TvNavigationMode currentMode = TvNavigationMode.LAUNCHER;
    public String currentContentRoot = "";
    public String lastLauncherRoot = "";
    public String lastExplicitAnchorHref = "";
    public String lastExplicitAnchorRoot = "";
    public long lastNativeInputTimeMs;
    public long lastNonAnchorInputTimeMs;
    public boolean lastNavigationStartedByAddressBarOrBookmark;
    public String lastDecisionReason = "";
}
