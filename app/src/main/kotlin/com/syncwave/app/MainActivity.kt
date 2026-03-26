package com.syncwave.app

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: FrameLayout
    private lateinit var errorText: TextView

    private val serverUrl = BuildConfig.SERVER_URL

    // Tracks whether JS has explicitly enabled background play
    private var bgPlayEnabled = false
    private var bgServiceBound = false

    private val bgServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { bgServiceBound = true }
        override fun onServiceDisconnected(name: ComponentName?) { bgServiceBound = false }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView      = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar  = findViewById(R.id.progressBar)
        errorView    = findViewById(R.id.errorView)
        errorText    = findViewById(R.id.errorText)

        setupWebView()
        setupSwipeRefresh()
        loadApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled                = true
            domStorageEnabled                = true
            allowFileAccess                  = true
            allowContentAccess               = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode                        = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            displayZoomControls              = false
            builtInZoomControls              = false
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            databaseEnabled                  = true
        }

        // Pass `this` so AndroidBridge can call startBgService / stopBgService
        webView.addJavascriptInterface(AndroidBridge(this, this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                errorView.visibility   = View.GONE
                webView.visibility     = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility    = View.GONE
                swipeRefresh.isRefreshing = false
                injectTheme()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    progressBar.visibility    = View.GONE
                    webView.visibility        = View.GONE
                    errorView.visibility      = View.VISIBLE
                    swipeRefresh.isRefreshing = false
                    errorText.text =
                        "Cannot connect to SyncWave server.\n\n" +
                        "Make sure Termux server is running.\n\n" +
                        "${error?.description}"
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (!url.startsWith(serverUrl) && (url.startsWith("http") || url.startsWith("https"))) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(getColor(R.color.cyan))
        swipeRefresh.setOnRefreshListener { loadApp() }
    }

    private fun loadApp() {
        errorView.visibility   = View.GONE
        webView.visibility     = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(serverUrl)
    }

    private fun injectTheme() {
        val js = """
            (function(){
                var m = document.querySelector('meta[name=viewport]');
                if(!m){ m=document.createElement('meta'); m.name='viewport'; document.head.appendChild(m); }
                m.content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no';
                document.body.style.userSelect='none';
                document.body.style.webkitUserSelect='none';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        // Stop service only if user hasn't explicitly enabled bg play
        if (!bgPlayEnabled) stopBgService()
    }

    override fun onPause() {
        super.onPause()
        // Start service only if user explicitly enabled bg play
        if (bgPlayEnabled) startBgService()
        // DO NOT call webView.onPause() — suspends the renderer and kills audio
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.onPause()
        webView.destroy()
        stopBgService()
    }

    // Public so AndroidBridge can call them from JS
    fun startBgService() {
        bgPlayEnabled = true
        val intent = Intent(this, AudioForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (!bgServiceBound) {
            bindService(intent, bgServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopBgService() {
        bgPlayEnabled = false
        try {
            if (bgServiceBound) {
                unbindService(bgServiceConnection)
                bgServiceBound = false
            }
        } catch (e: Exception) { /* already unbound */ }
        stopService(Intent(this, AudioForegroundService::class.java))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
