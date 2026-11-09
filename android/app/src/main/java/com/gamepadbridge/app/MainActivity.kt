package com.gamepadbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: PadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = Color(0xFF111114),
                    contentColor = Color.White,
                ) {
                    App(vm)
                }
            }
        }

        // Layar penuh: sembunyikan status bar dan navigation bar (muncul sebentar bila digeser dari tepi)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<BluetoothDevice> =
    context.getSystemService(BluetoothManager::class.java)
        ?.adapter?.bondedDevices?.toList()
        ?.sortedBy { it.name ?: it.address }
        ?: emptyList()

@Composable
private fun App(vm: PadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by vm.link.status.collectAsState()
    val message by vm.link.message.collectAsState()

    val settings = remember { Settings(context) }
    var layout by remember { mutableStateOf(settings.layout) }
    val changeLayout: (PadLayout) -> Unit = {
        layout = it
        settings.layout = it
    }

    // Latar belakang
    var bgImage by remember { mutableStateOf<ImageBitmap?>(BackgroundStore.load(context)) }
    var bgOpacity by remember { mutableStateOf(settings.bgOpacity) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { BackgroundStore.save(context, uri) }
                if (ok) bgImage = withContext(Dispatchers.IO) { BackgroundStore.load(context) }
            }
        }
    }

    var lastAddress by remember { mutableStateOf(settings.lastDevice) }

    var granted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    var refresh by remember { mutableStateOf(0) }
    val devices = remember(granted, refresh) { if (granted) bondedDevices(context) else emptyList() }

    AppBackground(bgImage, bgOpacity) {
        if (status == LinkStatus.Connected) {
            GamepadScreen(vm.hub, vm.link, layout, changeLayout, settings)
        } else {
            HomeScreen(
                devices = devices,
                granted = granted,
                status = status,
                message = message,
                layout = layout,
                onLayoutChange = changeLayout,
                lastAddress = lastAddress,
                hasBackground = bgImage != null,
                bgOpacity = bgOpacity,
                onOpacityChange = { bgOpacity = it },
                onOpacityFinished = { settings.bgOpacity = bgOpacity },
                onPickBackground = { pickImage.launch("image/*") },
                onRemoveBackground = {
                    BackgroundStore.delete(context)
                    bgImage = null
                },
                onRefresh = { refresh++ },
                onPick = { device ->
                    settings.lastDevice = device.address
                    lastAddress = device.address
                    vm.link.connect(device)
                },
            )
        }
    }
}