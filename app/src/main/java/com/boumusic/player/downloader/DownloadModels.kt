package com.boumusic.player.downloader

import com.boumusic.player.model.FtpConfig
import java.io.File

object AudioMetadataHelper {
    fun parseFileNameMetadata(rawName: String): Pair<String, String> {
        val clean = rawName.substringBeforeLast('.').trim()
        val separators = listOf(" - ", " – ", " — ", " _ ")
        for (sep in separators) {
            if (clean.contains(sep)) {
                val p1 = clean.substringBefore(sep).trim()
                val p2 = clean.substringAfter(sep).trim()
                if (p1.isNotBlank() && p2.isNotBlank()) {
                    return Pair(p1, p2)
                }
            }
        }
        return Pair("Desconocido", clean)
    }
}

data class DownloadTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val spotifyUrl: String = ""
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val displaySubtitle: String
        get() = if (album.isNotBlank()) "$artist • $album" else artist
}

enum class DestinationType(val label: String) {
    LOCAL("Almacenamiento del Teléfono"),
    SMB("Servidor SMB (Carpeta de red)"),
    FTP("Servidor FTP / FTPS"),
    WEBDAV("Nube WebDAV")
}

data class DownloadDestination(
    val type: DestinationType,
    val label: String,
    val localFolder: File? = null,
    val serverConfig: FtpConfig? = null,
    val remoteSubPath: String = ""
)

enum class DownloadStage(val label: String) {
    QUEUED("En cola"),
    SEARCHING_STREAM("Localizando audio de alta calidad..."),
    DOWNLOADING("Descargando stream de audio..."),
    CONVERTING("Procesando metadatos..."),
    UPLOADING("Subiendo a servidor remoto..."),
    COMPLETED("Descarga completada"),
    ERROR("Error en la descarga")
}

data class DownloadTask(
    val id: String,
    val track: DownloadTrackItem,
    val destination: DownloadDestination,
    val stage: DownloadStage = DownloadStage.QUEUED,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val error: String? = null,
    val outputFile: File? = null
)
