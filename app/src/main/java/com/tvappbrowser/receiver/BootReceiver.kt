package com.tvappbrowser.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tvappbrowser.MainActivity
import com.tvappbrowser.repository.SettingsRepository
import com.tvappbrowser.service.KioskLaunchService

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_DELAY_MS = 2000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Boot event: $action")
        
        if (!isBootAction(action)) return
        
        val settings = SettingsRepository(context).getSettings()
        if (!settings.startOnBoot) {
            Log.d(TAG, "Start on boot disabled in settings, ignoring.")
            return
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            launchViaForegroundService(context)
        } else {
            launchActivityDirect(context)
        }
    }

    private fun launchViaForegroundService(context: Context) {
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
            launchActivityDirect(context)
        }
    }

    private fun launchActivityDirect(context: Context) {
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
