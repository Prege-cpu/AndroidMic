package com.netmic.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.netmic.app.service.AudioSourceOption
import com.netmic.app.service.AudioStreamService
import com.netmic.app.ui.NetMicScreen
import com.netmic.app.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private var isBatteryOptIgnored by mutableStateOf(false)
    private var hasPermissions by mutableStateOf(false)

    // Launcher per richiedere permessi a runtime
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val postNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }
        hasPermissions = recordAudioGranted && postNotificationGranted
    }

    private var audioDeviceCallback: AudioDeviceCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        checkBatteryOptimization()
        setupAudioDeviceCallback()

        setContent {
            NetMicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val status by AudioStreamService.serviceState.collectAsState()
                    val clientIp by AudioStreamService.connectedClientIp.collectAsState()
                    val audioLevelDbfs by AudioStreamService.audioLevelDbfs.collectAsState()
                    val isClipping by AudioStreamService.isClipping.collectAsState()
                    val audioSource by AudioStreamService.audioSource.collectAsState()
                    val activeSourceActual by AudioStreamService.activeSourceActual.collectAsState()
                    val isUnprocessedSupported by AudioStreamService.isUnprocessedSupported.collectAsState()
                    val selectedDeviceId by AudioStreamService.selectedDeviceId.collectAsState()
                    val activeDeviceName by AudioStreamService.activeDeviceName.collectAsState()
                    val availableDevices by AudioStreamService.availableInputDevices.collectAsState()
                    val errorMessage by AudioStreamService.errorMessage.collectAsState()
                    val uptimeSeconds by AudioStreamService.uptimeSeconds.collectAsState()
                    val serverPort by AudioStreamService.serverPort.collectAsState()

                    val localIps = NetworkUtils.getAllLocalIps(this)

                    NetMicScreen(
                        serviceStatus = status,
                        connectedClientIp = clientIp,
                        audioLevelDbfs = audioLevelDbfs,
                        isClipping = isClipping,
                        audioSource = audioSource,
                        activeSourceActual = activeSourceActual,
                        isUnprocessedSupported = isUnprocessedSupported,
                        selectedDeviceId = selectedDeviceId,
                        activeDeviceName = activeDeviceName,
                        availableDevices = availableDevices,
                        errorMessage = errorMessage,
                        uptimeSeconds = uptimeSeconds,
                        currentPort = serverPort,
                        localIps = localIps,
                        isBatteryOptimized = !isBatteryOptIgnored,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { requestRequiredPermissions() },
                        onRequestIgnoreBatteryOpt = { requestIgnoreBatteryOptimization() },
                        onStartService = { startAudioService() },
                        onStopService = { stopAudioService() },
                        onSavePort = { newPort -> savePort(newPort) },
                        onSelectAudioSource = { source -> AudioStreamService.setAudioSource(this, source) },
                        onSelectDevice = { deviceId -> AudioStreamService.setPreferredDevice(this, deviceId) }
                    )
                }
            }
        }
    }

    private fun setupAudioDeviceCallback() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                AudioStreamService.refreshAvailableDevices(this@MainActivity)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                AudioStreamService.refreshAvailableDevices(this@MainActivity)
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    override fun onDestroy() {
        audioDeviceCallback?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.unregisterAudioDeviceCallback(it)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkBatteryOptimization()
        AudioStreamService.refreshAvailableDevices(this)
    }

    private fun checkPermissions() {
        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasPermissions = audioGranted && notificationGranted
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioStreamService::class.java).apply {
            action = AudioStreamService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAudioService() {
        val intent = Intent(this, AudioStreamService::class.java).apply {
            action = AudioStreamService.ACTION_STOP
        }
        startService(intent)
    }

    private fun savePort(newPort: Int) {
        val prefs = getSharedPreferences(AudioStreamService.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(AudioStreamService.PREF_PORT, newPort).apply()
        // Se il servizio è attivo, lo riavvia per applicare la nuova porta
        if (AudioStreamService.serviceState.value != AudioStreamService.ServiceStatus.STOPPED) {
            stopAudioService()
            startAudioService()
        }
    }
}

@Composable
fun NetMicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF0F172A),
            background = Color(0xFF090D16),
            surface = Color(0xFF131A29),
            onSurface = Color(0xFFF1F5F9),
            error = Color(0xFFF87171)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0284C7),
            onPrimary = Color(0xFFFFFFFF),
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            error = Color(0xFFDC2626)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}