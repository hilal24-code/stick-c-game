package com.gamepadbridge.app

import android.content.Context

enum class PadLayout(val label: String) {
    PlayStation("PlayStation"),
    Xbox("Xbox"),
}

/** Pengaturan yang disimpan di HP (bertahan setelah aplikasi ditutup). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("gamepad_settings", Context.MODE_PRIVATE)

    var layout: PadLayout
        get() = if (prefs.getString("layout", "ps") == "xbox") PadLayout.Xbox else PadLayout.PlayStation
        set(value) {
            prefs.edit().putString("layout", if (value == PadLayout.Xbox) "xbox" else "ps").apply()
        }

    var buttonScale: Float
        get() = prefs.getFloat("button_scale", 1f)
        set(value) {
            prefs.edit().putFloat("button_scale", value).apply()
        }

    var stickScale: Float
        get() = prefs.getFloat("stick_scale", 1f)
        set(value) {
            prefs.edit().putFloat("stick_scale", value).apply()
        }

    /** Seberapa terlihat gambar latar (0.1 = samar, 1.0 = penuh). */
    var bgOpacity: Float
        get() = prefs.getFloat("bg_opacity", 0.5f)
        set(value) {
            prefs.edit().putFloat("bg_opacity", value).apply()
        }

    /** Alamat Bluetooth perangkat yang terakhir dipakai. */
    var lastDevice: String?
        get() = prefs.getString("last_device", null)
        set(value) {
            prefs.edit().putString("last_device", value).apply()
        }

    /** Pergeseran posisi tiap kontrol (satuan dp) dari posisi bawaan, terpisah per layout. */
    fun loadPositions(layout: PadLayout): Map<String, Pair<Float, Float>> {
        val raw = prefs.getString("pos_${layout.name}", "") ?: ""
        val result = mutableMapOf<String, Pair<Float, Float>>()
        for (entry in raw.split(";")) {
            if (entry.isBlank()) continue
            val parts = entry.split(":", limit = 2)
            if (parts.size < 2) continue
            val xy = parts[1].split(",")
            val x = xy.getOrNull(0)?.toFloatOrNull()
            val y = xy.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) result[parts[0]] = x to y
        }
        return result
    }

    fun savePositions(layout: PadLayout, positions: Map<String, Pair<Float, Float>>) {
        val raw = positions.entries.joinToString(";") { "${it.key}:${it.value.first},${it.value.second}" }
        prefs.edit().putString("pos_${layout.name}", raw).apply()
    }
}