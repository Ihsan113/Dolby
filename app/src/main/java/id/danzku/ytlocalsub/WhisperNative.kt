package id.danzku.ytlocalsub

/** JNI bindings. Runs locally; no audio samples ever sent over network. */
object WhisperNative {
    init { System.loadLibrary("localwhisper") }
    external fun loadModel(path: String): Boolean
    external fun transcribe(pcm16k: FloatArray, languageCode: String, threads: Int): String
    external fun unload()
}
