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
import androidx.core.app.ServiceCompat
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
        // Promotion to foreground can legitimately fail: the FGS allowance may have been
        // revoked between startForegroundService() and here (user pressed Home/power in
        // that window), the app may be in the Restricted battery bucket, or an OEM
        // framework may only enforce at promotion time. `startForeground` then throws
        // ForegroundServiceStartNotAllowedException / SecurityException, which is
        // uncatchable from the caller side, so it has to be handled in the callback.
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }.isSuccess
        if (!promoted) {
            // stopSelf() is mandatory, not optional: a service started via
            // startForegroundService() that never reaches foreground state is killed
            // with ForegroundServiceDidNotStartInTimeException after ~5 s. Bailing out
            // here trades a crash for a silent no-op; the ViewModel keeps playing.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    /**
     * The transmit audio is owned by the ViewModel in the app process, so a task swipe
     * already cancels it. Drop the (now orphaned) notification rather than leaving an
     * ongoing indicator for a transmission that no longer exists.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
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
