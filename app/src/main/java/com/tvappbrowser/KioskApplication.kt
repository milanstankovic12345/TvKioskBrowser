package com.tvappbrowser

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView

class KioskApplication : Application() {
    companion object {
        private const val TAG = "KioskApp"
        var isWebViewAvailable: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        
        // Android 9+ WebView multi-process isolation
        isWebViewAvailable = initializeWebView()
        setupCrashRecovery()
    }

    private fun initializeWebView(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = getProcessNameFallback()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
            
            WebView(this).destroy()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "WebView init error: ${e.message}", e)
            return false
        }
    }

    private fun getProcessNameFallback(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName() ?: packageName
            } else {
                packageName
            }
        } catch (e: Exception) {
            packageName
        }
    }

    private fun setupCrashRecovery() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL CRASH", throwable)
            
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
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
