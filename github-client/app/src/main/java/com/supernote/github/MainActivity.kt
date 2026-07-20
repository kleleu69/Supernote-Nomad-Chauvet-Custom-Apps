package com.supernote.github

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        configureWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(GITHUB_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings: WebSettings = webView.settings

        // JavaScript required for GitHub SPA navigation, Copilot, and notifications
        settings.javaScriptEnabled = true

        // DOM storage for session data (required by GitHub's frontend)
        settings.domStorageEnabled = true

        // Mobile user-agent for GitHub's responsive mobile layout
        settings.userAgentString = GITHUB_MOBILE_UA

        // Persistent cookies so the user stays logged in between sessions
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Zoom: useful on e-ink for adjusting text size
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)

        // Default cache behaviour — use network when available, cache otherwise
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Images on — needed to read issues, PRs, and Copilot responses
        settings.loadsImagesAutomatically = true

        // Keep text zoom at 100% for predictable layout on e-ink screen
        settings.textZoom = 100

        webView.scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        webView.webViewClient = object : WebViewClient() {
            // API 21–23 fallback
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(url)

            // API 24+ preferred path
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleUrl(request.url.toString())

            private fun handleUrl(url: String): Boolean {
                return try {
                    val host = android.net.Uri.parse(url).host ?: return false
                    // Stay inside WebView for all github.com domains
                    if (host.endsWith("github.com") ||
                        host.endsWith("githubusercontent.com") ||
                        host.endsWith("githubassets.com")
                    ) {
                        false
                    } else {
                        // Open external URLs in the system browser
                        startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                        true
                    }
                } catch (e: android.content.ActivityNotFoundException) {
                    // No app installed to handle this URL — stay in WebView
                    false
                } catch (e: java.net.URISyntaxException) {
                    // Malformed URL — ignore
                    false
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.visibility = View.GONE
                // Flush cookies on every page load so login state is durable
                CookieManager.getInstance().flush()
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                // Cancel on SSL errors — never bypass for security
                handler.cancel()
            }
        }

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
        }
    }

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
        // GitHub home — includes notifications, issues, PRs, and Copilot
        private const val GITHUB_URL = "https://github.com"

        // Mobile browser UA that gets GitHub's responsive mobile layout,
        // equivalent to what the GitHub Android app renders for web content
        private const val GITHUB_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
