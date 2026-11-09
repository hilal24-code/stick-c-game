package com.gamepadbridge.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Accent = Color(0xFF3DD6F5)
private val Muted = Color(0xFF9AA0A6)
private val CardBg = Color(0xCC181B22)

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    devices: List<BluetoothDevice>,
    granted: Boolean,
    status: LinkStatus,
    message: String,
    layout: PadLayout,
    onLayoutChange: (PadLayout) -> Unit,
    lastAddress: String?,
    hasBackground: Boolean,
    bgOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    onOpacityFinished: () -> Unit,
    onPickBackground: () -> Unit,
    onRemoveBackground: () -> Unit,
    onRefresh: () -> Unit,
    onPick: (BluetoothDevice) -> Unit,
) {
    val sorted = remember(devices, lastAddress) { devices.sortedByDescending { it.address == lastAddress } }
    val busy = status == LinkStatus.Connecting

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ===== Panel kiri: judul, status, layout, latar =====
        Column(
            Modifier
                .width(310.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎮", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Play Zone", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("HP jadi controller PC lewat Bluetooth", fontSize = 11.sp, color = Muted)
                }
            }

            StatusPill(status, message)

            Section("Tata letak tombol") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayoutOption(
                        title = "PlayStation",
                        symbols = "▲ ● ✕ ■",
                        selected = layout == PadLayout.PlayStation,
                        modifier = Modifier.weight(1f),
                    ) { onLayoutChange(PadLayout.PlayStation) }
                    LayoutOption(
                        title = "Xbox",
                        symbols = "Y B A X",
                        selected = layout == PadLayout.Xbox,
                        modifier = Modifier.weight(1f),
                    ) { onLayoutChange(PadLayout.Xbox) }
                }
            }

            Section("Latar belakang") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPickBackground,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF101216)),
                    ) { Text("Pilih gambar", fontSize = 12.sp) }
                    if (hasBackground) {
                        OutlinedButton(onClick = onRemoveBackground) { Text("Hapus", fontSize = 12.sp) }
                    }
                }
                if (hasBackground) {
                    Text(
                        "Opasitas gambar: ${(bgOpacity * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = bgOpacity,
                        onValueChange = onOpacityChange,
                        onValueChangeFinished = onOpacityFinished,
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                    )
                } else {
                    Text(
                        "Belum ada gambar. Gambar landscape (mendatar) paling pas.",
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // ===== Panel kanan: daftar perangkat =====
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Perangkat terpasang", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) { Text("Muat ulang", fontSize = 12.sp, color = Accent) }
            }
            Text(
                "Jalankan server di PC lebih dulu, lalu pilih laptop/PC kamu.",
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            if (!granted) {
                Text("Izin Bluetooth belum diberikan. Berikan izin lewat pengaturan aplikasi.", color = Color(0xFFE5645F))
            } else if (devices.isEmpty()) {
                Text("Tidak ada perangkat terpasang. Nyalakan Bluetooth dan pairing dengan laptop dulu.", color = Muted)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sorted, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        highlight = device.address == lastAddress,
                        enabled = !busy,
                    ) { onPick(device) }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: LinkStatus, message: String) {
    val color = when (status) {
        LinkStatus.Connecting -> Color(0xFFF2C94C)
        LinkStatus.Failed -> Color(0xFFE5645F)
        else -> Color(0xFF8BE9B0)
    }
    val text = if (message.isNotEmpty()) message else "Siap. Pilih perangkat di sebelah kanan."
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Color(0x33FFFFFF), shape)
            .padding(12.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun LayoutOption(
    title: String,
    symbols: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.22f) else Color(0x22FFFFFF))
            .border(2.dp, if (selected) Accent else Color(0x44FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(symbols, fontSize = 12.sp, color = if (selected) Accent else Muted)
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceCard(device: BluetoothDevice, highlight: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Accent else Color(0x44FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name ?: "Tanpa nama", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(device.address, fontSize = 11.sp, color = Muted)
            if (highlight) Text("Terakhir dipakai", fontSize = 11.sp, color = Accent)
        }
        Text(if (enabled) "Hubungkan ▸" else "Menunggu...", fontSize = 13.sp, color = Accent)
    }
}