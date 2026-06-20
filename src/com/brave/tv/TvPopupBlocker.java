package com.brave.tv;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.util.Locale;

/**
 * Central decision engine for navigation quarantine on Android TV.
 * Uses KeenRiskScorer for threat-level-based decisions instead of
 * absolute cross-site lockdown.
 */
public final class TvPopupBlocker {
    private static final TvIntentLedger sIntentLedger = new TvIntentLedger();
    private static final TvPopupFeedback sFeedback = new TvPopupFeedback();
    private static final TvNavigationState sNavigationState = new TvNavigationState();

    private TvPopupBlocker() {}

    public static void recordNativeAction() {
        sIntentLedger.recordNativeAction();
        sNavigationState.lastNativeInputTimeMs = System.currentTimeMillis();
    }

    /**
     * Hook called from TabWebContentsDelegateAndroidImpl.addNewContents.
     * Processes new-window / popup requests.
     */
    public static boolean shouldAllowNewContents(Object parentWebContents, Object newWebContents, Object targetGurl, int disposition, boolean userGesture) {
        Log.i("TVPopupBlocker", "shouldAllowNewContents hook entered: parentWebContents=" + parentWebContents + ", targetGurl=" + targetGurl + ", disposition=" + disposition + ", userGesture=" + userGesture);
        if (parentWebContents == null || targetGurl == null) {
            Log.i("TVPopupBlocker", "decision=BLOCK reason=missing-new-window-target");
            return false;
        }

        try {
            java.lang.reflect.Method getUrlMethod = parentWebContents.getClass().getMethod("A");
            Object parentGurl = getUrlMethod.invoke(parentWebContents);
            if (parentGurl == null) {
                Log.w("TVPopupBlocker", "shouldAllowNewContents: parentGurl is null");
                return true;
            }

            java.lang.reflect.Method getSpecMethod = parentGurl.getClass().getMethod("j");
            String parentSpec = (String) getSpecMethod.invoke(parentGurl);
            String targetSpec = (String) getSpecMethod.invoke(targetGurl);

            Log.i("TVPopupBlocker", "shouldAllowNewContents: parentSpec=" + parentSpec + ", targetSpec=" + targetSpec);

            if (parentSpec == null || targetSpec == null) {
                return true;
            }

            boolean explicitVisibleAnchor =
                    sIntentLedger.hasRecentNativeAction()
                            && sIntentLedger.recentJsAnchorMatches(targetSpec);

            PopupDecision decision = decideNavigation(
                    parentSpec,
                    targetSpec,
                    true, // isNewWindow
                    true, // isMainFrame (always true for new top-level tab)
                    userGesture,
                    explicitVisibleAnchor,
                    false // appInitiatedNavigation is false for script window.open() popup attempts
            );

            Log.i("TVPopupBlocker", "shouldAllowNewContents decision=" + decision + ", reason=" + sNavigationState.lastDecisionReason);

            if (decision == PopupDecision.BLOCK) {
                sFeedback.showBlocked("New tab blocked");
                logBlock(parentSpec, targetSpec, "content-host-cross-root-new-window", true, true, userGesture, explicitVisibleAnchor);
                return false; // Reject window creation
            }

            if (decision == PopupDecision.LOAD_CURRENT_TAB) {
                loadUrlInWebContents(parentWebContents, targetGurl);
                return false; // Block new window, loaded in current tab instead
            }

        } catch (Throwable e) {
            Log.e("TVPopupBlocker", "Error in shouldAllowNewContents: " + e.getMessage(), e);
            sFeedback.showBlocked("New tab blocked");
            return false;
        }

        return true;
    }

