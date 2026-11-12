package id.danzku.clearsound.audio

import kotlin.math.ln

/** Ten logical handles. OEMs often expose only five physical EQ bands. */
object EqCurve {
    val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun gainAt(frequencyHz: Float, values: List<Float>): Float {
        require(values.size == frequencies.size) { "Expected 10 logical bands" }
        if (frequencyHz <= frequencies.first()) return values.first()
        if (frequencyHz >= frequencies.last()) return values.last()
        val upper = frequencies.indexOfFirst { it >= frequencyHz }
        val lower = upper - 1
        val ratio = (ln(frequencyHz) - ln(frequencies[lower])) /
            (ln(frequencies[upper]) - ln(frequencies[lower]))
        return values[lower] + (values[upper] - values[lower]) * ratio
    }
}
