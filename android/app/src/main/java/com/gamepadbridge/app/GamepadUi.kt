package com.gamepadbridge.app

import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val DEADZONE = 0.10f

private val Accent = Color(0xFF3DD6F5)
private val ColorGray = Color(0xFF9AA0A6)

// PlayStation
private val PsGreen = Color(0xFF4CC9A0)
private val PsRed = Color(0xFFE5645F)
private val PsBlue = Color(0xFF5B8DEF)
private val PsPink = Color(0xFFD97BB6)

// Xbox
private val XboxGreen = Color(0xFF6DD66D)
private val XboxRed = Color(0xFFE5645F)
private val XboxBlue = Color(0xFF5B8DEF)
private val XboxYellow = Color(0xFFF2C94C)

/** Tombol tahan: aktif selama jari menempel. Tiap kontrol melacak pointer-nya sendiri, jadi multitouch jalan. */
@Composable
fun HoldButton(
    label: String,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    fontSize: Int = 20,
    onHold: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val currentOnHold by rememberUpdatedState(onHold)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) color else color.copy(alpha = 0.22f))
            .border(2.dp, if (pressed) Color.White else color.copy(alpha = 0.9f), shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        currentOnHold(true)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            change.consume()
                        }
                        pressed = false
                        currentOnHold(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) Color(0xFF101216) else Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Joystick analog dengan deadzone radial. */
@Composable
fun Stick(diameter: Dp, left: Boolean, hub: InputHub, modifier: Modifier = Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val travelPx = with(LocalDensity.current) { diameter.toPx() / 4f }

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0x14FFFFFF), Color(0x38FFFFFF))))
            .border(2.dp, if (active) Accent else Color(0x66FFFFFF), CircleShape)
            .pointerInput(left) {
                awaitPointerEventScope {
                    val radius = size.width / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun applyPosition(pos: Offset) {
                        val delta = pos - center
                        val dist = delta.getDistance()
                        val unit = if (dist > 0f) delta / dist else Offset.Zero
                        val raw = (dist / radius).coerceAtMost(1f)
                        knob = unit * raw
                        val mag = ((raw - DEADZONE) / (1f - DEADZONE)).coerceAtLeast(0f)
                        val x = (unit.x * mag * 32767f).roundToInt().coerceIn(-32768, 32767)
                        val y = (-unit.y * mag * 32767f).roundToInt().coerceIn(-32768, 32767)
                        hub.update { if (left) it.copy(lx = x, ly = y) else it.copy(rx = x, ry = y) }
                    }

                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        active = true
                        applyPosition(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            applyPosition(change.position)
                            change.consume()
                        }
                        active = false
                        knob = Offset.Zero
                        hub.update { if (left) it.copy(lx = 0, ly = 0) else it.copy(rx = 0, ry = 0) }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset((knob.x * travelPx).roundToInt(), (knob.y * travelPx).roundToInt()) }
                .size(diameter / 2)
                .clip(CircleShape)
                .background(if (active) Accent.copy(alpha = 0.9f) else Color(0xAAFFFFFF))
        )
    }
}

@Composable
private fun DPad(hub: InputHub, modifier: Modifier = Modifier) {
    val small = RoundedCornerShape(10.dp)
    Box(modifier.size(132.dp)) {
        HoldButton("▲", ColorGray, small, Modifier.align(Alignment.TopCenter).size(44.dp)) { p ->
            hub.update { it.withButton(Buttons.DPAD_UP, p) }
        }
        HoldButton("▼", ColorGray, small, Modifier.align(Alignment.BottomCenter).size(44.dp)) { p ->
            hub.update { it.withButton(Buttons.DPAD_DOWN, p) }
        }
        HoldButton("◀", ColorGray, small, Modifier.align(Alignment.CenterStart).size(44.dp)) { p ->
            hub.update { it.withButton(Buttons.DPAD_LEFT, p) }
        }
        HoldButton("▶", ColorGray, small, Modifier.align(Alignment.CenterEnd).size(44.dp)) { p ->
            hub.update { it.withButton(Buttons.DPAD_RIGHT, p) }
        }
    }
}