    /**
     * Hook called from hs1.w (shouldOverrideUrlLoading).
     * Processes same-tab main-frame navigation.
     */
    public static Object shouldOverrideUrlLoading(Object delegate, Object navigationParams) {
        Log.i("TVPopupBlocker", "shouldOverrideUrlLoading hook entered: delegate=" + delegate + ", params=" + navigationParams);
        try {
            String parentUrl = getParentUrlFromDelegate(delegate);

            java.lang.reflect.Field urlField = navigationParams.getClass().getField("a");
            Object targetGurl = urlField.get(navigationParams);
            java.lang.reflect.Method getSpecMethod = targetGurl.getClass().getMethod("j");
            String targetUrl = (String) getSpecMethod.invoke(targetGurl);

            java.lang.reflect.Field isMainFrameField = navigationParams.getClass().getField("i");
            boolean isMainFrame = isMainFrameField.getBoolean(navigationParams);

            java.lang.reflect.Field hasUserGestureField = navigationParams.getClass().getField("j");
            boolean hasUserGesture = hasUserGestureField.getBoolean(navigationParams);

            java.lang.reflect.Field transitionTypeField = navigationParams.getClass().getField("d");
            int transitionType = transitionTypeField.getInt(navigationParams);

            boolean explicitVisibleAnchor =
                    sIntentLedger.hasRecentNativeAction()
                            && sIntentLedger.recentJsAnchorMatches(targetUrl);

            boolean appInitiatedNavigation = isAppInitiated(parentUrl, transitionType);

            Log.i("TVPopupBlocker", "shouldOverrideUrlLoading: parentUrl=" + parentUrl +
                    ", targetUrl=" + targetUrl +
                    ", isMainFrame=" + isMainFrame +
                    ", hasUserGesture=" + hasUserGesture +
                    ", transitionType=" + transitionType +
                    ", appInitiatedNavigation=" + appInitiatedNavigation +
                    ", explicitVisibleAnchor=" + explicitVisibleAnchor);

            PopupDecision decision = decideNavigation(
                    parentUrl,
                    targetUrl,
                    false, // isNewWindow
                    isMainFrame,
                    hasUserGesture,
                    explicitVisibleAnchor,
                    appInitiatedNavigation
            );

            Log.i("TVPopupBlocker", "shouldOverrideUrlLoading decision=" + decision + ", reason=" + sNavigationState.lastDecisionReason);

            if (decision == PopupDecision.BLOCK) {
                sFeedback.showBlocked("Navigation blocked");
                logBlock(parentUrl, targetUrl, "content-host-cross-root-same-tab-redirect", false, isMainFrame, hasUserGesture, explicitVisibleAnchor);

                Class<?> z06Class = Class.forName("z06");
                java.lang.reflect.Constructor<?> ctor = z06Class.getConstructor(int.class, int.class);
                return ctor.newInstance(1, 0); // 1 = INTERCEPT / BLOCK
            }

            if (decision == PopupDecision.LOAD_CURRENT_TAB) {
                return null; // Same-tab loading flows normally
            }

        } catch (Throwable e) {
            Log.e("TVPopupBlocker", "Error in shouldOverrideUrlLoading: " + e.getMessage(), e);
        }

        return null; // Allow navigation to execute normally
    }

    public static PopupDecision decideNavigation(
            String parentUrl,
            String targetUrl,
            boolean isNewWindow,
            boolean isMainFrame,
            boolean hasUserGesture,
            boolean explicitVisibleAnchor,
            boolean appInitiatedNavigation
    ) {
        String parentRoot = root(parentUrl);
        String targetRoot = root(targetUrl);
        int riskScore = KeenRiskScorer.getScore(parentRoot);
        boolean sameSite = !parentRoot.isEmpty() && parentRoot.equals(targetRoot);

        // --- Gate 1: empty/null targets are always blocked ---
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            sNavigationState.lastDecisionReason = "empty-target";
            KeenDebugLog.blocked(parentRoot, "empty-target", "empty-target", riskScore);
            return PopupDecision.BLOCK;
        }

        // --- Gate 2: sub-frame navigations in same tab are allowed ---
        if (!isMainFrame && !isNewWindow) {
            return PopupDecision.ALLOW;
        }

        // --- Gate 3: about:blank trampolines are always blocked ---
        if (isAboutBlank(targetUrl)) {
            KeenRiskScorer.signalAboutBlankTrampoline(parentRoot);
            sNavigationState.lastDecisionReason = "about-blank-trampoline";
            KeenDebugLog.blocked(parentRoot, "about:blank trampoline", "about-blank-trampoline", riskScore);
            return PopupDecision.BLOCK;
        }

