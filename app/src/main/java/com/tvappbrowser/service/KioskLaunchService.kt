package com.tvappbrowser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tvappbrowser.MainActivity

class KioskLaunchService : Service() {
    companion object {
        private const val TAG = "KioskLaunchService"
        const val ACTION_LAUNCH_KIOSK = "com.tvappbrowser.LAUNCH_KIOSK"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kiosk_launch_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KioskLaunchService started")
        
        // Android 8+ MUST show notification for foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (intent?.action == ACTION_LAUNCH_KIOSK) {
            launchMainActivity()
        }
        
        // Stop service immediately after launching
        stopSelf()
        
        return START_NOT_STICKY
    }

    private fun launchMainActivity() {
        try {
            val activityIntent = Intent(this, MainActivity::class.java)
            activityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(activityIntent)
            Log.d(TAG, "MainActivity launched from foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TV Background Service",
                NotificationManager.IMPORTANCE_NONE
            ).apply {
                description = "Keeps TV App functioning in background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Browser Background")
            .setContentText("Running background tasks")
            // Can use a transparent icon or the default one
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
