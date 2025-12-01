package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * CONSENSUS SOLUTION: All AI solutions agree on foreground service pattern for Android 9+
 * Addresses the core "cannot start activity from background" restriction
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_DELAY_MS = 2000L // 2-second delay (consensus from all solutions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot event: $action (Android ${Build.VERSION.SDK_INT})")
        
        if (!isBootAction(action)) {
            return
        }
        
        // Delay to ensure system is ready (prevents race conditions)
        Handler(Looper.getMainLooper()).postDelayed({
            launchKiosk(context)
        }, BOOT_DELAY_MS)
    }

    private fun isBootAction(action: String?): Boolean {
        return action == Intent.ACTION_BOOT_COMPLETED ||
               action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
               action == "android.intent.action.QUICKBOOT_POWERON" ||
               action == "com.hisense.action.BOOT_COMPLETED" ||
               action == "com.htc.intent.action.QUICKBOOT_POWERON"
    }

    private fun launchKiosk(context: Context) {
        // CRITICAL DECISION POINT: Android version-specific handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+: MUST use foreground service
            launchViaForegroundService(context)
        } else {
            // Android 7: Direct launch works
            launchActivityDirect(context)
        }
    }

    private fun launchViaForegroundService(context: Context) {
        Log.d(TAG, "Launching via foreground service (Android ${Build.VERSION.SDK_INT})")
        
        val serviceIntent = Intent(context, KioskLaunchService::class.java)
        serviceIntent.action = KioskLaunchService.ACTION_LAUNCH_KIOSK
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            // Fallback to direct launch (might fail on Android 9+, but worth trying)
            launchActivityDirect(context)
        }
    }

    private fun launchActivityDirect(context: Context) {
        Log.d(TAG, "Direct activity launch (Android 7 or fallback)")
        
        try {
            val activityIntent = Intent(context, MainActivity::class.java)
            activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity directly", e)
        }
    }
}

