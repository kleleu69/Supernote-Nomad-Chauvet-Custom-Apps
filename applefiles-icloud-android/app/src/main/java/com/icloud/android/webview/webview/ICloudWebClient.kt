package com.icloud.android.webview.webview

import android.graphics.Bitmap
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import com.icloud.android.webview.auth.AppleAuthHandler

class ICloudWebClient(
    private val progressBar: ProgressBar,
    private val appleAuthHandler: AppleAuthHandler? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        if (url.startsWith("https://appleid.apple.com/") || url.contains("apple.com/auth")) {
            appleAuthHandler?.let {
                val clientId = view?.context?.resources?.getString(
                    view.context.resources.getIdentifier("apple_login_client_id", "string", view.context.packageName)
                ) ?: return false
                it.startAuth(clientId)
                return true
            }
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        progressBar.visibility = View.VISIBLE
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        progressBar.visibility = View.GONE
        appleAuthHandler?.injectAppleLoginHandler(view ?: return)
    }
}