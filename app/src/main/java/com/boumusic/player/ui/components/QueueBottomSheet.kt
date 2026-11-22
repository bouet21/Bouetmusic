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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.model.Song
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.DarkSurfaceVariant
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSong: Song?,
    onPlaySongAt: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Cabecera de la cola
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
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cola de reproducción",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${queue.size} canciones en cola",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Vaciar", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay canciones en la cola de reproducción", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height((queue.size * 64).coerceAtMost(460).dp)
                ) {
                    itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { index, song ->
                        val isCurrent = song.id == currentSong?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) NeonCyan.copy(alpha = 0.12f) else DarkSurfaceVariant.copy(alpha = 0.5f))
                                .clickable { onPlaySongAt(index) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) NeonCyan else DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (isCurrent) DarkBackground else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) NeonCyan else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${song.formattedDuration}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Botones de reordenar hacia arriba / abajo
                            if (index > 0) {
                                IconButton(onClick = { onMoveQueueItem(index, index - 1) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (index < queue.size - 1) {
                                IconButton(onClick = { onMoveQueueItem(index, index + 1) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Eliminar de la cola
                            IconButton(onClick = { onRemoveFromQueue(index) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar de cola", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
