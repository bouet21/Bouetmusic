package com.boumusic.player.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boumusic.player.audio.EqualizerManager
import android.media.MediaScannerConnection
import com.boumusic.player.data.BackupManager
import com.boumusic.player.data.HistoryAndStatsManager
import com.boumusic.player.data.MusicRepository
import com.boumusic.player.data.PlaylistRepository
import com.boumusic.player.downloader.AndroidTrackDownloaderEngine
import com.boumusic.player.downloader.DestinationType
import com.boumusic.player.downloader.DownloadDestination
import com.boumusic.player.downloader.DownloadStage
import com.boumusic.player.downloader.DownloadTask
import com.boumusic.player.downloader.DownloadTrackItem
import com.boumusic.player.downloader.SpotifyEmbedResolver
import com.boumusic.player.ftp.FtpManager
import com.boumusic.player.ftp.FtpStreamBridge
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.ShareTarget
import com.boumusic.player.model.ShareType
import com.boumusic.player.model.Song
import com.boumusic.player.model.SongHistoryItem
import com.boumusic.player.model.SongSortOrder
import com.boumusic.player.service.MusicService
import com.boumusic.player.sync.NetworkDevice
import com.boumusic.player.sync.NetworkSyncManager
import com.boumusic.player.transfer.NetworkUtils
import com.boumusic.player.transfer.TransferServer
import com.boumusic.player.util.NotificationHelper
import com.boumusic.player.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val playlistRepo = PlaylistRepository(application)
    val ftpManager = FtpManager(application)
    private val ftpStreamBridge = FtpStreamBridge(ftpManager)
    val equalizerManager = EqualizerManager(application)
    val historyManager = HistoryAndStatsManager(application)
    private val audioPrefs = application.getSharedPreferences("boumusic_audio_prefs", Context.MODE_PRIVATE)

    // Orden de clasificación de canciones
    private val _songSortOrder = MutableStateFlow(
        runCatching {
            SongSortOrder.valueOf(audioPrefs.getString("song_sort_order", SongSortOrder.DEFAULT.name) ?: SongSortOrder.DEFAULT.name)
        }.getOrDefault(SongSortOrder.DEFAULT)
    )
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    fun setSongSortOrder(order: SongSortOrder) {
        _songSortOrder.value = order
        audioPrefs.edit().putString("song_sort_order", order.name).apply()
    }

    // Historial de canciones y estadísticas
    val historySongs: StateFlow<List<SongHistoryItem>> = historyManager.historyFlow
    val topSongs: StateFlow<List<SongHistoryItem>> = historyManager.topSongsFlow

    private val _showStatsDialog = MutableStateFlow(false)
    val showStatsDialog: StateFlow<Boolean> = _showStatsDialog.asStateFlow()

    fun setShowStatsDialog(show: Boolean) {
        _showStatsDialog.value = show
    }

    fun clearHistory() {
        historyManager.clearHistory()
    }

    fun playHistoryItem(item: SongHistoryItem) {
        val song = item.toSong(_songs.value)
        playSong(song)
    }

    // Pestaña principal (0 = Canciones, 1 = Listas, 2 = Servidor FTP)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Sub-pestaña en Canciones (0 = Todas, 1 = Artistas, 2 = Álbumes, 3 = Géneros)
    private val _songsSubTab = MutableStateFlow(0)
    val songsSubTab: StateFlow<Int> = _songsSubTab.asStateFlow()

    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    // Canciones y búsqueda
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    val genres: StateFlow<List<String>> = _songs.map { list ->
        list.map { it.genre.ifBlank { "Varios" } }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cola de reproducción editable
    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    // PIN de seguridad para transferencia Wi-Fi
    private val _transferPin = MutableStateFlow("")
    val transferPin: StateFlow<String> = _transferPin.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val rawFilteredSongs: StateFlow<List<Song>> = combine(
        _songs,
        _searchQuery,
        _selectedArtist,
        _selectedAlbum,
        _selectedGenre
    ) { songList, query, artist, album, genre ->
        if (artist == null && album == null && genre == null && query.isBlank()) {
            songList
        } else {
            val q = query.trim()
            val hasQuery = q.isNotBlank()
            songList.filter { s ->
                (artist == null || s.artist.equals(artist, ignoreCase = true)) &&
                (album == null || s.album.equals(album, ignoreCase = true)) &&
                (genre == null || s.genre.equals(genre, ignoreCase = true)) &&
                (!hasQuery || (
                    s.title.contains(q, ignoreCase = true) ||
                    s.artist.contains(q, ignoreCase = true) ||
                    s.album.contains(q, ignoreCase = true) ||
                    s.genre.contains(q, ignoreCase = true)
                ))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(
        rawFilteredSongs,
        _songSortOrder
    ) { songList, sortOrder ->
        when (sortOrder) {
            SongSortOrder.DEFAULT -> songList
            SongSortOrder.TITLE_ASC -> songList.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST_ASC -> songList.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SongSortOrder.DURATION_DESC -> songList.sortedByDescending { it.duration }
            SongSortOrder.RECENT -> songList.sortedByDescending { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Listas de reproducción
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    // Servidores FTP guardados y estado
    private val _savedFtpConfigs = MutableStateFlow<List<FtpConfig>>(emptyList())
    val savedFtpConfigs: StateFlow<List<FtpConfig>> = _savedFtpConfigs.asStateFlow()

    private val _currentFtpConfig = MutableStateFlow<FtpConfig?>(null)
    val currentFtpConfig: StateFlow<FtpConfig?> = _currentFtpConfig.asStateFlow()

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

    // Búsqueda en servidor FTP (Filtro local en carpeta y Búsqueda Profunda en todo el servidor)
    private val _ftpSearchQuery = MutableStateFlow("")
    val ftpSearchQuery: StateFlow<String> = _ftpSearchQuery.asStateFlow()

    private val _isFtpSearchingServer = MutableStateFlow(false)
    val isFtpSearchingServer: StateFlow<Boolean> = _isFtpSearchingServer.asStateFlow()

    private val _ftpSearchResults = MutableStateFlow<List<FtpItem>>(emptyList())
    val ftpSearchResults: StateFlow<List<FtpItem>> = _ftpSearchResults.asStateFlow()

    private val _isFtpDeepSearchActive = MutableStateFlow(false)
    val isFtpDeepSearchActive: StateFlow<Boolean> = _isFtpDeepSearchActive.asStateFlow()

    private var ftpSearchJob: Job? = null

    val filteredFtpItems: StateFlow<List<FtpItem>> = combine(_ftpItems, _ftpSearchQuery) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            val q = query.lowercase().trim()
            items.filter { it.name.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cola de canciones activas del FTP (para saltar siguiente/anterior fluidamente)
    private val _currentFtpPlaylist = MutableStateFlow<List<Song>>(emptyList())

    // Escaneo y reproducción entre carpetas FTP (Recursivo / Shuffle entre carpetas)
    private val _isFtpScanningAcrossFolders = MutableStateFlow(false)
    val isFtpScanningAcrossFolders: StateFlow<Boolean> = _isFtpScanningAcrossFolders.asStateFlow()

    private val _ftpScanProgressCount = MutableStateFlow(0)
    val ftpScanProgressCount: StateFlow<Int> = _ftpScanProgressCount.asStateFlow()

    private var ftpScanJob: Job? = null

    // Temporizador de apagado (Sleep Timer)
    private var sleepTimerJob: Job? = null
    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft: StateFlow<Int?> = _sleepTimerMinutesLeft.asStateFlow()

    // Estado del Reproductor
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

    // Sincronización automática en red local (Boumusic Desktop / Android)
    val networkSyncManager = NetworkSyncManager(
        context = application,
        serverProvider = { _savedFtpConfigs.value },
        playlistProvider = { _playlists.value },
        onDataReceived = { newServers, newPlaylists ->
            viewModelScope.launch {
                for (s in newServers) {
                    ftpManager.saveConfig(s)
                }
                loadSavedFtpConfigs()

                val currentPlaylists = _playlists.value.toMutableList()
                for (p in newPlaylists) {
                    currentPlaylists.removeAll { it.id == p.id || it.name.equals(p.name, ignoreCase = true) }
                    currentPlaylists.add(p)
                }
                playlistRepo.savePlaylists(currentPlaylists)
                loadPlaylists()
            }
        }
    )

    val discoveredSyncDevices: StateFlow<List<NetworkDevice>> = networkSyncManager.discoveredDevices
    val autoSyncDeviceIds: StateFlow<Set<String>> = networkSyncManager.autoSyncDeviceIds
    val syncStatus: StateFlow<String?> = networkSyncManager.syncStatus
    val isSyncScanning: StateFlow<Boolean> = networkSyncManager.isScanning
    val localSyncIp: String? get() = networkSyncManager.localIp
    val localSyncPort: Int get() = networkSyncManager.actualHttpPort

    fun toggleAutoSync(deviceId: String) = networkSyncManager.toggleAutoSync(deviceId)
    fun syncWithDevice(device: NetworkDevice) = networkSyncManager.syncWithDevice(device)
    fun scanSyncSubnet() = networkSyncManager.scanSubnet()
    fun connectSyncDirectly(ip: String, onDone: (Boolean, String) -> Unit) = networkSyncManager.connectDirectly(ip, onDone = onDone)

    // DESCARGADOR SPOTDL & STREAMS
    val downloaderEngine = AndroidTrackDownloaderEngine(
        context = application,
        ftpManager = ftpManager,
        smbManager = ftpManager.smbManager,
        pcDevicesProvider = { discoveredSyncDevices.value }
    )

    private val _downloaderQuery = MutableStateFlow("")
    val downloaderQuery: StateFlow<String> = _downloaderQuery.asStateFlow()
    fun setDownloaderQuery(q: String) { _downloaderQuery.value = q }

    private val _isResolvingDownloads = MutableStateFlow(false)
    val isResolvingDownloads: StateFlow<Boolean> = _isResolvingDownloads.asStateFlow()

    private val _resolvedDownloadTracks = MutableStateFlow<List<DownloadTrackItem>>(emptyList())
    val resolvedDownloadTracks: StateFlow<List<DownloadTrackItem>> = _resolvedDownloadTracks.asStateFlow()

    private val _downloadResolutionError = MutableStateFlow<String?>(null)
    val downloadResolutionError: StateFlow<String?> = _downloadResolutionError.asStateFlow()

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    val availableDestinations: StateFlow<List<DownloadDestination>> = _savedFtpConfigs.map { servers ->
        val list = mutableListOf<DownloadDestination>()
        list.add(
            DownloadDestination(
                type = DestinationType.LOCAL,
                label = "📁 Almacenamiento Local (Música)"
            )
        )
        servers.forEach { s ->
            val tag = if (s.isSmb) "🖥️ [SMB]" else if (s.isWebDav) "☁️ [WebDAV]" else "🌐 [FTP]"
            val path = if (s.isSmb) s.cleanSmbShare else if (s.isWebDav) s.webDavPath else "/"
            list.add(
                DownloadDestination(
                    type = if (s.isSmb) DestinationType.SMB else if (s.isWebDav) DestinationType.WEBDAV else DestinationType.FTP,
                    label = "$tag ${s.name} ($path)",
                    serverConfig = s,
                    remoteSubPath = path
                )
            )
        }
        list
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        listOf(DownloadDestination(DestinationType.LOCAL, "📁 Almacenamiento Local (Música)"))
    )

    private val _selectedDestination = MutableStateFlow<DownloadDestination>(
        DownloadDestination(DestinationType.LOCAL, "📁 Almacenamiento Local (Música)")
    )
    val selectedDestination: StateFlow<DownloadDestination> = _selectedDestination.asStateFlow()
    fun selectDestination(dest: DownloadDestination) { _selectedDestination.value = dest }

    private var _currentSearchLimit = 30

    fun searchOrResolveDownloads(query: String? = null, limit: Int = 30) {
        val q = (query ?: _downloaderQuery.value).trim()
        if (q.isBlank()) return
        _currentSearchLimit = limit
        viewModelScope.launch {
            _isResolvingDownloads.value = true
            _downloadResolutionError.value = null
            try {
                val results = SpotifyEmbedResolver.resolve(q, limit = limit)
                if (results.isEmpty()) {
                    _downloadResolutionError.value = "No se encontraron pistas para: '$q'"
                } else {
                    _resolvedDownloadTracks.value = results
                }
            } catch (e: Exception) {
                _downloadResolutionError.value = "Error al resolver pistas: ${e.message ?: e.toString()}"
            } finally {
                _isResolvingDownloads.value = false
            }
        }
    }

    fun loadMoreSearchResults() {
        val q = _downloaderQuery.value.trim()
        if (q.isBlank() || _isResolvingDownloads.value) return
        _currentSearchLimit += 25
        searchOrResolveDownloads(q, limit = _currentSearchLimit)
    }

    fun startDownload(track: DownloadTrackItem, customDest: DownloadDestination? = null) {
        val dest = customDest ?: _selectedDestination.value
        val task = DownloadTask(
            id = java.util.UUID.randomUUID().toString(),
            track = track,
            destination = dest,
            stage = DownloadStage.QUEUED,
            statusMessage = "En cola para descargar..."
        )
        _downloadTasks.value = listOf(task) + _downloadTasks.value

        viewModelScope.launch {
            downloaderEngine.executeDownload(task) { updated ->
                val current = _downloadTasks.value.toMutableList()
                val idx = current.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    current[idx] = updated
                    _downloadTasks.value = current
                }
            }
            if (dest.type == DestinationType.LOCAL) {
                loadSongs()
            }
        }
    }

    fun startDownloadAll(tracks: List<DownloadTrackItem>, customDest: DownloadDestination? = null) {
        val dest = customDest ?: _selectedDestination.value
        tracks.forEach { track ->
            startDownload(track, dest)
        }
    }

    fun clearCompletedDownloads() {
        _downloadTasks.value = _downloadTasks.value.filter {
            it.stage != DownloadStage.COMPLETED && it.stage != DownloadStage.ERROR
        }
    }

    fun removeDownloadTask(taskId: String) {
        _downloadTasks.value = _downloadTasks.value.filter { it.id != taskId }
    }

    // Compartir canciones y listas (Bluetooth o QR)
    private val _shareOptionsTarget = MutableStateFlow<ShareTarget?>(null)
    val shareOptionsTarget: StateFlow<ShareTarget?> = _shareOptionsTarget.asStateFlow()

    private val _shareTarget = MutableStateFlow<ShareTarget?>(null)
    val shareTarget: StateFlow<ShareTarget?> = _shareTarget.asStateFlow()

    // Filtros de biblioteca y carpetas excluidas
    private val _excludedFolders = MutableStateFlow(repository.getExcludedFolders())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _ignoreMessagingAudio = MutableStateFlow(repository.isMessagingIgnored())
    val ignoreMessagingAudio: StateFlow<Boolean> = _ignoreMessagingAudio.asStateFlow()

    private val _minDurationSeconds = MutableStateFlow(repository.getMinDurationSeconds())
    val minDurationSeconds: StateFlow<Int> = _minDurationSeconds.asStateFlow()

    // Crossfade (Fundido cruzado)
    private val _crossfadeSeconds = MutableStateFlow(audioPrefs.getInt("crossfade_seconds", 3))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private var fadeInJob: Job? = null
    private var isFadingIn: Boolean = false

    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    init {
        initializeMediaController()
        loadPlaylists()
        loadSavedFtpConfigs()
        ftpStreamBridge.start()
        networkSyncManager.start()
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
                equalizerManager.attachToSession(MusicService.activeAudioSessionId)
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
                // Buscar canción tanto en canciones locales como en la lista activa del FTP o playlist
                val found = _songs.value.find { it.id == mediaId }
                    ?: _currentFtpPlaylist.value.find { it.id == mediaId }

                _currentSong.value = found
                _currentPosition.value = 0L
                _duration.value = found?.duration?.takeIf { it > 0 } ?: player.duration.coerceAtLeast(0L)

                if (found != null) {
                    historyManager.recordSongPlayStart(found)
                }

                if (_crossfadeSeconds.value > 0) {
                    startFadeIn(_crossfadeSeconds.value)
                } else {
                    mediaController?.volume = 1.0f
                }

                // Si es una canción remota FTP y no estamos conectados al servidor FTP
                if (found != null && found.isFtp) {
                    if (!_isFtpConnected.value || !ftpManager.isConnected) {
                        handleFtpSongUnavailable(found, player)
                        return
                    }
                }
                consecutiveFtpSkips = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                val current = _currentSong.value
                if (current != null && current.isFtp) {
                    handleFtpSongUnavailable(current, player)
                }
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
                } else if (playbackState == Player.STATE_ENDED) {
                    val current = _currentSong.value
                    if (current != null && current.isFtp) {
                        skipToNextFtpFolder()
                    }
                }
            }
        })

        _isPlaying.value = player.isPlaying
        _isShuffleEnabled.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
    }

    private var consecutiveFtpSkips = 0

    private fun handleFtpSongUnavailable(song: Song, player: Player) {
        val context = getApplication<Application>()
        val protocolName = _currentFtpConfig.value?.protocolLabel ?: "Remoto"
        consecutiveFtpSkips++

        // 1. Mostrar notificación en la barra de estado
        NotificationHelper.notifyRemoteSkipped(context, song.title, protocolName)

        // 2. Mostrar Toast inmediato en la interfaz
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Servidor $protocolName no disponible: saltando \"${song.title}\"",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. Saltar a la siguiente canción automáticamente
        val maxSkips = (_currentFtpPlaylist.value.size).coerceAtLeast(1)
        if (consecutiveFtpSkips < maxSkips && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            consecutiveFtpSkips = 0
            player.stop()
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "No hay más canciones disponibles en la lista.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun setCurrentTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setSongsSubTab(subTab: Int) {
        _songsSubTab.value = subTab
        if (subTab == 0 || subTab == 4) {
            _selectedArtist.value = null
            _selectedAlbum.value = null
            _selectedGenre.value = null
        }
    }

    fun filterByArtist(artist: String?) {
        _selectedArtist.value = artist
        _selectedAlbum.value = null
        _selectedGenre.value = null
    }

    fun filterByAlbum(album: String?) {
        _selectedAlbum.value = album
        _selectedArtist.value = null
        _selectedGenre.value = null
    }

    fun filterByGenre(genre: String?) {
        _selectedGenre.value = genre
        _selectedArtist.value = null
        _selectedAlbum.value = null
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

    // COLA: REPRODUCIR A CONTINUACIÓN (PLAY NEXT)
    fun playNext(song: Song) {
        val controller = mediaController ?: return
        val mediaItem = MediaItem.Builder()
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

        val nextIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(nextIndex, mediaItem)

        val q = _playbackQueue.value.toMutableList()
        val insertIndex = nextIndex.coerceIn(0, q.size)
        q.add(insertIndex, song)
        _playbackQueue.value = q
    }

    // GESTIÓN DE COLA DE REPRODUCCIÓN
    fun playQueueSongAt(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, 0L)
            controller.play()
            _currentSong.value = _playbackQueue.value.getOrNull(index)
        }
    }

    fun removeSongFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until _playbackQueue.value.size) {
            val currentList = _playbackQueue.value.toMutableList()
            currentList.removeAt(index)
            _playbackQueue.value = currentList
            if (index in 0 until controller.mediaItemCount) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val currentList = _playbackQueue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _playbackQueue.value = currentList
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        _playbackQueue.value = emptyList()
        controller.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
    }

    fun setTransferPin(pin: String) {
        _transferPin.value = pin
        transferServer?.pin = pin.ifBlank { null }
    }

    // TEMPORIZADOR DE APAGADO (SLEEP TIMER)
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerMinutesLeft.value = null
            return
        }
        _sleepTimerMinutesLeft.value = minutes
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60000)
                remaining--
                _sleepTimerMinutesLeft.value = remaining
            }
            mediaController?.pause()
            _sleepTimerMinutesLeft.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = null
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

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepo.addSongToPlaylist(playlistId, song)
            loadPlaylists()
            _songToAddToPlaylist.value = null
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val song = _songToAddToPlaylist.value?.takeIf { it.id == songId }
            ?: _songs.value.find { it.id == songId }
        if (song != null) {
            addSongToPlaylist(playlistId, song)
        } else {
            viewModelScope.launch {
                playlistRepo.addSongToPlaylist(playlistId, songId)
                loadPlaylists()
                _songToAddToPlaylist.value = null
            }
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

    fun getPlaylistSongs(playlist: Playlist): List<Song> {
        val localMap = _songs.value.associateBy { it.id }
        val ftpMap = playlist.ftpSongs.associateBy { it.id }
        return playlist.songIds.mapNotNull { id ->
            localMap[id] ?: ftpMap[id]
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val playlistSongs = getPlaylistSongs(playlist)
        if (playlistSongs.isNotEmpty()) {
            playSongList(playlistSongs, 0)
        }
    }

    fun exportPlaylistM3u(playlist: Playlist, onDone: (File) -> Unit) {
        viewModelScope.launch {
            val file = playlistRepo.exportToM3u(playlist, _songs.value)
            onDone(file)
        }
    }

    // SERVIDOR FTP
    fun loadSavedFtpConfigs() {
        viewModelScope.launch {
            _savedFtpConfigs.value = ftpManager.getSavedConfigs()
        }
    }

    fun deleteSavedFtpConfig(id: String) {
        viewModelScope.launch {
            ftpManager.deleteConfig(id)
            loadSavedFtpConfigs()
        }
    }

    fun connectFtp(config: FtpConfig) {
        viewModelScope.launch {
            _isFtpLoading.value = true
            _ftpErrorMessage.value = null
            val result = ftpManager.connect(config)
            result.onSuccess {
                _currentFtpConfig.value = config
                _isFtpConnected.value = true
                _ftpCurrentPath.value = "/"
                loadSavedFtpConfigs()
                loadFtpFiles("/")
            }.onFailure {
                _currentFtpConfig.value = null
                _isFtpConnected.value = false
                _ftpErrorMessage.value = it.localizedMessage ?: "Error al conectar al servidor FTP"
            }
            _isFtpLoading.value = false
        }
    }

    fun disconnectFtp() {
        clearFtpSearch()
        ftpManager.disconnect()
        _isFtpConnected.value = false
        _currentFtpConfig.value = null
        _ftpItems.value = emptyList()
        _currentFtpPlaylist.value = emptyList()
        _ftpCurrentPath.value = "/"
    }

    // GESTIÓN DE BÚSQUEDA EN SERVIDOR FTP
    fun setFtpSearchQuery(query: String) {
        _ftpSearchQuery.value = query
        if (query.isBlank()) {
            cancelFtpDeepSearch()
            _isFtpDeepSearchActive.value = false
        }
    }

    fun setFtpDeepSearchActive(active: Boolean) {
        _isFtpDeepSearchActive.value = active
    }

    fun startFtpDeepSearch(query: String? = null) {
        val q = (query ?: _ftpSearchQuery.value).trim()
        if (q.isBlank()) return

        ftpSearchJob?.cancel()
        _isFtpSearchingServer.value = true
        _isFtpDeepSearchActive.value = true
        _ftpSearchResults.value = emptyList()

        ftpSearchJob = viewModelScope.launch {
            val result = ftpManager.searchSongs(
                query = q,
                startPath = "/",
                maxResults = 250,
                maxDepth = 10,
                onFoundBatch = { found ->
                    _ftpSearchResults.value = found
                }
            )
            result.onSuccess { list ->
                _ftpSearchResults.value = list
            }.onFailure {
                // Mantener los resultados parciales que se hayan encontrado
            }
            _isFtpSearchingServer.value = false
        }
    }

    fun cancelFtpDeepSearch() {
        ftpSearchJob?.cancel()
        ftpSearchJob = null
        _isFtpSearchingServer.value = false
    }

    fun clearFtpSearch() {
        cancelFtpDeepSearch()
        _ftpSearchQuery.value = ""
        _isFtpDeepSearchActive.value = false
        _ftpSearchResults.value = emptyList()
    }

    fun navigateToFtpItemFolder(item: FtpItem) {
        val parent = item.path.substringBeforeLast("/").ifBlank { "/" }
        clearFtpSearch()
        loadFtpFiles(parent)
    }

    fun addFtpSongToPlaylistDialog(item: FtpItem) {
        val cfg = _currentFtpConfig.value
        val host = cfg?.host ?: "Servidor FTP"
        val streamUrl = ftpStreamBridge.getStreamUrl(item.path, item.size)
        val song = Song(
            id = (item.path + host).hashCode().toLong(),
            title = item.name.substringBeforeLast("."),
            artist = cfg?.name ?: "Servidor FTP",
            album = item.path.substringBeforeLast("/").ifBlank { "/" },
            duration = 0L,
            contentUri = Uri.parse(streamUrl),
            albumArtUri = null,
            dataPath = item.path,
            isFtp = true,
            ftpPath = item.path,
            ftpServerHost = host
        )
        _songToAddToPlaylist.value = song
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
        clearFtpSearch()
        loadFtpFiles(path)
    }

    fun navigateFtpUp() {
        clearFtpSearch()
        val current = _ftpCurrentPath.value.trimEnd('/')
        val lastSlash = current.lastIndexOf('/')
        val parent = if (lastSlash <= 0) "/" else current.substring(0, lastSlash)
        loadFtpFiles(parent)
    }

    fun refreshFtpFolder() {
        loadFtpFiles(_ftpCurrentPath.value)
    }

    // REPRODUCCIÓN Y SALTO FLUIDO DE CANCIONES FTP
    fun playFtpSong(item: FtpItem, customQueue: List<FtpItem>? = null) {
        val controller = mediaController ?: return
        val audioItems = (customQueue ?: if (_isFtpDeepSearchActive.value) _ftpSearchResults.value else _ftpItems.value)
            .filter { it.isAudio }
        if (audioItems.isEmpty()) return

        val index = audioItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
        val cfg = _currentFtpConfig.value
        val host = cfg?.host ?: "Servidor FTP"

        // Crear lista completa de canciones FTP para que el skip (siguiente/anterior) funcione perfectamente
        val ftpSongs = audioItems.map { audio ->
            val streamUrl = ftpStreamBridge.getStreamUrl(audio.path, audio.size)
            Song(
                id = (audio.path + host).hashCode().toLong(),
                title = audio.name.substringBeforeLast("."),
                artist = cfg?.name ?: "Servidor FTP",
                album = audio.path.substringBeforeLast("/").ifBlank { "/" },
                duration = 0L,
                contentUri = Uri.parse(streamUrl),
                albumArtUri = null,
                dataPath = audio.path,
                isFtp = true,
                ftpPath = audio.path,
                ftpServerHost = host
            )
        }
        _currentFtpPlaylist.value = ftpSongs

        val mediaItems = ftpSongs.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }

        controller.setMediaItems(mediaItems, index, 0L)
        controller.prepare()
        controller.play()

        _currentSong.value = ftpSongs[index]
    }

    /**
     * Reproduce música entre carpetas (escaneo recursivo de subcarpetas).
     * Si [shuffle] es true, mezcla todas las canciones de todas las subcarpetas aleatoriamente.
     * Si [shuffle] es false, las reproduce en orden continuo carpeta por carpeta.
     */
    fun playFtpAcrossFolders(startPath: String = _ftpCurrentPath.value, shuffle: Boolean = false) {
        val controller = mediaController ?: return
        val cfg = _currentFtpConfig.value
        val host = cfg?.host ?: "Servidor FTP"

        ftpScanJob?.cancel()
        ftpScanJob = viewModelScope.launch {
            _isFtpScanningAcrossFolders.value = true
            _ftpScanProgressCount.value = 0

            val result = ftpManager.listAllAudioFilesRecursively(startPath) { count ->
                _ftpScanProgressCount.value = count
            }

            _isFtpScanningAcrossFolders.value = false

            result.onSuccess { rawItems ->
                if (rawItems.isEmpty()) {
                    Toast.makeText(getApplication(), "No se encontraron canciones en las carpetas seleccionadas", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }

                val items = if (shuffle) rawItems.shuffled() else rawItems
                val ftpSongs = items.map { audio ->
                    val streamUrl = ftpStreamBridge.getStreamUrl(audio.path, audio.size)
                    val folderName = audio.path.substringBeforeLast("/").ifBlank { "/" }
                    Song(
                        id = (audio.path + host).hashCode().toLong(),
                        title = audio.name.substringBeforeLast("."),
                        artist = cfg?.name ?: "Servidor FTP",
                        album = folderName,
                        duration = 0L,
                        contentUri = Uri.parse(streamUrl),
                        albumArtUri = null,
                        dataPath = audio.path,
                        isFtp = true,
                        ftpPath = audio.path,
                        ftpServerHost = host
                    )
                }

                _currentFtpPlaylist.value = ftpSongs

                val mediaItems = ftpSongs.map { s ->
                    MediaItem.Builder()
                        .setMediaId(s.id.toString())
                        .setUri(s.contentUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setAlbumTitle(s.album)
                                .build()
                        )
                        .build()
                }

                controller.setMediaItems(mediaItems, 0, 0L)
                controller.shuffleModeEnabled = shuffle
                controller.prepare()
                controller.play()

                _currentSong.value = ftpSongs.firstOrNull()

                val msg = if (shuffle) {
                    "🔀 Modo aleatorio: ${ftpSongs.size} canciones de varias carpetas"
                } else {
                    "📁 Reproduciendo ${ftpSongs.size} canciones entre carpetas"
                }
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(getApplication(), "Error al escanear carpetas: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelFtpScan() {
        ftpScanJob?.cancel()
        _isFtpScanningAcrossFolders.value = false
    }

    fun playCurrentFtpFolder(shuffle: Boolean = false) {
        val audioItems = _ftpItems.value.filter { it.isAudio }
        if (audioItems.isEmpty()) {
            Toast.makeText(getApplication(), "No hay canciones en esta carpeta", Toast.LENGTH_SHORT).show()
            return
        }
        val first = if (shuffle) audioItems.random() else audioItems.first()
        val items = if (shuffle) audioItems.shuffled() else audioItems
        playFtpSong(first, items)
        if (shuffle) {
            mediaController?.shuffleModeEnabled = true
        }
    }

    fun skipToNextFtpFolder() {
        val controller = mediaController ?: return
        val current = _currentSong.value ?: return
        if (!current.isFtp) return

        val playlist = _currentFtpPlaylist.value
        val currentFolder = current.album
        val currentIndex = playlist.indexOfFirst { it.id == current.id }

        var targetIndex = -1
        if (currentIndex in playlist.indices) {
            for (i in (currentIndex + 1) until playlist.size) {
                if (playlist[i].album != currentFolder) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex != -1) {
            controller.seekTo(targetIndex, 0L)
            controller.play()
            val folderName = playlist[targetIndex].album.substringAfterLast("/").ifBlank { playlist[targetIndex].album }
            Toast.makeText(getApplication(), "Carpeta: $folderName", Toast.LENGTH_SHORT).show()
        } else {
            // Si no hay más carpetas en la cola actual, buscar dinámicamente la siguiente carpeta en el servidor
            viewModelScope.launch {
                val currentPath = current.ftpPath
                val currentDir = currentPath.substringBeforeLast("/").ifBlank { "/" }
                val parentDir = currentDir.trimEnd('/').substringBeforeLast("/").ifBlank { "/" }

                val listRes = ftpManager.listFiles(parentDir)
                listRes.onSuccess { siblings ->
                    val dirs = siblings.filter { it.isDirectory }.map { it.path }
                    val curIdx = dirs.indexOf(currentDir)
                    val nextDir = if (curIdx != -1 && curIdx + 1 < dirs.size) {
                        dirs[curIdx + 1]
                    } else if (dirs.isNotEmpty() && dirs.first() != currentDir) {
                        dirs.first()
                    } else null

                    if (nextDir != null) {
                        val subRes = ftpManager.listFiles(nextDir)
                        subRes.onSuccess { subItems ->
                            val audios = subItems.filter { it.isAudio }
                            if (audios.isNotEmpty()) {
                                playFtpSong(audios.first(), audios)
                                val folderName = nextDir.substringAfterLast("/").ifBlank { nextDir }
                                Toast.makeText(getApplication(), "Siguiente carpeta: $folderName", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(getApplication(), "No hay canciones en la siguiente carpeta", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(getApplication(), "Fin de carpetas en el servidor", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun skipToPreviousFtpFolder() {
        val controller = mediaController ?: return
        val current = _currentSong.value ?: return
        if (!current.isFtp) return

        val playlist = _currentFtpPlaylist.value
        val currentFolder = current.album
        val currentIndex = playlist.indexOfFirst { it.id == current.id }

        if (currentIndex > 0) {
            var prevFolderStartIndex = -1
            for (i in (currentIndex - 1) downTo 0) {
                if (playlist[i].album != currentFolder) {
                    val prevFolder = playlist[i].album
                    var start = i
                    while (start > 0 && playlist[start - 1].album == prevFolder) {
                        start--
                    }
                    prevFolderStartIndex = start
                    break
                }
            }

            if (prevFolderStartIndex != -1) {
                controller.seekTo(prevFolderStartIndex, 0L)
                controller.play()
                val folderName = playlist[prevFolderStartIndex].album.substringAfterLast("/").ifBlank { playlist[prevFolderStartIndex].album }
                Toast.makeText(getApplication(), "Carpeta: $folderName", Toast.LENGTH_SHORT).show()
                return
            }
        }

        viewModelScope.launch {
            val currentPath = current.ftpPath
            val currentDir = currentPath.substringBeforeLast("/").ifBlank { "/" }
            val parentDir = currentDir.trimEnd('/').substringBeforeLast("/").ifBlank { "/" }

            val listRes = ftpManager.listFiles(parentDir)
            listRes.onSuccess { siblings ->
                val dirs = siblings.filter { it.isDirectory }.map { it.path }
                val curIdx = dirs.indexOf(currentDir)
                val prevDir = if (curIdx > 0) {
                    dirs[curIdx - 1]
                } else null

                if (prevDir != null) {
                    val subRes = ftpManager.listFiles(prevDir)
                    subRes.onSuccess { subItems ->
                        val audios = subItems.filter { it.isAudio }
                        if (audios.isNotEmpty()) {
                            playFtpSong(audios.first(), audios)
                            val folderName = prevDir.substringAfterLast("/").ifBlank { prevDir }
                            Toast.makeText(getApplication(), "Carpeta: $folderName", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(getApplication(), "Primera carpeta alcanzada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadFtpSong(item: FtpItem, onDone: () -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val destDir = File(musicDir, "Boumusic").apply { if (!exists()) mkdirs() }
            val destFile = File(destDir, item.name)

            NotificationHelper.notifyDownloadStarted(context, item.name)
            Toast.makeText(context, "Descargando: ${item.name}...", Toast.LENGTH_SHORT).show()

            val result = ftpManager.downloadSong(item.path, destFile) {}
            if (result.isSuccess) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("audio/*")
                ) { _, _ ->
                    viewModelScope.launch { loadSongs() }
                }
                NotificationHelper.notifyDownloadCompleted(context, item.name, true)
                Toast.makeText(context, "Descarga completada: ${item.name}", Toast.LENGTH_SHORT).show()
                onDone()
            } else {
                NotificationHelper.notifyDownloadCompleted(context, item.name, false)
                Toast.makeText(context, "Error al descargar: ${result.exceptionOrNull()?.message ?: "Servidor no responde"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // TRANSFERENCIA WI-FI Y COMPARTIR POR QR
    fun setTransferScreenOpen(open: Boolean) {
        _isTransferScreenOpen.value = open
    }

    fun startTransferServer() {
        val context = getApplication<Application>()
        if (transferServer == null) {
            transferServer = TransferServer(
                context = context,
                songProvider = { _songs.value },
                playlistProvider = { _playlists.value }
            ) { filename ->
                _transferredFilesCount.value += 1
                loadSongs()
            }
        } else {
            transferServer?.songProvider = { _songs.value }
            transferServer?.playlistProvider = { _playlists.value }
        }
        transferServer?.onAutoStopped = {
            _isTransferServerRunning.value = false
            _transferServerUrl.value = null
            NotificationHelper.notifyTransferServerState(context, false, null)
        }
        transferServer?.start(
            onSuccess = { url ->
                _isTransferServerRunning.value = true
                _transferServerUrl.value = url
                NotificationHelper.notifyTransferServerState(context, true, url)
            },
            onError = {
                _isTransferServerRunning.value = false
                _transferServerUrl.value = null
                NotificationHelper.notifyTransferServerState(context, false, null)
            }
        )
    }

    fun stopTransferServer() {
        transferServer?.stop()
        _isTransferServerRunning.value = false
        _transferServerUrl.value = null
        NotificationHelper.notifyTransferServerState(getApplication(), false, null)
    }

    // EXPORTACIÓN E IMPORTACIÓN DE COPIA DE SEGURIDAD Y M3U8
    fun exportBackup(onResult: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val servers = _savedFtpConfigs.value
                val playlists = _playlists.value
                val settings = mapOf(
                    "crossfade_seconds" to _crossfadeSeconds.value,
                    "min_duration_seconds" to _minDurationSeconds.value,
                    "ignore_messaging" to _ignoreMessagingAudio.value,
                    "excluded_folders" to _excludedFolders.value.toList()
                )
                val json = BackupManager.generateBackupJson(servers, playlists, settings)
                val file = BackupManager.saveBackupToFile(getApplication(), json)
                onResult(file)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    fun importBackup(jsonContent: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val data = BackupManager.parseBackupJson(jsonContent)
                for (s in data.servers) {
                    ftpManager.saveConfig(s)
                }
                loadSavedFtpConfigs()

                val currentPlaylists = _playlists.value.toMutableList()
                for (p in data.playlists) {
                    currentPlaylists.removeAll { it.id == p.id || it.name.equals(p.name, ignoreCase = true) }
                    currentPlaylists.add(p)
                }
                playlistRepo.savePlaylists(currentPlaylists)
                loadPlaylists()

                // Restaurar ajustes si existen
                (data.settings["crossfade_seconds"] as? Number)?.toInt()?.let { setCrossfadeSeconds(it) }
                (data.settings["min_duration_seconds"] as? Number)?.toInt()?.let { setMinDurationSeconds(it) }
                (data.settings["ignore_messaging"] as? Boolean)?.let { setIgnoreMessagingAudio(it) }

                onDone(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onDone(false)
            }
        }
    }

    fun exportPlaylistToM3u8(playlist: Playlist, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val file = BackupManager.savePlaylistM3u8ToFile(getApplication(), playlist, _songs.value)
                onResult(file)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    fun importM3u8(m3u8Content: String, fallbackName: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val playlist = BackupManager.parseM3u8(m3u8Content, fallbackName, _songs.value)
                val current = _playlists.value.toMutableList()
                current.removeAll { it.name.equals(playlist.name, ignoreCase = true) }
                current.add(playlist)
                playlistRepo.savePlaylists(current)
                loadPlaylists()
                onDone(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onDone(false)
            }
        }
    }

    fun openShareOptions(song: Song) {
        _shareOptionsTarget.value = ShareTarget(ShareType.SONG, song = song)
    }

    fun openShareOptions(playlist: Playlist) {
        _shareOptionsTarget.value = ShareTarget(ShareType.PLAYLIST, playlist = playlist)
    }

    fun dismissShareOptions() {
        _shareOptionsTarget.value = null
    }

    fun chooseShareBluetooth(context: Context, target: ShareTarget) {
        _shareOptionsTarget.value = null
        when (target.type) {
            ShareType.SONG -> {
                target.song?.let { song ->
                    ShareHelper.shareSongViaBluetooth(context, song)
                }
            }
            ShareType.PLAYLIST -> {
                target.playlist?.let { playlist ->
                    val songs = getPlaylistSongs(playlist)
                    ShareHelper.sharePlaylistViaBluetooth(context, playlist, songs)
                }
            }
        }
    }

    fun chooseShareQr(target: ShareTarget) {
        _shareOptionsTarget.value = null
        when (target.type) {
            ShareType.SONG -> target.song?.let { shareSong(it) }
            ShareType.PLAYLIST -> target.playlist?.let { sharePlaylist(it) }
        }
    }

    fun switchToBluetoothFromQr(context: Context, target: ShareTarget) {
        dismissShareDialog()
        when (target.type) {
            ShareType.SONG -> target.song?.let { ShareHelper.shareSongViaBluetooth(context, it) }
            ShareType.PLAYLIST -> target.playlist?.let { playlist ->
                val songs = getPlaylistSongs(playlist)
                ShareHelper.sharePlaylistViaBluetooth(context, playlist, songs)
            }
        }
    }

    fun shareSong(song: Song) {
        startTransferServer()
        _shareTarget.value = ShareTarget(ShareType.SONG, song = song)
    }

    fun sharePlaylist(playlist: Playlist) {
        startTransferServer()
        _shareTarget.value = ShareTarget(ShareType.PLAYLIST, playlist = playlist)
    }

    fun dismissShareDialog() {
        _shareTarget.value = null
    }

    fun getShareUrl(target: ShareTarget): String {
        val context = getApplication<Application>()
        val ip = NetworkUtils.getLocalIpAddress(context) ?: "127.0.0.1"
        return when (target.type) {
            ShareType.SONG -> "http://$ip:8080/share/song?id=${target.song?.id}"
            ShareType.PLAYLIST -> "http://$ip:8080/share/playlist?id=${target.playlist?.id}"
        }
    }

    fun playSong(selectedSong: Song) {
        val currentList = if (selectedSong.isFtp) {
            _selectedPlaylist.value?.let { getPlaylistSongs(it) } ?: listOf(selectedSong)
        } else {
            _songs.value
        }
        val index = currentList.indexOfFirst { it.id == selectedSong.id }.coerceAtLeast(0)
        playSongList(currentList, index)
    }

    private fun playSongList(list: List<Song>, startIndex: Int) {
        val controller = mediaController ?: return
        val mediaItems = list.map { song ->
            val uri = if (song.isFtp) {
                Uri.parse(ftpStreamBridge.getStreamUrl(song.ftpPath, 0L))
            } else {
                song.contentUri
            }
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(uri)
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

        _currentFtpPlaylist.value = list
        _playbackQueue.value = list
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        val startingSong = list.getOrNull(startIndex)
        _currentSong.value = startingSong
        _duration.value = startingSong?.duration ?: 0L
        startingSong?.let { historyManager.recordSongPlayStart(it) }
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
        fadeInJob?.cancel()
        isFadingIn = false
        mediaController?.volume = 1.0f
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            } else {
                val current = _currentSong.value
                if (current != null && current.isFtp) {
                    skipToNextFtpFolder()
                }
            }
        }
    }

    fun skipPrevious() {
        fadeInJob?.cancel()
        isFadingIn = false
        mediaController?.volume = 1.0f
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
        fadeInJob?.cancel()
        isFadingIn = false
        mediaController?.volume = 1.0f
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 10)
        _crossfadeSeconds.value = clamped
        audioPrefs.edit().putInt("crossfade_seconds", clamped).apply()
        if (clamped == 0) {
            mediaController?.volume = 1.0f
        }
    }

    private fun startFadeIn(seconds: Int) {
        fadeInJob?.cancel()
        if (seconds <= 0) {
            mediaController?.volume = 1.0f
            return
        }
        fadeInJob = viewModelScope.launch {
            isFadingIn = true
            val controller = mediaController
            if (controller != null) {
                controller.volume = 0.05f
                val steps = 20
                val stepDelay = (seconds * 1000L) / steps
                for (i in 1..steps) {
                    delay(stepDelay)
                    if (!isActive || !isFadingIn) break
                    controller.volume = (i.toFloat() / steps).coerceIn(0.05f, 1.0f)
                }
                controller.volume = 1.0f
            }
            isFadingIn = false
        }
    }

    fun addExcludedFolder(path: String) {
        repository.addExcludedFolder(path)
        _excludedFolders.value = repository.getExcludedFolders()
        loadSongs()
    }

    fun removeExcludedFolder(path: String) {
        repository.removeExcludedFolder(path)
        _excludedFolders.value = repository.getExcludedFolders()
        loadSongs()
    }

    fun setIgnoreMessagingAudio(ignore: Boolean) {
        repository.setMessagingIgnored(ignore)
        _ignoreMessagingAudio.value = ignore
        loadSongs()
    }

    fun setMinDurationSeconds(sec: Int) {
        repository.setMinDurationSeconds(sec)
        _minDurationSeconds.value = sec
        loadSongs()
    }

    fun excludeFolderOfSong(song: Song) {
        val path = song.dataPath
        if (path.isNotBlank()) {
            val parent = java.io.File(path).parent
            if (!parent.isNullOrBlank()) {
                addExcludedFolder(parent)
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        "Carpeta excluida: $parent",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
            var counter = 0
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val dur = controller.duration.coerceAtLeast(0L)
                        _currentPosition.value = pos

                        // Crossfade fade-out al aproximarse el final de la canción
                        val cfSec = _crossfadeSeconds.value
                        if (cfSec > 0 && dur > cfSec * 2000L && !isFadingIn) {
                            val cfMs = cfSec * 1000L
                            val remainingMs = dur - pos
                            if (remainingMs in 1..cfMs) {
                                val factor = (remainingMs.toFloat() / cfMs).coerceIn(0.05f, 1.0f)
                                controller.volume = factor
                            } else if (pos > cfMs && controller.volume < 0.98f) {
                                controller.volume = 1.0f
                            }
                        }

                        counter++
                        // Acumular duración de reproducción en el gestor de historial/estadísticas cada 5s
                        if (counter >= 10) {
                            counter = 0
                            _currentSong.value?.let { s ->
                                historyManager.recordDurationPlayed(s, 5000L)
                            }
                        }
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
        networkSyncManager.stop()
        sleepTimerJob?.cancel()
        ftpStreamBridge.stop()
        equalizerManager.release()
        mediaController?.release()
        mediaController = null
    }
}
