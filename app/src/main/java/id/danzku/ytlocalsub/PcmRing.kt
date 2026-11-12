package id.danzku.ytlocalsub

import kotlin.math.sqrt

/** Small synchronized ring buffer: 16kHz mono, last 5 seconds. */
class PcmRing(private val capacity: Int = 16_000 * 5) {
    private val ring = FloatArray(capacity)
    private var write = 0
    private var count = 0
    @Synchronized
    fun add(samples: ShortArray, n: Int): Double {
        var energy = 0.0
        for (i in 0 until n) {
            val normalized = samples[i] / 32768f
            ring[write] = normalized
            write = (write + 1) % capacity
            if (count < capacity) count++
            energy += normalized * normalized
        }
        return if (n == 0) 0.0 else sqrt(energy / n)
    }
    @Synchronized
    fun snapshot(): FloatArray {
        val copy = FloatArray(count)
        var cursor = (write - count + capacity) % capacity
        for (i in 0 until count) { copy[i] = ring[cursor]; cursor = (cursor + 1) % capacity }
        return copy
    }
}
