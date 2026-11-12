package id.danzku.clearsound.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class EqCurveTest {
    @Test fun `preserves logical handles exactly`() {
        val curve = (0..9).map(Int::toFloat)
        EqCurve.frequencies.forEachIndexed { i, f ->
            assertEquals(curve[i], EqCurve.gainAt(f, curve), 0.0001f)
        }
    }
    @Test fun `interpolates in logarithmic frequency space`() {
        val gains = listOf(0f, 6f) + List(8) { 6f }
        val midpoint = kotlin.math.sqrt(31f * 62f)
        assertEquals(3f, EqCurve.gainAt(midpoint, gains), 0.02f)
    }
    @Test fun `clamps to endpoints`() {
        val gains = List(10) { it.toFloat() }
        assertEquals(0f, EqCurve.gainAt(1f, gains), 0f)
        assertEquals(9f, EqCurve.gainAt(22000f, gains), 0f)
    }
}
