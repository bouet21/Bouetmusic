package com.boumusic.player.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val dataPath: String,
    val isFtp: Boolean = false,
    val ftpPath: String = "",
    val ftpServerHost: String = "",
    val genre: String = "Varios"
) {
    val formattedDuration: String
        get() {
            if (duration <= 0) return if (isFtp) "--:--" else "00:00"
            val totalSeconds = (duration / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }
}

enum class SongSortOrder(val displayName: String) {
    DEFAULT("Por defecto"),
    TITLE_ASC("Título (A-Z)"),
    ARTIST_ASC("Artista (A-Z)"),
    DURATION_DESC("Duración"),
    RECENT("Más recientes")
}
