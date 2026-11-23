package com.boumusic.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkCardBorder
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.DeepCyan
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary
import com.boumusic.player.ui.viewmodel.MusicViewModel

@Composable
fun TransferScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by viewModel.isTransferServerRunning.collectAsState()
    val serverUrl by viewModel.transferServerUrl.collectAsState()
    val transferredCount by viewModel.transferredFilesCount.collectAsState()

    val discoveredDevices by viewModel.discoveredSyncDevices.collectAsState()
    val autoSyncIds by viewModel.autoSyncDeviceIds.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isScanning by viewModel.isSyncScanning.collectAsState()
    val scrollState = rememberScrollState()

    var manualIp by remember { mutableStateOf("") }
    var manualResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Red Local y Transferencia PC",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fila de información local y botón de escaneo activo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Este móvil", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "IP: ${viewModel.localSyncIp ?: "Detectando..."} • Puerto: ${viewModel.localSyncPort}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { viewModel.scanSyncSubnet() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buscando...", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Escanear red", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN 1: SINCRONIZACIÓN AUTOMÁTICA EN RED LOCAL (UDP/HTTP)
        Text(
            text = "DISPOSITIVOS CONECTADOS (AUTO-SYNC)",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        syncStatus?.let { status ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(status, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Buscando PCs y dispositivos en la misma red...",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Abre Boumusic en tu PC (Windows/Linux) conectado al mismo Wi-Fi para sincronizar automáticamente servidores y listas.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                discoveredDevices.forEach { dev ->
                    val isAutoSync = autoSyncIds.contains(dev.id)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (isAutoSync) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isAutoSync) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (dev.type.equals("PC", ignoreCase = true)) Icons.Default.Laptop else Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = if (isAutoSync) NeonCyan else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(dev.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isAutoSync) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isAutoSync) "Auto-Sync" else dev.type,
                                                    color = if (isAutoSync) NeonCyan else TextSecondary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text("${dev.ip}:${dev.port}", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto", color = if (isAutoSync) NeonCyan else TextSecondary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = isAutoSync,
                                        onCheckedChange = { viewModel.toggleAutoSync(dev.id) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = DarkBackground,
                                            checkedTrackColor = NeonCyan,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = DarkSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { viewModel.syncWithDevice(dev) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar ahora con este dispositivo", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Conexión manual directa por IP (ideal para routers mesh, aislamiento Wi-Fi o VPNs)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Conectar manualmente por dirección IP",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Introduce la IP que muestra la pantalla de tu PC u otro móvil para conectar directamente.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = { Text("Ej: 192.168.68.64", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                viewModel.connectSyncDirectly(manualIp) { ok, msg ->
                                    manualResult = ok to msg
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Conectar", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                manualResult?.let { (success, msg) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (success) NeonCyan else androidx.compose.ui.graphics.Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 2: SERVIDOR WEB WI-FI (Subir archivos vía navegador)
        Text(
            text = "SUBIDA DE ARCHIVOS POR NAVEGADOR WEB",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = ElectricPurple
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Tarjeta de estado del servidor Wi-Fi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, if (isRunning) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isRunning) NeonCyan else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Servidor Web Local",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                color = TextPrimary
                            )
                            Text(
                                text = if (isRunning) "En línea y esperando archivos" else "Desactivado",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = if (isRunning) NeonCyan else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isRunning,
                        onCheckedChange = { active ->
                            if (active) viewModel.startTransferServer() else viewModel.stopTransferServer()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                if (isRunning && serverUrl != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Abre este enlace en el navegador de tu ordenador:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = serverUrl!!,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = NeonCyan
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("URL de Boumusic Transfer", serverUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar enlace",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ElectricPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Carpeta de destino: Music/Boumusic",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                    }

                    if (transferredCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Canciones recibidas en esta sesión: $transferredCount",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                color = NeonCyan
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instrucciones paso a paso
        Text(
            text = "¿CÓMO FUNCIONA?",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        InstructionStep(
            number = "1",
            title = "Misma red Wi-Fi",
            description = "Conecta tu ordenador y tu móvil a la misma red Wi-Fi o punto de acceso."
        )

        Spacer(modifier = Modifier.height(10.dp))

        InstructionStep(
            number = "2",
            title = "Sincronización instantánea con la app de PC",
            description = "Si abres Boumusic en tu PC (Windows/Linux), ambos dispositivos se detectan automáticamente. Activa 'Auto' para sincronizar listas y servidores sin pulsar nada."
        )

        Spacer(modifier = Modifier.height(10.dp))

        InstructionStep(
            number = "3",
            title = "Subida manual por navegador",
            description = "Activa el Servidor Web arriba, abre la dirección IP en Chrome o Edge y arrastra canciones MP3 o FLAC para transferirlas por Wi-Fi."
        )
    }
}

@Composable
private fun InstructionStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(NeonCyan, DeepCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = DarkBackground
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary
            )
        }
    }
}
