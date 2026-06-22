package com.atvriders.wsprtxrx.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atvriders.wsprtxrx.R

/**
 * Foreground service that keeps the ~110.6 s WSPR transmission alive when the app is
 * backgrounded or the screen turns off. The audio itself is rendered/played by the
 * ViewModel; this service exists only to hold a `mediaPlayback` foreground notification
 * so the OS does not throttle or kill the process mid-slot.
 *
 * Start/stop are best-effort: callers must tolerate the service failing to start (e.g.
 * background-start restrictions) and fall back to plain in-ViewModel playback.
 */
class TxForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "wspr_tx"
        private const val NOTIFICATION_ID = 4201

        /** Starts the service; returns false if the platform refused (caller falls back). */
        fun start(context: Context): Boolean = runCatching {
            val intent = Intent(context, TxForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        }.getOrDefault(false)

        /** Stops the service; safe to call even if it was never started. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TxForegroundService::class.java)) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.tx_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
                mgr.createNotificationChannel(channel)
            }
        }

        private fun buildNotification(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.tx_notification_title))
                .setContentText(context.getString(R.string.tx_notification_text))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
    }
}
