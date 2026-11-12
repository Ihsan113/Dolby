package id.danzku.clearsound

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.danzku.clearsound.audio.SoundQuickTile
import id.danzku.clearsound.audio.SoundStore
import id.danzku.clearsound.shizuku.ShizukuBridge
import id.danzku.clearsound.ui.ClearSoundApp

class MainActivity : ComponentActivity() {
    private lateinit var bridge: ShizukuBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bridge = ShizukuBridge(this)
        bridge.attach()
        val store = SoundStore(this)
        setContent {
            ClearSoundContent(store)
        }
    }

    @Composable
    private fun ClearSoundContent(store: SoundStore) {
        val shizuku = remember { bridge }
        ClearSoundApp(
            store = store,
            shizuku = shizuku,
            onRequestShizuku = { shizuku.requestPermission(::message) },
            onOpenShizuku = {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) startActivity(intent) else {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/download/")))
                }
            },
            onBatterySetup = {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            onBatteryWhitelist = {
                shizuku.requestBatteryWhitelist { ok ->
                    message(if (ok) "Permintaan pengecualian baterai diterima" else "Tidak didukung ROM / izin ditolak. Atur manual lewat Pengaturan.")
                }
            },
            onAddTile = { addTile() },
            onMessage = ::message
        )
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                ComponentName(this, SoundQuickTile::class.java),
                getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor
            ) { result ->
                if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                    message("Jika tidak muncul, edit Quick Settings dan tambahkan ClearSound manual.")
                }
            }
        } else {
            message("Tarik panel Quick Settings > Edit > tambahkan ClearSound.")
        }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) bridge.refresh()
    }
    override fun onDestroy() {
        if (::bridge.isInitialized) bridge.detach()
        super.onDestroy()
    }
}
