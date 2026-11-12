package id.danzku.ytlocalsub

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ModelStatus {
    data object Idle : ModelStatus
    data class Downloading(val model: WhisperModel, val percent: Int) : ModelStatus
    data class Ready(val model: WhisperModel) : ModelStatus
    data class Error(val message: String) : ModelStatus
}

/** Download only signed-by-hash model bytes; never download APKs or executable code. */
object ModelDownloader {
    var status: ModelStatus by mutableStateOf(ModelStatus.Idle)
        private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    fun destination(context: Context, model: WhisperModel): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, model.fileName)

    fun existing(context: Context, model: WhisperModel): Boolean {
        val file = destination(context, model)
        return file.isFile && file.length() > 20_000_000 &&
            runCatching { sha256(file).equals(model.sha256, ignoreCase = true) }.getOrDefault(false)
    }

    fun refresh(context: Context, model: WhisperModel) {
        if (busy.get()) return
        status = if (existing(context, model)) ModelStatus.Ready(model) else ModelStatus.Idle
    }

    fun download(context: Context, model: WhisperModel) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        status = ModelStatus.Downloading(model, 0)
        worker.execute {
            val target = destination(app, model)
            val temp = File(target.absolutePath + ".part")
            try {
                val url = URL(model.url)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 25_000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "LocalSub/1.0 Android")
                }
                try {
                    check(connection.responseCode in 200..299) {
                        "Unduhan gagal: HTTP ${connection.responseCode}"
                    }
                    val length = connection.contentLengthLong
                    check(length < 0 || length in 20_000_000L..200_000_000L) {
                        "Ukuran model tidak sesuai; periksa koneksi."
                    }
                    var readTotal = 0L
                    var lastPercent = -1
                    connection.inputStream.use { input ->
                        temp.outputStream().buffered(128 * 1024).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                readTotal += n
                                check(readTotal <= 200_000_000L) { "Respons terlalu besar" }
                                val percent = if (length > 0) ((100 * readTotal) / length).toInt().coerceIn(0, 100)
                                    else -1
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    main.post { status = ModelStatus.Downloading(model, percent) }
                                }
                            }
                        }
                    }
                    if (length > 0) check(readTotal == length) { "Unduhan tidak lengkap" }
                    check(sha256(temp).equals(model.sha256, ignoreCase = true)) {
                        "SHA-256 model tidak cocok. Unduh ulang."
                    }
                    check(temp.renameTo(target)) { "Gagal menyimpan model" }
                    main.post { status = ModelStatus.Ready(model) }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                temp.delete()
                main.post { status = ModelStatus.Error(e.message ?: "Gagal mengunduh model") }
            } finally {
                busy.set(false)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val bytes = ByteArray(128 * 1024)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
