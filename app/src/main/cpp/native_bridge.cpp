#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "whisper.h"

namespace {
    std::mutex g_mutex;
    whisper_context* g_ctx = nullptr;

    std::string jstring_to_utf8(JNIEnv* env, jstring value) {
        if (value == nullptr) return {};
        const char* raw = env->GetStringUTFChars(value, nullptr);
        if (!raw) return {};
        std::string result(raw);
        env->ReleaseStringUTFChars(value, raw);
        return result;
    }
    void throw_java(JNIEnv* env, const char* msg) {
        auto cls = env->FindClass("java/lang/IllegalStateException");
        if (cls) env->ThrowNew(cls, msg);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_loadModel(JNIEnv* env, jobject, jstring path) {
    const auto file = jstring_to_utf8(env, path);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { whisper_free(g_ctx); g_ctx = nullptr; }
    whisper_context_params params = whisper_context_default_params();
    g_ctx = whisper_init_from_file_with_params(file.c_str(), params);
    return g_ctx ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_danzku_ytlocalsub_WhisperNative_transcribe(JNIEnv* env, jobject,
    jfloatArray pcm, jstring lang, jint threads) {
    if (!pcm) { throw_java(env, "No PCM audio"); return nullptr; }
    const jsize n = env->GetArrayLength(pcm);
    if (n < 16000) return env->NewStringUTF("");
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    const auto language = jstring_to_utf8(env, lang);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx) { throw_java(env, "Whisper model not loaded"); return nullptr; }
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
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
    if (err != 0) { throw_java(env, "Whisper inference failed"); return nullptr; }
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
}
