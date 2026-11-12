package id.danzku.clearsound.audio

import android.content.Context

/** Presets stay deliberately conservative to reduce the likelihood of clipping. */
enum class SoundPreset(val label: String, val values: List<Float>) {
    NATURAL("Natural", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    VOICE("Clear Voice", listOf(-2f, -1.5f, -1f, -0.5f, 0f, 1f, 2f, 2f, 1f, 0f)),
    MUSIC("Music", listOf(1f, 1.5f, 1f, 0f, 0f, 0f, 0.5f, 1f, 0.5f, 0f)),
    CINEMA("Cinema", listOf(1.5f, 1f, 0.5f, 0f, 0f, 0.5f, 1.5f, 1.5f, 1f, 0f)),
    BASS("Bass", listOf(3f, 3f, 2f, 0f, -0.5f, -0.5f, 0f, 0.5f, 0f, 0f)),
    CUSTOM("Kustom", List(10) { 0f });
}

class SoundStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clear_sound_preferences", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) { prefs.edit().putBoolean("onboarding_done", value).apply() }

    var preset: SoundPreset
        get() = SoundPreset.entries.firstOrNull { it.name == prefs.getString("preset", "NATURAL") }
            ?: SoundPreset.NATURAL
        set(value) { prefs.edit().putString("preset", value.name).apply() }

    fun readBands(): List<Float> = List(10) { index -> prefs.getFloat("band_$index", 0f).coerceIn(-8f, 6f) }

    fun saveBands(bands: List<Float>) {
        require(bands.size == 10)
        prefs.edit().apply {
            bands.forEachIndexed { index, value -> putFloat("band_$index", value.coerceIn(-8f, 6f)) }
        }.apply()
    }

    fun selectPreset(next: SoundPreset) {
        if (next != SoundPreset.CUSTOM) saveBands(next.values)
        preset = next
    }
}
