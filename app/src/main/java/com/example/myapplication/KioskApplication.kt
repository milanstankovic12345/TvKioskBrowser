package com.example.myapplication

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView

/**
 * CRITICAL: Initializes WebView early to prevent Android 9+ multi-process crashes
 * All AI solutions agree this is essential for Android 9+ compatibility
 */
class KioskApplication : Application() {
    companion object {
        private const val TAG = "KioskApp"
        var isWebViewAvailable: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        // CRITICAL FIX: Android 9+ WebView multi-process isolation
        isWebViewAvailable = initializeWebView()
        
        // Optional: Global crash handler
        setupCrashRecovery()
        
        Log.d(TAG, "Kiosk initialized on Android ${Build.VERSION.SDK_INT}, WebView available: $isWebViewAvailable")
    }

    private fun initializeWebView(): Boolean {
        try {
            // Android 9+ requires unique data directory per process
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = determineProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
            
            // Pre-warm WebView (prevents first-launch delay)
            // Note: We create and immediately destroy to initialize the provider
            WebView(this).destroy()
            
            Log.d(TAG, "WebView initialized successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "WebView init error: ${e.message}", e)
            return false
        }
    }

    private fun setupCrashRecovery() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL CRASH", throwable)
            
            // Restart the app after 2 seconds
            android.os.Handler(mainLooper).postDelayed({
                try {
                    val intent = android.content.Intent(this, MainActivity::class.java)
                    intent.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK 
                        or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    startActivity(intent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart app", e)
                }
            }, 2000)
            
            // Call original handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun determineProcessName(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                // Fallback for older Android versions
                val pid = android.os.Process.myPid()
                val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val runningApps = manager.runningAppProcesses
                runningApps?.firstOrNull { it.pid == pid }?.processName ?: packageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get process name", e)
            packageName
        }
    }
}

