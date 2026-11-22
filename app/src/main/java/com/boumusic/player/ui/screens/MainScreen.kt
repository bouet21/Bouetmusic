package com.boumusic.player.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import com.boumusic.player.model.SongSortOrder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.R
import com.boumusic.player.model.ShareType
import com.boumusic.player.model.Song
import com.boumusic.player.ui.components.EqualizerDialog
import com.boumusic.player.ui.components.HistoryView
import com.boumusic.player.ui.components.MiniPlayer
import com.boumusic.player.ui.components.NowPlayingScreen
import com.boumusic.player.ui.components.QueueBottomSheet
import com.boumusic.player.ui.components.SettingsDialog
import com.boumusic.player.ui.components.ShareOptionsDialog
import com.boumusic.player.ui.components.ShareQrDialog
import com.boumusic.player.ui.components.SongListItem
import com.boumusic.player.ui.components.StatsDialog
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
fun MainScreen(
    viewModel: MusicViewModel,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val songsSubTab by viewModel.songsSubTab.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val playbackQueue by viewModel.playbackQueue.collectAsState()

    val songs by viewModel.filteredSongs.collectAsState()
    val allSongs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val isTransferScreenOpen by viewModel.isTransferScreenOpen.collectAsState()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val shareOptionsTarget by viewModel.shareOptionsTarget.collectAsState()
    val shareTarget by viewModel.shareTarget.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val songSortOrder by viewModel.songSortOrder.collectAsState()

    var songForOptionsMenu by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistInDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInDialog by remember { mutableStateOf("") }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    val showStatsDialog by viewModel.showStatsDialog.collectAsState()

    // Diálogos de Estadísticas, Ajustes, Ecualizador y Cola
    if (showStatsDialog) {
        StatsDialog(
            historyManager = viewModel.historyManager,
            onDismiss = { viewModel.setShowStatsDialog(false) }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onOpenEqualizer = { showEqualizerDialog = true },
            onOpenStats = { viewModel.setShowStatsDialog(true) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Ordenar Canciones", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SongSortOrder.values().forEach { order ->
                        val isSelected = songSortOrder == order
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    viewModel.setSongSortOrder(order)
                                    showSortDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.displayName,
                                color = if (isSelected) NeonCyan else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            equalizerManager = viewModel.equalizerManager,
            crossfadeSeconds = crossfadeSeconds,
            onCrossfadeChange = { viewModel.setCrossfadeSeconds(it) },
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = playbackQueue,
            currentSong = currentSong,
            onPlaySongAt = { viewModel.playQueueSongAt(it) },
            onRemoveFromQueue = { viewModel.removeSongFromQueue(it) },
            onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
            onClearQueue = { viewModel.clearQueue() },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Diálogo de opciones para compartir (Bluetooth / Quick Share vs Código QR)
    if (shareOptionsTarget != null) {
        val target = shareOptionsTarget!!
        val title = if (target.type == ShareType.SONG) {
            target.song?.title ?: "Canción"
        } else {
            target.playlist?.name ?: "Lista de reproducción"
        }
        val subtitle = if (target.type == ShareType.SONG) {
            target.song?.artist ?: ""
        } else {
            "${target.playlist?.songIds?.size ?: 0} canciones"
        }
        ShareOptionsDialog(
            title = title,
            subtitle = subtitle,
            onSelectBluetooth = { viewModel.chooseShareBluetooth(context, target) },
            onSelectQr = { viewModel.chooseShareQr(target) },
            onDismiss = { viewModel.dismissShareOptions() }
        )
    }

    // Diálogo QR para compartir canción o lista
    if (shareTarget != null) {
        val target = shareTarget!!
        val title = if (target.type == ShareType.SONG) "Compartir Canción" else "Compartir Lista"
        val subtitle = if (target.type == ShareType.SONG) {
            "${target.song?.title} • ${target.song?.artist}"
        } else {
            "${target.playlist?.name} (${target.playlist?.songIds?.size ?: 0} canciones)"
        }
        ShareQrDialog(
            title = title,
            subtitle = subtitle,
            shareUrl = viewModel.getShareUrl(target),
            onSwitchToBluetooth = { viewModel.switchToBluetoothFromQr(context, target) },
            onDismiss = { viewModel.dismissShareDialog() }
        )
    }

    // Menú de opciones de canción (Reproducir a continuación, Añadir a lista, Compartir vía QR)
    if (songForOptionsMenu != null) {
        val s = songForOptionsMenu!!
        AlertDialog(
            onDismissRequest = { songForOptionsMenu = null },
            containerColor = DarkSurface,
            title = {
                Text(s.title, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column {
                    Text("${s.artist} • ${s.album}", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.playNext(s)
                                songForOptionsMenu = null
                                Toast.makeText(context, "Se reproducirá a continuación", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Reproducir a continuación", color = TextPrimary, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.setSongToAddToPlaylist(s)
                                songForOptionsMenu = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Añadir a lista de reproducción...", color = TextPrimary, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.openShareOptions(s)
                                songForOptionsMenu = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Compartir canción (Bluetooth o QR)", color = TextPrimary, fontWeight = FontWeight.Medium)
                    }

                    if (!s.isFtp && s.dataPath.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.excludeFolderOfSong(s)
                                    songForOptionsMenu = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOff, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Excluir esta carpeta de la biblioteca", color = TextSecondary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { songForOptionsMenu = null }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo para añadir canción a lista de reproducción
    if (songToAddToPlaylist != null) {
        val targetSong = songToAddToPlaylist!!
        AlertDialog(
            onDismissRequest = { viewModel.setSongToAddToPlaylist(null) },
            containerColor = DarkSurface,
            title = {
                Text("Añadir a lista", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Canción: ${targetSong.title}", style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (showCreatePlaylistInDialog) {
                        OutlinedTextField(
                            value = newPlaylistNameInDialog,
                            onValueChange = { newPlaylistNameInDialog = it },
                            label = { Text("Nombre de la nueva lista", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkCardBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (newPlaylistNameInDialog.isNotBlank()) {
                                    viewModel.createPlaylist(newPlaylistNameInDialog.trim())
                                    newPlaylistNameInDialog = ""
                                    showCreatePlaylistInDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar lista", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (playlists.isEmpty()) {
                            Text("No tienes listas creadas.", color = TextSecondary)
                        } else {
                            LazyColumn(modifier = Modifier.height(180.dp)) {
                                items(playlists, key = { it.id }) { playlist ->
                                    val alreadyIn = playlist.songIds.contains(targetSong.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (alreadyIn) DarkSurfaceVariant else Color.Transparent)
                                            .clickable {
                                                if (!alreadyIn) {
                                                    viewModel.addSongToPlaylist(playlist.id, targetSong.id)
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = if (alreadyIn) NeonCyan else TextSecondary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = playlist.name,
                                            color = if (alreadyIn) NeonCyan else TextPrimary,
                                            fontWeight = if (alreadyIn) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (alreadyIn) {
                                            Text("✓ Incluida", color = NeonCyan, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { showCreatePlaylistInDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear nueva lista...", color = NeonCyan)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setSongToAddToPlaylist(null)
                    showCreatePlaylistInDialog = false
                }) {
                    Text("Cerrar", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            Column {
                if (currentSong != null && !isNowPlayingExpanded && !isTransferScreenOpen) {
                    MiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.skipNext() },
                        onExpandClick = { viewModel.setNowPlayingExpanded(true) }
                    )
                }

                if (!isNowPlayingExpanded && !isTransferScreenOpen) {
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { viewModel.setCurrentTab(0) },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Canciones") },
                            label = { Text("Canciones") },
                            colors = navBarColors()
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { viewModel.setCurrentTab(1) },
                            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Listas") },
                            label = { Text("Listas") },
                            colors = navBarColors()
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { viewModel.setCurrentTab(2) },
                            icon = { Icon(Icons.Default.Storage, contentDescription = "Servidores") },
                            label = { Text("Servidores") },
                            colors = navBarColors()
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                0 -> {
                    // PESTAÑA CANCIONES LOCALES
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(NeonCyan, ElectricPurple)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "Boumusic",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (sleepTimerMinutesLeft != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeonCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("⏱ ${sleepTimerMinutesLeft}m", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { viewModel.setTransferScreenOpen(true) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = "Transferir desde PC", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { viewModel.loadSongs() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Selector de sub-pestañas: [Todas | Artistas | Álbumes | Géneros | Historial]
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = songsSubTab == 0 && selectedArtist == null && selectedAlbum == null && selectedGenre == null,
                                onClick = { viewModel.setSongsSubTab(0) },
                                label = { Text("Todas (${allSongs.size})") },
                                colors = chipColors()
                            )
                            FilterChip(
                                selected = songsSubTab == 1 || selectedArtist != null,
                                onClick = { viewModel.setSongsSubTab(1) },
                                label = { Text(selectedArtist ?: "Artistas") },
                                colors = chipColors()
                            )
                            FilterChip(
                                selected = songsSubTab == 2 || selectedAlbum != null,
                                onClick = { viewModel.setSongsSubTab(2) },
                                label = { Text(selectedAlbum ?: "Álbumes") },
                                colors = chipColors()
                            )
                            FilterChip(
                                selected = songsSubTab == 3 || selectedGenre != null,
                                onClick = { viewModel.setSongsSubTab(3) },
                                label = { Text(selectedGenre ?: "Géneros") },
                                colors = chipColors()
                            )
                            FilterChip(
                                selected = songsSubTab == 4,
                                onClick = { viewModel.setSongsSubTab(4) },
                                label = { Text("Historial") },
                                colors = chipColors()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = TextSecondary)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp)),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface,
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { showSortDialog = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (songSortOrder != SongSortOrder.DEFAULT) NeonCyan.copy(alpha = 0.2f) else DarkSurface)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Ordenar",
                                    tint = if (songSortOrder != SongSortOrder.DEFAULT) NeonCyan else TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            !hasPermission -> {
                                PermissionRequiredContent(onRequestPermission = onRequestPermission)
                            }
                            isLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = NeonCyan)
                                }
                            }
                            songsSubTab == 1 && selectedArtist == null -> {
                                // Vista de agrupación por ARTISTAS
                                val artists = remember(allSongs) { allSongs.groupBy { it.artist } }
                                val sortedArtists = remember(artists) { artists.keys.toList().sorted() }
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(sortedArtists, key = { it }) { artistName ->
                                        val count = artists[artistName]?.size ?: 0
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurface)
                                                .clickable { viewModel.filterByArtist(artistName) }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(artistName, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text("$count canciones", color = TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                            songsSubTab == 2 && selectedAlbum == null -> {
                                // Vista de agrupación por ÁLBUMES
                                val albums = remember(allSongs) { allSongs.groupBy { it.album } }
                                val sortedAlbums = remember(albums) { albums.keys.toList().sorted() }
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(sortedAlbums, key = { it }) { albumName ->
                                        val albumSongs = albums[albumName] ?: emptyList()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurface)
                                                .clickable { viewModel.filterByAlbum(albumName) }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Album, contentDescription = null, tint = ElectricPurple)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(albumName, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text("${albumSongs.firstOrNull()?.artist ?: ""} • ${albumSongs.size} pistas", color = TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                            songsSubTab == 3 && selectedGenre == null -> {
                                // Vista de agrupación por GÉNEROS
                                val genres = remember(allSongs) { allSongs.groupBy { it.genre.ifBlank { "Varios" } } }
                                val sortedGenres = remember(genres) { genres.keys.toList().sorted() }
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(sortedGenres, key = { it }) { genreName ->
                                        val count = genres[genreName]?.size ?: 0
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurface)
                                                .clickable { viewModel.filterByGenre(genreName) }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Category, contentDescription = null, tint = ElectricPurple)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(genreName, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text("$count canciones", color = TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                            songsSubTab == 4 -> {
                                HistoryView(
                                    viewModel = viewModel,
                                    onSongMenuClick = { songForOptionsMenu = it },
                                    onOpenStats = { viewModel.setShowStatsDialog(true) }
                                )
                            }
                            allSongs.isEmpty() -> {
                                EmptyLibraryContent(
                                    onRefresh = { viewModel.loadSongs() },
                                    onOpenTransfer = { viewModel.setTransferScreenOpen(true) }
                                )
                            }
                            songs.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_songs_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextSecondary
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(songs, key = { it.id }) { song ->
                                        val isPlayingThis = (currentSong?.id == song.id)
                                        SongListItem(
                                            song = song,
                                            isPlayingThis = isPlayingThis,
                                            onClick = { viewModel.playSong(song) },
                                            onMenuClick = { songForOptionsMenu = song }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // PESTAÑA LISTAS DE REPRODUCCIÓN
                    PlaylistsScreen(viewModel = viewModel)
                }
                2 -> {
                    // PESTAÑA SERVIDOR FTP
                    FtpScreen(viewModel = viewModel)
                }
            }

            // Vista modal de pantalla completa del reproductor actual
            AnimatedVisibility(
                visible = isNowPlayingExpanded && currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentSong?.let { song ->
                    NowPlayingScreen(
                        song = song,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        sleepTimerMinutesLeft = sleepTimerMinutesLeft,
                        onCollapse = { viewModel.setNowPlayingExpanded(false) },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onPreviousClick = { viewModel.skipPrevious() },
                        onNextClick = { viewModel.skipNext() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeat = { viewModel.cycleRepeatMode() },
                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                        onShareClick = { viewModel.openShareOptions(it) },
                        onOpenEqualizer = { showEqualizerDialog = true },
                        onOpenQueue = { showQueueSheet = true },
                        onSkipToNextFolder = { viewModel.skipToNextFtpFolder() },
                        onSkipToPreviousFolder = { viewModel.skipToPreviousFtpFolder() },
                        onShuffleAcrossFolders = { viewModel.playFtpAcrossFolders(shuffle = true) }
                    )
                }
            }

            // Pantalla modal de Transferencia Wi-Fi desde PC
            AnimatedVisibility(
                visible = isTransferScreenOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                TransferScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.setTransferScreenOpen(false) }
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = NeonCyan,
    selectedLabelColor = DarkBackground,
    containerColor = DarkSurface,
    labelColor = TextSecondary
)

@Composable
private fun navBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = DarkBackground,
    selectedTextColor = NeonCyan,
    indicatorColor = NeonCyan,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

@Composable
private fun PermissionRequiredContent(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.permission_required), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.permission_desc), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                Text(stringResource(R.string.grant_permission), color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyLibraryContent(
    onRefresh: () -> Unit,
    onOpenTransfer: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.no_songs_found), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Transfiere canciones directamente desde tu ordenador por Wi-Fi o cópialas a la carpeta Music.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Button(onClick = onOpenTransfer, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transferir desde PC", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)) {
                    Text(stringResource(R.string.refresh), color = TextPrimary)
                }
            }
        }
    }
}
