package com.netmic.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.netmic.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Opzioni di sorgente audio per AudioRecord.
 * VOICE_RECOGNITION: soppressione rumore + AGC (tende ad attenuare voce a distanza).
 * MIC: elaborazione standard del produttore.
 * UNPROCESSED: segnale grezzo senza filtri software hardware (se supportato).
 * CAMCORDER: tarato per ripresa a distanza (ottimo per telefono appoggiato a 1.5m).
 */
enum class AudioSourceOption(val sourceId: Int, val displayName: String, val description: String) {
    VOICE_RECOGNITION(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "VOICE_RECOGNITION",
        "Soppressione rumore + AGC (attenua voce a distanza)"
    ),
    MIC(
        MediaRecorder.AudioSource.MIC,
        "MIC",
        "Elaborazione standard del dispositivo"
    ),
    UNPROCESSED(
        MediaRecorder.AudioSource.UNPROCESSED,
        "UNPROCESSED",
        "Segnale grezzo senza filtri (se supportato)"
    ),
    CAMCORDER(
        MediaRecorder.AudioSource.CAMCORDER,
        "CAMCORDER",
        "Tarato per ripresa a distanza (ottimo far-field per Whisper)"
    );

    companion object {
        fun fromName(name: String?): AudioSourceOption {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: VOICE_RECOGNITION
        }
    }
}

/**
 * Informazioni sui microfoni fisici rilevati con AudioManager.getDevices.
 */
data class InputDeviceInfo(
    val id: Int,
    val productName: String,
    val typeName: String,
    val typeId: Int
) {
    val displayLabel: String
        get() = if (id == -1) "Predefinito di Sistema (Auto)" else "$productName ($typeName, ID: $id)"
}

/**
 * Servizio in Foreground per lo streaming continuo 24/7 del microfono via TCP.
 * Supporta la commutazione a caldo (hot-swap) di sorgente e microfono senza chiudere il socket TCP.
 */
class AudioStreamService : Service() {

