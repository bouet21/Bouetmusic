package com.boumusic.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
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
fun FtpScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected by viewModel.isFtpConnected.collectAsState()
    val isLoading by viewModel.isFtpLoading.collectAsState()
    val ftpItems by viewModel.ftpItems.collectAsState()
    val currentPath by viewModel.ftpCurrentPath.collectAsState()
    val errorMessage by viewModel.ftpErrorMessage.collectAsState()
    val savedConfigs by viewModel.savedFtpConfigs.collectAsState()

    var name by remember { mutableStateOf("Mi Servidor FTP") }
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("anonymous") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Servidor FTP & Streaming",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isConnected) {
                IconButton(onClick = { viewModel.refreshFtpFolder() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = NeonCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isConnected) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    // Servidores guardados
                    if (savedConfigs.isNotEmpty()) {
                        Text(
                            text = "SERVIDORES GUARDADOS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        savedConfigs.forEach { cfg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurface)
                                    .clickable {
                                        name = cfg.name
                                        host = cfg.host
                                        port = cfg.port.toString()
                                        user = cfg.username
                                        pass = cfg.password
                                        viewModel.connectFtp(cfg)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cfg.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${cfg.host}:${cfg.port} (${cfg.username})", color = TextSecondary, fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSavedFtpConfig(cfg.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = TextSecondary.copy(alpha = 0.5f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Formulario de conexión FTP
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Nueva Conexión FTP",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nombre del servidor (ej: NAS Salón)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ftpTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("Dirección IP / Host", color = TextSecondary) },
                                singleLine = true,
                                modifier = Modifier.weight(2f),
                                colors = ftpTextFieldColors()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Puerto", color = TextSecondary) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = ftpTextFieldColors()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("Usuario (o 'anonymous')", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ftpTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Contraseña", color = TextSecondary) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ftpTextFieldColors()
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val parsedPort = port.toIntOrNull() ?: 21
                                val cfg = FtpConfig(
                                    name = name.ifBlank { "Servidor FTP" },
                                    host = host.trim(),
                                    port = parsedPort,
                                    username = user.trim(),
                                    password = pass
                                )
                                viewModel.connectFtp(cfg)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            enabled = !isLoading && host.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = DarkSurface, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Conectar y Guardar Servidor", color = DarkSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Explorador de archivos FTP conectado
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPath != "/" && currentPath.isNotBlank()) {
                    IconButton(
                        onClick = { viewModel.navigateFtpUp() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Subir nivel", tint = TextPrimary)
                    }
                }
                Text(
                    text = "Ruta: $currentPath",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { viewModel.disconnectFtp() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Desconectar", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else if (ftpItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No se encontraron archivos en este directorio.", color = TextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ftpItems, key = { it.path }) { item ->
                        FtpItemRow(
                            item = item,
                            onOpenDir = { viewModel.navigateFtpFolder(item.path) },
                            onPlay = { viewModel.playFtpSong(item) },
                            onAddToPlaylist = { viewModel.addFtpSongToPlaylistDialog(item) },
                            onDownload = {
                                viewModel.downloadFtpSong(item) {
                                    Toast.makeText(context, "Canción descargada en Music/Boumusic", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpItemRow(
    item: FtpItem,
    onOpenDir: () -> Unit,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable {
                if (item.isDirectory) onOpenDir() else onPlay()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (item.isDirectory) ElectricPurple.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (item.isDirectory) ElectricPurple else NeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.formattedSize,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary
            )
        }

        if (!item.isDirectory) {
            IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir por streaming", tint = NeonCyan)
            }
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Añadir a lista", tint = NeonCyan)
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Descargar a local", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun ftpTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = DarkCardBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
