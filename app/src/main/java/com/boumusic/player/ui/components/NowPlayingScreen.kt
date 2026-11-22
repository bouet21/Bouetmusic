package com.boumusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.boumusic.player.model.Song
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkCardBorder
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.DeepCyan
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMinutesLeft: Int? = null,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSleepTimer: (Int) -> Unit = {},
    onShareClick: (Song) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onSkipToNextFolder: (() -> Unit)? = null,
    onSkipToPreviousFolder: (() -> Unit)? = null,
    onShuffleAcrossFolders: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableFloatStateOf(0f) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Diálogo selector de temporizador de apagado
    if (showSleepTimerDialog) {
        val timerOptions = listOf(15, 30, 45, 60, 90)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Temporizador de Apagado",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (sleepTimerMinutesLeft != null) {
                            "Apagado en aproximadamente: $sleepTimerMinutesLeft min"
                        } else {
                            "Selecciona en cuántos minutos pausar la reproducción automáticamente:"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        timerOptions.forEach { minutes ->
                            Button(
                                onClick = {
                                    onSetSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sleepTimerMinutesLeft == minutes) NeonCyan else DarkSurfaceVariant,
                                    contentColor = if (sleepTimerMinutesLeft == minutes) DarkBackground else TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "${minutes}m",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (sleepTimerMinutesLeft != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onSetSleepTimer(0)
                                showSleepTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar temporizador", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra superior limpia y equilibrada
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimizar",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "REPRODUCIENDO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextSecondary
                )

                if (sleepTimerMinutesLeft != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⏱ ${sleepTimerMinutesLeft}m",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Cola de reproducción", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showOptionsMenu = false
                            onOpenQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ecualizador de audio", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showOptionsMenu = false
                            onOpenEqualizer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Temporizador de apagado", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showOptionsMenu = false
                            showSleepTimerDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir canción", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan) },
                        onClick = {
                            showOptionsMenu = false
                            onShareClick(song)
                        }
                    )
                    if (song.isFtp) {
                        DropdownMenuItem(
                            text = { Text("Saltar a siguiente carpeta", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showOptionsMenu = false
                                onSkipToNextFolder?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Volver a carpeta anterior", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showOptionsMenu = false
                                onSkipToPreviousFolder?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Shuffle entre carpetas", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = NeonCyan) },
                            onClick = {
                                showOptionsMenu = false
                                onShuffleAcrossFolders?.invoke()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Carátula adaptable (con tamaño flexible para caber en cualquier pantalla sin desbordar)
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .padding(vertical = 10.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), spotColor = NeonCyan.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElectricPurple, DeepCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                val imageRequest = remember(song.albumArtUri) {
                    ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(600, 600)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (song.isFtp) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = song.album.substringAfterLast("/").ifBlank { song.album },
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Título y artista
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${song.artist} • ${song.album}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Barra de progreso y tiempos (Slider)
        val effectivePosition = if (isUserDraggingSlider) draggedPosition else currentPosition.toFloat()
        val safeDuration = duration.coerceAtLeast(1L).toFloat()
        val sliderValue = (effectivePosition / safeDuration).coerceIn(0f, 1f)

        Slider(
            value = sliderValue,
            onValueChange = { fraction ->
                isUserDraggingSlider = true
                draggedPosition = fraction * safeDuration
            },
            onValueChangeFinished = {
                isUserDraggingSlider = false
                onSeek(draggedPosition.toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(effectivePosition),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = formatTime(safeDuration),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Controles de reproducción principales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aleatorio
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (isShuffleEnabled) NeonCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Anterior
            IconButton(onClick = onPreviousClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Reproducir / Pausar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan, DeepCyan)
                        )
                    )
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = DarkBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Siguiente
            IconButton(onClick = onNextClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Repetir
            IconButton(onClick = onCycleRepeat, modifier = Modifier.size(42.dp)) {
                val icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                }
                val tint = if (repeatMode != Player.REPEAT_MODE_OFF) NeonCyan else TextSecondary
                Icon(
                    imageVector = icon,
                    contentDescription = "Repetir",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fila de acciones secundarias (limpia, ordenada y con espacio uniforme)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenEqualizer, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = "Ecualizador", tint = NeonCyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showSleepTimerDialog = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Bedtime, contentDescription = "Temporizador", tint = if (sleepTimerMinutesLeft != null) NeonCyan else TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onShareClick(song) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = NeonCyan, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Cola", tint = NeonCyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatTime(timeMs: Float): String {
    val totalSeconds = (timeMs / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
