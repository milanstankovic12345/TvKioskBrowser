package com.tvappbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tvappbrowser.util.NetworkMonitor
import com.tvappbrowser.viewmodel.SettingsViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var offlineIndicator: LinearLayout
    private lateinit var networkMonitor: NetworkMonitor
    
    // Bookmark overlay variables
    private lateinit var bookmarkOverlay: android.widget.FrameLayout
    private lateinit var recyclerMainBookmarks: androidx.recyclerview.widget.RecyclerView
    private lateinit var textNoBookmarks: TextView
    private var isOverlayOpen = false
    
    private val viewModel: SettingsViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    
    // Tap Gesture Detection
    private var tapCount = 0
    private var lastTapTime = 0L
    private val TRIPLE_TAP_DELAY = 500L
    
    private var reloadIntervalRunnable: Runnable? = null
    
    private var isOffline = false

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!KioskApplication.isWebViewAvailable) {
            Toast.makeText(this, "WebView not available", Toast.LENGTH_LONG).show()
            return
        }

        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        offlineIndicator = findViewById(R.id.offlineIndicator)
        bookmarkOverlay = findViewById<android.widget.FrameLayout>(R.id.bookmarkOverlay)
        recyclerMainBookmarks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerMainBookmarks)
        textNoBookmarks = findViewById<TextView>(R.id.textNoBookmarks)
        
        setupImmersiveMode()
        setupBackButtonHandling()
        
        // Listen to settings changes
        viewModel.settings.observe(this) { settings ->
            setupWebView()
            startReloadTimer()
            
            // Kiosk blocking (partial via pinned mode fallback)
            if (settings.kioskMode) {
                startLockTaskMode()
            } else {
                stopLockTaskMode()
            }
        }
        
        // Network state listener
        networkMonitor = NetworkMonitor(this) { connected ->
            runOnUiThread {
                if (connected) {
                    if (isOffline) {
                        isOffline = false
                        offlineIndicator.visibility = View.GONE
                        webView.reload()
                    }
                } else {
                    isOffline = true
                    offlineIndicator.visibility = View.VISIBLE
                    showOfflinePage()
                }
            }
        }
        networkMonitor.startMonitoring()
        
        // Secret Gesture overlay receiver
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Check if touch is near corners (top left, top right, bottom left, bottom right)
                val width = webView.width
                val height = webView.height
                
                val cornerSize = 150 // pixels
                val x = event.x
                val y = event.y
                
                val isCorner = (x < cornerSize && y < cornerSize) || // TL
                              (x > width - cornerSize && y < cornerSize) || // TR
                              (x < cornerSize && y > height - cornerSize) || // BL
                              (x > width - cornerSize && y > height - cornerSize) // BR
                              
                if (isCorner) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime > TRIPLE_TAP_DELAY) {
                        tapCount = 1
                    } else {
                        tapCount++
                    }
                    lastTapTime = currentTime
                    
                    if (tapCount == 3) {
                        tapCount = 0
                        launchSettingsOrPin()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
        
        // Load initial
        viewModel.loadSettings()
        
        // Show hint on first launch
        Toast.makeText(this, "Triple-tap any corner to open Settings", Toast.LENGTH_LONG).show()
    }
    
    private fun showOfflinePage() {
        webView.loadUrl("file:///android_res/raw/offline.html")
    }

    private fun setupImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = viewModel.settings.value ?: return
        val webSettings: WebSettings = webView.settings
        
        webSettings.javaScriptEnabled = settings.javascriptEnabled
        webSettings.domStorageEnabled = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        if (settings.clearCache) {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
            webView.clearCache(true)
        } else {
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        
        webView.setLayerType(
            if (settings.hardwareAccel) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE, 
            null
        )
        
        webSettings.userAgentString = webSettings.userAgentString + " TVAppBrowser"
        
        // Apply zoom level (textZoom dynamically scales the content without needing a page reload
        // and respects the device's display density, unlike setInitialScale)
        webSettings.textZoom = settings.zoomLevel
        
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // If it's a main frame error and we are actually connected
                if (request?.isForMainFrame == true && !isOffline) {
                    showOfflinePage()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                // For a kiosk, sometimes ignoring self-signed is needed. 
                // But generally proceed with caution.
                handler?.proceed()
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                // Suppress JS alerts
                result?.confirm()
                return true
            }
        }
        
        // Initial load
        val currentUrl = webView.url
        if (currentUrl == null || !currentUrl.contains(settings.url)) {
            val urlToLoad = if (!settings.url.startsWith("http")) "https://${settings.url}" else settings.url
            webView.loadUrl(urlToLoad)
        }
    }

    private fun startReloadTimer() {
        val settings = viewModel.settings.value ?: return
        
        reloadIntervalRunnable?.let { handler.removeCallbacks(it) }
        
        reloadIntervalRunnable = Runnable {
            if (!isOffline) {
                if (settings.clearCache) webView.clearCache(true)
                webView.reload()
            }
            handler.postDelayed(reloadIntervalRunnable!!, settings.refreshIntervalSec * 1000L)
        }
        
        handler.postDelayed(reloadIntervalRunnable!!, settings.refreshIntervalSec * 1000L)
    }

    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOverlayOpen) {
                    closeBookmarkOverlay()
                } else {
                    launchSettingsOrPin()
                }
            }
        })
    }
    
    private fun launchSettingsOrPin() {
        startActivity(Intent(this, PinActivity::class.java))
    }

    private fun openBookmarkOverlay() {
        if (isOverlayOpen) return
        isOverlayOpen = true
        bookmarkOverlay.visibility = View.VISIBLE
        // Update adapter
        val settings = viewModel.settings.value
        val bookmarks = settings?.bookmarks ?: emptyList()
        if (bookmarks.isEmpty()) {
            textNoBookmarks.visibility = View.VISIBLE
            recyclerMainBookmarks.visibility = View.GONE
        } else {
            textNoBookmarks.visibility = View.GONE
            recyclerMainBookmarks.visibility = View.VISIBLE
            recyclerMainBookmarks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            recyclerMainBookmarks.adapter = BookmarkAdapter(bookmarks, { clickedBookmark ->
                // onSiteClicked: Load URL, UPDATE App Start URL globally, and close
                var urlToLoad = clickedBookmark.url
                if (!urlToLoad.startsWith("http")) urlToLoad = "https://$urlToLoad"
                webView.loadUrl(urlToLoad)
                
                // Override persistent Start URL with the clicked bookmark
                val currentSettings = viewModel.settings.value
                if (currentSettings != null) {
                    val updatedSettings = currentSettings.copy(url = urlToLoad)
                    viewModel.saveSettings(updatedSettings)
                }
                
                closeBookmarkOverlay()
            }, {
                // onEditClicked: Does nothing in quick-launch overlay
                closeBookmarkOverlay()
            }, {
                // onDeleteClicked: Does nothing in quick-launch overlay
                closeBookmarkOverlay()
            })
            recyclerMainBookmarks.requestFocus()
        }
    }

    private fun closeBookmarkOverlay() {
        isOverlayOpen = false
        bookmarkOverlay.visibility = View.GONE
        webView.requestFocus()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // If overlay is open, intercept EVERYTHING except exactly what we handle
        if (isOverlayOpen) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Allow navigation within the recycler view
                        return super.dispatchKeyEvent(event)
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        // Allow enter/click
                        return super.dispatchKeyEvent(event)
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        closeBookmarkOverlay()
                        return true
                    }
                    else -> return true // Block everything else!
                }
            }
            return super.dispatchKeyEvent(event)
        }

        // If overlay is CLOSED, intercept DPAD_UP and DPAD_DOWN to open it.
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || 
                event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                openBookmarkOverlay()
                return true
            }
        }
        
        return super.dispatchKeyEvent(event)
    }

    private fun startLockTaskMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            Log.e("MainActivity", "Device not configured for LockTask.", e)
            // Fallback screen pinning could be used here
        }
    }
    
    private fun stopLockTaskMode() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            // Unpinned
        }
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
        viewModel.loadSettings()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stopMonitoring()
        reloadIntervalRunnable?.let { handler.removeCallbacks(it) }
        webView.destroy()
    }
}
