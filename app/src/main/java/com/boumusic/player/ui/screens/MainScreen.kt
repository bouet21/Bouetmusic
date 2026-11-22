package com.boumusic.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.player.R
import com.boumusic.player.ui.components.MiniPlayer
import com.boumusic.player.ui.components.NowPlayingScreen
import com.boumusic.player.ui.components.SongListItem
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
    val currentTab by viewModel.currentTab.collectAsState()
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
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showCreatePlaylistInDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInDialog by remember { mutableStateOf("") }

    // Diálogo para añadir canción a lista de reproducción
    if (songToAddToPlaylist != null) {
        val targetSong = songToAddToPlaylist!!
        AlertDialog(
            onDismissRequest = { viewModel.setSongToAddToPlaylist(null) },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Añadir a lista",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Canción: ${targetSong.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
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
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = if (alreadyIn) NeonCyan else TextSecondary)
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
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Listas") },
                            label = { Text("Listas") },
                            colors = navBarColors()
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { viewModel.setCurrentTab(2) },
                            icon = { Icon(Icons.Default.Storage, contentDescription = "Servidor FTP") },
                            label = { Text("Servidor FTP") },
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
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "Boumusic",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { viewModel.setTransferScreenOpen(true) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Transferir desde PC",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { viewModel.loadSongs() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DarkSurfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
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
                                .fillMaxWidth()
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

                        Spacer(modifier = Modifier.height(16.dp))

                        when {
                            !hasPermission -> {
                                PermissionRequiredContent(onRequestPermission = onRequestPermission)
                            }
                            isLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = NeonCyan)
                                }
                            }
                            allSongs.isEmpty() -> {
                                EmptyLibraryContent(
                                    onRefresh = { viewModel.loadSongs() },
                                    onOpenTransfer = { viewModel.setTransferScreenOpen(true) }
                                )
                            }
                            songs.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
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
                                            onMenuClick = { viewModel.setSongToAddToPlaylist(song) }
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
                        onCollapse = { viewModel.setNowPlayingExpanded(false) },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onPreviousClick = { viewModel.skipPrevious() },
                        onNextClick = { viewModel.skipNext() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeat = { viewModel.cycleRepeatMode() }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_required),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text(
                    text = stringResource(R.string.grant_permission),
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_songs_found),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transfiere canciones directamente desde tu ordenador por Wi-Fi o cópialas a la carpeta Music.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Button(
                    onClick = onOpenTransfer,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transferir desde PC",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.refresh),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
