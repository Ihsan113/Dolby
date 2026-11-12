package id.danzku.ytlocalsub

import org.junit.Assert.*
import org.junit.Test

class VideoUrlTest {
    @Test fun typicalYouTubeUrls() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, VideoUrl.extract("https://www.youtube.com/watch?v=$id&t=24"))
        assertEquals(id, VideoUrl.extract("https://youtu.be/$id"))
        assertEquals(id, VideoUrl.extract("https://youtube.com/shorts/$id"))
        assertEquals(id, VideoUrl.extract("https://m.youtube.com/live/$id"))
    }
    @Test fun rejectOtherDomainsAndSchemes() {
        assertNull(VideoUrl.extract("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"))
        assertNull(VideoUrl.extract("javascript:alert(1)"))
        assertNull(VideoUrl.extract("http://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(VideoUrl.extract("https://www.youtube.com/watch?v=123"))
    }
    @Test fun ringIsDeterministic() {
        val ring = PcmRing(3)
        ring.add(shortArrayOf(1000, 2000), 2)
        ring.add(shortArrayOf(3000, 4000), 2)
        val values = ring.snapshot()
        assertEquals(3, values.size)
        assertEquals(2000f / 32768f, values[0], 1e-5f)
        assertEquals(4000f / 32768f, values[2], 1e-5f)
    }
}
