package com.boumusic.player.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.boumusic.player.downloader.DestinationType
import com.boumusic.player.downloader.DownloadDestination
import com.boumusic.player.downloader.DownloadStage
import com.boumusic.player.downloader.DownloadTask
import com.boumusic.player.downloader.DownloadTrackItem
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
fun DownloaderScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val query by viewModel.downloaderQuery.collectAsState()
    val isResolving by viewModel.isResolvingDownloads.collectAsState()
    val resolvedTracks by viewModel.resolvedDownloadTracks.collectAsState()
    val resolutionError by viewModel.downloadResolutionError.collectAsState()
    val tasks by viewModel.downloadTasks.collectAsState()
    val availableDests by viewModel.availableDestinations.collectAsState()
    val selectedDest by viewModel.selectedDestination.collectAsState()

    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, ElectricPurple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Descargas SpotDL",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "Descarga y guarda en tu Teléfono, SMB o FTP",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta de Búsqueda y Destino
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setDownloaderQuery(it) },
                    placeholder = {
                        Text(
                            "Pega enlace de Spotify o escribe artista / canción...",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setDownloaderQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchOrResolveDownloads() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botón Pegar Portapapeles
                IconButton(
                    onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            viewModel.setDownloaderQuery(clipText.trim())
                            viewModel.searchOrResolveDownloads(clipText.trim())
                        } else {
                            Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Pegar",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botón Buscar
                Button(
                    onClick = { viewModel.searchOrResolveDownloads() },
                    enabled = !isResolving && query.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = DarkBackground,
                        disabledContainerColor = NeonCyan.copy(alpha = 0.3f),
                        disabledContentColor = TextSecondary.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    if (isResolving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selector de Destino de Guardado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { destinationDropdownExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDest.label,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = destinationDropdownExpanded,
                        onDismissRequest = { destinationDropdownExpanded = false },
                        modifier = Modifier.background(DarkSurface).border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                    ) {
                        availableDests.forEach { dest ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = dest.label,
                                            color = if (dest == selectedDest) NeonCyan else TextPrimary,
                                            fontWeight = if (dest == selectedDest) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                        if (dest.type != DestinationType.LOCAL && dest.serverConfig != null) {
                                            Text(
                                                text = "${dest.serverConfig.host}:${dest.serverConfig.port}",
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectDestination(dest)
                                    destinationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (resolvedTracks.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.startDownloadAll(resolvedTracks) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricPurple,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Descargar todas (${resolvedTracks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Mensaje de Error
        resolutionError?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3B1818))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = err, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lista de Cola y Resultados
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // SECCIÓN: COLA DE DESCARGAS
            if (tasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cola de Descargas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${tasks.size}",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.clearCompletedDownloads() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Limpiar", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                items(tasks, key = { it.id }) { task ->
                    AndroidDownloadTaskCard(
                        task = task,
                        onCancel = { viewModel.removeDownloadTask(task.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // SECCIÓN: RESULTADOS DE BÚSQUEDA
            if (resolvedTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Canciones Encontradas (${resolvedTracks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                items(resolvedTracks, key = { it.id }) { track ->
                    val trackTask = tasks.firstOrNull { it.track.id == track.id }
                    AndroidResolvedTrackCard(
                        track = track,
                        task = trackTask,
                        onDownload = { viewModel.startDownload(track) }
                    )
                }

                item {
                    val isDirectUrl = query.contains("spotify.com") || query.startsWith("spotify:")
                    if (!isDirectUrl) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { viewModel.loadMoreSearchResults() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceVariant,
                                    contentColor = NeonCyan
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isResolving) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = NeonCyan, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buscando más canciones...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cargar más canciones (+25)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else if (!isResolving && tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Busca una canción o pega un enlace de Spotify",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Guarda directamente en el teléfono o tus servidores SMB / FTP",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidResolvedTrackCard(
    track: DownloadTrackItem,
    task: DownloadTask?,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val isQueued = task != null && (task.stage == DownloadStage.QUEUED || task.stage == DownloadStage.SEARCHING_STREAM)
    val isDownloading = task != null && task.stage == DownloadStage.DOWNLOADING
    val isUploading = task != null && task.stage == DownloadStage.UPLOADING
    val isCompleted = task != null && task.stage == DownloadStage.COMPLETED
    val isError = task != null && task.stage == DownloadStage.ERROR

    val cardBorderColor = when {
        isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.6f)
        isError -> Color(0xFFFF5252).copy(alpha = 0.6f)
        isDownloading || isUploading || isQueued -> NeonCyan.copy(alpha = 0.5f)
        else -> DarkCardBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
            .clickable {
                if (task == null || isError) {
                    Toast.makeText(context, "Añadida a descargas: ${track.title}", Toast.LENGTH_SHORT).show()
                    onDownload()
                } else if (isDownloading) {
                    Toast.makeText(context, "Descargando: ${(task.progress * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                } else if (isCompleted) {
                    Toast.makeText(context, "Canción descargada con éxito", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Carátula
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!track.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.displaySubtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when {
                        isCompleted -> "✅ Guardado en ${task.destination.label}"
                        isError -> "❌ ${task.error ?: "Error al descargar"}"
                        isUploading -> "☁️ Subiendo a ${task.destination.label}..."
                        isDownloading -> "⬇️ Descargando ${(task.progress * 100).toInt()}%"
                        else -> "⏳ En cola de descarga"
                    }
                    val statusColor = when {
                        isCompleted -> Color(0xFF81C784)
                        isError -> Color(0xFFFF8A80)
                        else -> NeonCyan
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = track.formattedDuration,
            color = TextSecondary,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Botón táctil optimizado sin problemas de target size
        val buttonBg = when {
            isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.2f)
            isError -> Color(0xFFFF5252).copy(alpha = 0.2f)
            isDownloading || isUploading || isQueued -> NeonCyan.copy(alpha = 0.2f)
            else -> NeonCyan.copy(alpha = 0.15f)
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(buttonBg)
                .clickable {
                    if (task == null || isError) {
                        Toast.makeText(context, "Añadida a descargas: ${track.title}", Toast.LENGTH_SHORT).show()
                        onDownload()
                    } else if (isDownloading) {
                        Toast.makeText(context, "Descargando: ${(task.progress * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                    } else if (isCompleted) {
                        Toast.makeText(context, "Canción descargada con éxito", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isCompleted -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Descargado", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                }
                isError -> {
                    Icon(Icons.Default.Refresh, contentDescription = "Reintentar", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                }
                isDownloading -> {
                    CircularProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.size(20.dp),
                        color = NeonCyan,
                        strokeWidth = 2.5.dp
                    )
                }
                isQueued || isUploading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Icon(Icons.Default.Download, contentDescription = "Descargar", tint = NeonCyan, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AndroidDownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit
) {
    val isError = task.stage == DownloadStage.ERROR
    val isCompleted = task.stage == DownloadStage.COMPLETED
    val borderColor = when {
        isError -> Color(0xFFFF5252).copy(alpha = 0.5f)
        isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.5f)
        else -> NeonCyan.copy(alpha = 0.4f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isError -> Color(0xFFFF5252).copy(alpha = 0.15f)
                            isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else -> NeonCyan.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isError -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    isCompleted -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    else -> CircularProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.size(16.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${task.track.title} • ${task.track.artist}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.statusMessage.ifBlank { task.stage.label },
                        color = if (isError) Color(0xFFFF8A80) else if (isCompleted) Color(0xFF81C784) else NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→ ${task.destination.label}",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
        }

        if (!isCompleted && !isError) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = NeonCyan,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}