/** Empat tombol aksi. PlayStation: ▲=Y ●=B ✕=A ■=X. Xbox: huruf Y B A X dengan warna Xbox. */
@Composable
private fun FaceButtons(layout: PadLayout, hub: InputHub, modifier: Modifier = Modifier) {
    val ps = layout == PadLayout.PlayStation
    Box(modifier.size(132.dp)) {
        HoldButton(
            if (ps) "▲" else "Y", if (ps) PsGreen else XboxYellow, CircleShape,
            Modifier.align(Alignment.TopCenter).size(46.dp),
        ) { p -> hub.update { it.withButton(Buttons.Y, p) } }

        HoldButton(
            if (ps) "✕" else "A", if (ps) PsBlue else XboxGreen, CircleShape,
            Modifier.align(Alignment.BottomCenter).size(46.dp),
        ) { p -> hub.update { it.withButton(Buttons.A, p) } }

        HoldButton(
            if (ps) "■" else "X", if (ps) PsPink else XboxBlue, CircleShape,
            Modifier.align(Alignment.CenterStart).size(46.dp),
        ) { p -> hub.update { it.withButton(Buttons.X, p) } }

        HoldButton(
            if (ps) "●" else "B", if (ps) PsRed else XboxRed, CircleShape,
            Modifier.align(Alignment.CenterEnd).size(46.dp),
        ) { p -> hub.update { it.withButton(Buttons.B, p) } }
    }
}

/** Data untuk mode atur: posisi tiap kontrol dan callback saat digeser. */
private class EditContext(
    val editMode: Boolean,
    val positions: Map<String, Pair<Float, Float>>,
    val onMove: (id: String, dxDp: Float, dyDp: Float) -> Unit,
)

/**
 * Pembungkus kontrol yang bisa digeser dan diubah ukurannya.
 * Di mode atur, lapisan transparan menangkap sentuhan sehingga kontrol di bawahnya tidak aktif.
 */
@Composable
private fun Movable(
    id: String,
    ctx: EditContext,
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dens = LocalDensity.current.density
    val pos = ctx.positions[id] ?: (0f to 0f)

    Box(
        modifier
            .offset { IntOffset((pos.first * dens).roundToInt(), (pos.second * dens).roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
        if (ctx.editMode) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color(0x3300E5FF))
                    .border(1.dp, Accent)
                    .pointerInput(id, scale) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // drag dalam koordinat lokal (belum diskalakan), jadi dikali skala agar mengikuti jari
                            ctx.onMove(id, drag.x * scale / dens, drag.y * scale / dens)
                        }
                    }
            )
        }
    }
}