        // --- Gate 4: Android intent/market scheme firewall ---
        if (isIntentScheme(targetUrl)) {
            KeenRiskScorer.signalIntentEscape(parentRoot);
            if (explicitVisibleAnchor && hasUserGesture) {
                // Visible user-chosen app link — redirect in current tab (let system handle)
                sNavigationState.lastDecisionReason = "intent-visible-user-choice";
                KeenDebugLog.redirected(parentRoot, "intent://", "intent-visible-user-choice", riskScore);
                return PopupDecision.LOAD_CURRENT_TAB;
            }
            sNavigationState.lastDecisionReason = "intent-escape-blocked";
            KeenDebugLog.blocked(parentRoot, "intent://", "intent-escape-blocked", riskScore);
            return PopupDecision.BLOCK;
        }

        // --- Gate 5: auth host exception ---
        if (isAuthHost(targetRoot)) {
            sNavigationState.lastDecisionReason = "auth-host-exception";
            KeenDebugLog.allowed(parentRoot, "auth-host", "auth-host-exception", riskScore);
            return PopupDecision.LOAD_CURRENT_TAB;
        }

        // --- Gate 6: app-initiated navigation (chrome://, brave://, initial load) ---
        if (appInitiatedNavigation) {
            sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
            sNavigationState.currentContentRoot = targetRoot;
            sNavigationState.lastDecisionReason = "app-initiated-navigation";
            KeenDebugLog.allowed(parentRoot, "app-initiated", "app-initiated-navigation", riskScore);
            return PopupDecision.LOAD_CURRENT_TAB;
        }

        // --- Gate 7: same-site navigation is always allowed ---
        if (sameSite) {
            sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
            sNavigationState.currentContentRoot = targetRoot;
            sNavigationState.lastDecisionReason = "same-root-allowed";
            KeenDebugLog.allowed(parentRoot, sameSite ? "same-site" : "cross-site", "same-root-allowed", riskScore);
            return isNewWindow ? PopupDecision.LOAD_CURRENT_TAB : PopupDecision.ALLOW;
        }

        // --- Gate 8: launcher host navigating outward ---
        if (isLauncherHost(parentRoot)) {
            if (explicitVisibleAnchor || hasUserGesture) {
                sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
                sNavigationState.currentContentRoot = targetRoot;
                sNavigationState.lastDecisionReason = explicitVisibleAnchor
                        ? "launcher-explicit-anchor" : "launcher-user-gesture";
                KeenDebugLog.redirected(parentRoot, "launcher-outbound", sNavigationState.lastDecisionReason, riskScore);
                return PopupDecision.LOAD_CURRENT_TAB;
            }
            sNavigationState.lastDecisionReason = "launcher-blocked-non-anchor";
            KeenDebugLog.blocked(parentRoot, "launcher-non-anchor", "launcher-blocked-non-anchor", riskScore);
            return PopupDecision.BLOCK;
        }

        // --- Gate 9: navigating back to a launcher host ---
        if (isLauncherHost(targetRoot)) {
            sNavigationState.currentMode = TvNavigationMode.LAUNCHER;
            sNavigationState.currentContentRoot = "";
            sNavigationState.lastDecisionReason = "navigate-to-launcher";
            KeenDebugLog.allowed(parentRoot, "to-launcher", "navigate-to-launcher", riskScore);
            return isNewWindow ? PopupDecision.LOAD_CURRENT_TAB : PopupDecision.ALLOW;
        }

        // --- Gate 10: RISK-SCORED cross-site decisions ---
        // This is the core firewall logic replacing the old absolute lockdown.
        riskScore = KeenRiskScorer.getScore(parentRoot);

