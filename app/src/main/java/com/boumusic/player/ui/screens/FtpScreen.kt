package com.boumusic.player.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    val ftpItems by viewModel.ftpItems.collectAsState()
    val currentPath by viewModel.ftpCurrentPath.collectAsState()
    val errorMessage by viewModel.ftpErrorMessage.collectAsState()
    val savedConfigs by viewModel.savedFtpConfigs.collectAsState()

    // Búsqueda en servidor FTP
    val ftpSearchQuery by viewModel.ftpSearchQuery.collectAsState()
    val isFtpSearchingServer by viewModel.isFtpSearchingServer.collectAsState()
    val ftpSearchResults by viewModel.ftpSearchResults.collectAsState()
    val isFtpDeepSearchActive by viewModel.isFtpDeepSearchActive.collectAsState()
    val filteredFtpItems by viewModel.filteredFtpItems.collectAsState()

    // Escaneo recursivo entre carpetas
    val isFtpScanningAcrossFolders by viewModel.isFtpScanningAcrossFolders.collectAsState()
    val ftpScanProgressCount by viewModel.ftpScanProgressCount.collectAsState()

    if (isFtpScanningAcrossFolders) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelFtpScan() },
            containerColor = DarkSurface,
            title = {
                Text("Escaneando carpetas...", color = TextPrimary, fontWeight = FontWeight.Bold)
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
                        text = "Buscando en subcarpetas para reproducción continua y shuffle...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.cancelFtpScan() }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    var selectedProtocol by remember { mutableIntStateOf(0) } // 0 = FTP, 1 = FTPS, 2 = WebDAV
    var name by remember { mutableStateOf("Mi Servidor") }
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("21") }
    var user by remember { mutableStateOf("anonymous") }
    var pass by remember { mutableStateOf("") }
    var isFtps by remember { mutableStateOf(false) }
    var webDavPath by remember { mutableStateOf("/") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Servidores & Red",
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
                                        isFtps = cfg.isFtps
                                        viewModel.connectFtp(cfg)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cfg.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val badgeColor = when {
                                            cfg.isWebDav -> ElectricPurple
                                            cfg.isFtps -> NeonCyan
                                            else -> TextSecondary
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(cfg.protocolLabel, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    val protoDetail = if (cfg.isWebDav) "${cfg.host}:${cfg.port}${cfg.webDavPath}" else "${cfg.host}:${cfg.port}"
                                    Text("$protoDetail (${cfg.username})", color = TextSecondary, fontSize = 12.sp)
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

                    // Formulario de conexión
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Añadir Nuevo Servidor",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Selector de protocolo
                        Text("PROTOCOLO DE RED", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold), color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("FTP" to 0, "FTPS (TLS)" to 1, "WebDAV" to 2).forEach { (label, protoIndex) ->
                                FilterChip(
                                    selected = selectedProtocol == protoIndex,
                                    onClick = {
                                        selectedProtocol = protoIndex
                                        if (protoIndex == 2 && port == "21") port = "443"
                                        if (protoIndex != 2 && (port == "443" || port == "80")) port = "21"
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (protoIndex == 2) ElectricPurple else NeonCyan,
                                        selectedLabelColor = if (protoIndex == 2) TextPrimary else DarkSurface,
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
                                label = { Text(if (selectedProtocol == 2) "Host / Dominio" else "Dirección IP / Host", color = TextSecondary) },
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

                        if (selectedProtocol == 2) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = webDavPath,
                                onValueChange = { webDavPath = it },
                                label = { Text("Ruta WebDAV (ej: / o /remote.php/webdav/)", color = TextSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
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
                                val parsedPort = port.toIntOrNull() ?: if (selectedProtocol == 2) 443 else 21
                                val cfg = FtpConfig(
                                    name = name.ifBlank { if (selectedProtocol == 2) "Servidor WebDAV" else "Servidor FTP" },
                                    host = host.trim(),
                                    port = parsedPort,
                                    username = user.trim(),
                                    password = pass,
                                    isFtps = (selectedProtocol == 1),
                                    isWebDav = (selectedProtocol == 2),
                                    webDavPath = if (webDavPath.isBlank()) "/" else webDavPath.trim()
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
            // EXPLORADOR Y BUSCADOR FTP CONECTADO

            // 1. Buscador de canciones en el servidor FTP
            OutlinedTextField(
                value = ftpSearchQuery,
                onValueChange = { viewModel.setFtpSearchQuery(it) },
                placeholder = { Text("Buscar canciones en FTP...", color = TextSecondary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = NeonCyan, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (ftpSearchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearFtpSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.startFtpDeepSearch() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Barra de estado o navegación
            if (isFtpDeepSearchActive) {
                // Modo búsqueda profunda activa
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (isFtpSearchingServer) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buscando... (${ftpSearchResults.size} canciones)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = NeonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Encontradas: ${ftpSearchResults.size} canciones",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = NeonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isFtpSearchingServer) {
                        OutlinedButton(
                            onClick = { viewModel.cancelFtpDeepSearch() },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Detener", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.setFtpDeepSearchActive(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Volver a carpetas", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                // Modo explorador de carpetas normal
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Subir nivel", tint = TextPrimary)
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

                // Opción para buscar en todo el servidor si el usuario ha escrito una consulta
                if (ftpSearchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("En esta carpeta (${filteredFtpItems.size})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.15f),
                                selectedLabelColor = NeonCyan
                            )
                        )

                        FilterChip(
                            selected = false,
                            onClick = { viewModel.startFtpDeepSearch() },
                            label = { Text("🔍 Buscar en todo el servidor", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Barra de Acciones de Reproducción entre carpetas y en carpeta actual
                Spacer(modifier = Modifier.height(6.dp))
                val audioCountInFolder = filteredFtpItems.count { it.isAudio }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.playFtpAcrossFolders(currentPath, shuffle = false) },
                        leadingIcon = {
                            Icon(Icons.Default.FolderCopy, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Pasar entre carpetas", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceVariant
                        )
                    )

                    FilterChip(
                        selected = false,
                        onClick = { viewModel.playFtpAcrossFolders(currentPath, shuffle = true) },
                        leadingIcon = {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Shuffle carpetas", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceVariant
                        )
                    )

                    if (audioCountInFolder > 0) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.playCurrentFtpFolder(shuffle = false) },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Esta carpeta ($audioCountInFolder)", fontSize = 12.sp, color = TextPrimary) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Lista de resultados o contenido de carpeta
            if (isFtpDeepSearchActive) {
                // LISTA DE RESULTADOS DE BÚSQUEDA PROFUNDA
                if (ftpSearchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        if (isFtpSearchingServer) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeonCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Explorando carpetas del servidor FTP...", color = TextSecondary)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No se encontraron canciones que coincidan con \"$ftpSearchQuery\"", color = TextSecondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.setFtpDeepSearchActive(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                                ) {
                                    Text("Volver a explorar carpetas", color = NeonCyan)
                                }
                            }
                        }
                    }
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
                // LISTA NORMAL DE CARPETA (O FILTRADA LOCALMENTE)
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                } else if (filteredFtpItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        if (ftpSearchQuery.isNotBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sin resultados en esta carpeta.", color = TextSecondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.startFtpDeepSearch() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("🔍 Buscar en todo el servidor FTP", color = DarkSurface, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text("No se encontraron archivos en este directorio.", color = TextSecondary)
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
                .size(40.dp)
                .clip(CircleShape)
                .background(NeonCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = NeonCyan,
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
            val parentPath = item.path.substringBeforeLast("/").ifBlank { "/" }
            Text(
                text = "${item.formattedSize} • 📁 $parentPath",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Download, contentDescription = "Descargar a local", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
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

        if (item.isDirectory) {
            var showFolderMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showFolderMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de carpeta", tint = TextSecondary, modifier = Modifier.size(20.dp))
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
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Añadir a lista", tint = NeonCyan, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Descargar a local", tint = TextPrimary, modifier = Modifier.size(18.dp))
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
