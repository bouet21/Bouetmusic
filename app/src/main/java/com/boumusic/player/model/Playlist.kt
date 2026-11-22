package com.boumusic.player.model

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val ftpSongs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
