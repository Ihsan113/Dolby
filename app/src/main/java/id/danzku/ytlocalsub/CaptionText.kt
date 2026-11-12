package id.danzku.ytlocalsub

/**
 * Remove repeated tokens at the seam of overlapping whisper.cpp windows.
 * Works on plain words, preserves original punctuation of new text.
 */
object CaptionText {
    fun removeOverlap(previous: String, incoming: String): String {
        val newWords = incoming.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (newWords.isEmpty()) return ""
        val oldWords = previous.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (oldWords.isEmpty()) return incoming.trim()

        val maxOverlap = minOf(oldWords.size, newWords.size, 15)
        for (n in maxOverlap downTo 1) {
            if (n == 1 && oldWords.last().length < 4) continue
            val oldNormalized = oldWords.takeLast(n).map(::normalize)
            val newNormalized = newWords.take(n).map(::normalize)
            if (oldNormalized == newNormalized) return newWords.drop(n).joinToString(" ")
        }
        return newWords.joinToString(" ")
    }

    private fun normalize(word: String): String = word.lowercase().trim(' ', ',', '.', '?', '!', ':', ';', '"', '\'', '(', ')')
}
