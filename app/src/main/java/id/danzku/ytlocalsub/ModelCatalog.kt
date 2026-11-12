package id.danzku.ytlocalsub

enum class WhisperModel(val title: String, val fileName: String, val sha256: String, val sizeText: String) {
    TINY(
        "Cepat · Tiny Q5", "ggml-tiny-q5_1.bin",
        "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7", "32 MB"
    ),
    BASE(
        "Lebih teliti · Base Q5", "ggml-base-q5_1.bin",
        "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", "60 MB"
    );
    val url: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"
}

enum class ComputeMode(val label: String) { AUTO("GPU Vulkan (Auto)"), CPU("CPU stabil") }

enum class AudioSourceMode { DEVICE, MICROPHONE }

enum class SpeechLang(val label: String, val code: String) {
    AUTO("Otomatis", "auto"), ID("Indonesia", "id"), EN("English", "en"),
    JA("Jepang", "ja"), ZH("Mandarin", "zh"), KO("Korea", "ko"),
    ES("Spanyol", "es")
}
