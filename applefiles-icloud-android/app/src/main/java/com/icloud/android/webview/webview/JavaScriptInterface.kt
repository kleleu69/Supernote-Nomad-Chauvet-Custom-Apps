package com.icloud.android.webview.webview

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.icloud.android.webview.util.NotificationHelper

class JavaScriptInterface(private val context: Context) {

    // Apple登录监听器接口
    interface AppleLoginListener {
        fun onAppleLoginRequested()
    }
    
    // 登录监听器
    private var appleLoginListener: AppleLoginListener? = null
    
    // 设置Apple登录监听器
    fun setAppleLoginListener(listener: AppleLoginListener) {
        this.appleLoginListener = listener
    }

    @JavascriptInterface
    fun onNewMail(count: String) {
        val mailCount = count.trim().toIntOrNull() ?: 0
        if (mailCount > 0) {
            NotificationHelper.showNewMailNotification(context, mailCount)
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    @JavascriptInterface
    fun handleAppleLogin() {
        // 通过监听器通知Activity处理Apple登录
        appleLoginListener?.onAppleLoginRequested()
    }
} 