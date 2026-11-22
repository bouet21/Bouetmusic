package com.boumusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkCardBorder
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary
import com.boumusic.player.ui.viewmodel.MusicViewModel

@Composable
fun SettingsDialog(
    viewModel: MusicViewModel,
    onOpenEqualizer: () -> Unit,
    onOpenStats: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var transferPin by remember { mutableStateOf(viewModel.transferPin.value) }
    var autoSkipFtp by remember { mutableStateOf(true) }
    var showExcludedFoldersDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    val excludedFolders by viewModel.excludedFolders.collectAsState()
    val ignoreMessaging by viewModel.ignoreMessagingAudio.collectAsState()
    val minDurationSec by viewModel.minDurationSeconds.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Ajustes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Sección 1: Reproducción & Audio
                SettingsSectionHeader("REPRODUCCIÓN & AUDIO", Icons.Default.MusicNote)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Salto inteligente remoto", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Salta pistas si el servidor FTP/WebDAV está apagado", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoSkipFtp,
                        onCheckedChange = { autoSkipFtp = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ecualizador, Crossfade & Loudness", color = NeonCyan, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onOpenStats()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Estadísticas y Resumen Trimestral", color = NeonCyan, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 2: Filtros de Biblioteca
                SettingsSectionHeader("BIBLIOTECA & FILTROS DE CARPETAS", Icons.Default.FolderOff)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ignorar audios de mensajería", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Oculta audios de WhatsApp, Telegram, grabaciones...", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = ignoreMessaging,
                        onCheckedChange = { viewModel.setIgnoreMessagingAudio(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Duración mínima de pista:", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3 to "3s", 15 to "15s", 30 to "30s", 60 to "1m").forEach { (sec, label) ->
                        FilterChip(
                            selected = minDurationSec == sec,
                            onClick = { viewModel.setMinDurationSeconds(sec) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricPurple,
                                selectedLabelColor = TextPrimary,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showExcludedFoldersDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gestionar carpetas ignoradas (${excludedFolders.size})", color = TextPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 3: Transferencia Wi-Fi y Seguridad
                SettingsSectionHeader("TRANSFERENCIA WI-FI & SEGURIDAD", Icons.Default.Wifi)

                OutlinedTextField(
                    value = transferPin,
                    onValueChange = {
                        transferPin = it.take(6)
                        viewModel.setTransferPin(transferPin)
                    },
                    label = { Text("PIN de seguridad web (opcional)", color = TextSecondary, fontSize = 12.sp) },
                    placeholder = { Text("Ej: 1234 (vacío = sin PIN)", color = TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text("Apagado automático del servidor Wi-Fi tras 15 min de inactividad.", color = TextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 4: Reescaneo manual
                SettingsSectionHeader("REESCANEAR ARCHIVOS", Icons.Default.Refresh)

                Button(
                    onClick = {
                        viewModel.loadSongs()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reescanear música del dispositivo", color = TextPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Formatos compatibles: MP3, FLAC, M4A, WAV, OGG, AAC, OPUS.", color = TextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 5: Migración y Copia de Seguridad (Windows / Linux)
                SettingsSectionHeader("MIGRACIÓN & BACKUP (WINDOWS / LINUX)", Icons.Default.Sync)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportBackup { file ->
                                if (file != null) {
                                    Toast.makeText(context, "Copia guardada: ${file.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Error al exportar copia", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar JSON", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar JSON", color = ElectricPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Exporta servidores SMB/FTP y listas para sincronizarlos con la app de Windows y Linux.", color = TextSecondary, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 6: Acerca de
                SettingsSectionHeader("ACERCA DE BOUMUSIC", Icons.Default.Info)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Boumusic v1.9.2", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Reproductor de audio nativo con soporte SMB (Windows/Linux), FTP, FTPS, WebDAV, streaming en red, ecualizador, crossfade y transferencia sin cables.", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("100% libre, sin publicidad ni rastreadores.", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Listo", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showExcludedFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showExcludedFoldersDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Carpetas Excluidas", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                if (excludedFolders.isEmpty()) {
                    Text(
                        "No hay carpetas excluidas manualmente.\nPuedes excluir carpetas manteniendo presionada una canción o desde su menú de opciones.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(excludedFolders.toList()) { folderPath ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = folderPath,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.removeExcludedFolder(folderPath) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Restaurar", tint = TextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludedFoldersDialog = false }) {
                    Text("Cerrar", color = NeonCyan)
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Importar Configuración", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Pega el contenido del archivo JSON de copia de seguridad para restaurar servidores, listas y ajustes:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text("Pega aquí el JSON...", color = TextSecondary, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            viewModel.importBackup(importJsonText) { success ->
                                if (success) {
                                    Toast.makeText(context, "¡Copia importada con éxito!", Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                    importJsonText = ""
                                } else {
                                    Toast.makeText(context, "Error al procesar el archivo JSON", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Restaurar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(icon, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
    }
}
