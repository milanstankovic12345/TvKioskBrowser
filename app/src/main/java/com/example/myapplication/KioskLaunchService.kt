package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * CRITICAL COMPONENT: Foreground service that launches activity from background
 * This is the consensus solution from all AI responses for Android 9+
 */
class KioskLaunchService : Service() {
    companion object {
        private const val TAG = "KioskLaunchService"
        const val ACTION_LAUNCH_KIOSK = "com.example.myapplication.LAUNCH"
        
        private const val CHANNEL_ID = "kiosk_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting...")
        
        // CRITICAL: Start foreground IMMEDIATELY (within 5 seconds on Android 9+)
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (intent != null && ACTION_LAUNCH_KIOSK == intent.action) {
            // Small delay to ensure foreground state is established
            Handler(Looper.getMainLooper()).postDelayed({
                launchKioskActivity()
                stopServiceAfterDelay()
            }, 500)
        }
        
        return START_STICKY
    }

    private fun launchKioskActivity() {
        try {
            val activityIntent = Intent(this, MainActivity::class.java)
            
            // CRITICAL FLAGS for background launch
            var flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
            
            // Android 11+ additional flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            
            activityIntent.addFlags(flags)
            startActivity(activityIntent)
            
            Log.d(TAG, "Kiosk activity launched successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity", e)
        }
    }

    private fun stopServiceAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                stopForeground(true)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping service", e)
            }
        }, 2000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Required for kiosk mode startup"
            channel.setShowBadge(false)
            channel.setSound(null, null)
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        
        var pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags = pendingFlags or PendingIntent.FLAG_IMMUTABLE
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kiosk Starting")
            .setContentText("Display initializing...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

