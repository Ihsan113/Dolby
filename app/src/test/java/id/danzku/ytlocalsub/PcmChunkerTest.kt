package id.danzku.ytlocalsub

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmChunkerTest {
    @Test fun buildsTwoWindowsWithOverlap() {
        val c = PcmChunker(windowSamples = 4, overlapSamples = 1)
        assertEquals(0, c.feed(shortArrayOf(1, 2), 2).size)
        val first = c.feed(shortArrayOf(3, 4), 2)
        assertEquals(1, first.size)
        val second = c.feed(shortArrayOf(5, 6, 7), 3)
        assertEquals(1, second.size)
        assertEquals(4f / 32768f, second[0][0], 0.00001f)
        assertEquals(7f / 32768f, second[0][3], 0.00001f)
    }
}
