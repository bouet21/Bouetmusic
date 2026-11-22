package com.boumusic.player.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boumusic.player.data.MusicRepository
import com.boumusic.player.data.PlaylistRepository
import com.boumusic.player.ftp.FtpManager
import com.boumusic.player.ftp.FtpStreamBridge
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.Song
import com.boumusic.player.service.MusicService
import com.boumusic.player.transfer.TransferServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val playlistRepo = PlaylistRepository(application)
    val ftpManager = FtpManager()
    private val ftpStreamBridge = FtpStreamBridge(ftpManager)

    // Pestaña activa (0 = Canciones, 1 = Listas, 2 = Servidor FTP)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredSongs: StateFlow<List<Song>> = combine(_songs, _searchQuery) { songList, query ->
        if (query.isBlank()) {
            songList
        } else {
            songList.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Listas de reproducción
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    // Servidor FTP
    private val _isFtpConnected = MutableStateFlow(false)
    val isFtpConnected: StateFlow<Boolean> = _isFtpConnected.asStateFlow()

    private val _isFtpLoading = MutableStateFlow(false)
    val isFtpLoading: StateFlow<Boolean> = _isFtpLoading.asStateFlow()

    private val _ftpItems = MutableStateFlow<List<FtpItem>>(emptyList())
    val ftpItems: StateFlow<List<FtpItem>> = _ftpItems.asStateFlow()

    private val _ftpCurrentPath = MutableStateFlow("/")
    val ftpCurrentPath: StateFlow<String> = _ftpCurrentPath.asStateFlow()

    private val _ftpErrorMessage = MutableStateFlow<String?>(null)
    val ftpErrorMessage: StateFlow<String?> = _ftpErrorMessage.asStateFlow()

    // Reproducción
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isTransferScreenOpen = MutableStateFlow(false)
    val isTransferScreenOpen: StateFlow<Boolean> = _isTransferScreenOpen.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Servidor Wi-Fi PC
    private var transferServer: TransferServer? = null
    private val _isTransferServerRunning = MutableStateFlow(false)
    val isTransferServerRunning: StateFlow<Boolean> = _isTransferServerRunning.asStateFlow()

    private val _transferServerUrl = MutableStateFlow<String?>(null)
    val transferServerUrl: StateFlow<String?> = _transferServerUrl.asStateFlow()

    private val _transferredFilesCount = MutableStateFlow(0)
    val transferredFilesCount: StateFlow<Int> = _transferredFilesCount.asStateFlow()

    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    init {
        initializeMediaController()
        loadPlaylists()
        ftpStreamBridge.start()
    }

    private fun initializeMediaController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                setupPlayerListener(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toLongOrNull()
                _currentSong.value = _songs.value.find { it.id == mediaId }
                _currentPosition.value = 0L
                _duration.value = _currentSong.value?.duration ?: player.duration.coerceAtLeast(0L)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                _repeatMode.value = mode
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }
        })

        _isPlaying.value = player.isPlaying
        _isShuffleEnabled.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
    }

    fun setCurrentTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            loadSongs()
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = repository.getLocalSongs()
            _songs.value = loaded
            _isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // LISTAS DE REPRODUCCIÓN
    fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = playlistRepo.getPlaylists()
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepo.createPlaylist(name)
            loadPlaylists()
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistRepo.deletePlaylist(id)
            if (_selectedPlaylist.value?.id == id) {
                _selectedPlaylist.value = null
            }
            loadPlaylists()
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun setSongToAddToPlaylist(song: Song?) {
        _songToAddToPlaylist.value = song
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch {
            playlistRepo.addSongToPlaylist(playlistId, songId)
            loadPlaylists()
            _songToAddToPlaylist.value = null
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        viewModelScope.launch {
            playlistRepo.removeSongFromPlaylist(playlistId, songId)
            loadPlaylists()
            _selectedPlaylist.value?.let {
                if (it.id == playlistId) {
                    _selectedPlaylist.value = it.copy(songIds = it.songIds.filter { id -> id != songId })
                }
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val playlistSongs = _songs.value.filter { playlist.songIds.contains(it.id) }
        if (playlistSongs.isNotEmpty()) {
            playSongList(playlistSongs, 0)
        }
    }

    // SERVIDOR FTP
    fun connectFtp(config: FtpConfig) {
        viewModelScope.launch {
            _isFtpLoading.value = true
            _ftpErrorMessage.value = null
            val result = ftpManager.connect(config)
            result.onSuccess {
                _isFtpConnected.value = true
                _ftpCurrentPath.value = "/"
                loadFtpFiles("/")
            }.onFailure {
                _isFtpConnected.value = false
                _ftpErrorMessage.value = it.localizedMessage ?: "Error al conectar al servidor FTP"
            }
            _isFtpLoading.value = false
        }
    }

    fun disconnectFtp() {
        _isFtpConnected.value = false
        _ftpItems.value = emptyList()
        _ftpCurrentPath.value = "/"
    }

    fun loadFtpFiles(path: String) {
        viewModelScope.launch {
            _isFtpLoading.value = true
            val result = ftpManager.listFiles(path)
            result.onSuccess {
                _ftpItems.value = it
                _ftpCurrentPath.value = path
            }.onFailure {
                _ftpErrorMessage.value = it.localizedMessage
            }
            _isFtpLoading.value = false
        }
    }

    fun navigateFtpFolder(path: String) {
        loadFtpFiles(path)
    }

    fun navigateFtpUp() {
        val current = _ftpCurrentPath.value.trimEnd('/')
        val lastSlash = current.lastIndexOf('/')
        val parent = if (lastSlash <= 0) "/" else current.substring(0, lastSlash)
        loadFtpFiles(parent)
    }

    fun refreshFtpFolder() {
        loadFtpFiles(_ftpCurrentPath.value)
    }

    fun playFtpSong(item: FtpItem) {
        val controller = mediaController ?: return
        val streamUrl = ftpStreamBridge.getStreamUrl(item.path)

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.path.hashCode().toLong().toString())
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.name)
                    .setArtist("Servidor FTP")
                    .setAlbumTitle(_ftpCurrentPath.value)
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        _currentSong.value = Song(
            id = item.path.hashCode().toLong(),
            title = item.name,
            artist = "Servidor FTP",
            album = _ftpCurrentPath.value,
            duration = 0L,
            contentUri = Uri.parse(streamUrl),
            albumArtUri = null,
            dataPath = item.path
        )
    }

    fun downloadFtpSong(item: FtpItem, onDone: () -> Unit) {
        viewModelScope.launch {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val destDir = File(musicDir, "Boumusic").apply { if (!exists()) mkdirs() }
            val destFile = File(destDir, item.name)

            val result = ftpManager.downloadSong(item.path, destFile) {}
            if (result.isSuccess) {
                loadSongs()
                onDone()
            }
        }
    }

    // TRANSFERENCIA WI-FI
    fun setTransferScreenOpen(open: Boolean) {
        _isTransferScreenOpen.value = open
    }

    fun startTransferServer() {
        if (transferServer == null) {
            val context = getApplication<Application>()
            transferServer = TransferServer(context) { filename ->
                _transferredFilesCount.value += 1
                loadSongs()
            }
        }
        transferServer?.start(
            onSuccess = { url ->
                _isTransferServerRunning.value = true
                _transferServerUrl.value = url
            },
            onError = {
                _isTransferServerRunning.value = false
                _transferServerUrl.value = null
            }
        )
    }

    fun stopTransferServer() {
        transferServer?.stop()
        _isTransferServerRunning.value = false
        _transferServerUrl.value = null
    }

    fun playSong(selectedSong: Song) {
        val currentList = _songs.value
        val index = currentList.indexOfFirst { it.id == selectedSong.id }
        if (index != -1) {
            playSongList(currentList, index)
        }
    }

    private fun playSongList(list: List<Song>, startIndex: Int) {
        val controller = mediaController ?: return
        val mediaItems = list.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        _currentSong.value = list[startIndex]
        _duration.value = list[startIndex].duration
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun skipPrevious() {
        mediaController?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
            } else if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else {
                it.seekTo(0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun toggleShuffle() {
        mediaController?.let {
            val newMode = !it.shuffleModeEnabled
            it.shuffleModeEnabled = newMode
            _isShuffleEnabled.value = newMode
        }
    }

    fun cycleRepeatMode() {
        mediaController?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        stopTransferServer()
        ftpStreamBridge.stop()
        mediaController?.release()
        mediaController = null
    }
}
