package com.boumusic.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.boumusic.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun getLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.GENRE
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID
            )
        }

        // Filtrar archivos que sean identificados como música y con al menos 5 segundos de duración
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = it.getString(titleCol) ?: "Pista desconocida"
                    val rawArtist = it.getString(artistCol) ?: "Artista desconocido"
                    val rawAlbum = it.getString(albumCol) ?: "Álbum desconocido"
                    val duration = it.getLong(durationCol)
                    val dataPath = it.getString(dataCol) ?: ""
                    val albumId = it.getLong(albumIdCol)
                    val rawGenre = if (genreCol != -1) it.getString(genreCol) else null
                    val genre = if (rawGenre.isNullOrBlank() || rawGenre == "<unknown>") "Varios" else rawGenre

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )

                    val artist = if (rawArtist == "<unknown>" || rawArtist.isBlank()) "Artista desconocido" else rawArtist
                    val album = if (rawAlbum == "<unknown>" || rawAlbum.isBlank()) "Álbum desconocido" else rawAlbum

                    songList.add(
                        Song(
                            id = id,
                            title = rawTitle.ifBlank { "Pista desconocida" },
                            artist = artist,
                            album = album,
                            duration = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            dataPath = dataPath,
                            genre = genre
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }
}
