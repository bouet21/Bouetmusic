package com.boumusic.player.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.Song
import java.io.File

object ShareHelper {

    /**
     * Comparte una canción directamente a través de Bluetooth o Quick Share.
     */
    fun shareSongViaBluetooth(context: Context, song: Song) {
        if (song.isFtp) {
            // Comprobar si el archivo FTP fue descargado a la carpeta pública Boumusic
            val downloadedFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Boumusic/${File(song.dataPath).name}"
            )
            if (downloadedFile.exists() && downloadedFile.length() > 0) {
                shareFile(context, downloadedFile, song.title)
                return
            }
            Toast.makeText(
                context,
                "Esta canción está en el servidor FTP remoto. Descárgala primero o compártela mediante Código QR.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uri = getShareableUri(context, song)
        if (uri == null) {
            Toast.makeText(context, "No se pudo obtener el archivo de audio para compartir", Toast.LENGTH_SHORT).show()
            return
        }

        launchShareIntent(
            context = context,
            uris = arrayListOf(uri),
            title = "Enviar \"${song.title}\" por Bluetooth o Quick Share",
            chooserTitle = "Compartir por Bluetooth / Quick Share"
        )
    }

    /**
     * Comparte todas las canciones de una lista mediante Bluetooth o Quick Share.
     */
    fun sharePlaylistViaBluetooth(context: Context, playlist: Playlist, songs: List<Song>) {
        if (songs.isEmpty()) {
            Toast.makeText(context, "La lista está vacía", Toast.LENGTH_SHORT).show()
            return
        }

        val shareableUris = ArrayList<Uri>()
        var skippedFtpCount = 0

        for (song in songs) {
            if (song.isFtp) {
                val downloadedFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Boumusic/${File(song.dataPath).name}"
                )
                if (downloadedFile.exists() && downloadedFile.length() > 0) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            downloadedFile
                        )
                        shareableUris.add(uri)
                    } catch (e: Exception) {
                        skippedFtpCount++
                    }
                } else {
                    skippedFtpCount++
                }
            } else {
                val uri = getShareableUri(context, song)
                if (uri != null) {
                    shareableUris.add(uri)
                }
            }
        }

        if (shareableUris.isEmpty()) {
            Toast.makeText(
                context,
                "No hay canciones descargadas localmente en esta lista para enviar por Bluetooth.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (skippedFtpCount > 0) {
            Toast.makeText(
                context,
                "Se enviarán ${shareableUris.size} canciones (${skippedFtpCount} remotas de FTP no descargadas omitidas).",
                Toast.LENGTH_SHORT
            ).show()
        }

        launchShareIntent(
            context = context,
            uris = shareableUris,
            title = "Enviar lista \"${playlist.name}\" (${shareableUris.size} canciones)",
            chooserTitle = "Compartir lista por Bluetooth / Quick Share"
        )
    }

    private fun shareFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            launchShareIntent(
                context = context,
                uris = arrayListOf(uri),
                title = "Enviar \"$title\" por Bluetooth o Quick Share",
                chooserTitle = "Compartir por Bluetooth / Quick Share"
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Error al preparar archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getShareableUri(context: Context, song: Song): Uri? {
        if (song.dataPath.isNotBlank()) {
            val file = File(song.dataPath)
            if (file.exists() && file.canRead()) {
                try {
                    return FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    // Fall back to contentUri
                }
            }
        }
        return song.contentUri
    }

    private fun launchShareIntent(
        context: Context,
        uris: ArrayList<Uri>,
        title: String,
        chooserTitle: String
    ) {
        val isSingle = uris.size == 1
        val intent = if (isSingle) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No se encontró aplicación para compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
