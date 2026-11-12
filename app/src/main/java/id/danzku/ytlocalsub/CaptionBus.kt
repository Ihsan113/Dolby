package id.danzku.ytlocalsub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionCue(val elapsedMs: Long, val text: String)
data class CaptionViewState(
    val phase: String = "Belum aktif",
    val running: Boolean = false,
    val readyForPlayback: Boolean = false,
    val audioDetected: Boolean = false,
    val backend: String = "Belum dipilih",
    val warmupMs: Long = 0,
    val warning: String? = null,
    val lastInferenceMs: Long = 0,
    val cues: List<CaptionCue> = emptyList()
)

object CaptionBus {
    private val mutable = MutableStateFlow(CaptionViewState())
    val state = mutable.asStateFlow()

    fun preparing() = mutable.update {
        CaptionViewState(phase = "Memuat model AI dan menyiapkan akselerasi...", running = true)
    }
    fun warmedUp(backend: String, durationMs: Long) = mutable.update {
        it.copy(phase = "Mesin AI siap. Menyiapkan perekaman...", backend = backend, warmupMs = durationMs)
    }
    fun started() = mutable.update {
        it.copy(phase = "Siap • tekan Play pada video", running = true, readyForPlayback = true, warning = null)
    }
    fun heardSound() = mutable.update {
        if (it.audioDetected) it else it.copy(audioDetected = true, phase = "Audio diterima • menyusun subtitle...", warning = null)
    }
    fun noAudioWarning() = mutable.update {
        it.copy(warning = "Tidak ada audio yang dapat direkam. Video mungkin memblokir AudioPlaybackCapture. Coba Mikrofon atau video lain.")
    }
    fun processingBehind() = mutable.update {
        it.copy(warning = "Pemrosesan lebih lambat dari audio. Potongan lama dilewati agar subtitle tidak terus tertinggal. Gunakan Tiny/CPU atau GPU Vulkan jika stabil.")
    }
    fun transcribed(ms: Long, text: String, tookMs: Long) = mutable.update { current ->
        val original = text.replace(Regex("\\s+"), " ").trim().take(300)
        val previous = current.cues.lastOrNull()?.text.orEmpty()
        val clean = CaptionText.removeOverlap(previous, original)
        if (clean.isBlank() || original.matches(Regex("(?i)\\[(music|applause|silence)]"))) {
            current.copy(lastInferenceMs = tookMs)
        } else {
            current.copy(
                phase = "Subtitle lokal berjalan • ${current.backend}",
                lastInferenceMs = tookMs,
                cues = (current.cues + CaptionCue(ms, clean)).takeLast(240)
            )
        }
    }
    fun stopping() = mutable.update { it.copy(phase = "Menutup model AI...", readyForPlayback = false, running = true) }
    fun fail(message: String) = mutable.update {
        it.copy(phase = "Gagal", running = false, readyForPlayback = false, warning = message)
    }
    fun stopped() = mutable.update {
        it.copy(phase = "Nonaktif", running = false, readyForPlayback = false)
    }
    fun reset() { mutable.value = CaptionViewState() }
}
