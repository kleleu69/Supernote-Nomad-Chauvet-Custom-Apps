package com.supernote.einkbro

import android.net.Uri

/**
 * All iCloud-specific patches for EinkBro on Supernote Nomad.
 *
 * Applied patches:
 *  1. Domain-specific User-Agent — iOS Safari UA for *.icloud.com / *.apple.com so Apple's
 *     browser-compatibility checks pass and iCloud Drive loads fully.
 *  2. JavaScript injection — overrides navigator.vendor / navigator.platform at document start
 *     so Apple's JS-side browser detection treats the WebView as Safari-compatible, suppressing
 *     the "iCloud is not supported in this browser" warning banner.
 *  3. Cookie configuration — third-party cookies enabled so auth state persists across the
 *     apple.com ↔ icloud.com sub-domain boundary.
 *  4. WebSettings — domStorage + database enabled for iCloud Drive's IndexedDB, localStorage,
 *     and Web Crypto requirements.
 */
object ICloudPatcher {

    /**
     * Safari iOS 17 User-Agent presented to *.icloud.com and *.apple.com.
     * iCloud Drive fully supports this UA and serves its complete PWA interface.
     */
    const val ICLOUD_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/17.5 Mobile/15E148 Safari/604.1"

    /**
     * Default Chrome-on-Android UA for all non-Apple pages.
     * Matches a current Chrome 124 build for broad site compatibility.
     */
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 10; K) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * JavaScript injected into every iCloud / Apple page as early as possible.
     *
     * - Sets navigator.vendor to "Apple Computer, Inc." — the value Safari reports —
     *   so Apple's client-side browser detection succeeds and no warning banner appears.
     * - Sets navigator.platform to "iPhone" to stay consistent with the iOS UA string.
     *
     * Object.defineProperty is used so the overrides survive any subsequent reads by
     * the page's own scripts during initialisation. The try/catch prevents a crash if
     * a property was already made non-configurable by a prior script.
     */
    const val ICLOUD_JS_PATCH = """
(function() {
    'use strict';
    try {
        Object.defineProperty(navigator, 'vendor', {
            get: function() { return 'Apple Computer, Inc.'; },
            configurable: true
        });
        Object.defineProperty(navigator, 'platform', {
            get: function() { return 'iPhone'; },
            configurable: true
        });
    } catch (e) { /* property already non-configurable — ignore */ }
})();
"""

    /** Origin-rule patterns used with addDocumentStartJavaScript. */
    val ICLOUD_ORIGINS: Set<String> = setOf(
        "https://icloud.com",
        "https://*.icloud.com",
        "https://apple.com",
        "https://*.apple.com"
    )

    /** Returns true for any URL hosted on icloud.com or apple.com (including all subdomains). */
    fun isICloudDomain(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            host.endsWith(".icloud.com") || host == "icloud.com" ||
                host.endsWith(".apple.com") || host == "apple.com"
        } catch (e: Exception) {
            false
        }
    }

    /** Returns the correct User-Agent string for the given URL. */
    fun getUaForUrl(url: String): String = if (isICloudDomain(url)) ICLOUD_UA else DEFAULT_UA
}
