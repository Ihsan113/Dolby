package id.danzku.clearsound.audio

import android.media.audiofx.Equalizer
import android.util.Log
import kotlin.math.roundToInt

/**
 * Real AudioEffect engine, not a visual-only equalizer.
 * Attaching to Audio Session 0 is deprecated and may be rejected/ignored by a ROM.
 * This app never claims that every other app is affected without an on-device test.
 */
class SystemAudioEngine {
    private var eq: Equalizer? = null
    var nativeBandCount: Int = 0
        private set

    @Suppress("DEPRECATION")
    fun start(curve: List<Float>): Result<Int> = runCatching {
        stop()
        val candidate = Equalizer(0, 0)
        try {
            val bands = candidate.numberOfBands.toInt()
            check(bands in 1..32) { "Jumlah band perangkat tidak valid ($bands)" }
            check(candidate.hasControl()) { "EQ sistem dikendalikan aplikasi lain" }
            candidate.enabled = true
            check(candidate.enabled) { "Efek EQ tidak bisa diaktifkan pada ROM ini" }
            applyTo(candidate, curve)
            eq = candidate
            nativeBandCount = bands
            bands
        } catch (t: Throwable) {
            candidate.release()
            throw t
        }
    }.onFailure { Log.w("ClearSound", "Audio Session 0 not available", it) }

    fun update(curve: List<Float>): Result<Unit> = runCatching {
        val current = checkNotNull(eq) { "Mesin audio belum aktif" }
        check(current.hasControl()) { "Kontrol efek audio berpindah ke aplikasi lain" }
        applyTo(current, curve)
    }

    private fun applyTo(effect: Equalizer, curve: List<Float>) {
        val supportedRange = effect.bandLevelRange
        val minimum = supportedRange[0].toInt()
        val maximum = supportedRange[1].toInt()
        for (index in 0 until effect.numberOfBands.toInt()) {
            val freqHz = effect.getCenterFreq(index.toShort()) / 1000f
            // All gain values are decibels; the Android API expects millibels.
            val levelMb = (EqCurve.gainAt(freqHz.coerceAtLeast(1f), curve) * 100f)
                .roundToInt().coerceIn(minimum, maximum).toShort()
            effect.setBandLevel(index.toShort(), levelMb)
        }
    }

    fun stop() {
        val current = eq
        eq = null
        nativeBandCount = 0
        try { current?.enabled = false } catch (_: Throwable) { }
        try { current?.release() } catch (_: Throwable) { }
    }
}
