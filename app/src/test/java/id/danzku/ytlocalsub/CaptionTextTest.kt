package id.danzku.ytlocalsub

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionTextTest {
    @Test fun trimsRepeatedPhrase() {
        assertEquals("and then we left", CaptionText.removeOverlap("He was there", "He was there and then we left"))
    }
    @Test fun preservesIndependentUtterance() {
        assertEquals("This is new.", CaptionText.removeOverlap("Hello world", "This is new."))
    }
    @Test fun removesIdenticalWindow() {
        assertEquals("", CaptionText.removeOverlap("Thank you very much", "Thank you very much"))
    }
}
