package id.danzku.ytlocalsub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionCue(val elapsedMs: Long, val text: String)
data class CaptionViewState(
    val phase: String = "Belum aktif",
    val running: Boolean = false,
    val audioDetected: Boolean = false,
    val warning: String? = null,
    val lastInferenceMs: Long = 0,
    val cues: List<CaptionCue> = emptyList()
)

object CaptionBus {
    private val mutable = MutableStateFlow(CaptionViewState())
    val state = mutable.asStateFlow()

    fun preparing() = mutable.update { CaptionViewState(phase = "Menyiapkan model & audio...", running = true) }
    fun started() = mutable.update { it.copy(phase = "Mendengarkan audio...", running = true, warning = null) }
    fun heardSound() = mutable.update { if (it.audioDetected) it else it.copy(audioDetected = true, warning = null) }
    fun noAudioWarning() = mutable.update { it.copy(warning = "Audio pemutar tidak terdeteksi. Coba putar video, atau ganti ke mode Mikrofon.") }
    fun processingBehind() = mutable.update { it.copy(warning = "CPU memproses lebih lambat dari pemutaran. Gunakan model Tiny agar tidak melewati ucapan.") }
    fun transcribed(ms: Long, text: String, tookMs: Long) = mutable.update { current ->
        val original = text.replace(Regex("\\s+"), " ").trim().take(300)
        val previous = current.cues.lastOrNull()?.text.orEmpty()
        val clean = CaptionText.removeOverlap(previous, original)
        if (clean.isBlank() || original.matches(Regex("(?i)\\[(music|applause|silence)]"))) {
            current.copy(lastInferenceMs = tookMs)
        } else {
            current.copy(
                phase = "Subtitle diproses lokal",
                lastInferenceMs = tookMs,
                cues = (current.cues + CaptionCue(ms, clean)).takeLast(240)
            )
        }
    }
    fun fail(message: String) = mutable.update { it.copy(phase = "Berhenti", running = false, warning = message) }
    fun stopped() = mutable.update { it.copy(phase = "Nonaktif", running = false) }
    fun reset() { mutable.value = CaptionViewState() }
}
