package com.gamepadbridge.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

enum class LinkStatus { Disconnected, Connecting, Connected, Failed }

/** Penampung state gamepad yang ditulis oleh UI (thread utama) dan dibaca oleh thread pengirim. */
class InputHub {
    @Volatile
    var state: GamepadState = GamepadState()
        private set

    @Synchronized
    fun update(block: (GamepadState) -> GamepadState) {
        state = block(state)
    }
}

/** Koneksi RFCOMM ke server PC: kirim paket input ~125 Hz + heartbeat, terima rumble. */
class BluetoothLink(private val hub: InputHub, private val scope: CoroutineScope) {

    private val _status = MutableStateFlow(LinkStatus.Disconnected)
    val status: StateFlow<LinkStatus> = _status

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    /** Intensitas rumble terbesar (0..255) dari game di PC. */
    private val _rumble = MutableStateFlow(0)
    val rumble: StateFlow<Int> = _rumble

    private var job: Job? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (_status.value == LinkStatus.Connecting || _status.value == LinkStatus.Connected) return
        _status.value = LinkStatus.Connecting
        _message.value = "Menghubungkan..."

        job = scope.launch {
            val uuid = UUID.fromString(Protocol.SERVICE_UUID)
            var connected: BluetoothSocket? = null
            var error = "tidak diketahui"

            for (attempt in 1..2) {
                if (!isActive) return@launch
                var attemptSocket: BluetoothSocket? = null
                try {
                    attemptSocket = device.createRfcommSocketToServiceRecord(uuid)
                    socket = attemptSocket
                    attemptSocket.connect()
                    connected = attemptSocket
                    break
                } catch (e: IOException) {
                    error = e.message ?: error
                    closeQuietly(attemptSocket)
                    delay(500)
                } catch (e: SecurityException) {
                    error = "izin Bluetooth ditolak"
                    closeQuietly(attemptSocket)
                    break
                }
            }

            if (connected == null) {
                _status.value = LinkStatus.Failed
                _message.value = "Gagal terhubung ($error). Pastikan server PC berjalan dan HP sudah pairing."
                return@launch
            }

            _status.value = LinkStatus.Connected
            _message.value = "Terhubung"
            hub.update { GamepadState() }

            val reader = launch { readLoop(connected) }
            try {
                sendLoop(connected)
            } catch (e: IOException) {
                _message.value = "Koneksi terputus."
            } finally {
                reader.cancel()
                closeQuietly(connected)
                if (socket === connected) socket = null
                _rumble.value = 0
                if (_status.value == LinkStatus.Connected) _status.value = LinkStatus.Disconnected
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        closeQuietly(socket)
        socket = null
        _rumble.value = 0
        _status.value = LinkStatus.Disconnected
        _message.value = "Terputus"
    }

    private suspend fun sendLoop(sock: BluetoothSocket) {
        val out = sock.outputStream
        val packet = ByteArray(Protocol.INPUT_LENGTH)
        var seq = 0
        var last: GamepadState? = null
        var lastSent = 0L

        while (currentCoroutineContext().isActive) {
            val now = System.nanoTime()
            val s = hub.state
            if (s != last || now - lastSent >= HEARTBEAT_NS) {
                Protocol.encodeInput(s, seq, packet)
                seq = (seq + 1) and 0xFF
                out.write(packet)
                out.flush()
                last = s
                lastSent = now
            }
            delay(8)
        }
    }

    private fun readLoop(sock: BluetoothSocket) {
        val parser = FeedbackParser { large, small -> _rumble.value = maxOf(large, small) }
        val buf = ByteArray(64)
        try {
            val input = sock.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                parser.feed(buf, n)
            }
        } catch (_: IOException) {
            // socket ditutup
        }
    }

    private fun closeQuietly(s: BluetoothSocket?) {
        try {
            s?.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val HEARTBEAT_NS = 50_000_000L   // 50 ms (batas server: 500 ms)
    }
}
