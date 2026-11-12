package id.danzku.ytlocalsub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Foreground worker for approved playback capture or explicit microphone fallback. */
class CaptionService : Service() {
    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var captureThread: Thread? = null
    private var recognitionThread: Thread? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var modelLoaded = false
    private var startElapsedMs = 0L

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "LocalSub transcription", NotificationManager.IMPORTANCE_LOW)
        )
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopWork()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || running.get() || modelLoaded) return START_NOT_STICKY

        val mode = runCatching { AudioSourceMode.valueOf(intent.getStringExtra(EXTRA_SOURCE).orEmpty()) }
            .getOrDefault(AudioSourceMode.DEVICE)
        val model = runCatching { WhisperModel.valueOf(intent.getStringExtra(EXTRA_MODEL).orEmpty()) }
            .getOrDefault(WhisperModel.TINY)
        val lang = intent.getStringExtra(EXTRA_LANGUAGE) ?: "auto"
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_RESULT_DATA)

        try {
            val type = if (mode == AudioSourceMode.DEVICE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), type)
            check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Izin mikrofon/rekam audio belum diberikan"
            }
            if (mode == AudioSourceMode.DEVICE) {
                check(resultCode == android.app.Activity.RESULT_OK && resultData != null) {
                    "Persetujuan perekaman audio perangkat dibatalkan"
                }
            }
            CaptionBus.preparing()
            running.set(true)
            captureThread = Thread({ runCapture(model, lang, mode, resultCode, resultData) }, "LocalSub-Capture").apply { start() }
        } catch (e: Exception) {
            CaptionBus.fail("Tidak bisa memulai: ${e.message ?: "izin ditolak"}")
            stopWork()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runCapture(model: WhisperModel, language: String, mode: AudioSourceMode, resultCode: Int, resultData: Intent?) {
        try {
            val modelFile = ModelDownloader.destination(this, model)
            check(modelFile.isFile && modelFile.length() > 20_000_000L) { "Model ASR belum tersedia" }
            check(WhisperNative.loadModel(modelFile.absolutePath)) { "Model Whisper gagal dimuat" }
            modelLoaded = true
            if (!running.get()) {
                WhisperNative.unload()
                modelLoaded = false
                return
            }

            val sampleRate = 16_000
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Format perekaman 16 kHz tidak didukung" }
            val bufferBytes = maxOf(minBuffer * 2, 32_000)
            val audio = if (mode == AudioSourceMode.DEVICE) {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val p = mgr.getMediaProjection(resultCode, checkNotNull(resultData))
                projection = p
                val callback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (running.get()) {
                            CaptionBus.fail("Sesi izin perekaman dihentikan oleh Android.")
                            requestStop()
                        }
                    }
                }
                projectionCallback = callback
                p.registerCallback(callback, Handler(Looper.getMainLooper()))
                val config = android.media.AudioPlaybackCaptureConfiguration.Builder(p)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build()
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            } else {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord gagal dibuat" }
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Rekaman tidak berjalan" }
            startElapsedMs = SystemClock.elapsedRealtime()
            CaptionBus.started()
            val chunker = PcmChunker()
            val pendingChunks = ArrayBlockingQueue<Pair<Long, FloatArray>>(3)
            recognitionThread = Thread({ runRecognition(pendingChunks, language) }, "LocalSub-Whisper").apply { start() }
            val block = ShortArray(2048)
            var detected = false
            while (running.get()) {
                val n = audio.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) {
                    if (n < 0) throw IllegalStateException("AudioRecord berhenti: $n")
                    continue
                }
                var signal = 0.0
                for (i in 0 until n) { val f = block[i] / 32768.0; signal += f * f }
                val rms = kotlin.math.sqrt(signal / n)
                val produced = chunker.feed(block, n)
                for (pcm in produced) {
                    val item = (SystemClock.elapsedRealtime() - startElapsedMs) to pcm
                    if (!pendingChunks.offer(item)) {
                        pendingChunks.poll() // bounded queue: prefer freshness over unbounded lag
                        pendingChunks.offer(item)
                        CaptionBus.processingBehind()
                    }
                }
                if (rms > 0.0030) {
                    detected = true
                    CaptionBus.heardSound()
                }
                if (!detected && mode == AudioSourceMode.DEVICE &&
                    SystemClock.elapsedRealtime() - startElapsedMs > 12_000L) {
                    CaptionBus.noAudioWarning()
                    detected = true // avoid repeatedly updating UI
                }
            }
        } catch (e: Exception) {
            if (running.get()) CaptionBus.fail(e.message ?: "Kesalahan pemrosesan audio")
        } finally {
            running.set(false)
            Handler(Looper.getMainLooper()).post { stopSelf() }
        }
    }

    private fun runRecognition(chunks: ArrayBlockingQueue<Pair<Long, FloatArray>>, language: String) {
        while (running.get()) {
            val item = try { chunks.poll(500L, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { break } ?: continue
            if (!running.get()) break
            val (elapsed, data) = item
            var energy = 0.0
            data.forEach { energy += abs(it) }
            if (energy / data.size < 0.0025) continue
            try {
                val begin = SystemClock.elapsedRealtime()
                val text = WhisperNative.transcribe(data, language, 4)
                val took = SystemClock.elapsedRealtime() - begin
                if (running.get()) CaptionBus.transcribed(elapsed, text, took)
            } catch (e: Exception) {
                if (running.get()) CaptionBus.fail("Whisper: ${e.message ?: "inferensi gagal"}")
                requestStop()
                return
            }
        }
    }

    private fun requestStop() {
        Handler(Looper.getMainLooper()).post {
            if (running.get()) {
                stopWork()
                stopSelf()
            }
        }
    }

    private fun stopWork() {
        running.set(false)
        recognitionThread?.interrupt()
        recognitionThread = null
        recorder?.let { a ->
            runCatching { a.stop() }
            runCatching { a.release() }
        }
        recorder = null
        projection?.let { p ->
            projectionCallback?.let { cb -> runCatching { p.unregisterCallback(cb) } }
            runCatching { p.stop() }
        }
        projection = null
        projectionCallback = null
        // Native unload serializes against in-flight transcribe, no dangling model pointer.
        if (modelLoaded) { runCatching { WhisperNative.unload() }; modelLoaded = false }
        if (CaptionBus.state.value.running) CaptionBus.stopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, CaptionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("LocalSub aktif")
            .setContentText("Subtitle diproses di perangkat Anda")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }

    override fun onDestroy() {
        stopWork()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "id.danzku.ytlocalsub.START"
        const val ACTION_STOP = "id.danzku.ytlocalsub.STOP"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MODEL = "model"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val CHANNEL_ID = "localsub_capture"
        const val NOTIFICATION_ID = 2401

        fun start(context: Context, source: AudioSourceMode, model: WhisperModel, language: SpeechLang,
                  code: Int = 0, data: Intent? = null) {
            val intent = Intent(context, CaptionService::class.java).setAction(ACTION_START).apply {
                putExtra(EXTRA_SOURCE, source.name)
                putExtra(EXTRA_MODEL, model.name)
                putExtra(EXTRA_LANGUAGE, language.code)
                putExtra(EXTRA_RESULT_CODE, code)
                if (data != null) putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptionService::class.java).setAction(ACTION_STOP))
        }
    }
}
