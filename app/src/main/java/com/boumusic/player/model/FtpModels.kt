package com.boumusic.player.model

import java.util.UUID

data class FtpConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Servidor FTP",
    val host: String = "",
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = ""
)

data class FtpItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Carpeta"
            val mb = size / (1024.0 * 1024.0)
            return String.format("%.2f MB", mb)
        }

    val isAudio: Boolean
        get() = !isDirectory && name.matches(Regex("(?i).*\\.(mp3|flac|m4a|wav|ogg|aac|opus)$"))
}
