package com.boumusic.player.model

enum class ShareType {
    SONG,
    PLAYLIST
}

data class ShareTarget(
    val type: ShareType,
    val song: Song? = null,
    val playlist: Playlist? = null
)
