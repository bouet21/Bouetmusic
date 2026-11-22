package com.boumusic.player.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.boumusic.player.ui.theme.DarkCardBorder
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
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
    val currentConfig by viewModel.currentFtpConfig.collectAsState()
    val currentPath by viewModel.ftpCurrentPath.collectAsState()
    val errorMessage by viewModel.ftpErrorMessage.collectAsState()
    val savedConfigs by viewModel.savedFtpConfigs.collectAsState()

    // Búsqueda en servidor
    val ftpSearchQuery by viewModel.ftpSearchQuery.collectAsState()
    val isFtpSearchingServer by viewModel.isFtpSearchingServer.collectAsState()
    val ftpSearchResults by viewModel.ftpSearchResults.collectAsState()
    val isFtpDeepSearchActive by viewModel.isFtpDeepSearchActive.collectAsState()
    val filteredFtpItems by viewModel.filteredFtpItems.collectAsState()

    // Escaneo recursivo
    val isFtpScanningAcrossFolders by viewModel.isFtpScanningAcrossFolders.collectAsState()
    val ftpScanProgressCount by viewModel.ftpScanProgressCount.collectAsState()

    var showAddServerDialog by remember { mutableStateOf(false) }

    if (isFtpScanningAcrossFolders) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelFtpScan() },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Explorando servidor...", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Encontradas: $ftpScanProgressCount canciones",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Preparando pistas para reproducción continua...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelFtpScan() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        if (!isConnected) {
            // ==========================================
            // VISTA DESCONECTADO (PANTALLA LIMPIA DE CONEXIÓN)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Servidores Remotos",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Reproduce música de tu PC o NAS",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = { showAddServerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DarkSurface, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir", color = DarkSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (savedConfigs.isEmpty()) {
                // Estado vacío: bienvenida y botón grande
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sin servidores configurados",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conéctate a tu ordenador o disco en red mediante FTP, FTPS o WebDAV para reproducir tu biblioteca remota en streaming.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showAddServerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DarkSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Añadir mi primer servidor", color = DarkSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Lista de servidores guardados como tarjetas limpias
                Text(
                    text = "TUS SERVIDORES (${savedConfigs.size})",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(savedConfigs, key = { it.id }) { cfg ->
                        ServerCard(
                            config = cfg,
                            isLoading = isLoading,
                            onConnect = { viewModel.connectFtp(cfg) },
                            onDelete = { viewModel.deleteSavedFtpConfig(cfg.id) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        } else {
            // ==========================================
            // VISTA CONECTADO (EXPLORADOR LIMPIO & ERGONÓMICO)
            // ==========================================

            // 1. Cabecera con estado de conexión y desconectar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = currentConfig?.name ?: "Servidor Conectado",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val protoLabel = currentConfig?.protocolLabel ?: "FTP"
                        Text(
                            text = "$protoLabel • ${currentConfig?.host}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = NeonCyan
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshFtpFolder() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = NeonCyan, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnectFtp() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text("Desconectar", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Buscador integrado
            OutlinedTextField(
                value = ftpSearchQuery,
                onValueChange = { viewModel.setFtpSearchQuery(it) },
                placeholder = { Text("Buscar canción en el servidor...", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = NeonCyan, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (ftpSearchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearFtpSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.startFtpDeepSearch() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Barra de Navegación Interactiva (Migas de Pan / Breadcrumbs)
            if (!isFtpDeepSearchActive) {
                BreadcrumbsBar(
                    currentPath = currentPath,
                    onNavigateTo = { viewModel.navigateFtpFolder(it) },
                    onNavigateUp = { viewModel.navigateFtpUp() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Barra de Acciones de Reproducción (Clara y limpia)
                val audioCount = filteredFtpItems.count { it.isAudio }
                PlaybackActionsBar(
                    audioCount = audioCount,
                    currentPath = currentPath,
                    onPlayFolder = { viewModel.playCurrentFtpFolder(shuffle = false) },
                    onShuffleFolder = { viewModel.playCurrentFtpFolder(shuffle = true) },
                    onPlayAcross = { viewModel.playFtpAcrossFolders(currentPath, shuffle = false) },
                    onShuffleAcross = { viewModel.playFtpAcrossFolders(currentPath, shuffle = true) }
                )

                Spacer(modifier = Modifier.height(10.dp))
            } else {
                // Banner de Búsqueda Profunda
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (isFtpSearchingServer) {
                            CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscando en todo el servidor...", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Resultados: ${ftpSearchResults.size} canciones", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(
                        onClick = { viewModel.setFtpDeepSearchActive(false) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("Volver a carpetas", color = NeonCyan, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 5. Contenido: Lista de Archivos y Carpetas
            if (isFtpDeepSearchActive) {
                // Resultados de búsqueda profunda
                if (ftpSearchResults.isEmpty() && !isFtpSearchingServer) {
                    EmptyStateView("No se encontraron canciones para \"$ftpSearchQuery\"")
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(ftpSearchResults, key = { it.path }) { item ->
                            FtpSearchResultRow(
                                item = item,
                                onPlay = { viewModel.playFtpSong(item, ftpSearchResults) },
                                onAddToPlaylist = { viewModel.addFtpSongToPlaylistDialog(item) },
                                onDownload = {
                                    viewModel.downloadFtpSong(item) {
                                        Toast.makeText(context, "Canción descargada en Music/Boumusic", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onGoToFolder = { viewModel.navigateToFtpItemFolder(item) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            } else {
                // Lista normal de carpeta
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Cargando archivos...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (filteredFtpItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No hay archivos en esta carpeta", color = TextSecondary, fontSize = 13.sp)
                            if (ftpSearchQuery.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.startFtpDeepSearch() }) {
                                    Text("🔍 Buscar \"$ftpSearchQuery\" en todo el servidor", color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredFtpItems, key = { it.path }) { item ->
                            FtpItemRow(
                                item = item,
                                onOpenDir = { viewModel.navigateFtpFolder(item.path) },
                                onPlay = { viewModel.playFtpSong(item) },
                                onAddToPlaylist = { viewModel.addFtpSongToPlaylistDialog(item) },
                                onDownload = {
                                    viewModel.downloadFtpSong(item) {
                                        Toast.makeText(context, "Canción descargada en Music/Boumusic", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onPlayFolder = { path, shuffle ->
                                    viewModel.playFtpAcrossFolders(path, shuffle)
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    // Diálogo emergente para añadir nuevo servidor (Claro y guiado)
    if (showAddServerDialog) {
        AddServerDialog(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onDismiss = { showAddServerDialog = false },
            onConnect = { config ->
                viewModel.connectFtp(config)
            }
        )
    }
}

/**
 * Tarjeta limpia para un servidor guardado
 */
@Composable
private fun ServerCard(
    config: FtpConfig,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when {
        config.isWebDav -> ElectricPurple
        config.isFtps -> NeonCyan
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onConnect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = badgeColor, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(config.protocolLabel, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            val address = if (config.isWebDav) "${config.host}:${config.port}${config.webDavPath}" else "${config.host}:${config.port}"
            Text(
                text = "$address • ${config.username}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(6.dp))

        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp),
            enabled = !isLoading
        ) {
            Text("Conectar", color = DarkSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Barra interactiva de migas de pan (Breadcrumbs)
 */
@Composable
private fun BreadcrumbsBar(
    currentPath: String,
    onNavigateTo: (String) -> Unit,
    onNavigateUp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPath != "/" && currentPath.isNotBlank()) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Subir", tint = NeonCyan, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono Inicio
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateTo("/") }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, contentDescription = "Inicio", tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Inicio", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            val segments = currentPath.split("/").filter { it.isNotBlank() }
            var accumulated = ""
            segments.forEachIndexed { index, segment ->
                accumulated += "/$segment"
                val target = accumulated
                val isLast = (index == segments.size - 1)

                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = segment,
                    color = if (isLast) TextPrimary else NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isLast) { onNavigateTo(target) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Barra limpia de acciones de reproducción (Reproducir, Shuffle y Avanzadas)
 */
@Composable
private fun PlaybackActionsBar(
    audioCount: Int,
    currentPath: String,
    onPlayFolder: () -> Unit,
    onShuffleFolder: () -> Unit,
    onPlayAcross: () -> Unit,
    onShuffleAcross: () -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Botón Reproducir esta carpeta
        Button(
            onClick = onPlayFolder,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            enabled = audioCount > 0
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkSurface, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reproducir ($audioCount)", color = DarkSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        // 2. Botón Aleatorio
        OutlinedButton(
            onClick = onShuffleFolder,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
            enabled = audioCount > 0
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Aleatorio", color = NeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }

        // 3. Menú de opciones avanzadas (subcarpetas)
        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
            ) {
                Icon(Icons.Default.FolderCopy, contentDescription = "Opciones con subcarpetas", tint = ElectricPurple, modifier = Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Pasar entre subcarpetas (continuo)", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.FolderCopy, contentDescription = null, tint = NeonCyan) },
                    onClick = {
                        showMoreMenu = false
                        onPlayAcross()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Shuffle de todas las subcarpetas", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = ElectricPurple) },
                    onClick = {
                        showMoreMenu = false
                        onShuffleAcross()
                    }
                )
            }
        }
    }
}

/**
 * Diálogo guiado y simplificado para añadir un nuevo servidor
 */
@Composable
private fun AddServerDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConnect: (FtpConfig) -> Unit
) {
    var protocol by remember { mutableIntStateOf(0) } // 0 = FTP, 1 = FTPS, 2 = WebDAV
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("anonymous") }
    var pass by remember { mutableStateOf("") }
    var webDavPath by remember { mutableStateOf("/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Añadir Servidor", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Selector de protocolo
                Text("TIPO DE SERVIDOR", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold), color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("FTP" to 0, "FTPS Seguro" to 1, "WebDAV" to 2).forEach { (label, index) ->
                        FilterChip(
                            selected = protocol == index,
                            onClick = {
                                protocol = index
                                if (index == 2 && port == "21") port = "443"
                                if (index != 2 && (port == "443" || port == "80")) port = "21"
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (index == 2) ElectricPurple else NeonCyan,
                                selectedLabelColor = if (index == 2) TextPrimary else DarkSurface,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej: NAS Casa)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ftpTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Dirección IP o Host", color = TextSecondary) },
                        placeholder = { Text("Ej: 192.168.1.50", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        colors = ftpTextFieldColors()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Puerto", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = ftpTextFieldColors()
                    )
                }

                if (protocol == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPath,
                        onValueChange = { webDavPath = it },
                        label = { Text("Ruta WebDAV", color = TextSecondary) },
                        placeholder = { Text("Ej: / o /remote.php/webdav/", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ftpTextFieldColors()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Usuario", color = TextSecondary) },
                    placeholder = { Text("anonymous (si no tiene)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ftpTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Contraseña (opcional)", color = TextSecondary) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ftpTextFieldColors()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPort = port.toIntOrNull() ?: if (protocol == 2) 443 else 21
                    val config = FtpConfig(
                        name = name.ifBlank { if (protocol == 2) "Servidor WebDAV" else "Servidor FTP" },
                        host = host.trim(),
                        port = parsedPort,
                        username = user.trim().ifBlank { "anonymous" },
                        password = pass,
                        isFtps = (protocol == 1),
                        isWebDav = (protocol == 2),
                        webDavPath = if (webDavPath.isBlank()) "/" else webDavPath.trim()
                    )
                    onConnect(config)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                enabled = !isLoading && host.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = DarkSurface, modifier = Modifier.size(16.dp))
                } else {
                    Text("Conectar", color = DarkSurface, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun FtpSearchResultRow(
    item: FtpItem,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onGoToFolder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NeonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
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
            val parentPath = item.path.substringBeforeLast("/").ifBlank { "/" }
            Text(
                text = "${item.formattedSize} • 📁 $parentPath",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Download, contentDescription = "Descargar", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Añadir a lista", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = NeonCyan) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ir a carpeta", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElectricPurple) },
                    onClick = {
                        showMenu = false
                        onGoToFolder()
                    }
                )
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
    onDownload: () -> Unit,
    onPlayFolder: ((path: String, shuffle: Boolean) -> Unit)? = null
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
                .size(38.dp)
                .clip(CircleShape)
                .background(if (item.isDirectory) ElectricPurple.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (item.isDirectory) ElectricPurple else NeonCyan,
                modifier = Modifier.size(20.dp)
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }

        if (item.isDirectory) {
            var showFolderMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showFolderMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showFolderMenu,
                    onDismissRequest = { showFolderMenu = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Reproducir con subcarpetas", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showFolderMenu = false
                            onPlayFolder?.invoke(item.path, false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle de esta carpeta", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showFolderMenu = false
                            onPlayFolder?.invoke(item.path, true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Abrir carpeta", color = TextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            showFolderMenu = false
                            onOpenDir()
                        }
                    )
                }
            }
        } else {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Añadir a lista", tint = NeonCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Descargar", tint = TextPrimary, modifier = Modifier.size(18.dp))
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
