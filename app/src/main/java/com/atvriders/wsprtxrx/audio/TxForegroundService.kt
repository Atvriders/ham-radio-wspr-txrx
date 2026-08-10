package com.atvriders.wsprtxrx.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.atvriders.wsprtxrx.MainActivity
import com.atvriders.wsprtxrx.R
import com.atvriders.wsprtxrx.WsprApp

/**
 * Foreground service that keeps the WSPR transmit alive from the moment the user taps
 * Transmit — through the up-to-two-minute wait for the even UTC minute and the ~110.6 s
 * transmission itself. The audio is rendered/played by the ViewModel; this service holds
 * the `mediaPlayback` foreground notification (and a bounded partial wake lock) so the
 * OS neither throttles the process nor denies audio focus mid-slot.
 *
 * Start/stop are best-effort: callers must tolerate the service failing to start (e.g.
 * background-start restrictions) and fall back to plain in-ViewModel playback.
 */
class TxForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Route Stop through the app-scoped signal so the transmit *job* ends. A bare
            // stopSelf() here would tear down the notification while the tone kept
            // playing — strictly worse than having no Stop action at all.
            runCatching {
                (applicationContext as? WsprApp)?.container?.onTxStopRequested?.invoke()
            }
            stopSelf()
            return START_NOT_STICKY
        }

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
                buildNotification(this, endAtMs = intent?.getLongExtra(EXTRA_END_AT, 0L) ?: 0L),
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

        acquireWakeLock()
        return START_NOT_STICKY
    }

    /**
     * The transmit audio is owned by the ViewModel in the app process, so a task swipe
     * already cancels it. Stop the transmit explicitly and drop the now-orphaned
     * notification rather than leaving an ongoing indicator for a dead transmission.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            (applicationContext as? WsprApp)?.container?.onTxStopRequested?.invoke()
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
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

    /**
     * A foreground service alone does not keep the CPU awake, and the wait + transmit is
     * a timing-exact ~231 s window. Bounded so a leak can never hold the CPU
     * indefinitely; also released in [onDestroy].
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "wspr_tx"
        private const val NOTIFICATION_ID = 4201
        private const val WAKE_LOCK_TAG = "wsprtxrx:transmit"

        /**
         * Bounded wake-lock budget: up to 120 s waiting for the even UTC minute plus the
         * 110.6 s transmission, with headroom. (The audit's 150 s figure predates moving
         * the keep-alive start to the Transmit tap, which is what makes the wait
         * covered at all.)
         */
        private const val WAKE_LOCK_TIMEOUT_MS = 250_000L

        /** Notification-action intent: cancel the transmit, not merely the service. */
        const val ACTION_STOP = "com.atvriders.wsprtxrx.action.STOP_TX"

        /** Wall-clock epoch millis at which the transmission ends (0 = still waiting). */
        private const val EXTRA_END_AT = "end_at_ms"

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

        /**
         * Switches the notification from "waiting" to "transmitting", giving the
         * chronometer a target so it counts down the remaining seconds of the slot.
         *
         * Posted with [NotificationManagerCompat.notify] against the same id the service
         * is already foregrounded with, rather than a second `startService` — updating an
         * existing notification has no background-start semantics to trip over.
         */
        @SuppressLint("MissingPermission")
        fun transmitting(context: Context, endAtMs: Long) {
            runCatching {
                ensureChannel(context)
                // POST_NOTIFICATIONS is declared in the manifest; if the user denied it
                // there is simply no shade entry to update, and notify() is a no-op.
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, buildNotification(context, endAtMs))
            }
        }

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
                channel.description = context.getString(R.string.tx_notification_channel_desc)
                channel.setShowBadge(false)
                mgr.createNotificationChannel(channel)
            }
        }

        private fun buildNotification(context: Context, endAtMs: Long): Notification {
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stopIntent = PendingIntent.getService(
                context,
                1,
                Intent(context, TxForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val transmitting = endAtMs > 0L
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.tx_notification_title))
                .setContentText(
                    context.getString(
                        if (transmitting) R.string.tx_notification_text
                        else R.string.tx_notification_text_waiting,
                    ),
                )
                .setSmallIcon(R.drawable.ic_stat_tx)
                .setContentIntent(contentIntent)
                .addAction(0, context.getString(R.string.tx_notification_stop), stopIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Without this, Android 12+ may defer the notification by ~10 s — about
                // 9% of the entire transmission, and the shot the Play foreground-service
                // demo video has to show.
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            if (transmitting) {
                // Live count-down of the remaining slot time.
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(endAtMs)
            } else {
                builder.setShowWhen(false)
            }
            return builder.build()
        }
    }
}
