package id.danzku.clearsound.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/** Shizuku helps with OEM battery management; it does NOT install a Dolby/DSP driver. */
class ShizukuBridge(private val context: Context) {
    var available by mutableStateOf(false)
        private set
    var authorized by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == PERMISSION_CODE) refresh()
    }

    fun attach() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun detach() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    fun refresh() {
        available = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        authorized = available && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission(onMessage: (String) -> Unit) {
        refresh()
        when {
            !available -> onMessage("Shizuku belum berjalan. Jalankan melalui Wireless Debugging terlebih dahulu.")
            authorized -> onMessage("Izin Shizuku sudah diberikan.")
            else -> try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    onMessage("Izin pernah ditolak. Izinkan ClearSound melalui aplikasi Shizuku.")
                } else {
                    Shizuku.requestPermission(PERMISSION_CODE)
                }
            } catch (e: Exception) {
                onMessage("Tidak dapat meminta izin: ${e.message ?: "coba lagi"}")
            }
        }
    }

    fun requestBatteryWhitelist(onResult: (Boolean) -> Unit) {
        refresh()
        if (!authorized) {
            onResult(false)
            return
        }
        val arguments = Shizuku.UserServiceArgs(ComponentName(context, ShellToolsService::class.java))
            .daemon(false)
            .processNameSuffix("audio_setup")
            .version(1)
        val completed = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Thread {
                    val granted = runCatching {
                        IShellTools.Stub.asInterface(service).whitelistBattery(context.packageName)
                    }.getOrDefault(false)
                    handler.post {
                        if (completed.compareAndSet(false, true)) onResult(granted)
                        runCatching { Shizuku.unbindUserService(arguments, this, true) }
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handler.post { if (completed.compareAndSet(false, true)) onResult(false) }
            }
        }
        try {
            Shizuku.bindUserService(arguments, connection)
        } catch (_: Exception) {
            if (completed.compareAndSet(false, true)) onResult(false)
        }
    }

    companion object { private const val PERMISSION_CODE = 0xC51 }
}
