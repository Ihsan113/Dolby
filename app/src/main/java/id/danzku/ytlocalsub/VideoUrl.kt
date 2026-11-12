package id.danzku.ytlocalsub

import java.net.URI

/** Allow only official YouTube link forms. Never hand arbitrary pasted URLs to WebView. */
object VideoUrl {
    private val videoId = Regex("^[a-zA-Z0-9_-]{11}$")

    fun extract(input: String): String? {
        return try {
            val value = input.trim()
            if (value.length > 2048 || value.any { it == '\r' || it == '\n' }) return null
            val uri = URI(value)
            if (uri.scheme != "https") return null
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
            val candidate = when (host) {
                "youtu.be" -> segments.firstOrNull()
                "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when (segments.firstOrNull()) {
                    "watch" -> uri.rawQuery.orEmpty().split('&')
                        .firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    "shorts", "live", "embed" -> segments.getOrNull(1)
                    else -> null
                }
                else -> null
            }
            candidate?.takeIf(videoId::matches)
        } catch (_: Exception) { null }
    }
}
