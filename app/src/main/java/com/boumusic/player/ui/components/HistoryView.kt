package com.boumusic.player.ui.components

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.model.Song
import com.boumusic.player.ui.theme.DarkBackground
import com.boumusic.player.ui.theme.DarkSurface
import com.boumusic.player.ui.theme.ElectricPurple
import com.boumusic.player.ui.theme.NeonCyan
import com.boumusic.player.ui.theme.TextPrimary
import com.boumusic.player.ui.theme.TextSecondary
import com.boumusic.player.ui.viewmodel.MusicViewModel

@Composable
fun HistoryView(
    viewModel: MusicViewModel,
    onSongMenuClick: (Song) -> Unit,
    onOpenStats: () -> Unit
) {
    val historySongs by viewModel.historySongs.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val allSongs by viewModel.songs.collectAsState()

    var showTopSongsTab by remember { mutableStateOf(false) } // false = Recientes, true = Más escuchadas
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val songsMap = remember(allSongs) { allSongs.associateBy { it.id } }
    val currentList = if (showTopSongsTab) topSongs else historySongs

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = DarkSurface,
            title = { Text("Borrar historial", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas reiniciar tu historial de canciones escuchadas?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearConfirmDialog = false
                }) {
                    Text("Borrar", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Banner Trimestral llamativo
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onOpenStats() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonCyan.copy(alpha = 0.15f), ElectricPurple.copy(alpha = 0.18f))
                        )
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NeonCyan, ElectricPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("✨ Resumen Trimestral", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Minutos, canciones top y artistas del trimestre", color = TextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onOpenStats,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Ver 📊", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controles de Historial (Filtros: Recientes / Más escuchadas + Botón Borrar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan,
                    selectedLabelColor = DarkBackground,
                    containerColor = DarkSurface,
                    labelColor = TextSecondary
                )

                FilterChip(
                    selected = !showTopSongsTab,
                    onClick = { showTopSongsTab = false },
                    label = { Text("Recientes (${historySongs.size})", fontSize = 12.sp) },
                    colors = chipColors
                )

                FilterChip(
                    selected = showTopSongsTab,
                    onClick = { showTopSongsTab = true },
                    label = { Text("Más escuchadas", fontSize = 12.sp) },
                    colors = chipColors
                )
            }

            if (historySongs.isNotEmpty()) {
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Limpiar historial",
                        tint = TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Sin historial aún",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Las canciones que reproduzcas aparecerán aquí automáticamente.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(currentList, key = { _, item -> item.songId }) { index, item ->
                    val song = item.toSong(localSongsMap = songsMap)
                    val isPlayingThis = currentSong?.id == song.id
                    val subtitle = if (showTopSongsTab) {
                        "#${index + 1} • Escuchada ${item.playCount} veces • ${item.formattedTotalTime()}"
                    } else {
                        "Escuchada ${item.playCount} veces • ${item.formattedLastPlayed()}"
                    }

                    SongListItem(
                        song = song,
                        isPlayingThis = isPlayingThis,
                        onClick = { viewModel.playHistoryItem(item) },
                        onMenuClick = { onSongMenuClick(song) },
                        historySubtitle = subtitle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
