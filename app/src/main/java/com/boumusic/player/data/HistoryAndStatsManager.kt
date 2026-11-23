package com.boumusic.player.data

import android.content.Context
import com.boumusic.player.model.PlaybackRecord
import com.boumusic.player.model.QuarterStats
import com.boumusic.player.model.Song
import com.boumusic.player.model.SongHistoryItem
import com.boumusic.player.model.StatsPeriodType
import com.boumusic.player.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class HistoryAndStatsManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val historyFile = File(context.filesDir, "playback_history_stats.json")

    private val historyMap = ConcurrentHashMap<Long, SongHistoryItem>()
    private val recordsList = Collections.synchronizedList(mutableListOf<PlaybackRecord>())

    private val _historyFlow = MutableStateFlow<List<SongHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<SongHistoryItem>> = _historyFlow.asStateFlow()

    private val _topSongsFlow = MutableStateFlow<List<SongHistoryItem>>(emptyList())
    val topSongsFlow: StateFlow<List<SongHistoryItem>> = _topSongsFlow.asStateFlow()

    private var saveJob: Job? = null

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        scope.launch {
            if (!historyFile.exists()) return@launch
            try {
                val jsonString = historyFile.readText()
                if (jsonString.isBlank()) return@launch
                val root = JSONObject(jsonString)

                // 1. Cargar catálogo de canciones e historial
                val historyArray = root.optJSONArray("history") ?: JSONArray()
                val loadedMap = mutableMapOf<Long, SongHistoryItem>()
                for (i in 0 until historyArray.length()) {
                    val obj = historyArray.getJSONObject(i)
                    val item = SongHistoryItem(
                        songId = obj.getLong("songId"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        genre = obj.optString("genre", "Varios"),
                        albumArtUriString = if (obj.has("albumArtUriString") && !obj.isNull("albumArtUriString")) {
                            obj.getString("albumArtUriString").takeIf { it != "null" && it.isNotBlank() }
                        } else null,
                        durationMs = obj.optLong("durationMs", 0L),
                        playCount = obj.optInt("playCount", 0),
                        lastPlayedTimestamp = obj.optLong("lastPlayedTimestamp", 0L),
                        totalPlayedMs = obj.optLong("totalPlayedMs", 0L),
                        isFtp = obj.optBoolean("isFtp", false),
                        ftpPath = obj.optString("ftpPath", ""),
                        ftpServerHost = obj.optString("ftpServerHost", "")
                    )
                    loadedMap[item.songId] = item
                }
                historyMap.clear()
                historyMap.putAll(loadedMap)

                // 2. Cargar eventos de reproducción para estadísticas
                val recordsArray = root.optJSONArray("records") ?: JSONArray()
                val loadedRecords = mutableListOf<PlaybackRecord>()
                for (i in 0 until recordsArray.length()) {
                    val obj = recordsArray.getJSONObject(i)
                    val record = PlaybackRecord(
                        songId = obj.getLong("songId"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        genre = obj.optString("genre", "Varios"),
                        timestamp = obj.getLong("timestamp"),
                        durationPlayedMs = obj.optLong("durationPlayedMs", 0L),
                        year = obj.optInt("year", 2026),
                        quarter = obj.optInt("quarter", 4)
                    )
                    loadedRecords.add(record)
                }
                synchronized(recordsList) {
                    recordsList.clear()
                    recordsList.addAll(loadedRecords)
                }

                updateFlows()
                checkQuarterlyNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000) // Debounce de 1 segundo para no sobrecargar el disco
            saveToDiskInternal()
        }
    }

    private suspend fun saveToDiskInternal() = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()

            // 1. Serializar Historial
            val historyArray = JSONArray()
            val items = historyMap.values.toList()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("songId", item.songId)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("genre", item.genre)
                    put("albumArtUriString", item.albumArtUriString)
                    put("durationMs", item.durationMs)
                    put("playCount", item.playCount)
                    put("lastPlayedTimestamp", item.lastPlayedTimestamp)
                    put("totalPlayedMs", item.totalPlayedMs)
                    put("isFtp", item.isFtp)
                    put("ftpPath", item.ftpPath)
                    put("ftpServerHost", item.ftpServerHost)
                }
                historyArray.put(obj)
            }
            root.put("history", historyArray)

            // 2. Serializar Registros de reproducción (máximo los últimos 5.000 para mantener ligereza)
            val recordsArray = JSONArray()
            val records = synchronized(recordsList) {
                if (recordsList.size > 5000) {
                    recordsList.subList(recordsList.size - 5000, recordsList.size).toList()
                } else {
                    recordsList.toList()
                }
            }
            for (rec in records) {
                val obj = JSONObject().apply {
                    put("songId", rec.songId)
                    put("title", rec.title)
                    put("artist", rec.artist)
                    put("album", rec.album)
                    put("genre", rec.genre)
                    put("timestamp", rec.timestamp)
                    put("durationPlayedMs", rec.durationPlayedMs)
                    put("year", rec.year)
                    put("quarter", rec.quarter)
                }
                recordsArray.put(obj)
            }
            root.put("records", recordsArray)
            historyFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun recordSongPlayStart(song: Song) {
        val now = System.currentTimeMillis()
        val existing = historyMap[song.id]
        val updated = if (existing != null) {
            existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedTimestamp = now,
                albumArtUriString = song.albumArtUri?.toString() ?: existing.albumArtUriString,
                genre = if (song.genre.isNotBlank() && song.genre != "Varios") song.genre else existing.genre,
                durationMs = if (song.duration > 0) song.duration else existing.durationMs
            )
        } else {
            SongHistoryItem(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                genre = song.genre,
                albumArtUriString = song.albumArtUri?.toString(),
                durationMs = song.duration,
                playCount = 1,
                lastPlayedTimestamp = now,
                totalPlayedMs = 0L,
                isFtp = song.isFtp,
                ftpPath = song.ftpPath,
                ftpServerHost = song.ftpServerHost
            )
        }
        historyMap[song.id] = updated
        updateFlows()
        scheduleSave()
    }

    fun recordDurationPlayed(song: Song, playedMs: Long) {
        if (playedMs <= 0) return
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0..11
        val quarter = (month / 3) + 1 // 1..4

        // 1. Actualizar tiempo acumulado de la canción
        val existing = historyMap[song.id]
        if (existing != null) {
            historyMap[song.id] = existing.copy(
                totalPlayedMs = existing.totalPlayedMs + playedMs,
                lastPlayedTimestamp = now
            )
        }

        // 2. Añadir registro para estadísticas periódicas
        val record = PlaybackRecord(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre.ifBlank { "Varios" },
            timestamp = now,
            durationPlayedMs = playedMs,
            year = year,
            quarter = quarter
        )
        synchronized(recordsList) {
            recordsList.add(record)
            if (recordsList.size > 5000) {
                recordsList.removeAt(0)
            }
        }

        updateFlows()
        scheduleSave()
    }

    private fun updateFlows() {
        val list = historyMap.values.toList()
        _historyFlow.value = list.sortedByDescending { it.lastPlayedTimestamp }
        _topSongsFlow.value = list.sortedByDescending { it.playCount }
    }

    fun calculateStats(periodType: StatsPeriodType, selectedQuarter: String = ""): QuarterStats {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentQuarter = (currentMonth / 3) + 1

        val filteredRecords = synchronized(recordsList) {
            when (periodType) {
                StatsPeriodType.LAST_3_MONTHS -> {
                    val cutoff = now - (90L * 24 * 60 * 60 * 1000)
                    recordsList.filter { it.timestamp >= cutoff }
                }
                StatsPeriodType.CURRENT_QUARTER -> {
                    recordsList.filter { it.year == currentYear && it.quarter == currentQuarter }
                }
                StatsPeriodType.PREVIOUS_QUARTER -> {
                    val prevQuarter = if (currentQuarter == 1) 4 else currentQuarter - 1
                    val prevYear = if (currentQuarter == 1) currentYear - 1 else currentYear
                    recordsList.filter { it.year == prevYear && it.quarter == prevQuarter }
                }
                StatsPeriodType.ALL_TIME -> {
                    recordsList.toList()
                }
            }
        }

        val label = when (periodType) {
            StatsPeriodType.LAST_3_MONTHS -> "Últimos 3 Meses"
            StatsPeriodType.CURRENT_QUARTER -> "Trimestre Actual (T$currentQuarter $currentYear)"
            StatsPeriodType.PREVIOUS_QUARTER -> {
                val prevQuarter = if (currentQuarter == 1) 4 else currentQuarter - 1
                val prevYear = if (currentQuarter == 1) currentYear - 1 else currentYear
                "Trimestre Anterior (T$prevQuarter $prevYear)"
            }
            StatsPeriodType.ALL_TIME -> "Histórico Completo"
        }

        val totalPlayedMs = filteredRecords.sumOf { it.durationPlayedMs }
        val totalMinutes = totalPlayedMs / 60000

        // Canciones más escuchadas en este período
        val songPlayCounts = filteredRecords.groupBy { it.songId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val topSongsList = songPlayCounts.take(5).mapNotNull { (songId, count) ->
            val historyItem = historyMap[songId] ?: filteredRecords.find { it.songId == songId }?.let { r ->
                SongHistoryItem(
                    songId = r.songId,
                    title = r.title,
                    artist = r.artist,
                    album = r.album,
                    genre = r.genre,
                    playCount = count
                )
            }
            if (historyItem != null) Pair(historyItem, count) else null
        }

        val topSong = topSongsList.firstOrNull()?.first

        // Artistas más escuchados en este período (por minutos escuchados)
        val artistDurations = filteredRecords.groupBy { it.artist }
            .mapValues { entry -> entry.value.sumOf { it.durationPlayedMs } / 60000 }
            .toList()
            .sortedByDescending { it.second }

        val topArtistsList = artistDurations.take(5)
        val topArtist = topArtistsList.firstOrNull()?.first

        // Géneros dominantes
        val genreCounts = filteredRecords.groupBy { it.genre.ifBlank { "Varios" } }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val topGenre = genreCounts.firstOrNull()?.first

        // Momento del día favorito
        val timeOfDayCount = mutableMapOf(
            "Mañanas (06:00 - 12:00) 🌅" to 0,
            "Tardes (12:00 - 18:00) 🌇" to 0,
            "Noches (18:00 - 24:00) 🌙" to 0,
            "Madrugadas (00:00 - 06:00) 🌌" to 0
        )

        val cal = Calendar.getInstance()
        for (rec in filteredRecords) {
            cal.timeInMillis = rec.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..11 -> timeOfDayCount["Mañanas (06:00 - 12:00) 🌅"] = (timeOfDayCount["Mañanas (06:00 - 12:00) 🌅"] ?: 0) + 1
                in 12..17 -> timeOfDayCount["Tardes (12:00 - 18:00) 🌇"] = (timeOfDayCount["Tardes (12:00 - 18:00) 🌇"] ?: 0) + 1
                in 18..23 -> timeOfDayCount["Noches (18:00 - 24:00) 🌙"] = (timeOfDayCount["Noches (18:00 - 24:00) 🌙"] ?: 0) + 1
                else -> timeOfDayCount["Madrugadas (00:00 - 06:00) 🌌"] = (timeOfDayCount["Madrugadas (00:00 - 06:00) 🌌"] ?: 0) + 1
            }
        }
        val favoriteTime = timeOfDayCount.maxByOrNull { it.value }?.key ?: "Tardes (12:00 - 18:00) 🌇"

        return QuarterStats(
            periodLabel = label,
            totalMinutes = totalMinutes,
            totalPlays = filteredRecords.size,
            topSong = topSong,
            topSongs = topSongsList,
            topArtist = topArtist,
            topArtists = topArtistsList,
            topGenre = topGenre,
            topGenres = genreCounts.take(5),
            favoriteTimeOfDay = favoriteTime
        )
    }

    fun clearHistory() {
        historyMap.clear()
        synchronized(recordsList) {
            recordsList.clear()
        }
        if (historyFile.exists()) {
            historyFile.delete()
        }
        updateFlows()
    }

    fun exportSyncData(): JSONObject {
        val root = JSONObject()
        val historyArray = JSONArray()
        for (item in historyMap.values) {
            val obj = JSONObject().apply {
                put("songId", item.songId)
                put("title", item.title)
                put("artist", item.artist)
                put("album", item.album)
                put("genre", item.genre)
                put("playCount", item.playCount)
                put("lastPlayedTimestamp", item.lastPlayedTimestamp)
                put("totalPlayedMs", item.totalPlayedMs)
            }
            historyArray.put(obj)
        }
        root.put("history", historyArray)

        val recordsArray = JSONArray()
        val records = synchronized(recordsList) { recordsList.toList() }
        for (rec in records) {
            val obj = JSONObject().apply {
                put("songId", rec.songId)
                put("title", rec.title)
                put("artist", rec.artist)
                put("album", rec.album)
                put("genre", rec.genre)
                put("timestamp", rec.timestamp)
                put("durationPlayedMs", rec.durationPlayedMs)
                put("year", rec.year)
                put("quarter", rec.quarter)
            }
            recordsArray.put(obj)
        }
        root.put("records", recordsArray)
        return root
    }

    fun mergeSyncData(syncJson: JSONObject) {
        val recordsArr = syncJson.optJSONArray("records") ?: return
        val existingTimestamps = synchronized(recordsList) { recordsList.map { it.timestamp }.toSet() }
        val newRecords = mutableListOf<PlaybackRecord>()

        for (i in 0 until recordsArr.length()) {
            val obj = recordsArr.getJSONObject(i)
            val ts = obj.getLong("timestamp")
            if (!existingTimestamps.contains(ts)) {
                newRecords.add(
                    PlaybackRecord(
                        songId = obj.optLong("songId", 0L),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        genre = obj.optString("genre", "Varios"),
                        timestamp = ts,
                        durationPlayedMs = obj.optLong("durationPlayedMs", 0L),
                        year = obj.optInt("year", 2026),
                        quarter = obj.optInt("quarter", 4)
                    )
                )
            }
        }

        if (newRecords.isNotEmpty()) {
            synchronized(recordsList) {
                recordsList.addAll(newRecords)
                recordsList.sortBy { it.timestamp }
            }
            updateFlows()
            scheduleSave()
        }
    }

    private fun checkQuarterlyNotification() {
        scope.launch {
            val prefs = context.getSharedPreferences("boumusic_stats_prefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)
            val currentQuarter = (currentMonth / 3) + 1

            // Trimestre anterior
            val prevQuarter = if (currentQuarter == 1) 4 else currentQuarter - 1
            val prevYear = if (currentQuarter == 1) currentYear - 1 else currentYear
            val prevQuarterKey = "${prevYear}_Q$prevQuarter"

            val lastNotified = prefs.getString("last_notified_quarter", "")
            if (lastNotified != prevQuarterKey) {
                // Verificar si hay actividad suficiente en el trimestre anterior para notificar
                val prevRecords = synchronized(recordsList) {
                    recordsList.filter { it.year == prevYear && it.quarter == prevQuarter }
                }
                if (prevRecords.size >= 5) {
                    val prevStats = calculateStats(StatsPeriodType.PREVIOUS_QUARTER)
                    NotificationHelper.notifyQuarterlyStatsReady(
                        context = context,
                        quarterLabel = "Trimestre T$prevQuarter $prevYear",
                        totalMinutes = prevStats.totalMinutes,
                        topSongTitle = prevStats.topSong?.title ?: "Tus canciones favoritas",
                        topArtist = prevStats.topArtist ?: "Tus artistas"
                    )
                    prefs.edit().putString("last_notified_quarter", prevQuarterKey).apply()
                }
            }
        }
    }
}
