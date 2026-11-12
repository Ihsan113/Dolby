package id.danzku.clearsound.audio

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import id.danzku.clearsound.MainActivity
import id.danzku.clearsound.R

class AudioEngineService : Service() {
    private val engine = SystemAudioEngine()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                if (AudioRuntime.active) {
                    engine.update(SoundStore(this).readBands()).onFailure {
                        AudioRuntime.failed(it.message ?: "Gagal memperbarui efek")
                        shutdown(keepError = true)
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (AudioRuntime.active) return START_NOT_STICKY
                try {
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, notification(),
                        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                    )
                } catch (e: Exception) {
                    AudioRuntime.failed("Layanan latar belakang ditolak: ${e.message ?: "izin tidak tersedia"}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                engine.start(SoundStore(this).readBands())
                    .onSuccess { AudioRuntime.started(it) }
                    .onFailure {
                        AudioRuntime.failed("EQ global tidak didukung atau bentrok: ${it.message ?: "efek tidak tersedia"}")
                        shutdown(keepError = true)
                        stopSelf()
                    }
                updateTile(this)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AudioEngineService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_tile, "Nonaktifkan", stop)
            .build()
    }

    private fun shutdown(keepError: Boolean = false) {
        engine.stop()
        if (!keepError) AudioRuntime.stopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        updateTile(this)
    }

    override fun onDestroy() {
        engine.stop()
        if (AudioRuntime.active) AudioRuntime.stopped()
        updateTile(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "clearsound_engine"
        private const val NOTIFICATION_ID = 72
        const val ACTION_START = "id.danzku.clearsound.START"
        const val ACTION_STOP = "id.danzku.clearsound.STOP"
        const val ACTION_UPDATE = "id.danzku.clearsound.UPDATE"

        fun start(context: Context): Result<Unit> = runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, AudioEngineService::class.java).setAction(ACTION_START)
            )
            Unit
        }
        fun stop(context: Context) {
            if (AudioRuntime.active) {
                context.startService(Intent(context, AudioEngineService::class.java).setAction(ACTION_STOP))
            } else {
                AudioRuntime.stopped()
                updateTile(context)
            }
        }
        fun refresh(context: Context) {
            if (AudioRuntime.active) {
                context.startService(Intent(context, AudioEngineService::class.java).setAction(ACTION_UPDATE))
            }
        }
        private fun updateTile(context: Context) {
            SoundQuickTile.refresh(context)
        }
    }
}