@Composable
fun GamepadScreen(
    hub: InputHub,
    link: BluetoothLink,
    layout: PadLayout,
    onLayoutChange: (PadLayout) -> Unit,
    settings: Settings,
) {
    val context = LocalContext.current
    val message by link.message.collectAsState()
    val rumble by link.rumble.collectAsState()
    val ps = layout == PadLayout.PlayStation

    var editMode by remember { mutableStateOf(false) }
    var buttonScale by remember { mutableStateOf(settings.buttonScale) }
    var stickScale by remember { mutableStateOf(settings.stickScale) }
    val positions = remember(layout) {
        mutableStateMapOf<String, Pair<Float, Float>>().apply { putAll(settings.loadPositions(layout)) }
    }

    fun saveAll() {
        settings.buttonScale = buttonScale
        settings.stickScale = stickScale
        settings.savePositions(layout, positions)
    }

    val ec = EditContext(editMode, positions) { id, dx, dy ->
        val old = positions[id] ?: (0f to 0f)
        positions[id] = (old.first + dx) to (old.second + dy)
    }

    // Rumble dari game di PC -> getar HP
    LaunchedEffect(rumble) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return@LaunchedEffect
        if (rumble > 0) {
            val amp = rumble.coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000), intArrayOf(0, amp), 0))
        } else {
            vibrator.cancel()
        }
    }
    DisposableEffect(Unit) {
        onDispose { context.getSystemService(Vibrator::class.java)?.cancel() }
    }

    val rect = RoundedCornerShape(12.dp)
    val small = RoundedCornerShape(10.dp)
    val shoulderSize = Modifier.size(76.dp, 44.dp)
    val menuSize = Modifier.size(84.dp, 34.dp)

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Bahu kiri
        Movable("l2", ec, buttonScale, Modifier.align(Alignment.TopStart)) {
            HoldButton("L2", ColorGray, rect, shoulderSize, 16) { p -> hub.update { it.copy(lt = if (p) 255 else 0) } }
        }
        Movable("l1", ec, buttonScale, Modifier.align(Alignment.TopStart).offset(x = 84.dp)) {
            HoldButton("L1", ColorGray, rect, shoulderSize, 16) { p -> hub.update { it.withButton(Buttons.LB, p) } }
        }
        // Bahu kanan
        Movable("r1", ec, buttonScale, Modifier.align(Alignment.TopEnd).offset(x = (-84).dp)) {
            HoldButton("R1", ColorGray, rect, shoulderSize, 16) { p -> hub.update { it.withButton(Buttons.RB, p) } }
        }
        Movable("r2", ec, buttonScale, Modifier.align(Alignment.TopEnd)) {
            HoldButton("R2", ColorGray, rect, shoulderSize, 16) { p -> hub.update { it.copy(rt = if (p) 255 else 0) } }
        }

        // Select / Start (View / Menu di Xbox)
        Movable("select", ec, buttonScale, Modifier.align(Alignment.TopCenter).offset(x = (-47).dp)) {
            HoldButton(if (ps) "SELECT" else "VIEW", ColorGray, small, menuSize, 11) { p ->
                hub.update { it.withButton(Buttons.BACK, p) }
            }
        }
        Movable("start", ec, buttonScale, Modifier.align(Alignment.TopCenter).offset(x = 47.dp)) {
            HoldButton(if (ps) "START" else "MENU", ColorGray, small, menuSize, 11) { p ->
                hub.update { it.withButton(Buttons.START, p) }
            }
        }

        // Info koneksi dan menu kecil (tidak bisa digeser)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, color = Color(0xFF8BE9B0), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                val pad = PaddingValues(horizontal = 8.dp)
                TextButton(onClick = { link.disconnect() }, contentPadding = pad) {
                    Text("Putuskan", fontSize = 11.sp)
                }
                TextButton(
                    onClick = { onLayoutChange(if (ps) PadLayout.Xbox else PadLayout.PlayStation) },
                    contentPadding = pad,
                ) {
                    Text("Layout: ${layout.label}", fontSize = 11.sp, color = Accent)
                }
                TextButton(onClick = { editMode = true }, contentPadding = pad) {
                    Text("Atur tombol", fontSize = 11.sp, color = Accent)
                }
            }
        }

        // Tombol Guide (PS / Xbox)
        Movable("guide", ec, buttonScale, Modifier.align(Alignment.BottomCenter).offset(y = (-24).dp)) {
            HoldButton(if (ps) "PS" else "XBOX", ColorGray, CircleShape, Modifier.size(48.dp), if (ps) 14 else 9) { p ->
                hub.update { it.withButton(Buttons.GUIDE, p) }
            }
        }

        // L3 dan R3 (menekan analog)
        Movable("l3", ec, buttonScale, Modifier.align(Alignment.BottomStart).offset(x = 70.dp)) {
            HoldButton("L3", ColorGray, CircleShape, Modifier.size(44.dp), 12) { p ->
                hub.update { it.withButton(Buttons.L3, p) }
            }
        }
        Movable("r3", ec, buttonScale, Modifier.align(Alignment.BottomEnd).offset(x = (-70).dp)) {
            HoldButton("R3", ColorGray, CircleShape, Modifier.size(44.dp), 12) { p ->
                hub.update { it.withButton(Buttons.R3, p) }
            }
        }

        if (ps) {
            // PlayStation: D-pad kiri atas, analog kiri bawah, tombol aksi kanan atas, analog kanan bawah
            Movable("dpad", ec, buttonScale, Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 64.dp)) {
                DPad(hub)
            }
            Movable("lstick", ec, stickScale, Modifier.align(Alignment.BottomStart).offset(x = 150.dp)) {
                Stick(130.dp, true, hub)
            }
            Movable("face", ec, buttonScale, Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 64.dp)) {
                FaceButtons(layout, hub)
            }
            Movable("rstick", ec, stickScale, Modifier.align(Alignment.BottomEnd).offset(x = (-150).dp)) {
                Stick(130.dp, false, hub)
            }
        } else {
            // Xbox: analog kiri atas, D-pad kiri bawah, tombol aksi kanan atas, analog kanan bawah
            Movable("lstick", ec, stickScale, Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = 64.dp)) {
                Stick(130.dp, true, hub)
            }
            Movable("dpad", ec, buttonScale, Modifier.align(Alignment.BottomStart).offset(x = 150.dp)) {
                DPad(hub)
            }
            Movable("face", ec, buttonScale, Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 64.dp)) {
                FaceButtons(layout, hub)
            }
            Movable("rstick", ec, stickScale, Modifier.align(Alignment.BottomEnd).offset(x = (-150).dp)) {
                Stick(130.dp, false, hub)
            }
        }

        // Panel mode atur (paling atas)
        if (editMode) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .width(300.dp)
                    .background(Color(0xF0181B22), RoundedCornerShape(16.dp))
                    .border(1.dp, Accent, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text("Mode atur: seret kontrol untuk memindahkannya", fontSize = 12.sp)
                Text(
                    "Ukuran tombol: ${(buttonScale * 100).roundToInt()}%",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(value = buttonScale, onValueChange = { buttonScale = it }, valueRange = 0.7f..1.4f)
                Text("Ukuran joystick: ${(stickScale * 100).roundToInt()}%", fontSize = 12.sp)
                Slider(value = stickScale, onValueChange = { stickScale = it }, valueRange = 0.7f..1.4f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        positions.clear()
                        buttonScale = 1f
                        stickScale = 1f
                    }) { Text("Reset") }
                    Button(onClick = {
                        saveAll()
                        editMode = false
                    }) { Text("Selesai") }
                }
            }
        }
    }
}