    companion object {
        const val ACTION_START = "com.netmic.app.ACTION_START"
        const val ACTION_STOP = "com.netmic.app.ACTION_STOP"
        const val CHANNEL_ID = "netmic_audio_stream_channel"
        const val NOTIFICATION_ID = 8888

        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val PREF_NAME = "netmic_prefs"
        const val PREF_PORT = "server_port"
        const val PREF_AUDIO_SOURCE = "audio_source"
        const val PREF_DEVICE_ID = "preferred_device_id"
        const val DEFAULT_PORT = 8888
        const val DEFAULT_DEVICE_ID = -1

        // Stato osservabile dall'UI Jetpack Compose
        private val _serviceState = MutableStateFlow(ServiceStatus.STOPPED)
        val serviceState: StateFlow<ServiceStatus> = _serviceState.asStateFlow()

        private val _connectedClientIp = MutableStateFlow<String?>(null)
        val connectedClientIp: StateFlow<String?> = _connectedClientIp.asStateFlow()

        private val _audioLevelDbfs = MutableStateFlow(-60f)
        val audioLevelDbfs: StateFlow<Float> = _audioLevelDbfs.asStateFlow()

        private val _isClipping = MutableStateFlow(false)
        val isClipping: StateFlow<Boolean> = _isClipping.asStateFlow()

        private val _audioSource = MutableStateFlow(AudioSourceOption.VOICE_RECOGNITION)
        val audioSource: StateFlow<AudioSourceOption> = _audioSource.asStateFlow()

        private val _activeSourceActual = MutableStateFlow("VOICE_RECOGNITION")
        val activeSourceActual: StateFlow<String> = _activeSourceActual.asStateFlow()

        private val _isUnprocessedSupported = MutableStateFlow(true)
        val isUnprocessedSupported: StateFlow<Boolean> = _isUnprocessedSupported.asStateFlow()

        private val _selectedDeviceId = MutableStateFlow(DEFAULT_DEVICE_ID)
        val selectedDeviceId: StateFlow<Int> = _selectedDeviceId.asStateFlow()

        private val _activeDeviceName = MutableStateFlow("Predefinito di Sistema (Auto)")
        val activeDeviceName: StateFlow<String> = _activeDeviceName.asStateFlow()

        private val _availableInputDevices = MutableStateFlow<List<InputDeviceInfo>>(emptyList())
        val availableInputDevices: StateFlow<List<InputDeviceInfo>> = _availableInputDevices.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _uptimeSeconds = MutableStateFlow(0L)
        val uptimeSeconds: StateFlow<Long> = _uptimeSeconds.asStateFlow()

        private val _serverPort = MutableStateFlow(DEFAULT_PORT)
        val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

        // Contatore versione per commutazione a caldo senza perdita di connessione
        private val hotSwapVersion = AtomicInteger(0)

        fun setAudioSource(context: Context, source: AudioSourceOption) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_AUDIO_SOURCE, source.name).apply()
            _audioSource.value = source
            hotSwapVersion.incrementAndGet()
        }

        fun setPreferredDevice(context: Context, deviceId: Int) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(PREF_DEVICE_ID, deviceId).apply()
            _selectedDeviceId.value = deviceId
            hotSwapVersion.incrementAndGet()
        }

        fun refreshAvailableDevices(context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val list = mutableListOf(
                InputDeviceInfo(-1, "Predefinito di Sistema", "Auto", 0)
            )
            for (dev in devices) {
                val name = if (dev.productName.isNullOrBlank()) "Microfono" else dev.productName.toString()
                list.add(
                    InputDeviceInfo(
                        id = dev.id,
                        productName = name,
                        typeName = getReadableDeviceTypeName(dev.type),
                        typeId = dev.type
                    )
                )
            }
            _availableInputDevices.value = list
        }

        private fun getReadableDeviceTypeName(type: Int): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "TYPE_BUILTIN_MIC"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "TYPE_BLUETOOTH_SCO"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "TYPE_USB_DEVICE"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
                AudioDeviceInfo.TYPE_TELEPHONY -> "TYPE_TELEPHONY"
                else -> "TYPE_OTHER ($type)"
            }
        }
    }

    enum class ServiceStatus {
        STOPPED,
        LISTENING,
        CONNECTED,
        ERROR
    }

    @Volatile
    private var isRunning = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var serverJob: Job? = null
    private var uptimeJob: Job? = null
    private var clipResetJob: Job? = null

    private var serverSocket: ServerSocket? = null
    private var currentClientSocket: Socket? = null
    private val clientLock = Any()

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _serverPort.value = prefs.getInt(PREF_PORT, DEFAULT_PORT)

        val savedSource = prefs.getString(PREF_AUDIO_SOURCE, AudioSourceOption.VOICE_RECOGNITION.name)
        _audioSource.value = AudioSourceOption.fromName(savedSource)
        _selectedDeviceId.value = prefs.getInt(PREF_DEVICE_ID, DEFAULT_DEVICE_ID)

        refreshAvailableDevices(this)
        createNotificationChannel()

        // Inizializza WakeLock per prevenire lo sleep della CPU durante lo streaming
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NetMic::AudioStreamingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        // Inizializza WifiLock ad alte prestazioni
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "NetMic::WifiStreamLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundServiceWithNotification("Avvio del server TCP...")
                startStreamingServer()
                return START_STICKY
            }
        }
    }

    private fun startForegroundServiceWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetMic - Microfono di Rete TCP")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ferma", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NetMic Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica persistente del server microfono di rete"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startStreamingServer() {
        if (serverJob?.isActive == true) return
        isRunning = true

        serverJob = serviceScope.launch {
            val port = prefs.getInt(PREF_PORT, DEFAULT_PORT)
            _serverPort.value = port

            while (isActive && isRunning) {
                try {
                    _errorMessage.value = null
                    serverSocket = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress("0.0.0.0", port))
                    }

                    _serviceState.value = ServiceStatus.LISTENING
                    updateNotification("In ascolto sulla porta $port")

                    wifiLock?.acquire()

                    while (isActive && isRunning && serverSocket?.isClosed == false) {
                        try {
                            val newSocket = serverSocket?.accept() ?: break

                            newSocket.tcpNoDelay = true
                            newSocket.keepAlive = true
                            newSocket.soTimeout = 0

                            val clientIp = newSocket.inetAddress.hostAddress ?: "Sconosciuto"

                            synchronized(clientLock) {
                                currentClientSocket?.let { old ->
                                    try {
                                        old.close()
                                    } catch (_: Exception) {}
                                }
                                currentClientSocket = newSocket
                            }

                            _connectedClientIp.value = clientIp
                            _serviceState.value = ServiceStatus.CONNECTED
                            updateNotification("Connesso a $clientIp")

                            wakeLock?.acquire()
                            startUptimeCounter()

                            // Gestione streaming con supporto hot-swap della sorgente e del microfono
                            handleClientStream(newSocket)

                        } catch (e: SocketException) {
                            if (!isActive || !isRunning || serverSocket?.isClosed == true) break
                        } catch (e: Exception) {
                            _errorMessage.value = e.localizedMessage ?: "Errore socket"
                        } finally {
                            stopUptimeCounter()
                            wakeLock?.let { if (it.isHeld) it.release() }
                            _connectedClientIp.value = null
                            _audioLevelDbfs.value = -60f
                            _isClipping.value = false

                            if (isActive && isRunning && serverSocket?.isClosed == false) {
                                _serviceState.value = ServiceStatus.LISTENING
                                updateNotification("In ascolto sulla porta $port")
                            }
                        }
                    }

                } catch (e: IOException) {
                    _serviceState.value = ServiceStatus.ERROR
                    _errorMessage.value = "Impossibile aprire porta $port: ${e.localizedMessage}"
                    updateNotification("Errore porta: ${e.localizedMessage}")
                    delay(3000)
                } finally {
                    try {
                        serverSocket?.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Cattura audio da AudioRecord e scrive campioni PCM grezzi 16kHz little-endian sul socket TCP.
     * Mantiene aperto il socket TCP se l'utente cambia sorgente o capsula microfonica (Hot-Swap).
     */
    @SuppressLint("MissingPermission")
    private fun handleClientStream(socket: Socket) {
        var outputStream: OutputStream? = null

        try {
            outputStream = socket.getOutputStream()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Loop per supportare il cambio sorgente/microfono A CALDO senza perdere il socket
            while (socket.isConnected && !socket.isClosed && isRunning) {
                val currentVersion = hotSwapVersion.get()
                val requestedSource = _audioSource.value
                val targetDeviceId = _selectedDeviceId.value

                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val bufferSize = max(minBufSize, 4096)

                // Verifica supporto hardware per UNPROCESSED
                val hardwareSupportsUnprocessed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)?.toBoolean() ?: false
                } else {
                    false
                }
                _isUnprocessedSupported.value = hardwareSupportsUnprocessed

                var sourceIdToUse = requestedSource.sourceId
                var actualSourceName = requestedSource.displayName

                if (requestedSource == AudioSourceOption.UNPROCESSED && !hardwareSupportsUnprocessed) {
                    sourceIdToUse = MediaRecorder.AudioSource.MIC
                    actualSourceName = "MIC (Fallback da UNPROCESSED non supportato)"
                }

                var audioRecord: AudioRecord? = null
                try {
                    audioRecord = AudioRecord(
                        sourceIdToUse,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )

                    if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                        audioRecord.release()
                        audioRecord = null

                        if (requestedSource == AudioSourceOption.UNPROCESSED) {
                            sourceIdToUse = MediaRecorder.AudioSource.MIC
                            actualSourceName = "MIC (Fallback da UNPROCESSED non inizializzato)"
                            audioRecord = AudioRecord(
                                sourceIdToUse,
                                SAMPLE_RATE,
                                CHANNEL_CONFIG,
                                AUDIO_FORMAT,
                                bufferSize
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (requestedSource == AudioSourceOption.UNPROCESSED) {
                        try {
                            sourceIdToUse = MediaRecorder.AudioSource.MIC
                            actualSourceName = "MIC (Fallback da UNPROCESSED)"
                            audioRecord = AudioRecord(
                                sourceIdToUse,
                                SAMPLE_RATE,
                                CHANNEL_CONFIG,
                                AUDIO_FORMAT,
                                bufferSize
                            )
                        } catch (_: Exception) {}
                    }
                }

                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    _errorMessage.value = "Inizializzazione microfono fallita, riprovo..."
                    try {
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                _activeSourceActual.value = actualSourceName

                // Applica il microfono fisico preferito (se selezionato dall'utente)
                if (targetDeviceId != DEFAULT_DEVICE_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    val targetDevice = inputDevices.find { it.id == targetDeviceId }
                    if (targetDevice != null) {
                        val applied = audioRecord.setPreferredDevice(targetDevice)
                        _activeDeviceName.value = if (applied) {
                            "${targetDevice.productName} (${getReadableDeviceTypeName(targetDevice.type)})"
                        } else {
                            "Predefinito (Impossibile applicare ID $targetDeviceId)"
                        }
                    } else {
                        _activeDeviceName.value = "Predefinito (ID $targetDeviceId non collegato)"
                    }
                } else {
                    _activeDeviceName.value = "Predefinito di Sistema (Auto)"
                }

                try {
                    audioRecord.startRecording()
                    val audioBuffer = ByteArray(bufferSize)

                    // Loop di streaming audio effettivo
                    while (socket.isConnected && !socket.isClosed &&
                        audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING &&
                        isRunning
                    ) {
                        // Verifica se l'utente ha richiesto una commutazione a caldo di microfono o sorgente
                        if (currentVersion != hotSwapVersion.get()) {
                            // Interrompe solo AudioRecord per ricrearlo subito; il socket TCP rimane aperto!
                            break
                        }

                        val bytesRead = audioRecord.read(audioBuffer, 0, bufferSize)

                        if (bytesRead > 0) {
                            // Nessun guadagno software: il segnale grezzo little-endian viene inviato inalterato
                            outputStream.write(audioBuffer, 0, bytesRead)
                            outputStream.flush()

                            // Calcolo dBFS e rilevamento clipping
                            val levelInfo = calculateDbfs(audioBuffer, bytesRead)
                            _audioLevelDbfs.value = levelInfo.dbfs
                            if (levelInfo.clipped) {
                                signalClipping()
                            }
                        } else if (bytesRead < 0) {
                            break
                        }
                    }

                } finally {
                    try {
                        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord.stop()
                        }
                        audioRecord.release()
                    } catch (_: Exception) {}
                }

                // Se il socket si è chiuso o il servizio è stato fermato, usciamo dal loop
                if (!socket.isConnected || socket.isClosed || !isRunning) {
                    break
                }
            }

        } catch (e: Exception) {
            // Errori socket o disconnessione client gestiti dal finally del chiamante
        } finally {
            try {
                outputStream?.close()
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private data class LevelData(val dbfs: Float, val clipped: Boolean)

    /**
     * Calcola il livello in dBFS (20 * log10(rms / 32768)) su scala da -60 a 0 dBFS,
     * e controlla se qualche campione tocca il fondo scala (clipping a 32767 o -32768).
     */
    private fun calculateDbfs(buffer: ByteArray, bytesRead: Int): LevelData {
        var sumSquares = 0.0
        var isClipped = false
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) return LevelData(-60f, false)

        var i = 0
        while (i < bytesRead - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low // Campione short signed 16-bit
            if (sample >= 32760 || sample <= -32760) {
                isClipped = true
            }
            sumSquares += (sample.toDouble() * sample.toDouble())
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount)
        val dbfs = if (rms > 0.0) {
            (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }

        return LevelData(dbfs, isClipped)
    }

    private fun signalClipping() {
        _isClipping.value = true
        clipResetJob?.cancel()
        clipResetJob = serviceScope.launch {
            delay(500)
            _isClipping.value = false
        }
    }

    private fun startUptimeCounter() {
        _uptimeSeconds.value = 0L
        uptimeJob?.cancel()
        uptimeJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                _uptimeSeconds.value += 1
            }
        }
    }

    private fun stopUptimeCounter() {
        uptimeJob?.cancel()
        _uptimeSeconds.value = 0L
    }

    private fun stopStreaming() {
        isRunning = false
        serverJob?.cancel()
        stopUptimeCounter()
        clipResetJob?.cancel()

        synchronized(clientLock) {
            try {
                currentClientSocket?.close()
            } catch (_: Exception) {}
            currentClientSocket = null
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }

        _serviceState.value = ServiceStatus.STOPPED
        _connectedClientIp.value = null
        _audioLevelDbfs.value = -60f
        _isClipping.value = false
        _uptimeSeconds.value = 0L
    }

    override fun onDestroy() {
        isRunning = false
        stopStreaming()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}