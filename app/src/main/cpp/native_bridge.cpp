#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <chrono>
#include "whisper.h"
#include "ggml-backend.h"

namespace {
    std::mutex g_mutex;
    whisper_context* g_ctx = nullptr;
    std::string g_backend = "CPU ARM64";

    std::string as_utf8(JNIEnv* env, jstring value) {
        if (!value) return {};
        const char* c = env->GetStringUTFChars(value, nullptr);
        if (!c) return {};
        std::string out(c);
        env->ReleaseStringUTFChars(value, c);
        return out;
    }
    void error(JNIEnv* env, const char* what) {
        jclass cls = env->FindClass("java/lang/IllegalStateException");
        if (cls) env->ThrowNew(cls, what);
    }
    bool gpu_exists() {
#ifdef LOCALSUB_WITH_VULKAN
        for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
            auto* dev = ggml_backend_dev_get(i);
            auto type = ggml_backend_dev_type(dev);
            if (type == GGML_BACKEND_DEVICE_TYPE_GPU || type == GGML_BACKEND_DEVICE_TYPE_IGPU) return true;
        }
#endif
        return false;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_loadModel(JNIEnv* env, jobject, jstring path, jboolean preferGpu) {
    const auto file = as_utf8(env, path);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
    g_backend = "CPU ARM64";
    if (preferGpu && gpu_exists()) {
        auto params = whisper_context_default_params();
        params.use_gpu = true;
        params.gpu_device = 0;
        g_ctx = whisper_init_from_file_with_params(file.c_str(), params);
        // The availability check is not a GPU utilization benchmark; report it honestly.
        if (g_ctx) g_backend = "Vulkan tersedia • GPU dicoba";
    }
    if (!g_ctx) {
        auto params = whisper_context_default_params();
        params.use_gpu = false;
        g_ctx = whisper_init_from_file_with_params(file.c_str(), params);
        if (preferGpu) g_backend = "CPU fallback";
    }
    return g_ctx ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_backendLabel(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return env->NewStringUTF(g_backend.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_warmup(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) { error(env, "Model belum dimuat"); return -1; }
    // Prime the inference backend and compile initial GPU kernels, if available.
    // Silent data are intentionally not emitted as captions.
    const auto begin = std::chrono::steady_clock::now();
    std::vector<float> silence(16'000, 0.f);
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = 4;
    p.language = "en";
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = true;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.suppress_blank = true;
    p.max_tokens = 16;
    (void) whisper_full(g_ctx, p, silence.data(), static_cast<int>(silence.size()));
    return static_cast<jlong>(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - begin).count());
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_transcribe(JNIEnv* env, jobject,
    jfloatArray pcm, jstring lang, jint threads) {
    if (!pcm) { error(env, "Audio kosong"); return nullptr; }
    const jsize n = env->GetArrayLength(pcm);
    if (n < 16000) return env->NewStringUTF("");
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    const auto language = as_utf8(env, lang);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) { error(env, "Model belum dimuat"); return nullptr; }
    auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = std::max(1, std::min(6, static_cast<int>(threads)));
    p.language = (language.empty() || language == "auto") ? "auto" : language.c_str();
    p.translate = false;
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = true;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.suppress_blank = true;
    p.suppress_nst = true;
    p.max_len = 120;
    p.max_tokens = 90;
    const int err = whisper_full(g_ctx, p, samples.data(), static_cast<int>(samples.size()));
    if (err != 0) { error(env, "Whisper inference gagal"); return nullptr; }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(g_ctx); ++i) {
        const char* part = whisper_full_get_segment_text(g_ctx, i);
        if (part) text += part;
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_unload(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
    g_backend = "CPU ARM64";
}
