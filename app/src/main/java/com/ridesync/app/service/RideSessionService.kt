package com.ridesync.app.service

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
import com.ridesync.app.MainActivity
import com.ridesync.app.R
import com.ridesync.app.core.RLog

/**
 * Foreground service that keeps a ride alive with the screen off and the app
 * backgrounded — essential for a riding app. Declares microphone + media
 * playback types so voice capture and music continue.
 *
 * It holds a partial wake lock (CPU stays up for audio threads) and a Wi-Fi
 * lock (so the radio doesn't nap and drop the local link). The session itself
 * lives in the app container; this service is just the OS-facing keep-alive +
 * the ride notification.
 */
class RideSessionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Volatile
    private var projectionActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }

            ACTION_START_CAPTURE -> {
                startAudioCapture(intent)
            }

            ACTION_STOP_CAPTURE -> {
                projectionActive = false
                stopAudioCaptureShare()
                startForegroundWith(
                    intent?.getStringExtra(EXTRA_TITLE) ?: "RideSync",
                    intent?.getStringExtra(EXTRA_TEXT) ?: "Ride in progress",
                )
            }

            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "RideSync"
                val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Ride in progress"
                startForegroundWith(title, text)
                acquireLocks()
            }
        }
        return START_STICKY
    }

    /**
     * Android 14 requires a mediaProjection-typed foreground service to be
     * running BEFORE the MediaProjection is created, so we do the whole
     * sequence here inside the service: re-enter foreground with the
     * mediaProjection type, acquire the projection, then start capture.
     */
    private fun startAudioCapture(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) return

        projectionActive = true
        startForegroundWith(
            intent.getStringExtra(EXTRA_TITLE) ?: "RideSync",
            "Sharing phone audio with your riders",
        )
        acquireLocks()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            RLog.w(RLog.Cat.MUSIC, "audio capture needs Android 10+")
            return
        }
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as android.media.projection.MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, resultData) ?: return
            val app = application as com.ridesync.app.RideSyncApp
            val ok = app.container.sessionManager.startPhoneAudioShare(projection)
            if (!ok) RLog.w(RLog.Cat.MUSIC, "no host session to share audio to")
        } catch (e: Exception) {
            RLog.e(RLog.Cat.MUSIC, "start audio capture failed", e)
        }
    }

    private fun stopAudioCaptureShare() {
        runCatching {
            (application as com.ridesync.app.RideSyncApp).container.sessionManager.stopPhoneAudioShare()
        }
    }

    private fun startForegroundWith(title: String, text: String) {
        createChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RideSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(0, "End ride", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (projectionActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideSync:session").apply {
                setReferenceCounted(false)
                acquire(MAX_LOCK_MS)
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, "RideSync:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
            RLog.i(RLog.Cat.SESSION, "session locks acquired")
        } catch (e: Exception) {
            RLog.w(RLog.Cat.SESSION, "lock acquire failed", e)
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun stopSelfSafely() {
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ride session",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows while a RideSync ride is active"
                    setShowBadge(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ride_session"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.ridesync.app.STOP_SESSION"
        const val ACTION_START_CAPTURE = "com.ridesync.app.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.ridesync.app.STOP_CAPTURE"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val MAX_LOCK_MS = 6L * 60 * 60 * 1000 // safety cap: 6h

        fun start(context: Context, title: String, text: String) {
            val intent = Intent(context, RideSessionService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Kick off phone-audio sharing after the user grants the projection. */
        fun startAudioCapture(context: Context, resultCode: Int, data: Intent, title: String) {
            val intent = Intent(context, RideSessionService::class.java)
                .setAction(ACTION_START_CAPTURE)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAudioCapture(context: Context) {
            context.startService(
                Intent(context, RideSessionService::class.java).setAction(ACTION_STOP_CAPTURE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RideSessionService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
