package com.gamepadbridge.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PadViewModel : ViewModel() {
    val hub = InputHub()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val link = BluetoothLink(hub, scope)

    override fun onCleared() {
        link.disconnect()
        scope.cancel()
    }
}
