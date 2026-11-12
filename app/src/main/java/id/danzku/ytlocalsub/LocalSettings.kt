package id.danzku.ytlocalsub

import android.content.Context

class LocalSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yt_local_sub", Context.MODE_PRIVATE)
    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(value) { prefs.edit().putBoolean("setup_done", value).apply() }
    var model: WhisperModel
        get() = enumValueOrDefault(prefs.getString("model", null), WhisperModel.TINY)
        set(value) { prefs.edit().putString("model", value.name).apply() }
    var language: SpeechLang
        get() = enumValueOrDefault(prefs.getString("language", null), SpeechLang.AUTO)
        set(value) { prefs.edit().putString("language", value.name).apply() }
    var source: AudioSourceMode
        get() = enumValueOrDefault(prefs.getString("source", null), AudioSourceMode.DEVICE)
        set(value) { prefs.edit().putString("source", value.name).apply() }
    var lastUrl: String
        get() = prefs.getString("last_url", "") ?: ""
        set(value) { prefs.edit().putString("last_url", value.take(2048)).apply() }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
