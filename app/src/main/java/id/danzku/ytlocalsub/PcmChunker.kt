package id.danzku.ytlocalsub

/** Builds continuous 2.5-second PCM windows with overlap; model should be warmed before capture. */
class PcmChunker(
    private val windowSamples: Int = 16_000 * 5 / 2,
    private val overlapSamples: Int = 16_000 / 4
) {
    init { require(windowSamples > overlapSamples && overlapSamples >= 0) }
    private val working = FloatArray(windowSamples)
    private var used = 0

    fun feed(block: ShortArray, count: Int): List<FloatArray> {
        require(count in 0..block.size)
        val completed = ArrayList<FloatArray>()
        for (i in 0 until count) {
            working[used++] = block[i] / 32768f
            if (used == windowSamples) {
                completed.add(working.copyOf())
                working.copyInto(working, destinationOffset = 0,
                    startIndex = windowSamples - overlapSamples, endIndex = windowSamples)
                used = overlapSamples
            }
        }
        return completed
    }
}
