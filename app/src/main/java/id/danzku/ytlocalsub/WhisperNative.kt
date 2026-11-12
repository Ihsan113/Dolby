package id.danzku.ytlocalsub

/** Runs only on background worker threads, never on the UI thread. */
object WhisperNative {
    init { System.loadLibrary("localwhisper") }
    external fun loadModel(path: String, requestGpu: Boolean): Boolean
    external fun backendLabel(): String
    external fun warmup(): Long
    external fun transcribe(pcm16k: FloatArray, languageCode: String, threads: Int): String
    external fun unload()
}
