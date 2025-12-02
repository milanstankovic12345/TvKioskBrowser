package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

data class RecommendedSite(val name: String, val url: String)

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var settingsPanel: View
    private lateinit var inputUrl: EditText
    private lateinit var inputIntervalSec: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnAddSite: Button
    private lateinit var btnChangePin: Button
    private lateinit var seekZoom: SeekBar
    private lateinit var textZoomValue: TextView
    private lateinit var recommendedSitesContainer: LinearLayout
    
    // Track focusable views in settings panel for remote control navigation
    private val settingsFocusableViews = mutableListOf<View>()
    private var currentFocusIndex = 0
    
    private var recommendedSites: MutableList<RecommendedSite> = mutableListOf()
    
    private val reloadHandler: Handler = Handler(Looper.getMainLooper())
    private var reloadIntervalMs: Long = RELOAD_INTERVAL_MS
    private var currentUrl: String = START_URL
    private var zoomPercent: Int = DEFAULT_ZOOM_PERCENT

    private val reloadRunnable: Runnable = object : Runnable {
        override fun run() {
            if (::webView.isInitialized) {
                // Handler is on main looper, so this runs on UI thread
                webView.reload()
            }
            // Schedule next reload unconditionally
            reloadHandler.postDelayed(this, reloadIntervalMs)
        }
    }
    
    private fun startReloadTimer() {
        reloadHandler.removeCallbacks(reloadRunnable)
        reloadHandler.postDelayed(reloadRunnable, reloadIntervalMs)
    }
    
    companion object {
        private const val RELOAD_INTERVAL_MS = 300_000L // 5 minutes
        private const val START_URL = "https://www.google.com"
        private const val DEFAULT_ZOOM_PERCENT = 100
        private const val MIN_ZOOM_PERCENT = 50
        private const val MAX_ZOOM_PERCENT = 200
        private const val DEFAULT_PIN = "1234"
        
        private val DEFAULT_RECOMMENDED_SITES = listOf(
//            RecommendedSite("Google Gemini", "https://gemini.google.com"),
//            RecommendedSite("Gmail", "https://mail.google.com"),
//            RecommendedSite("Reddit", "https://www.reddit.com"),
//            RecommendedSite("Wikipedia", "https://www.wikipedia.org"),
//            RecommendedSite("YouTube", "https://www.youtube.com")

            RecommendedSite("Predmontaza L2", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin2Predmontaza?barChart=1"),
            RecommendedSite("Montaza L2", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin2Montaza?barChart=1"),
            RecommendedSite("Prevzem L2", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin2Prevzem?barChart=1"),
            RecommendedSite("Predmontaza L4", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin4Predmontaza?barChart=1"),
            RecommendedSite("Montaza L4", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin4Montaza?barChart=1"),
            RecommendedSite("Prevzem L4", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo2Lin4Prevzem?barChart=1"),
            RecommendedSite("Predmontaza1 L3", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo3Lin3Predmontaza1?barChart=1"),
            RecommendedSite("Predmontaza2 L3", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo3Lin3Predmontaza2?barChart=1"),
            RecommendedSite("Montaza L3", "http://itprod.ad.gorenje.si/VpogledLinijeValjevo/Valjevo3Lin3?barChart=1")
        )
    }
    
    private var currentPinDialog: AlertDialog? = null
    private var currentPinChangeDialog: AlertDialog? = null
    
    private inner class ReloadingWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            // Legacy callback: validate and decide
            val target = url ?: return true
            return !isAllowedUrl(target)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url?.toString() ?: return true
            return !isAllowedUrl(target)
        }
        
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Avoid logging full URLs; log host only when available
            try {
                val host = url?.let { android.net.Uri.parse(it).host }
                android.util.Log.d("MainActivity", "Page loaded" + (host?.let { ": host=$it" } ?: ""))
            } catch (_: Exception) { /* ignore */ }
            applyZoomToWebView(zoomPercent)
        }
        
        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            // Avoid leaking full URL in logs
            val host = try { failingUrl?.let { android.net.Uri.parse(it).host } } catch (_: Exception) { null }
            android.util.Log.e("MainActivity", "WebView error($errorCode)" + (host?.let { ", host=$it" } ?: ""))
            // Disabled recovery mechanism - only use user-configured reload interval
            // No automatic recovery reloads - let the normal reload timer handle refreshes
        }
        
        // CRITICAL: Handle renderer process crashes (Android 8+)
        override fun onRenderProcessGone(
            view: WebView?,
            detail: android.webkit.RenderProcessGoneDetail?
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail != null) {
                if (!detail.didCrash()) {
                    // Renderer killed by system for memory -> Just reload
                    android.util.Log.w("MainActivity", "Renderer killed by system, reloading...")
                    view?.reload()
                    return true
                }
            }
            // Actual crash -> Restart Activity
            android.util.Log.e("MainActivity", "Renderer crashed, restarting activity...")
            @Suppress("DEPRECATION")
            recreate()
            return true
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            // Set up immersive full-screen mode for Android TV (after setContentView)
            setupImmersiveMode()
            
            // Initialize WebView
            webView = findViewById(R.id.webView)
            settingsPanel = findViewById(R.id.settingsPanel)
            inputUrl = findViewById(R.id.inputUrl)
            inputIntervalSec = findViewById(R.id.inputIntervalSec)
            btnSave = findViewById(R.id.btnSave)
            btnCancel = findViewById(R.id.btnCancel)
            btnAddSite = findViewById(R.id.btnAddSite)
            btnChangePin = findViewById(R.id.btnChangePin)
            seekZoom = findViewById(R.id.seekZoom)
            textZoomValue = findViewById(R.id.textZoomValue)
            recommendedSitesContainer = findViewById(R.id.recommendedSitesContainer)

            // Load saved preferences if present
            loadPreferences()
            
            // Setup recommended sites
            setupRecommendedSites()
            bindSettingsButtons()
            setupWebView()
            
            // Load the specified URL
            webView.loadUrl(currentUrl)
            
            // Start periodic reload timer
            startReloadTimer()
            
            // Set up back button handling
            setupBackButtonHandling()
            
            // Set up remote control navigation
            setupRemoteControlNavigation()
            
            android.util.Log.d("MainActivity", "App initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupImmersiveMode() {
        try {
            // Keep screen on for TV environment
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Show when locked (Android 8+) - important for kiosk mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            
            // Use modern WindowInsetsController for API 30+ or fallback to deprecated method
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Modern approach for API 30+ - check if insetsController is available
                val insetsController = window.insetsController
                if (insetsController != null) {
                    insetsController.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    android.util.Log.d("MainActivity", "Immersive mode set using WindowInsetsController")
                } else {
                    // Fallback to deprecated method if insetsController is null
                    android.util.Log.w("MainActivity", "WindowInsetsController is null, falling back to deprecated method")
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
            } else {
                // Fallback for older APIs (deprecated but still functional)
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                android.util.Log.d("MainActivity", "Immersive mode set using deprecated method for API < 30")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up immersive mode: ${e.message}", e)
            // Don't throw the exception, just log it and continue
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        try {
            val webSettings: WebSettings = webView.settings
            
            // Enable JavaScript
            webSettings.javaScriptEnabled = true
            
            // Enable DOM storage
            webSettings.domStorageEnabled = true
            
            // Prefer compatibility mode to reduce risk while keeping broad support
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            
            // Enable zoom controls
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.displayZoomControls = false
            // Allow proper scaling behavior
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            
            // Set cache mode
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
            
            // Set user agent for better compatibility
            webSettings.userAgentString = webSettings.userAgentString + " AndroidTV"
            
            // Configure WebView client (includes renderer crash handling)
            webView.webViewClient = ReloadingWebViewClient()
            
            // Configure WebChrome client for better TV experience
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    android.util.Log.d("MainActivity", "Loading progress: $newProgress%")
                }
            }
            
            // Set up focus handling for TV navigation
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            webView.requestFocus()
            
            // Enable smooth scrolling for TV
            webView.isHorizontalScrollBarEnabled = false
            webView.isVerticalScrollBarEnabled = false
            
            android.util.Log.d("MainActivity", "WebView configured successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up WebView: ${e.message}", e)
            throw e
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-apply immersive mode when activity resumes
        setupImmersiveMode()
    }
    
    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If PIN dialog is showing, close it
                if (currentPinDialog?.isShowing == true) {
                    currentPinDialog?.dismiss()
                    currentPinDialog = null
                    return
                }
                // If PIN change dialog is showing, close it
                if (currentPinChangeDialog?.isShowing == true) {
                    currentPinChangeDialog?.dismiss()
                    currentPinChangeDialog = null
                    return
                }
                // If settings panel is visible, hide it
                if (::settingsPanel.isInitialized && settingsPanel.visibility == View.VISIBLE) {
                    settingsPanel.visibility = View.GONE
                    return
                }
                // Otherwise, show PIN entry to access settings
                showPinEntryDialog()
            }
        })
    }
    
    private fun showSettingsPanel() {
        if (!::settingsPanel.isInitialized) return
        settingsPanel.visibility = View.VISIBLE
        // Pre-fill with current values
        inputUrl.setText(currentUrl)
        inputIntervalSec.setText((reloadIntervalMs / 1000L).toString())
        // Setup zoom UI
        textZoomValue.text = "${zoomPercent}%"
        seekZoom.max = MAX_ZOOM_PERCENT - MIN_ZOOM_PERCENT
        val progress = (zoomPercent - MIN_ZOOM_PERCENT)
        seekZoom.progress = if (progress < 0) 0 else if (progress > seekZoom.max) seekZoom.max else progress
        
        // Refresh recommended sites display
        displayRecommendedSites()
        
        // Set focus to first field for remote control navigation
        resetSettingsPanelFocus()
    }
    
    private fun setupRemoteControlNavigation() {
        // Initial setup - will be rebuilt when sites are displayed
        rebuildFocusList()
        
        // Set focus change listeners to update current focus index
        updateFocusListeners()
    }
    
    private fun updateFocusListeners() {
        settingsFocusableViews.forEachIndexed { index, view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    // Find the actual index in case list changed
                    val actualIndex = settingsFocusableViews.indexOf(v)
                    if (actualIndex >= 0) {
                        currentFocusIndex = actualIndex
                    }
                    // Ensure smooth scrolling to focused view
                    v.post {
                        v.requestRectangleOnScreen(android.graphics.Rect(), true)
                    }
                }
            }
        }
    }
    
    private fun resetSettingsPanelFocus() {
        currentFocusIndex = 0
        if (settingsFocusableViews.isNotEmpty()) {
            settingsFocusableViews[0].requestFocus()
        }
    }

    @Suppress("DEPRECATION")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Handle PIN dialogs - number keys are handled in the dialog's onKeyListener
        // D-pad navigation is handled by system via nextFocus attributes
        // OK/Enter and Back are handled here
        if (currentPinDialog?.isShowing == true || currentPinChangeDialog?.isShowing == true) {
            val action = event.action
            val keyCode = event.keyCode
            
            if (action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // Find focused view in the dialog
                        val dialog = currentPinDialog ?: currentPinChangeDialog
                        val focusedView = dialog?.currentFocus
                        if (focusedView is Button) {
                            focusedView.performClick()
                            return true
                        }
                        // If on a PIN digit field, let the onKeyListener handle it
                        // Otherwise try to submit
                        if (currentPinDialog?.isShowing == true) {
                            val dialogView = currentPinDialog?.window?.decorView
                            val pinSubmitBtn = dialogView?.findViewById<Button>(R.id.pinSubmit)
                            pinSubmitBtn?.performClick()
                            return true
                        }
                        if (currentPinChangeDialog?.isShowing == true) {
                            val dialogView = currentPinChangeDialog?.window?.decorView
                            val pinChangeSubmit = dialogView?.findViewById<Button>(R.id.pinChangeSubmit)
                            pinChangeSubmit?.performClick()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        // Cancel dialog
                        currentPinDialog?.dismiss()
                        currentPinChangeDialog?.dismiss()
                        currentPinDialog = null
                        currentPinChangeDialog = null
                        return true
                    }
                }
            }
            // Let system handle D-pad navigation for PIN fields
            return false
        }
        
        // Only handle remote control when settings panel is visible
        if (::settingsPanel.isInitialized && settingsPanel.visibility == View.VISIBLE) {
            val action = event.action
            val keyCode = event.keyCode
            
            // Handle key down events only
            if (action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val currentView = settingsFocusableViews.getOrNull(currentFocusIndex)
                        // For RecommendedSite buttons, let system handle Up/Down to use nextFocus attributes
                        // This allows vertical navigation between btnSite and btnMenu within same site
                        if (currentView is Button && currentView.id == R.id.btnSite) {
                            return false // Let system handle with nextFocusUp
                        }
                        if (currentView is ImageButton && currentView.id == R.id.btnSiteMenu) {
                            return false // Let system handle with nextFocusUp
                        }
                        // Check if we're at the first recommended site - should go to SeekBar
                        if (currentView == btnAddSite || (currentView is Button && currentView.id == R.id.btnSite && currentView.tag == 0)) {
                            // Try to use nextFocusUp if available, otherwise navigate
                            if (currentView.nextFocusUpId != View.NO_ID) {
                                return false // Let system handle
                            }
                        }
                        // For other views, use sequential navigation
                        navigateSettingsFocus(-1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val currentView = settingsFocusableViews.getOrNull(currentFocusIndex)
                        // For RecommendedSite buttons, let system handle Up/Down to use nextFocus attributes
                        // This allows vertical navigation between btnSite and btnMenu within same site
                        if (currentView is Button && currentView.id == R.id.btnSite) {
                            return false // Let system handle with nextFocusDown
                        }
                        if (currentView is ImageButton && currentView.id == R.id.btnSiteMenu) {
                            return false // Let system handle with nextFocusDown
                        }
                        // Check if we're at the last recommended site - should go to Add New Site
                        val lastSiteIndex = recommendedSites.size - 1
                        if (currentView is Button && currentView.id == R.id.btnSite && currentView.tag == lastSiteIndex) {
                            // Try to navigate to Add New Site button
                            if (btnAddSite in settingsFocusableViews) {
                                val addIndex = settingsFocusableViews.indexOf(btnAddSite)
                                if (addIndex >= 0) {
                                    currentFocusIndex = addIndex
                                    btnAddSite.requestFocus()
                                    return true
                                }
                            }
                        }
                        // For other views, use sequential navigation
                        navigateSettingsFocus(1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // Handle left navigation
                        if (handleLeftKey()) {
                            return true
                        }
                        // If not handled, let system try
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        // Handle right navigation
                        if (handleRightKey()) {
                            return true
                        }
                        // If not handled, let system try
                        return false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // Activate current view (for buttons) or enter edit mode (for EditText)
                        handleCenterKey()
                        return true
                    }
                }
            }
        }
        
        return super.dispatchKeyEvent(event)
    }
    
    private fun navigateSettingsFocus(direction: Int) {
        if (settingsFocusableViews.isEmpty()) return
        
        // Ensure currentFocusIndex is valid
        if (currentFocusIndex < 0 || currentFocusIndex >= settingsFocusableViews.size) {
            currentFocusIndex = 0
        }
        
        val newIndex = (currentFocusIndex + direction).coerceIn(0, settingsFocusableViews.size - 1)
        if (newIndex != currentFocusIndex) {
            currentFocusIndex = newIndex
            val targetView = settingsFocusableViews[currentFocusIndex]
            targetView.requestFocus()
            // Scroll to view if needed (for ScrollView)
            targetView.requestRectangleOnScreen(android.graphics.Rect(), true)
        }
    }
    
    private fun handleLeftKey(): Boolean {
        val currentView = settingsFocusableViews.getOrNull(currentFocusIndex)
        
        when (currentView) {
            seekZoom -> {
                // Decrease SeekBar value
                val newProgress = (seekZoom.progress - 5).coerceAtLeast(0)
                seekZoom.progress = newProgress
                val percent = MIN_ZOOM_PERCENT + newProgress
                textZoomValue.text = "${percent}%"
                applyZoomToWebView(percent)
                return true
            }
            btnSave -> {
                // Navigate to Cancel button (they're horizontal)
                if (btnCancel in settingsFocusableViews) {
                    currentFocusIndex = settingsFocusableViews.indexOf(btnCancel)
                    btnCancel.requestFocus()
                    return true
                }
            }
            btnCancel -> {
                // From Cancel, can go to Add New Site button or previous element
                if (btnAddSite in settingsFocusableViews) {
                    val addIndex = settingsFocusableViews.indexOf(btnAddSite)
                    if (addIndex >= 0) {
                        currentFocusIndex = addIndex
                        btnAddSite.requestFocus()
                        return true
                    }
                }
            }
            is Button -> {
                // Check if this is a RecommendedSite button
                if (currentView.id == R.id.btnSite) {
                    navigateToPreviousSiteButton(currentView, isEditButton = false)
                    return true
                } else if (currentView == btnAddSite) {
                    // From Add New Site, navigate to last recommended site or previous element
                    // Let system handle or navigate to previous in list
                    navigateSettingsFocus(-1)
                    return true
                }
                // Other buttons - let system handle
                return false
            }
            is ImageButton -> {
                // Check if this is the edit menu button
                if (currentView.id == R.id.btnSiteMenu) {
                    navigateToPreviousSiteButton(currentView, isEditButton = true)
                    return true
                }
                // Other ImageButton - let system handle
                return false
            }
            is EditText -> {
                // For EditText, move cursor left
                val selection = currentView.selectionStart
                if (selection > 0) {
                    currentView.setSelection(selection - 1)
                    return true
                }
                // If at start, navigate to previous element
                navigateSettingsFocus(-1)
                return true
            }
            else -> {
                // Let system handle
                return false
            }
        }
        return false
    }
    
    private fun navigateToPreviousSiteButton(currentView: View, isEditButton: Boolean) {
        val currentIndex = currentView.tag as? Int ?: return
        
        // Find the previous site's corresponding button (btnSite or btnMenu)
        for (i in currentIndex - 1 downTo 0) {
            // Find the siteView with this index
            val siteView = recommendedSitesContainer.getChildAt(i) as? View
            if (siteView != null) {
                val targetButtonId = if (isEditButton) R.id.btnSiteMenu else R.id.btnSite
                val targetView = siteView.findViewById<View>(targetButtonId)
                
                if (targetView != null && targetView in settingsFocusableViews) {
                    val newIndex = settingsFocusableViews.indexOf(targetView)
                    if (newIndex >= 0) {
                        currentFocusIndex = newIndex
                        targetView.requestFocus()
                        targetView.requestRectangleOnScreen(android.graphics.Rect(), true)
                    }
                    return
                }
            }
        }
    }
    
    private fun navigateToNextSiteButton(currentView: View, isEditButton: Boolean) {
        val currentIndex = currentView.tag as? Int ?: return
        
        // Find the next site's corresponding button (btnSite or btnMenu)
        for (i in currentIndex + 1 until recommendedSitesContainer.childCount) {
            // Find the siteView at this position
            val siteView = recommendedSitesContainer.getChildAt(i) as? View
            if (siteView != null) {
                val targetButtonId = if (isEditButton) R.id.btnSiteMenu else R.id.btnSite
                val targetView = siteView.findViewById<View>(targetButtonId)
                
                if (targetView != null && targetView in settingsFocusableViews) {
                    val newIndex = settingsFocusableViews.indexOf(targetView)
                    if (newIndex >= 0) {
                        currentFocusIndex = newIndex
                        targetView.requestFocus()
                        targetView.requestRectangleOnScreen(android.graphics.Rect(), true)
                    }
                    return
                }
            }
        }
    }
    
    private fun handleRightKey(): Boolean {
        val currentView = settingsFocusableViews.getOrNull(currentFocusIndex)
        
        when (currentView) {
            seekZoom -> {
                // Increase SeekBar value
                val newProgress = (seekZoom.progress + 5).coerceAtMost(seekZoom.max)
                seekZoom.progress = newProgress
                val percent = MIN_ZOOM_PERCENT + newProgress
                textZoomValue.text = "${percent}%"
                applyZoomToWebView(percent)
                return true
            }
            btnCancel -> {
                // Navigate to Save button (they're horizontal)
                if (btnSave in settingsFocusableViews) {
                    currentFocusIndex = settingsFocusableViews.indexOf(btnSave)
                    btnSave.requestFocus()
                    return true
                }
            }
            btnAddSite -> {
                // From Add New Site, navigate to first recommended site or next element
                // Try to find first recommended site button
                if (recommendedSitesContainer.childCount > 0) {
                    val firstSiteView = recommendedSitesContainer.getChildAt(0) as? View
                    val firstBtnSite = firstSiteView?.findViewById<Button>(R.id.btnSite)
                    if (firstBtnSite != null && firstBtnSite in settingsFocusableViews) {
                        val newIndex = settingsFocusableViews.indexOf(firstBtnSite)
                        if (newIndex >= 0) {
                            currentFocusIndex = newIndex
                            firstBtnSite.requestFocus()
                            return true
                        }
                    }
                }
                // Otherwise navigate to next in list
                navigateSettingsFocus(1)
                return true
            }
            is Button -> {
                // Check if this is a RecommendedSite button
                if (currentView.id == R.id.btnSite) {
                    navigateToNextSiteButton(currentView, isEditButton = false)
                    return true
                } else if (currentView == btnAddSite) {
                    // Already handled above
                    return true
                }
                // Other buttons - let system handle
                return false
            }
            is ImageButton -> {
                // Check if this is the edit menu button
                if (currentView.id == R.id.btnSiteMenu) {
                    navigateToNextSiteButton(currentView, isEditButton = true)
                    return true
                }
                // Other ImageButton - let system handle
                return false
            }
            is EditText -> {
                // For EditText, move cursor right
                val selection = currentView.selectionStart
                val textLength = currentView.text?.length ?: 0
                if (selection < textLength) {
                    currentView.setSelection(selection + 1)
                    return true
                }
                // If at end, navigate to next element
                navigateSettingsFocus(1)
                return true
            }
            else -> {
                // Let system handle
                return false
            }
        }
        return false
    }
    
    private fun handleCenterKey() {
        val currentView = settingsFocusableViews.getOrNull(currentFocusIndex)
        
        when (currentView) {
            is Button -> {
                // Check if this is a site button (has tag with index)
                val siteIndex = currentView.tag as? Int
                if (siteIndex != null && currentView.id == R.id.btnSite) {
                    // This is a recommended site button - click it
                    currentView.performClick()
                } else {
                    // Regular button - simulate click
                    currentView.performClick()
                }
            }
            is ImageButton -> {
                // Check if this is the edit menu button
                val siteIndex = currentView.tag as? Int
                if (siteIndex != null && currentView.id == R.id.btnSiteMenu) {
                    // This is the edit button - open edit dialog
                    if (siteIndex >= 0 && siteIndex < recommendedSites.size) {
                        showEditSiteDialog(siteIndex, recommendedSites[siteIndex])
                    }
                } else {
                    // Other ImageButton - perform click
                    currentView.performClick()
                }
            }
            is EditText -> {
                // Enter edit mode - request focus and show keyboard if needed
                currentView.requestFocus()
                currentView.selectAll()
            }
            is SeekBar -> {
                // SeekBar is already interactive, just ensure focus
                currentView.requestFocus()
            }
            is LinearLayout -> {
                // Check if this is a recommended site view
                val btnSite = currentView.findViewById<Button>(R.id.btnSite)
                if (btnSite != null) {
                    // This is a recommended site - click the site button
                    btnSite.performClick()
                } else {
                    // Regular LinearLayout - just ensure focus
                    currentView.requestFocus()
                }
            }
            else -> {
                // For other views, try to find a clickable child or perform click
                if (currentView?.isClickable == true) {
                    currentView.performClick()
                }
            }
        }
    }

    private fun bindSettingsButtons() {
        seekZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = MIN_ZOOM_PERCENT + progress
                textZoomValue.text = "${percent}%"
                if (fromUser) {
                    applyZoomToWebView(percent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val newUrl = inputUrl.text?.toString()?.trim().orEmpty()
            val intervalSecText = inputIntervalSec.text?.toString()?.trim().orEmpty()
            val intervalSec = intervalSecText.toLongOrNull()
            val selectedZoom = MIN_ZOOM_PERCENT + seekZoom.progress
            if (newUrl.isNotEmpty() && intervalSec != null && intervalSec > 0 && isAllowedUrl(newUrl)) {
                currentUrl = newUrl
                reloadIntervalMs = intervalSec * 1000L
                zoomPercent = selectedZoom
                savePreferences(currentUrl, reloadIntervalMs, zoomPercent)
                // Apply immediately
                webView.setInitialScale(zoomPercent)
                webView.loadUrl(currentUrl)
                startReloadTimer()
                // Will also be applied on page finished
                applyZoomToWebView(zoomPercent)
                settingsPanel.visibility = View.GONE
            } else {
                android.widget.Toast.makeText(this, "Invalid URL or interval", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        btnCancel.setOnClickListener {
            settingsPanel.visibility = View.GONE
        }
        
        btnAddSite.setOnClickListener {
            showAddSiteDialog()
        }
        
        btnChangePin.setOnClickListener {
            showChangePinDialog()
        }
    }
    
    private fun setupRecommendedSites() {
        // Load recommended sites from preferences or use defaults
        loadRecommendedSites()
        displayRecommendedSites()
    }
    
    private fun displayRecommendedSites() {
        recommendedSitesContainer.removeAllViews()
        
        // Rebuild focus list properly (sites will be inserted)
        rebuildFocusList()
        
        recommendedSites.forEachIndexed { index, site ->
            val siteView = LayoutInflater.from(this).inflate(R.layout.item_recommended_site, recommendedSitesContainer, false)
            val btnSite = siteView.findViewById<Button>(R.id.btnSite)
            val btnMenu = siteView.findViewById<ImageButton>(R.id.btnSiteMenu)
            val textName = siteView.findViewById<TextView>(R.id.textSiteName)
            
            // Set site name (first letter or first 2 letters)
            val displayText = if (site.name.length >= 2) site.name.substring(0, 2).uppercase() else site.name.uppercase()
            btnSite.text = displayText
            textName.text = site.name
            
            // Click site button to open URL and update Start URL field
            btnSite.setOnClickListener {
                if (isAllowedUrl(site.url)) {
                    currentUrl = site.url
                    inputUrl.setText(site.url) // Update Start URL field
                    webView.loadUrl(currentUrl)
                    settingsPanel.visibility = View.GONE
                }
            }
            
            // Click menu button to edit
            btnMenu.setOnClickListener {
                showEditSiteDialog(index, site)
            }
            
            // Click on the site view itself to open URL
            siteView.setOnClickListener {
                if (isAllowedUrl(site.url)) {
                    currentUrl = site.url
                    inputUrl.setText(site.url) // Update Start URL field
                    webView.loadUrl(currentUrl)
                    settingsPanel.visibility = View.GONE
                }
            }
            
            // Make focusable for TV navigation
            // siteView itself is not directly focusable - only btnSite and btnMenu are
            siteView.isFocusable = false
            siteView.isFocusableInTouchMode = false
            btnSite.isFocusable = true
            btnSite.isFocusableInTouchMode = true
            btnMenu.isFocusable = true
            btnMenu.isFocusableInTouchMode = true
            
            // Store site index in button tag for later retrieval
            btnMenu.tag = index
            btnSite.tag = index
            // Also store index in siteView for finding buttons
            siteView.tag = index
            
            // Set up nextFocus attributes for proper navigation
            // First site button should be able to go up to SeekBar
            if (index == 0) {
                btnSite.nextFocusUpId = R.id.seekZoom
            }
            // Last site button should be able to go down to Add New Site
            if (index == recommendedSites.size - 1) {
                btnSite.nextFocusDownId = R.id.btnAddSite
                btnMenu.nextFocusDownId = R.id.btnAddSite
            }
            
            // Add only btnSite and btnMenu to focus list (before btnAddSite)
            // btnSite comes first, then btnMenu for each site
            val addSiteIndex = settingsFocusableViews.indexOf(btnAddSite)
            if (addSiteIndex >= 0) {
                settingsFocusableViews.add(addSiteIndex, btnSite)
                settingsFocusableViews.add(addSiteIndex + 1, btnMenu)
            } else {
                settingsFocusableViews.add(btnSite)
                settingsFocusableViews.add(btnMenu)
            }
            
            recommendedSitesContainer.addView(siteView)
        }
        
        // Update focus listeners after adding sites
        updateFocusListeners()
    }
    
    private fun rebuildFocusList() {
        // Clear and rebuild focus list in correct order
        settingsFocusableViews.clear()
        settingsFocusableViews.add(inputUrl)
        settingsFocusableViews.add(inputIntervalSec)
        settingsFocusableViews.add(seekZoom)
        // Site views will be added dynamically in displayRecommendedSites
        settingsFocusableViews.add(btnAddSite)
        settingsFocusableViews.add(btnChangePin)
        settingsFocusableViews.add(btnCancel)
        settingsFocusableViews.add(btnSave)
        
        // Make all views focusable
        settingsFocusableViews.forEach { view ->
            view.isFocusable = true
            view.isFocusableInTouchMode = true
        }
    }
    
    private fun showEditSiteDialog(index: Int, site: RecommendedSite) {
        val inputName = EditText(this)
        inputName.hint = "Site Name"
        inputName.setText(site.name)
        inputName.setPadding(32, 16, 32, 16)
        
        val inputUrl = EditText(this)
        inputUrl.hint = "URL (http:// or https://)"
        inputUrl.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        inputUrl.setText(site.url)
        inputUrl.setPadding(32, 16, 32, 16)
        
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.addView(inputName)
        container.addView(inputUrl)
        container.setPadding(32, 16, 32, 16)
        
        AlertDialog.Builder(this)
            .setTitle("Edit Site")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newUrl = inputUrl.text.toString().trim()
                if (newName.isNotEmpty() && newUrl.isNotEmpty() && isAllowedUrl(newUrl)) {
                    recommendedSites[index] = RecommendedSite(newName, newUrl)
                    saveRecommendedSites()
                    displayRecommendedSites()
                } else {
                    android.widget.Toast.makeText(this, "Invalid name or URL", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                recommendedSites.removeAt(index)
                saveRecommendedSites()
                displayRecommendedSites()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    
    private fun showAddSiteDialog() {
        val inputName = EditText(this)
        inputName.hint = "Site Name"
        inputName.setPadding(32, 16, 32, 16)
        
        val inputUrl = EditText(this)
        inputUrl.hint = "URL (http:// or https://)"
        inputUrl.inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        inputUrl.setPadding(32, 16, 32, 16)
        
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.addView(inputName)
        container.addView(inputUrl)
        container.setPadding(32, 16, 32, 16)
        
        AlertDialog.Builder(this)
            .setTitle("Add New Site")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newUrl = inputUrl.text.toString().trim()
                if (newName.isNotEmpty() && newUrl.isNotEmpty() && isAllowedUrl(newUrl)) {
                    recommendedSites.add(RecommendedSite(newName, newUrl))
                    saveRecommendedSites()
                    displayRecommendedSites()
                } else {
                    android.widget.Toast.makeText(this, "Invalid name or URL", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadRecommendedSites() {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        val savedSitesJson = prefs.getString("recommended_sites", null)
        
        if (!savedSitesJson.isNullOrEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(savedSitesJson)
                recommendedSites.clear()
                for (i in 0 until jsonArray.length()) {
                    val siteObj = jsonArray.getJSONObject(i)
                    val name = siteObj.getString("name")
                    val url = siteObj.getString("url")
                    recommendedSites.add(RecommendedSite(name, url))
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading recommended sites: ${e.message}")
                recommendedSites.clear()
                recommendedSites.addAll(DEFAULT_RECOMMENDED_SITES)
            }
        } else {
            // Use defaults if nothing saved
            recommendedSites.clear()
            recommendedSites.addAll(DEFAULT_RECOMMENDED_SITES)
        }
    }
    
    private fun saveRecommendedSites() {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        try {
            val jsonArray = org.json.JSONArray()
            recommendedSites.forEach { site ->
                val siteObj = org.json.JSONObject()
                siteObj.put("name", site.name)
                siteObj.put("url", site.url)
                jsonArray.put(siteObj)
            }
            prefs.edit().putString("recommended_sites", jsonArray.toString()).commit()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error saving recommended sites: ${e.message}")
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("start_url", null)
        val savedInterval = prefs.getLong("reload_interval_ms", -1L)
        val savedZoom = prefs.getInt("zoom_percent", -1)
        if (!savedUrl.isNullOrEmpty()) {
            currentUrl = savedUrl
        }
        if (savedInterval > 0) {
            reloadIntervalMs = savedInterval
        }
        if (savedZoom in MIN_ZOOM_PERCENT..MAX_ZOOM_PERCENT) {
            zoomPercent = savedZoom
        }
        // Recommended sites are loaded separately in setupRecommendedSites()
    }
    
    private fun getPin(): String {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        return prefs.getString("admin_pin", DEFAULT_PIN) ?: DEFAULT_PIN
    }
    
    private fun savePin(pin: String) {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        prefs.edit().putString("admin_pin", pin).commit()
    }
    
    private fun showPinEntryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin_entry, null)
        val pinDigit1 = dialogView.findViewById<EditText>(R.id.pinDigit1)
        val pinDigit2 = dialogView.findViewById<EditText>(R.id.pinDigit2)
        val pinDigit3 = dialogView.findViewById<EditText>(R.id.pinDigit3)
        val pinDigit4 = dialogView.findViewById<EditText>(R.id.pinDigit4)
        val pinError = dialogView.findViewById<TextView>(R.id.pinError)
        val pinCancel = dialogView.findViewById<Button>(R.id.pinCancel)
        val pinSubmitBtn = dialogView.findViewById<Button>(R.id.pinSubmit)
        
        val pinDigits = listOf(pinDigit1, pinDigit2, pinDigit3, pinDigit4)
        
        // Set up auto-advance for PIN digits
        pinDigits.forEachIndexed { index, editText ->
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // Handle number keys (0-9)
                    if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                        val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                        editText.setText(digit)
                        // Move to next field
                        if (index < pinDigits.size - 1) {
                            pinDigits[index + 1].requestFocus()
                        } else {
                            // Last digit - submit
                            pinSubmitBtn.performClick()
                        }
                        return@setOnKeyListener true
                    }
                    // Handle backspace/delete
                    if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (editText.text.isNotEmpty()) {
                            editText.setText("")
                        } else if (index > 0) {
                            pinDigits[index - 1].requestFocus()
                            pinDigits[index - 1].setText("")
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }
            
            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s?.length == 1 && index < pinDigits.size - 1) {
                        pinDigits[index + 1].requestFocus()
                    }
                }
            })
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        currentPinDialog = dialog
        
        pinCancel.setOnClickListener {
            dialog.dismiss()
            currentPinDialog = null
        }
        
        pinSubmitBtn.setOnClickListener {
            val enteredPin = "${pinDigit1.text}${pinDigit2.text}${pinDigit3.text}${pinDigit4.text}"
            if (enteredPin.length == 4) {
                if (enteredPin == getPin()) {
                    dialog.dismiss()
                    currentPinDialog = null
                    showSettingsPanel()
                } else {
                    pinError.text = "Incorrect PIN"
                    pinError.visibility = View.VISIBLE
                    // Clear PIN fields
                    pinDigits.forEach { it.setText("") }
                    pinDigit1.requestFocus()
                }
            } else {
                pinError.text = "Please enter 4 digits"
                pinError.visibility = View.VISIBLE
            }
        }
        
        // Handle Enter key on last digit
        pinDigit4.setOnEditorActionListener { _, _, _ ->
            pinSubmitBtn.performClick()
            true
        }
        
        dialog.show()
        // Focus first digit
        pinDigit1.requestFocus()
    }
    
    private fun showChangePinDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_pin, null)
        val newPinDigit1 = dialogView.findViewById<EditText>(R.id.newPinDigit1)
        val newPinDigit2 = dialogView.findViewById<EditText>(R.id.newPinDigit2)
        val newPinDigit3 = dialogView.findViewById<EditText>(R.id.newPinDigit3)
        val newPinDigit4 = dialogView.findViewById<EditText>(R.id.newPinDigit4)
        val confirmPinDigit1 = dialogView.findViewById<EditText>(R.id.confirmPinDigit1)
        val confirmPinDigit2 = dialogView.findViewById<EditText>(R.id.confirmPinDigit2)
        val confirmPinDigit3 = dialogView.findViewById<EditText>(R.id.confirmPinDigit3)
        val confirmPinDigit4 = dialogView.findViewById<EditText>(R.id.confirmPinDigit4)
        val pinChangeError = dialogView.findViewById<TextView>(R.id.pinChangeError)
        val pinChangeCancel = dialogView.findViewById<Button>(R.id.pinChangeCancel)
        val pinChangeSubmit = dialogView.findViewById<Button>(R.id.pinChangeSubmit)
        
        val newPinDigits = listOf(newPinDigit1, newPinDigit2, newPinDigit3, newPinDigit4)
        val confirmPinDigits = listOf(confirmPinDigit1, confirmPinDigit2, confirmPinDigit3, confirmPinDigit4)
        
        // Set up auto-advance for PIN digits (same as PIN entry)
        (newPinDigits + confirmPinDigits).forEachIndexed { index, editText ->
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                        val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                        editText.setText(digit)
                        val currentList = if (index < 4) newPinDigits else confirmPinDigits
                        val currentIndex = if (index < 4) index else index - 4
                        if (currentIndex < currentList.size - 1) {
                            currentList[currentIndex + 1].requestFocus()
                        }
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (editText.text.isNotEmpty()) {
                            editText.setText("")
                        } else {
                            val currentList = if (index < 4) newPinDigits else confirmPinDigits
                            val currentIndex = if (index < 4) index else index - 4
                            if (currentIndex > 0) {
                                currentList[currentIndex - 1].requestFocus()
                                currentList[currentIndex - 1].setText("")
                            }
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }
            
            editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s?.length == 1) {
                        val currentList = if (index < 4) newPinDigits else confirmPinDigits
                        val currentIndex = if (index < 4) index else index - 4
                        if (currentIndex < currentList.size - 1) {
                            currentList[currentIndex + 1].requestFocus()
                        }
                    }
                }
            })
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        currentPinChangeDialog = dialog
        
        pinChangeCancel.setOnClickListener {
            dialog.dismiss()
            currentPinChangeDialog = null
        }
        
        pinChangeSubmit.setOnClickListener {
            val newPin = "${newPinDigit1.text}${newPinDigit2.text}${newPinDigit3.text}${newPinDigit4.text}"
            val confirmPin = "${confirmPinDigit1.text}${confirmPinDigit2.text}${confirmPinDigit3.text}${confirmPinDigit4.text}"
            
            if (newPin.length != 4 || confirmPin.length != 4) {
                pinChangeError.text = "Please enter 4 digits in both fields"
                pinChangeError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            
            if (newPin != confirmPin) {
                pinChangeError.text = "PINs do not match"
                pinChangeError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            
            savePin(newPin)
            dialog.dismiss()
            currentPinChangeDialog = null
            android.widget.Toast.makeText(this, "PIN changed successfully", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
        newPinDigit1.requestFocus()
    }

    private fun savePreferences(url: String, intervalMs: Long, zoomPercent: Int) {
        val prefs = getSharedPreferences("display_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("start_url", url)
            .putLong("reload_interval_ms", intervalMs)
            .putInt("zoom_percent", zoomPercent)
            .commit()
    }

    private fun applyZoomToWebView(percent: Int) {
        val clamped = percent.coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)
        val scaleFloat = clamped / 100.0
        val js = """
            (function(){
                try {
                    var p = ${clamped};
                    var z = p + '%';
                    var s = ${scaleFloat};
                    var inv = (s === 0 ? 1 : (1.0/s));
                    // Apply CSS zoom (widely supported in WebView)
                    var de = document.documentElement; if (de){ de.style.zoom = z; de.style.transformOrigin='0 0'; de.style.transform='scale('+s+')'; }
                    var b = document.body; if (b){ b.style.zoom = z; b.style.transformOrigin='0 0'; b.style.transform='scale('+s+')'; }
                    // Ensure viewport exists so scale behaves consistently
                    var vp = document.querySelector('meta[name="viewport"]');
                    if (!vp){ vp = document.createElement('meta'); vp.name='viewport'; if (document.head) document.head.appendChild(vp); }
                    if (vp){ vp.setAttribute('content','width=device-width, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0'); }
                } catch(e) {}
                return true;
            })();
        """.trimIndent()
        if (::webView.isInitialized) {
            // Hint WebView about initial scale as well
            webView.setInitialScale(clamped)
            webView.evaluateJavascript(js, null)
        }
    }

    private fun isAllowedUrl(url: String): Boolean {
        try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            
            // Only allow http and https schemes (standard web browsing)
            if (scheme != "http" && scheme != "https") return false
            
            // Must have a valid host
            val host = uri.host ?: return false
            if (host.isBlank()) return false
            
            // Allow any valid http/https URL (like a standard browser)
            return true
        } catch (_: Exception) {
            return false
        }
    }
    
    override fun onDestroy() {
        // Stop periodic reloads
        reloadHandler.removeCallbacks(reloadRunnable)
        // Clean up WebView
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}