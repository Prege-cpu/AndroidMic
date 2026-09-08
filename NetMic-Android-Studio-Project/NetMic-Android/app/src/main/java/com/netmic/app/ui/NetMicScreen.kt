package com.netmic.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netmic.app.service.AudioSourceOption
import com.netmic.app.service.AudioStreamService.ServiceStatus
import com.netmic.app.service.InputDeviceInfo
import com.netmic.app.util.NetworkUtils.NetworkInterfaceInfo

@Composable
fun NetMicScreen(
    serviceStatus: ServiceStatus,
    connectedClientIp: String?,
    audioLevelDbfs: Float,
    isClipping: Boolean,
    audioSource: AudioSourceOption,
    activeSourceActual: String,
    isUnprocessedSupported: Boolean,
    selectedDeviceId: Int,
    activeDeviceName: String,
    availableDevices: List<InputDeviceInfo>,
    errorMessage: String?,
    uptimeSeconds: Long,
    currentPort: Int,
    localIps: List<NetworkInterfaceInfo>,
    isBatteryOptimized: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestIgnoreBatteryOpt: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSavePort: (Int) -> Unit,
    onSelectAudioSource: (AudioSourceOption) -> Unit,
    onSelectDevice: (Int) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var portInputText by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    var isEditingPort by remember { mutableStateOf(false) }

    var micMenuExpanded by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    val isRunning = serviceStatus != ServiceStatus.STOPPED
    val isConnected = serviceStatus == ServiceStatus.CONNECTED

    // Animazione fluida del livello dBFS (da -60 a 0 dBFS)
    val animatedDbfs by animateFloatAsState(
        targetValue = if (isConnected) audioLevelDbfs else -60f,
        label = "AudioLevelDbfs"
    )

    // Mappatura barra progress da -60 dBFS a 0 dBFS su 0f..1f
    val progressFraction = ((animatedDbfs + 60f) / 60f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Intestazione
        Text(
            text = "NETMIC",
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Microfono di Rete TCP • Far-Field Optimizer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        // Banner per permessi mancanti
        if (!hasPermissions) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Permessi necessari mancanti",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444)
                    )
                    Text(
                        text = "È richiesto il permesso RECORD_AUDIO e POST_NOTIFICATIONS per lo streaming.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Concedi Permessi", color = Color.White)
                    }
                }
            }
        }

        // Banner Ottimizzazione Batteria (24/7)
        if (isBatteryOptimized) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ottimizzazione Batteria Attiva",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "Escludi l'app per garantire lo streaming continuo a schermo spento.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestIgnoreBatteryOpt,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Escludi", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sezione IP e Porta (Layout ottimizzato anti-overflow con Copia negli Appunti)
        val primaryIp = localIps.firstOrNull()?.ip ?: "127.0.0.1"
        val ipLabel = localIps.firstOrNull()?.displayName ?: "Rete Locale"

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    clipboardManager.setText(AnnotatedString(primaryIp))
                    Toast.makeText(context, "IP copiato: $primaryIp", Toast.LENGTH_SHORT).show()
                },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INDIRIZZO IP TELEFONO (TOCCA PER COPIARE)",
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = primaryIp,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = ipLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Porta TCP
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Porta TCP: ",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (isEditingPort) {
                OutlinedTextField(
                    value = portInputText,
                    onValueChange = { portInputText = it.filter { ch -> ch.isDigit() }.take(5) },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val p = portInputText.toIntOrNull() ?: currentPort
                        onSavePort(p.coerceIn(1024, 65535))
                        isEditingPort = false
                    })
                )
            } else {
                Text(
                    text = currentPort.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { isEditingPort = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Cambia", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pillola di Stato
        val statusColor by animateColorAsState(
            targetValue = when (serviceStatus) {
                ServiceStatus.CONNECTED -> Color(0xFF10B981)
                ServiceStatus.LISTENING -> Color(0xFF38BDF8)
                ServiceStatus.ERROR -> Color(0xFFEF4444)
                ServiceStatus.STOPPED -> Color(0xFF6B7280)
            },
            label = "StatusColor"
        )

        val statusText = when (serviceStatus) {
            ServiceStatus.CONNECTED -> "Connesso a ${connectedClientIp ?: "PC Client"}"
            ServiceStatus.LISTENING -> "In ascolto su porta $currentPort"
            ServiceStatus.ERROR -> "Errore: ${errorMessage ?: "Sconosciuto"}"
            ServiceStatus.STOPPED -> "Servizio arrestato"
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(30.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
        }

        // Uptime connessione
        AnimatedVisibility(visible = isConnected) {
            Text(
                text = "Uptime: ${formatUptime(uptimeSeconds)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // SEZIONE 1: CONFIGURAZIONE ATTIVA E HOT-SWAP
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIGURAZIONE AUDIO ATTIVA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ HOT-SWAP ATTIVO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Microfono in uso: $activeDeviceName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sorgente in uso: $activeSourceActual",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Segnalazione hardware se UNPROCESSED non è supportato
                if (audioSource == AudioSourceOption.UNPROCESSED && !isUnprocessedSupported) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "⚠️ UNPROCESSED non supportato dall'hardware. Fallback automatico attivo su MIC.",
                            fontSize = 11.sp,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Menu a tendina 1: Scelta Sorgente Audio
                Text(
                    text = "Sorgente Audio (influenza diretta ripresa a distanza):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { sourceMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = audioSource.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = audioSource.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(text = "▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        AudioSourceOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = option.displayName,
                                            fontWeight = if (option == audioSource) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == audioSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = option.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectAudioSource(option)
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Menu a tendina 2: Scelta Microfono Fisico
                Text(
                    text = "Microfono Fisico (AudioManager.getDevices):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentDevLabel = availableDevices.find { it.id == selectedDeviceId }?.displayLabel ?: "Predefinito di Sistema (Auto)"

                    OutlinedCard(
                        onClick = { micMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDevLabel,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = "▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    DropdownMenu(
                        expanded = micMenuExpanded,
                        onDismissRequest = { micMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        availableDevices.forEach { dev ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = dev.displayLabel,
                                        fontWeight = if (dev.id == selectedDeviceId) FontWeight.Bold else FontWeight.Normal,
                                        color = if (dev.id == selectedDeviceId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSelectDevice(dev.id)
                                    micMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Consiglio per 1,5m: prova CAMCORDER o UNPROCESSED. VOICE_RECOGNITION tende a cancellare il parlato lontano trattandolo come rumore d'ambiente.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // SEZIONE 2: SCALA dBFS (-60 a 0) & CLIPPING
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVELLO SEGNALE AUDIO (dBFS)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Valore numerico in dBFS
                        Text(
                            text = if (isConnected) String.format("%.1f dBFS", animatedDbfs) else "Inattivo",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Indicatore rosso di CLIPPING a fondo scala
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isClipping) Color(0xFFEF4444) else Color(0xFF374151).copy(alpha = 0.4f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CLIP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isClipping) Color.White else Color.Gray.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Barra dBFS da -60 a 0 dBFS
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    color = when {
                        isClipping -> Color(0xFFEF4444)
                        animatedDbfs > -6f -> Color(0xFFEF4444)
                        animatedDbfs > -18f -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // Scala graduata da -60 a 0 dBFS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-60", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("-40", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("-20", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("-12", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("-6", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("0 dBFS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nessun guadagno software applicato: il PCM 16-bit a 16000 Hz viene inviato nativo al PC per massimizzare la precisione di faster-whisper.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sezione Inferiore: Pulsante Avvia/Ferma Servizio
        Button(
            onClick = {
                if (isRunning) onStopService() else onStartService()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isRunning) "FERMA SERVIZIO" else "AVVIA SERVIZIO",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Foreground Service attivo 24/7 con WakeLock e WifiLock",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
    }
}

private fun formatUptime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}