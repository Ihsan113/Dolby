package id.danzku.clearsound.audio

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** One-tap toggle in Android Quick Settings; reflects process-local engine truth. */
class SoundQuickTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (AudioRuntime.active) {
            AudioEngineService.stop(this)
        } else {
            AudioEngineService.start(this).onFailure {
                AudioRuntime.failed("Gagal menjalankan layanan: ${it.message}")
            }
        }
        render()
    }

    private fun render() {
        qsTile?.apply {
            state = if (AudioRuntime.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "ClearSound"
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (AudioRuntime.active) "Aktif" else "Nonaktif"
            updateTile()
        }
    }

    companion object {
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, SoundQuickTile::class.java))
            } catch (_: Exception) { /* Some OEMs do not refresh until panel is opened. */ }
        }
    }
}
