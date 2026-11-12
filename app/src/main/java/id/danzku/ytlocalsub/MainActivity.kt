package id.danzku.ytlocalsub

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var settings: LocalSettings
    private data class StartRequest(val source: AudioSourceMode, val model: WhisperModel, val language: SpeechLang)
    private var pending: StartRequest? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) continueStart() else toast("Izin rekam audio diperlukan untuk membuat subtitle lokal.")
    }
    private val projectionPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val request = pending
        if (result.resultCode == Activity.RESULT_OK && result.data != null && request != null) {
            runCatching {
                CaptionService.start(this, request.source, request.model, request.language, result.resultCode, result.data)
            }.onFailure { CaptionBus.fail("Tidak dapat memulai layanan: ${it.message}") }
        } else {
            CaptionBus.fail("Izin tangkap audio dibatalkan. Pilih Mikrofon jika audio video diblokir.")
        }
        pending = null
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(if (granted) "Notifikasi diizinkan" else "Notifikasi ditolak; layanan mungkin tetap berjalan")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = LocalSettings(this)
        setContent {
            LocalSubApp(
                settings = settings,
                start = ::startCapture,
                stop = { CaptionService.stop(this) },
                notifyPermission = {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else toast("Android tidak membutuhkan izin notifikasi tambahan")
                },
                feedback = ::toast
            )
        }
    }

    private fun startCapture(source: AudioSourceMode, model: WhisperModel, lang: SpeechLang) {
        if (CaptionBus.state.value.running) return
        if (!ModelDownloader.existing(this, model)) {
            toast("Unduh model ASR terlebih dahulu.")
            return
        }
        pending = StartRequest(source, model, lang)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else continueStart()
    }

    private fun continueStart() {
        val request = pending ?: return
        if (request.source == AudioSourceMode.MICROPHONE) {
            runCatching { CaptionService.start(this, request.source, request.model, request.language) }
                .onFailure { CaptionBus.fail("Gagal memulai mikrofon: ${it.message}") }
            pending = null
            return
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionPermission.launch(mgr.createScreenCaptureIntent())
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