        // NORMAL (0–3): allow visible cross-site links, force into current tab
        if (riskScore < KeenRiskScorer.LEVEL_WATCH) {
            if (explicitVisibleAnchor || hasUserGesture) {
                sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
                sNavigationState.currentContentRoot = targetRoot;
                sNavigationState.lastDecisionReason = "cross-site-normal-visible";
                KeenDebugLog.redirected(parentRoot, "cross-site-visible", "cross-site-normal-visible", riskScore);
                return PopupDecision.LOAD_CURRENT_TAB;
            }
            // No visible intent but low risk — still block hidden actions
            if (isNewWindow) {
                KeenRiskScorer.signalCrossSitePopupHidden(parentRoot);
                sNavigationState.lastDecisionReason = "cross-site-normal-hidden-popup";
                KeenDebugLog.blocked(parentRoot, "cross-site-hidden-popup", "cross-site-normal-hidden-popup", riskScore);
                return PopupDecision.BLOCK;
            }
            // Same-tab redirect without gesture at low risk — allow cautiously
            sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
            sNavigationState.currentContentRoot = targetRoot;
            sNavigationState.lastDecisionReason = "cross-site-normal-redirect";
            KeenDebugLog.allowed(parentRoot, "cross-site-redirect", "cross-site-normal-redirect", riskScore);
            return PopupDecision.ALLOW;
        }

        // WATCH (4–6): allow visible links, block hidden
        if (riskScore < KeenRiskScorer.LEVEL_RESTRICT) {
            if (explicitVisibleAnchor) {
                sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
                sNavigationState.currentContentRoot = targetRoot;
                sNavigationState.lastDecisionReason = "cross-site-watch-visible-anchor";
                KeenDebugLog.redirected(parentRoot, "cross-site-visible", "cross-site-watch-visible-anchor", riskScore);
                return PopupDecision.LOAD_CURRENT_TAB;
            }
            sNavigationState.lastDecisionReason = "cross-site-watch-blocked";
            KeenDebugLog.blocked(parentRoot, "cross-site-watch", "cross-site-watch-blocked", riskScore);
            sFeedback.showBlocked("Navigation restricted");
            return PopupDecision.BLOCK;
        }

        // RESTRICT (7–10): block popups, block cross-site unless explicit visible anchor
        if (riskScore < KeenRiskScorer.LEVEL_STRICT) {
            if (explicitVisibleAnchor && !isNewWindow) {
                sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
                sNavigationState.currentContentRoot = targetRoot;
                sNavigationState.lastDecisionReason = "cross-site-restrict-visible-same-tab";
                KeenDebugLog.redirected(parentRoot, "cross-site-restrict-visible", "cross-site-restrict-visible-same-tab", riskScore);
                return PopupDecision.ALLOW;
            }
            sNavigationState.lastDecisionReason = "cross-site-restrict-blocked";
            KeenDebugLog.blocked(parentRoot, "cross-site-restrict", "cross-site-restrict-blocked", riskScore);
            sFeedback.showBlocked("Site restricted");
            return PopupDecision.BLOCK;
        }

