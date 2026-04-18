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
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.transition.TransitionManager
import android.transition.AutoTransition
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
    
    // Browser bar views
    private lateinit var browserBarContainer: LinearLayout
    private lateinit var searchBarIcon: ImageView
    private lateinit var searchBarProgress: ProgressBar
    private lateinit var searchBarUrlInput: EditText
    private lateinit var searchBarBookmarkBtn: ImageButton
    private lateinit var searchBarSettingsBtn: ImageButton
    
    private lateinit var bookmarkHelper: com.tvappbrowser.util.BookmarkPreferencesHelper
    

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
        
        browserBarContainer = findViewById(R.id.browserBarContainer)
        searchBarIcon = findViewById(R.id.searchBarIcon)
        searchBarProgress = findViewById(R.id.searchBarProgress)
        searchBarUrlInput = findViewById(R.id.searchBarUrlInput)
        searchBarBookmarkBtn = findViewById(R.id.searchBarBookmarkBtn)
        searchBarSettingsBtn = findViewById(R.id.searchBarSettingsBtn)
        

        bookmarkHelper = com.tvappbrowser.util.BookmarkPreferencesHelper(this)
        
        setupBrowserBar()
        
        setupImmersiveMode()
        setupBackButtonHandling()
        
        // Listen to settings changes
        viewModel.settings.observe(this) { settings ->
            setupWebView()
            startReloadTimer()
            
            // Kiosk blocking (partial via pinned mode fallback)
            if (settings.kioskMode) {
                animateBrowserBar(false)
                startLockTaskMode()
            } else {
                animateBrowserBar(true)
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
    
    private fun setupBrowserBar() {
        searchBarSettingsBtn.setOnClickListener { launchSettingsOrPin() }
        
        searchBarUrlInput.showSoftInputOnFocus = false
        searchBarUrlInput.setOnClickListener { v ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
        
        searchBarUrlInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                val text = v.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (text.contains(" ") || (!text.contains(".") && !text.startsWith("http"))) {
                        // Search
                        webView.loadUrl("https://www.google.com/search?q=${android.net.Uri.encode(text)}")
                    } else if (!text.startsWith("http://") && !text.startsWith("https://")) {
                        webView.loadUrl("https://$text")
                    } else {
                        webView.loadUrl(text)
                    }
                }
                
                // Hide keyboard and clear focus
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                webView.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }
        
        searchBarUrlInput.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        
        searchBarBookmarkBtn.setOnClickListener {
            handleBookmarkStarClick()
        }
    }
    
    private fun animateBrowserBar(visible: Boolean) {
        if ((visible && browserBarContainer.visibility == View.VISIBLE) || 
            (!visible && browserBarContainer.visibility == View.GONE)) return
            
        val transition = AutoTransition()
        transition.duration = 300
        transition.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
        val root = findViewById<View>(android.R.id.content) as android.view.ViewGroup
        TransitionManager.beginDelayedTransition(root, transition)
        
        browserBarContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }
    
    private fun updateBookmarkStar(url: String?) {
        if (url == null) return
        val isBookmarked = bookmarkHelper.isBookmarked(url)
        if (isBookmarked) {
            searchBarBookmarkBtn.setImageResource(R.drawable.ic_star_filled)
        } else {
            searchBarBookmarkBtn.setImageResource(R.drawable.ic_star_outline)
        }
    }
    
    private fun handleBookmarkStarClick() {
        // Quick scale animation
        searchBarBookmarkBtn.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).withEndAction {
            searchBarBookmarkBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }.start()

        val currentUrl = webView.url ?: return
        val currentTitle = webView.title ?: currentUrl
        val existingBookmark = bookmarkHelper.getBookmarkByUrl(currentUrl)

        val dialogView = layoutInflater.inflate(R.layout.dialog_bookmark_entry, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setView(dialogView)
            .create()
            
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val inputName = dialogView.findViewById<EditText>(R.id.inputBookmarkName)
        val inputUrlDialog = dialogView.findViewById<EditText>(R.id.inputBookmarkUrl)
        val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSaveBookmark)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<android.widget.Button>(R.id.btnDelete)
        val textTitle = dialogView.findViewById<TextView>(R.id.textDialogTitle)

        inputName.showSoftInputOnFocus = false
        inputUrlDialog.showSoftInputOnFocus = false
        
        val showKeyboard = View.OnClickListener { v ->
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        inputName.setOnClickListener(showKeyboard)
        inputUrlDialog.setOnClickListener(showKeyboard)

        inputName.requestFocus()
        inputName.setText(existingBookmark?.name ?: currentTitle)
        inputUrlDialog.setText(existingBookmark?.url ?: currentUrl)

        if (existingBookmark != null) {
            textTitle.text = "Edit Bookmark"
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener {
                bookmarkHelper.deleteBookmark(existingBookmark.id)
                updateBookmarkStar(currentUrl)
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        } else {
            textTitle.text = "Add Bookmark"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val url = inputUrlDialog.text.toString().trim()
            if (name.isNotEmpty() && url.isNotEmpty()) {
                if (existingBookmark != null) {
                    bookmarkHelper.updateBookmark(com.tvappbrowser.model.Bookmark(existingBookmark.id, name, url))
                } else {
                    bookmarkHelper.addBookmark(com.tvappbrowser.model.Bookmark(java.util.UUID.randomUUID().toString(), name, url))
                    Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
                }
                updateBookmarkStar(url)
                
                // Force sync with viewmodel if it also caches it
                viewModel.loadSettings()
                
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter both Name and URL", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
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
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                searchBarIcon.visibility = View.GONE
                searchBarProgress.visibility = View.VISIBLE
                searchBarUrlInput.setText(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                searchBarProgress.visibility = View.GONE
                searchBarIcon.visibility = View.VISIBLE
                
                if (url != null) {
                    searchBarUrlInput.setText(url)
                    if (url.startsWith("https://")) {
                        searchBarIcon.setImageResource(R.drawable.ic_lock)
                    } else {
                        searchBarIcon.setImageResource(R.drawable.ic_globe)
                    }
                    updateBookmarkStar(url)
                }
            }
            
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
                val settings = viewModel.settings.value
                if (settings != null && !settings.kioskMode) {
                    finishAffinity()
                    kotlin.system.exitProcess(0)
                } else {
                    launchSettingsOrPin()
                }
            }
        })
    }
    
    private fun launchSettingsOrPin() {
        startActivity(Intent(this, PinActivity::class.java))
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            when (focusedView?.id) {
                R.id.webView -> {
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (webView.scrollY > 0) {
                                return super.dispatchKeyEvent(event)
                            } else {
                                searchBarUrlInput.requestFocus()
                                return true
                            }
                        }
                    }
                }
                R.id.searchBarUrlInput -> {
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            searchBarUrlInput.setSelection(0)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            webView.requestFocus()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            searchBarBookmarkBtn.requestFocus()
                            return true
                        }
                    }
                }
                R.id.searchBarBookmarkBtn -> {
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> return true
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            webView.requestFocus()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            searchBarUrlInput.requestFocus()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            searchBarSettingsBtn.requestFocus()
                            return true
                        }
                    }
                }
                R.id.searchBarSettingsBtn -> {
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> return true
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            webView.requestFocus()
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            searchBarBookmarkBtn.requestFocus()
                            return true
                        }
                    }
                }
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
