package com.boumusic.player.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SongHistoryItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "Varios",
    val albumArtUriString: String? = null,
    val durationMs: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val totalPlayedMs: Long = 0L,
    val isFtp: Boolean = false,
    val ftpPath: String = "",
    val ftpServerHost: String = ""
) {
    val albumArtUri: Uri?
        get() = albumArtUriString?.let { Uri.parse(it) }

    fun formattedLastPlayed(): String {
        if (lastPlayedTimestamp <= 0) return "Nunca"
        val now = System.currentTimeMillis()
        val diffMs = now - lastPlayedTimestamp
        val diffMin = diffMs / 60000
        val diffHours = diffMin / 60
        val diffDays = diffHours / 24

        return when {
            diffMin < 1 -> "Ahora mismo"
            diffMin < 60 -> "Hace $diffMin min"
            diffHours < 24 -> "Hace $diffHours h"
            diffDays == 1L -> "Ayer"
            diffDays < 7 -> "Hace $diffDays días"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.format(Date(lastPlayedTimestamp))
            }
        }
    }

    fun formattedTotalTime(): String {
        val totalSec = totalPlayedMs / 1000
        val minutes = totalSec / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSec}s"
        }
    }

    fun toSong(localSongs: List<Song> = emptyList()): Song {
        // Si la canción existe en la lista local, usar su URI de contenido real
        val existing = localSongs.find { it.id == songId }
        if (existing != null) return existing

        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            duration = durationMs,
            contentUri = if (isFtp) Uri.EMPTY else (albumArtUri ?: Uri.EMPTY),
            albumArtUri = albumArtUri,
            dataPath = ftpPath,
            isFtp = isFtp,
            ftpPath = ftpPath,
            ftpServerHost = ftpServerHost,
            genre = genre
        )
    }
}

data class PlaybackRecord(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val timestamp: Long,
    val durationPlayedMs: Long,
    val year: Int,
    val quarter: Int // 1, 2, 3 o 4
)

enum class StatsPeriodType {
    LAST_3_MONTHS,
    CURRENT_QUARTER,
    PREVIOUS_QUARTER,
    ALL_TIME
}

data class QuarterStats(
    val periodLabel: String,
    val totalMinutes: Long,
    val totalPlays: Int,
    val topSong: SongHistoryItem?,
    val topSongs: List<Pair<SongHistoryItem, Int>>, // Canción y reproducciones
    val topArtist: String?,
    val topArtists: List<Pair<String, Long>>, // Artista y minutos
    val topGenre: String?,
    val topGenres: List<Pair<String, Int>>, // Género y reproducciones
    val favoriteTimeOfDay: String
)
