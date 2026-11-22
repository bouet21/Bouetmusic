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
    val dataPath: String
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (duration / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }
}
