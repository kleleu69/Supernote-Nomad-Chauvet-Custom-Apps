package com.icloud.android.webview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.icloud.android.webview.auth.AppleAuthHandler
import com.icloud.android.webview.auth.AppleAuthResult
import com.icloud.android.webview.databinding.ActivityMainBinding
import com.icloud.android.webview.webview.ICloudWebChromeClient
import com.icloud.android.webview.webview.ICloudWebClient
import com.icloud.android.webview.webview.JavaScriptInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), JavaScriptInterface.AppleLoginListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var jsInterface: JavaScriptInterface
    private lateinit var appleAuthHandler: AppleAuthHandler

    private val iCloudUrl = "https://www.icloud.com/iclouddrive/"
    private val legacyWritePermissionRequestCode = 2001
    private val manageStorageRequestCode = 2002
    private var pendingDownload: PendingDownload? = null

    /** Supernote Chauvet internal sync folder */
    private val syncFolderPath: String
        get() = Environment.getExternalStorageDirectory().absolutePath + "/Document/iCloud"

    private data class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        appleAuthHandler = AppleAuthHandler(this)
        ensureStorageAccess()
        setupWebView()
        setupSwipeRefresh()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                binding.webView.reload()
                true
            }
            R.id.action_sync_target -> {
                Toast.makeText(this, getString(R.string.sync_target_path), Toast.LENGTH_LONG).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
        }

        CookieManager.getInstance().apply {
            setAcceptThirdPartyCookies(binding.webView, true)
            setAcceptCookie(true)
        }

        jsInterface = JavaScriptInterface(this)
        jsInterface.setAppleLoginListener(this)
        binding.webView.addJavascriptInterface(jsInterface, "Android")

        binding.webView.webViewClient = ICloudWebClient(binding.progressBar, appleAuthHandler)
        binding.webView.webChromeClient = ICloudWebChromeClient()
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.isNullOrBlank()) return@setDownloadListener

            pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
            if (!hasStorageAccess()) {
                requestStorageAccess()
                return@setDownloadListener
            }

            startSyncDownload(url, userAgent, contentDisposition, mimeType)
        }
        binding.webView.loadUrl(iCloudUrl)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val data = intent.data ?: return
        if (data.scheme == "com.icloud.android.callback") {
            processAuthResult(AppleAuthHandler.handleCallback(data))
        }
    }

    private fun processAuthResult(result: AppleAuthResult?) {
        if (result == null) return

        if (result.success && result.code != null) {
            Toast.makeText(this, getString(R.string.apple_login_success), Toast.LENGTH_SHORT).show()
            appleAuthHandler.completeLogin(binding.webView, result.code)
        } else {
            val errorMsg = result.error ?: getString(R.string.apple_login_cancelled)
            Toast.makeText(this, getString(R.string.apple_login_failed, errorMsg), Toast.LENGTH_LONG).show()
        }
    }

    override fun onAppleLoginRequested() {
        appleAuthHandler.startAuth(getString(R.string.apple_login_client_id))
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != legacyWritePermissionRequestCode) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ensureSyncFolderExists()
            pendingDownload?.let {
                startSyncDownload(it.url, it.userAgent, it.contentDisposition, it.mimeType)
            }
        } else {
            Toast.makeText(this, getString(R.string.sync_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == manageStorageRequestCode) {
            if (hasStorageAccess()) {
                ensureSyncFolderExists()
                pendingDownload?.let {
                    startSyncDownload(it.url, it.userAgent, it.contentDisposition, it.mimeType)
                }
            } else {
                Toast.makeText(this, getString(R.string.sync_permission_needed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ensureStorageAccess() {
        if (!hasStorageAccess()) {
            requestStorageAccess()
        } else {
            ensureSyncFolderExists()
        }
    }

    private fun ensureSyncFolderExists() {
        val syncDir = File(syncFolderPath)
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                this.data = Uri.fromParts("package", packageName, null)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, manageStorageRequestCode)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                legacyWritePermissionRequestCode
            )
        }
    }

    private fun startSyncDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        Toast.makeText(this, getString(R.string.sync_download_started, fileName), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = downloadIntoSyncFolder(url, userAgent, mimeType, fileName)
            if (result.isSuccess) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sync_download_completed, fileName),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.sync_download_failed,
                        result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun downloadIntoSyncFolder(
        url: String,
        userAgent: String?,
        mimeType: String?,
        fileName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", userAgent ?: binding.webView.settings.userAgentString)

                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }

            try {
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }

                connection.inputStream.use { input ->
                    val targetDir = File(syncFolderPath)
                    if (!targetDir.exists() && !targetDir.mkdirs()) {
                        throw IOException("Failed to create sync folder: $syncFolderPath")
                    }

                    val targetFile = File(targetDir, fileName)
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
