package com.icloud.android.webview.webview

import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.util.Log

class ICloudWebChromeClient : WebChromeClient() {

    companion object {
        private const val TAG = "ICloudWebChromeClient"
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage != null) {
            Log.d(
                TAG,
                "JS[${consoleMessage.messageLevel()}] ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
            )
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Log.d(TAG, "JS alert from ${url ?: "unknown"}: ${message ?: ""}")
        result?.confirm()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Log.d(TAG, "JS confirm from ${url ?: "unknown"}: ${message ?: ""}")
        result?.confirm()
        return true
    }
} 