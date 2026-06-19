package com.brave.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Strict Popup Lock engine for Keen Browser on Android TV.
 * Uses behavioural verification gates to block malicious redirects and overlays.
 */
public final class TvPopupBlocker {
    private static final String PREFS_NAME = "keen_strict_popup_lock_prefs";
    private static final String KEY_AUTHORISED = "authorised_hosts";
    private static final long VELOCITY_THRESHOLD_MS = 500;
    
    private static long sLastWindowTime = 0;
    private static final Set<String> sInMemoryAuthorisedHosts = new HashSet<>();
    private static boolean sPrefsLoaded = false;

    // Minified JavaScript DOM Scrubber to remove transparent click-hijacking overlays.
    // Uses MutationObserver to dynamically clean elements with absolute/fixed layout,
    // high z-index, transparent opacity, and covering more than 50% of the viewport.
    private static final String DOM_SCRUBBER_JS = 
        "(function(){" +
        "const s=()=>{document.querySelectorAll('*').forEach(e=>{" +
        "const t=window.getComputedStyle(e);" +
        "if((t.position==='absolute'||t.position==='fixed')&&" +
        "(parseFloat(t.opacity)===0||t.backgroundColor==='transparent')&&" +
        "parseInt(t.zIndex)>0){" +
        "const r=e.getBoundingClientRect();" +
        "if(r.width*r.height>(window.innerWidth*window.innerHeight*0.5)){" +
        "e.remove();" +
        "}" +
        "}" +
        "});};" +
        "s();" +
        "new MutationObserver(s).observe(document.documentElement,{childList:true,subtree:true});" +
        "})();";

    private TvPopupBlocker() {}

    /**
     * Entry point for native window creation interception.
     * Evaluates whether a window allocation request is behavioural or hostile.
     */
    public static boolean shouldAllowNewContents(Object parentWebContents, Object newWebContents, Object targetGurl, int disposition, boolean userGesture) {
        if (parentWebContents == null || targetGurl == null) {
            return true;
        }

        try {
            // Retrieve parent visible URL spec via reflection
            java.lang.reflect.Method getUrlMethod = parentWebContents.getClass().getMethod("A");
            Object parentGurl = getUrlMethod.invoke(parentWebContents);
            if (parentGurl == null) {
                return true;
            }

            java.lang.reflect.Method getSpecMethod = parentGurl.getClass().getMethod("j");
            String parentSpec = (String) getSpecMethod.invoke(parentGurl);
            String targetSpec = (String) getSpecMethod.invoke(targetGurl);

            if (parentSpec == null || targetSpec == null) {
                return true;
            }

            String parentHost = getHost(parentSpec);
            String targetHost = getHost(targetSpec);

            // Load preferences if not already done
            ensurePrefsLoaded();

            // Bypass check if the parent domain is explicitly authorised by the user
            String parentRoot = getRootDomain(parentHost);
            if (isHostAuthorised(parentRoot)) {
                return true;
            }

            // Gate A: The Blank-Canvas Gate
            if (targetSpec.trim().isEmpty() || "about:blank".equalsIgnoreCase(targetSpec.trim())) {
                System.out.println("[Strict Lock] Blocked window request: Blank-Canvas Gate (about:blank trampoline)");
                return false;
            }

            // Gate B: The Interaction-Velocity Gate
            long currentTime = SystemClock.elapsedRealtime();
            if (userGesture) {
                if (currentTime - sLastWindowTime < VELOCITY_THRESHOLD_MS) {
                    System.out.println("[Strict Lock] Blocked window request: Interaction-Velocity Gate (high frequency window spawning)");
                    return false;
                }
                sLastWindowTime = currentTime;
            }

            // Gate C: The Context-Escape Gate
            String targetRoot = getRootDomain(targetHost);
            if (!targetRoot.isEmpty() && !targetRoot.equalsIgnoreCase(parentRoot)) {
                // Allow global authentication hosts to bypass isolation boundaries
                if (isGlobalAuthHost(targetHost)) {
                    return true;
                }
                // Allow user-initiated link clicks (e.g. clicking external links on directory sites)
                if (userGesture) {
                    return true;
                }
                System.out.println("[Strict Lock] Blocked window request: Context-Escape Gate (cross-site mismatch). Parent: " + parentRoot + ", Target: " + targetRoot);
                return false;
            }

        } catch (Exception e) {
            System.err.println("[Strict Lock] Error evaluating content permission: " + e.getMessage());
        }

        return true;
    }

    /**
     * Injects the DOM overlay scrubber JavaScript into the main frame of the WebContents.
     */
    public static void injectDomScrubber(Object webContents) {
        if (webContents == null) {
            return;
        }
        try {
            java.lang.reflect.Method[] methods = webContents.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (m.getName().equals("evaluateJavaScript") || m.getName().equals("c0")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == String.class) {
                        m.invoke(webContents, DOM_SCRUBBER_JS, null);
                        System.out.println("[Strict Lock] Injected DOM scrubber script successfully");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Strict Lock] Failed to inject DOM scrubber script: " + e.getMessage());
        }
    }

    /**
     * Manually authorises a parent root domain to allow popup navigation.
     */
    public static void authoriseHost(String rootDomain) {
        if (rootDomain == null || rootDomain.trim().isEmpty()) {
            return;
        }
        String cleanRoot = rootDomain.toLowerCase().trim();
        synchronized (sInMemoryAuthorisedHosts) {
            sInMemoryAuthorisedHosts.add(cleanRoot);
            Context context = getAppContext();
            if (context != null) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putStringSet(KEY_AUTHORISED, sInMemoryAuthorisedHosts).apply();
            }
        }
    }

    /**
     * Checks if a parent root domain is authorised.
     */
    public static boolean isHostAuthorised(String rootDomain) {
        if (rootDomain == null) {
            return false;
        }
        synchronized (sInMemoryAuthorisedHosts) {
            return sInMemoryAuthorisedHosts.contains(rootDomain.toLowerCase().trim());
        }
    }

    private static void ensurePrefsLoaded() {
        if (sPrefsLoaded) {
            return;
        }
        Context context = getAppContext();
        if (context != null) {
            synchronized (sInMemoryAuthorisedHosts) {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> saved = prefs.getStringSet(KEY_AUTHORISED, null);
                if (saved != null) {
                    sInMemoryAuthorisedHosts.clear();
                    for (String host : saved) {
                        sInMemoryAuthorisedHosts.add(host.toLowerCase().trim());
                    }
                }
                sPrefsLoaded = true;
            }
        }
    }

    private static String getHost(String urlSpec) {
        try {
            return new URL(urlSpec).getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private static String getRootDomain(String host) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        String cleanHost = host.toLowerCase();
        String[] parts = cleanHost.split("\\.");
        if (parts.length >= 2) {
            String secondToLast = parts[parts.length - 2];
            // Handle common double-extension domains (e.g. co.uk, com.br)
            if (parts.length >= 3 && (secondToLast.equals("co") || secondToLast.equals("com") || 
                secondToLast.equals("org") || secondToLast.equals("net") || 
                secondToLast.equals("gov") || secondToLast.equals("edu"))) {
                return parts[parts.length - 3] + "." + secondToLast + "." + parts[parts.length - 1];
            }
            return secondToLast + "." + parts[parts.length - 1];
        }
        return cleanHost;
    }

    private static boolean isGlobalAuthHost(String host) {
        if (host == null) {
            return false;
        }
        String cleanHost = host.toLowerCase();
        return cleanHost.endsWith("accounts.google.com") || 
               cleanHost.endsWith("appleid.apple.com") || 
               cleanHost.endsWith("facebook.com");
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
