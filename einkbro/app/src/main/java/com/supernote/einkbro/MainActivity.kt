package com.supernote.einkbro

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * EinkBro iCloud — e-ink optimised browser for Supernote Nomad with iCloud Drive support.
 *
 * iCloud-specific patches (see also ICloudPatcher):
 *  - Domain-aware User-Agent: iOS Safari UA for *.icloud.com / *.apple.com, Chrome otherwise.
 *  - Document-start JS injection: overrides navigator.vendor / platform so Apple's browser
 *    detection treats the WebView as Safari, suppressing the unsupported-browser banner.
 *  - Third-party cookies enabled across apple.com ↔ icloud.com for persistent auth.
 *  - domStorage + database enabled for IndexedDB / Web Crypto (iCloud Drive requirement).
 *  - File chooser wired up (onShowFileChooser) to enable iCloud Drive file uploads.
 *  - Download listener redirects iCloud Drive file downloads to the system.
 *
 * E-ink optimisations:
 *  - hardwareAccelerated="false" in manifest — avoids partial-refresh artefacts on e-ink.
 *  - No window animations.
 *  - Text zoom 110 % for better readability on Supernote's 300 dpi monochrome screen.
 *  - Minimal toolbar: ← → URL-bar ↻ ☁ with disabled-state alpha for nav buttons.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var urlBar: EditText
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button

    /** Pending callback for the WebChromeClient file chooser. */
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    /**
     * Tracks whether the current page is in iCloud/Apple mode so we only switch UA
     * (and reload) when crossing a domain-type boundary.
     */
    private var isICloudMode = true   // default true because the home page is iCloud

    /** ActivityResult launcher for the iCloud Drive file-upload picker. */
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView     = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        urlBar      = findViewById(R.id.urlBar)
        btnBack     = findViewById(R.id.btnBack)
        btnForward  = findViewById(R.id.btnForward)

        configureWebView()
        setupToolbar()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            navigateTo(ICLOUD_HOME)
        }
    }

    // ── WebView configuration ────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings: WebSettings = webView.settings

        // JavaScript is required by iCloud Drive's SPA and all modern sites.
        settings.javaScriptEnabled = true

        // DOM storage (localStorage) + Web SQL database for iCloud Drive (IndexedDB, Web Crypto).
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.setDatabaseEnabled(true)

        // Start in iCloud UA mode — the initial page is iCloud.com.
        settings.userAgentString = ICloudPatcher.ICLOUD_UA

        // iCloud is fully HTTPS; never allow mixed content.
        @Suppress("DEPRECATION")
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Third-party cookies required: Apple auth spans apple.com ↔ icloud.com subdomains.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Pinch-to-zoom: useful for adjusting text size on e-ink.
        settings.builtInZoomControls = true
        settings.displayZoomControls = false   // hide the +/- overlay buttons
        settings.setSupportZoom(true)

        // Slightly larger default text for e-ink readability.
        settings.textZoom = 110

        // Use network when available; fall back to cache when offline.
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Images needed for iCloud Drive file previews and thumbnails.
        settings.loadsImagesAutomatically = true

        webView.scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        // ── iCloud JS patch — document-start injection ───────────────────────
        // WebViewFeature.DOCUMENT_START_SCRIPT (Chrome/WebView ≥102) injects the script
        // before ANY page JavaScript runs, which is the most reliable suppression path.
        // Older WebView versions fall back to evaluateJavascript in onPageStarted.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                ICloudPatcher.ICLOUD_JS_PATCH,
                ICloudPatcher.ICLOUD_ORIGINS
            )
        }

        // ── WebViewClient ────────────────────────────────────────────────────
        webView.webViewClient = object : WebViewClient() {

            // API 21-23 fallback
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleNavigation(view, url)

            // API 24+ preferred path
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleNavigation(view, request.url.toString())

            /**
             * Switches the User-Agent when navigation crosses the iCloud/Apple ↔ other
             * domain boundary.  Changing settings.userAgentString and calling view.loadUrl()
             * re-issues the request with the updated UA header on the next load.
             *
             * Returns true (URL handled) only when a UA switch is needed, so normal
             * same-domain navigation continues without reloads.
             */
            private fun handleNavigation(view: WebView, url: String): Boolean {
                val needsICloud = ICloudPatcher.isICloudDomain(url)
                if (needsICloud != isICloudMode) {
                    isICloudMode = needsICloud
                    view.settings.userAgentString = ICloudPatcher.getUaForUrl(url)
                    view.loadUrl(url)
                    return true     // we re-issued the request with the new UA
                }
                return false        // let WebView handle it normally
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                urlBar.setText(url)
                updateNavButtons()

                // Fallback for WebView versions that don't support DOCUMENT_START_SCRIPT.
                // evaluateJavascript here fires as soon as the new document is created,
                // before most SPA frameworks initialise.
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                    ICloudPatcher.isICloudDomain(url)
                ) {
                    view.evaluateJavascript(ICloudPatcher.ICLOUD_JS_PATCH, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                urlBar.setText(url)
                updateNavButtons()
                // Flush cookies to persistent storage after every page load.
                CookieManager.getInstance().flush()

                // Re-apply the patch at page-finish as a final safety net for pages that
                // dynamically replace the navigator object after load.
                if (ICloudPatcher.isICloudDomain(url)) {
                    view.evaluateJavascript(ICloudPatcher.ICLOUD_JS_PATCH, null)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                // Always cancel on SSL errors — never bypass TLS for Apple services.
                handler.cancel()
            }
        }

        // ── WebChromeClient ──────────────────────────────────────────────────
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                supportActionBar?.title = title
            }

            /**
             * File chooser for iCloud Drive uploads.
             * Launches the system file picker; the selected URI(s) are returned to the
             * WebView via filePathCallback so iCloud Drive can read and upload the file.
             */
            override fun onShowFileChooser(
                view: WebView,
                filePathCb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                // Cancel any stale callback before registering the new one.
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCb

                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    false
                }
            }
        }

        // ── Download listener ────────────────────────────────────────────────
        // When iCloud Drive offers a file download, hand the URL to the system
        // so it is processed by the default browser / download manager.
        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) { /* no handler installed */ }
        }
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        btnBack.setOnClickListener    { if (webView.canGoBack())    webView.goBack()    }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btnICloud).setOnClickListener  { navigateTo(ICLOUD_HOME) }

        urlBar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateTo(normalizeUrl(v.text.toString().trim()))
                hideKeyboard()
                true
            } else false
        }
    }

    /**
     * Loads [url] into the WebView, switching the User-Agent if needed before the load
     * so the very first request is sent with the correct UA header.
     */
    private fun navigateTo(url: String) {
        val needsICloud = ICloudPatcher.isICloudDomain(url)
        if (needsICloud != isICloudMode) {
            isICloudMode = needsICloud
            webView.settings.userAgentString = ICloudPatcher.getUaForUrl(url)
        }
        webView.loadUrl(url)
    }

    /**
     * Normalises URL-bar input:
     *  - Already has a scheme   → use as-is.
     *  - Looks like a hostname  → prepend "https://".
     *  - Otherwise              → wrap in a DuckDuckGo search query.
     */
    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        return if (input.contains(".") && !input.contains(" ")) {
            "https://$input"
        } else {
            "https://duckduckgo.com/?q=${Uri.encode(input)}"
        }
    }

    /** Dims and disables back/forward buttons when there is no history in that direction. */
    private fun updateNavButtons() {
        btnBack.isEnabled    = webView.canGoBack()
        btnBack.alpha        = if (webView.canGoBack())    1.0f else 0.35f
        btnForward.isEnabled = webView.canGoForward()
        btnForward.alpha     = if (webView.canGoForward()) 1.0f else 0.35f
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /** Default home page — iCloud Drive. */
        private const val ICLOUD_HOME = "https://www.icloud.com"
    }
}
