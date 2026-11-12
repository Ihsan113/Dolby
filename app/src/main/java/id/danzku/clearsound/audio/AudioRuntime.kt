package id.danzku.clearsound.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-local truth: avoids a tile claiming active after service process death. */
object AudioRuntime {
    var active by mutableStateOf(false)
        private set
    var physicalBands by mutableIntStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun started(bandCount: Int) {
        active = true
        physicalBands = bandCount
        lastError = null
    }
    fun failed(message: String) {
        active = false
        physicalBands = 0
        lastError = message
    }
    fun stopped() {
        active = false
        physicalBands = 0
        lastError = null
    }
}
