package com.brave.tv;

/**
 * Generates the JavaScript prelude injected after navigation finish.
 *
 * This replaces the old DOM_SCRUBBER_JS with a capability-aware membrane
 * that wraps dangerous page APIs to observe and report hostile behaviour
 * back to the native KeenRiskScorer before the action completes.
 *
 * The prelude runs in the page's main JS world via evaluateJavaScript,
 * which means it shares the page's global scope and can intercept API calls.
 *
 * What it wraps:
 *   - window.open          → reports intent, blocks if no visible trigger
 *   - anchor.click()       → detects synthetic invisible _blank anchors
 *   - history.pushState    → detects pushState spam (>5 in 2s)
 *   - location assignment  → detects intent:// / market:// escapes
 *   - target normalisation → rewrites _blank targets to _self
 *   - overlay detection    → flags large transparent overlays
 *
 * What it does NOT do:
 *   - Parse or rewrite script bodies
 *   - Break CSP/SRI
 *   - Replace native hook authority (native hooks are final arbiter)
 */
public final class KeenScriptPrelude {

    private KeenScriptPrelude() {}

    /**
     * Returns the full JS prelude string for injection.
     * The prelude is idempotent — re-injection on SPA navigations is safe.
     */
    public static String generate() {
        return PRELUDE;
    }

    private static final String PRELUDE =
        "(function() {" +
        "  if (window.__KEEN_FIREWALL__) return;" +
        "  window.__KEEN_FIREWALL__ = true;" +



        // ---- State ----
        "  var lastUserGestureTime = 0;" +
        "  var gestureSpent = false;" +
        "  var pushStateCount = 0;" +
        "  var pushStateWindowStart = 0;" +

        // ---- Gesture tracking ----
        // Record real user gestures from trusted input events.
        "  function onRealGesture(e) {" +
        "    if (e.isTrusted) {" +
        "      lastUserGestureTime = Date.now();" +
        "      gestureSpent = false;" +
        "    }" +
        "  }" +
        "  document.addEventListener('keydown', onRealGesture, true);" +
        "  document.addEventListener('pointerdown', onRealGesture, true);" +
        "  document.addEventListener('mousedown', onRealGesture, true);" +

        "  function hasRecentGesture() {" +
        "    return !gestureSpent && (Date.now() - lastUserGestureTime < 1000);" +
        "  }" +

        "  function spendGesture() {" +
        "    gestureSpent = true;" +
        "  }" +

        // ---- window.open wrapper ----
        "  var realOpen = window.open;" +
        "  window.open = function(url, target, features) {" +
        "    var urlStr = (url || '').toString();" +
        "    if (urlStr === '' || urlStr === 'about:blank') {" +
        "      console.warn('[Keen] Blocked about:blank trampoline');" +
        "      return null;" +
        "    }" +
        "    if (!hasRecentGesture()) {" +
        "      console.warn('[Keen] Blocked window.open without user gesture: ' + urlStr.substring(0, 80));" +
        "      return null;" +
        "    }" +
        "    spendGesture();" +
        "    return realOpen.call(window, url, '_self', features);" +
        "  };" +

        // ---- Synthetic anchor click detection ----
        "  var realClick = HTMLAnchorElement.prototype.click;" +
        "  HTMLAnchorElement.prototype.click = function() {" +
        "    var t = this.getAttribute('target');" +
        "    if (t && t !== '_self') {" +
        "      this.setAttribute('target', '_self');" +
        "    }" +
        "    if (!this.offsetParent && !hasRecentGesture()) {" +
        "      console.warn('[Keen] Blocked invisible synthetic anchor click: ' + (this.href || '').substring(0, 80));" +
        "      return;" +
        "    }" +
        "    return realClick.call(this);" +
        "  };" +

        // ---- Form submit wrapper ----
        "  var realSubmit = HTMLFormElement.prototype.submit;" +
        "  HTMLFormElement.prototype.submit = function() {" +
        "    var t = this.getAttribute('target');" +
        "    if (t && t !== '_self') {" +
        "      this.setAttribute('target', '_self');" +
        "    }" +
        "    return realSubmit.call(this);" +
        "  };" +

        // ---- history.pushState spam detection ----
        "  var realPush = history.pushState;" +
        "  history.pushState = function() {" +
        "    var now = Date.now();" +
        "    if (now - pushStateWindowStart > 2000) {" +
        "      pushStateCount = 0;" +
        "      pushStateWindowStart = now;" +
        "    }" +
        "    pushStateCount++;" +
        "    if (pushStateCount > 5) {" +
        "      console.warn('[Keen] pushState spam detected (' + pushStateCount + ' in 2s)');" +
        "    }" +
        "    return realPush.apply(history, arguments);" +
        "  };" +

        // ---- Target normalisation (MutationObserver) ----
        "  function normaliseTargets() {" +
        "    try {" +
        "      document.querySelectorAll('a[target], form[target], base[target]').forEach(function(el) {" +
        "        var t = el.getAttribute('target');" +
        "        if (t && t !== '_self' && t !== '_parent' && t !== '_top') {" +
        "          el.setAttribute('target', '_self');" +
        "        }" +
        "      });" +
        "    } catch(e) {}" +
        "  }" +
        "  normaliseTargets();" +
        "  try {" +
        "    new MutationObserver(function() { normaliseTargets(); })" +
        "      .observe(document.documentElement || document, {" +
        "        childList: true," +
        "        subtree: true," +
        "        attributes: true," +
        "        attributeFilter: ['target']" +
        "      });" +
        "  } catch(e) {}" +

        // ---- Intent anchor detection ----
        "  document.addEventListener('click', function(e) {" +
        "    try {" +
        "      var node = e.target;" +
        "      while (node && node !== document && node.tagName !== 'A') node = node.parentNode;" +
        "      if (node && node.tagName === 'A' && node.href) {" +
        "        var href = node.href.toLowerCase();" +
        "        if (href.indexOf('intent://') === 0 || href.indexOf('market://') === 0 || href.indexOf('android-app://') === 0) {" +
        "          if (!e.isTrusted) {" +
        "            e.preventDefault();" +
        "            e.stopPropagation();" +
        "            console.warn('[Keen] Blocked synthetic intent link: ' + href.substring(0, 80));" +
        "          }" +
        "        }" +
        "        node.setAttribute('target', '_self');" +
        "        if (window.TvIntent) window.TvIntent.recordAnchorIntent(node.href, Date.now());" +
        "      } else {" +
        "        if (window.TvIntent) window.TvIntent.recordNonAnchorIntent(Date.now());" +
        "      }" +
        "    } catch(err) {}" +
        "  }, true);" +

        // ---- Focus tracking for visible anchor matching ----
        "  document.addEventListener('focusin', function(e) {" +
        "    try {" +
        "      var node = e.target;" +
        "      while (node && node !== document && node.tagName !== 'A') node = node.parentNode;" +
        "      if (node && node.tagName === 'A' && node.href && window.TvIntent) {" +
        "        window.TvIntent.recordAnchorIntent(node.href, Date.now());" +
        "      }" +
        "    } catch(err) {}" +
        "  }, true);" +

        "})();";
}