        // STRICT (11+): block all non-same-origin
        sNavigationState.lastDecisionReason = "cross-site-strict-blocked";
        KeenDebugLog.blocked(parentRoot, "cross-site-strict", "cross-site-strict-blocked", riskScore);
        sFeedback.showBlocked("Site blocked");
        return PopupDecision.BLOCK;
    }

    private static String getParentUrlFromDelegate(Object delegate) {
        try {
            java.lang.reflect.Field aField = delegate.getClass().getField("a");
            Object q06 = aField.get(delegate);
            if (q06 == null) {
                Log.w("TVPopupBlocker", "getParentUrlFromDelegate: q06 is null");
                return "";
            }

            java.lang.reflect.Field tabField = q06.getClass().getField("a");
            Object tab = tabField.get(q06);
            if (tab == null) {
                Log.w("TVPopupBlocker", "getParentUrlFromDelegate: tab is null");
                return "";
            }

            java.lang.reflect.Method getWebContentsMethod = tab.getClass().getMethod("getWebContents");
            Object webContents = getWebContentsMethod.invoke(tab);
            if (webContents == null) {
                Log.w("TVPopupBlocker", "getParentUrlFromDelegate: webContents is null");
                return "";
            }

            java.lang.reflect.Method getUrlMethod = webContents.getClass().getMethod("A");
            Object parentGurl = getUrlMethod.invoke(webContents);
            if (parentGurl == null) {
                Log.w("TVPopupBlocker", "getParentUrlFromDelegate: parentGurl is null");
                return "";
            }

            java.lang.reflect.Method getSpecMethod = parentGurl.getClass().getMethod("j");
            String parentUrl = (String) getSpecMethod.invoke(parentGurl);
            Log.i("TVPopupBlocker", "getParentUrlFromDelegate: successfully resolved parentUrl=" + parentUrl);
            return parentUrl;

        } catch (Exception e) {
            Log.e("TVPopupBlocker", "getParentUrlFromDelegate failed", e);
            return "";
        }
    }

    private static void loadUrlInWebContents(Object webContents, Object targetGurl) {
        try {
            java.lang.reflect.Method getNavigationControllerMethod = webContents.getClass().getMethod("C");
            Object navigationController = getNavigationControllerMethod.invoke(webContents);
            if (navigationController == null) {
                return;
            }

            Class<?> loadUrlParamsClass = Class.forName("org.chromium.content_public.browser.LoadUrlParams");
            Class<?> gurlClass = Class.forName("org.chromium.url.GURL");
            java.lang.reflect.Constructor<?> ctor = loadUrlParamsClass.getConstructor(gurlClass);
            Object loadUrlParams = ctor.newInstance(targetGurl);

            java.lang.reflect.Method jMethod = navigationController.getClass().getMethod("j", loadUrlParamsClass);
            jMethod.invoke(navigationController, loadUrlParams);

        } catch (Exception e) {
            Log.e("TVPopupBlocker", "Failed to loadUrlInWebContents: " + e.getMessage());
        }
    }

    private static boolean isAppInitiated(String parentUrl, int transitionType) {
        if (parentUrl == null || parentUrl.trim().isEmpty() || "about:blank".equalsIgnoreCase(parentUrl)) {
            return true;
        }
        if (parentUrl.startsWith("chrome://") || parentUrl.startsWith("chrome-native://") || parentUrl.startsWith("brave://")) {
            return true;
        }
        return false;
    }

    private static boolean isLauncherHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String cleanHost = host.toLowerCase(Locale.ROOT).trim();
        return cleanHost.equals("fmhy.net") ||
               cleanHost.endsWith(".fmhy.net") ||
               cleanHost.equals("fmhy.pages.dev") ||
               cleanHost.endsWith(".fmhy.pages.dev") ||
               cleanHost.equals("google.com") ||
               cleanHost.endsWith(".google.com") ||
               cleanHost.contains(".google.") ||
               cleanHost.equals("duckduckgo.com") ||
               cleanHost.endsWith(".duckduckgo.com") ||
               cleanHost.equals("bing.com") ||
               cleanHost.endsWith(".bing.com");
    }

    private static boolean isAuthHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("accounts.google.com") ||
               host.equals("appleid.apple.com") ||
               host.equals("login.microsoftonline.com") ||
               host.equals("okta.com") || host.endsWith(".okta.com") ||
               host.equals("auth0.com") || host.endsWith(".auth0.com");
    }

    private static String root(String url) {
        if (url == null) return "";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
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

    private static boolean isAboutBlank(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.equals("about:blank") || lower.startsWith("about:blank?") || lower.startsWith("about:blank#");
    }

    private static boolean isIntentScheme(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("intent://") || lower.startsWith("market://") || lower.startsWith("android-app://");
    }

    public static void injectDomScrubber(Object webContents) {
        if (webContents == null) {
            return;
        }
        try {
            String prelude = KeenScriptPrelude.generate();
            java.lang.reflect.Method[] methods = webContents.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m.getName().equals("evaluateJavaScript") || m.getName().equals("c0")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == String.class) {
                        m.invoke(webContents, prelude, null);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TVPopupBlocker", "Failed to inject firewall prelude: " + e.getMessage());
        }
    }

    public static void onMainFrameNavigationFinished(Object webContents) {
        if (webContents == null) return;

        // 1. Inject DOM Scrubber
        injectDomScrubber(webContents);

        // 2. Resolve URL and update mode
        try {
            java.lang.reflect.Method getUrlMethod = webContents.getClass().getMethod("A");
            Object gurl = getUrlMethod.invoke(webContents);
            if (gurl == null) {
                Log.w("TVPopupBlocker", "onMainFrameNavigationFinished: current GURL is null");
                return;
            }

            java.lang.reflect.Method getSpecMethod = gurl.getClass().getMethod("j");
            String url = (String) getSpecMethod.invoke(gurl);

            if (url == null || url.trim().isEmpty() || "about:blank".equalsIgnoreCase(url)) {
                return;
            }

            String hostRoot = root(url);
            if (isLauncherHost(hostRoot)) {
                sNavigationState.currentMode = TvNavigationMode.LAUNCHER;
                sNavigationState.currentContentRoot = "";
                Log.i("TVPopupBlocker", "Main frame navigation finished: URL=" + url + " -> mode=LAUNCHER");
            } else {
                sNavigationState.currentMode = TvNavigationMode.CONTENT_LOCKDOWN;
                sNavigationState.currentContentRoot = hostRoot;
                Log.i("TVPopupBlocker", "Main frame navigation finished: URL=" + url + " -> mode=CONTENT_LOCKDOWN, root=" + hostRoot);
            }

            // Notify back-trap detector of navigation completion
            TvCursorController.notifyNavigationFinished(url);

            // Decay risk scores on each navigation to prevent permanent escalation
            KeenRiskScorer.decayAll();
        } catch (Throwable e) {
            Log.e("TVPopupBlocker", "Error in onMainFrameNavigationFinished: " + e.getMessage(), e);
        }
    }

    private static void logBlock(
            String parentUrl,
            String targetUrl,
            String blockType,
            boolean isNewWindow,
            boolean isMainFrame,
            boolean hasUserGesture,
            boolean explicitVisibleAnchor
    ) {
        String parentRoot = root(parentUrl);
        String targetRoot = root(targetUrl);
        String redactedUrl = redactLongUrl(targetUrl);

        Log.i("TVPopupBlocker",
            "decision=BLOCK" +
            " reason=" + sNavigationState.lastDecisionReason +
            " mode=" + sNavigationState.currentMode +
            " parentRoot=" + parentRoot +
            " contentRoot=" + sNavigationState.currentContentRoot +
            " targetRoot=" + targetRoot +
            " targetUrl=" + redactedUrl +
            " isNewWindow=" + isNewWindow +
            " isMainFrame=" + isMainFrame +
            " hasUserGesture=" + hasUserGesture +
            " explicitVisibleAnchor=" + explicitVisibleAnchor
        );
    }

    public static boolean shouldShowPermissionDialog(Object delegate) {
        if (delegate == null) {
            return true;
        }
        try {
            java.lang.reflect.Field permissionsField = delegate.getClass().getField("o");
            int[] permissions = (int[]) permissionsField.get(delegate);
            if (permissions != null) {
                for (int p : permissions) {
                    Log.i("TVPopupBlocker", "PermissionDialog requested content setting type: " + p);
                    if (p == 2) { // 2 = ContentSettingsType.NOTIFICATIONS
                        Log.i("TVPopupBlocker", "Blocking notification permission dialog request");
                        java.lang.reflect.Field nativeDelegateField = delegate.getClass().getField("f");
                        long nativePtr = nativeDelegateField.getLong(delegate);
                        if (nativePtr != 0) {
                            java.lang.reflect.Method vijMethod = Class.forName("LJ.N").getMethod("VIJ", int.class, int.class, long.class);
                            // 56 is onClicked method ID, 1 is DENIED (Deny decision)
                            vijMethod.invoke(null, 56, 1, nativePtr);
                            Log.i("TVPopupBlocker", "Natively denied notification permission dialog for ptr: " + nativePtr);
                        }
                        return false;
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("TVPopupBlocker", "Error in shouldShowPermissionDialog: " + e.getMessage(), e);
        }
        return true;
    }

    private static String redactLongUrl(String url) {
        if (url == null) return "null";
        if (url.length() <= 100) return url;
        return url.substring(0, 97) + "...";
    }
}
