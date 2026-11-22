package com.boumusic.player.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boumusic.player.data.MusicRepository
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

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

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

    // Servidor Wi-Fi para transferir canciones desde el ordenador
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

        // Sincronizar estado inicial
        _isPlaying.value = player.isPlaying
        _isShuffleEnabled.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
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

    fun setTransferScreenOpen(open: Boolean) {
        _isTransferScreenOpen.value = open
    }

    fun startTransferServer() {
        if (transferServer == null) {
            val context = getApplication<Application>()
            transferServer = TransferServer(context) { filename ->
                _transferredFilesCount.value += 1
                loadSongs() // Actualizar biblioteca en tiempo real al recibir canción
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
        val controller = mediaController ?: return
        val currentList = _songs.value
        val index = currentList.indexOfFirst { it.id == selectedSong.id }
        if (index == -1) return

        val mediaItems = currentList.map { song ->
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

        controller.setMediaItems(mediaItems, index, 0L)
        controller.prepare()
        controller.play()

        _currentSong.value = selectedSong
        _duration.value = selectedSong.duration
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
        mediaController?.release()
        mediaController = null
    }
}
