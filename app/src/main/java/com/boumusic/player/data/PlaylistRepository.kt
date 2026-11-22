package com.boumusic.player.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PlaylistRepository(private val context: Context) {

    private val playlistFile: File
        get() = File(context.filesDir, "playlists.json")

    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Playlist>()
        if (!playlistFile.exists()) return@withContext list

        try {
            val jsonStr = playlistFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Lista sin nombre")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val songIdsArray = obj.optJSONArray("songIds")
                val songIds = mutableListOf<Long>()
                if (songIdsArray != null) {
                    for (j in 0 until songIdsArray.length()) {
                        songIds.add(songIdsArray.getLong(j))
                    }
                }

                val ftpSongsArray = obj.optJSONArray("ftpSongs")
                val ftpSongs = mutableListOf<Song>()
                if (ftpSongsArray != null) {
                    for (k in 0 until ftpSongsArray.length()) {
                        val fObj = ftpSongsArray.getJSONObject(k)
                        val sId = fObj.getLong("id")
                        val sTitle = fObj.optString("title", "Pista FTP")
                        val sArtist = fObj.optString("artist", "Servidor FTP")
                        val sAlbum = fObj.optString("album", "")
                        val sPath = fObj.optString("ftpPath", "")
                        val sHost = fObj.optString("ftpServerHost", "")
                        val sUri = fObj.optString("contentUri", "ftp://$sHost$sPath")

                        ftpSongs.add(
                            Song(
                                id = sId,
                                title = sTitle,
                                artist = sArtist,
                                album = sAlbum,
                                duration = 0L,
                                contentUri = Uri.parse(sUri),
                                albumArtUri = null,
                                dataPath = sPath,
                                isFtp = true,
                                ftpPath = sPath,
                                ftpServerHost = sHost
                            )
                        )
                    }
                }

                list.add(Playlist(id, name, songIds, ftpSongs, createdAt))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for (p in playlists) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("createdAt", p.createdAt)

                val songIdsArray = JSONArray()
                p.songIds.forEach { songIdsArray.put(it) }
                obj.put("songIds", songIdsArray)

                if (p.ftpSongs.isNotEmpty()) {
                    val ftpArray = JSONArray()
                    for (fs in p.ftpSongs) {
                        val fObj = JSONObject()
                        fObj.put("id", fs.id)
                        fObj.put("title", fs.title)
                        fObj.put("artist", fs.artist)
                        fObj.put("album", fs.album)
                        fObj.put("ftpPath", fs.ftpPath)
                        fObj.put("ftpServerHost", fs.ftpServerHost)
                        fObj.put("contentUri", fs.contentUri.toString())
                        ftpArray.put(fObj)
                    }
                    obj.put("ftpSongs", ftpArray)
                }

                jsonArray.put(obj)
            }
            playlistFile.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val current = getPlaylists().toMutableList()
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Nueva lista" },
            songIds = emptyList(),
            ftpSongs = emptyList()
        )
        current.add(newPlaylist)
        savePlaylists(current)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        val current = getPlaylists().filter { it.id != playlistId }
        savePlaylists(current)
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        val current = getPlaylists().toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx != -1) {
            val target = current[idx]
            if (!target.songIds.contains(song.id)) {
                val updatedSongIds = target.songIds + song.id
                val updatedFtpSongs = if (song.isFtp) {
                    target.ftpSongs.filter { it.id != song.id } + song
                } else {
                    target.ftpSongs
                }
                current[idx] = target.copy(songIds = updatedSongIds, ftpSongs = updatedFtpSongs)
                savePlaylists(current)
            }
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: Long) {
        val current = getPlaylists().toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx != -1) {
            val target = current[idx]
            if (!target.songIds.contains(songId)) {
                val updatedList = target.songIds + songId
                current[idx] = target.copy(songIds = updatedList)
                savePlaylists(current)
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val current = getPlaylists().toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx != -1) {
            val target = current[idx]
            val updatedSongIds = target.songIds.filter { it != songId }
            val updatedFtpSongs = target.ftpSongs.filter { it.id != songId }
            current[idx] = target.copy(songIds = updatedSongIds, ftpSongs = updatedFtpSongs)
            savePlaylists(current)
        }
    }

    // Exportar lista de reproducción a formato estándar .M3U
    suspend fun exportToM3u(playlist: Playlist, allSongs: List<Song>): File = withContext(Dispatchers.IO) {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val exportDir = File(musicDir, "Boumusic/Playlists").apply { if (!exists()) mkdirs() }
        val cleanName = playlist.name.replace(Regex("[^a-zA-Z0-9_ -]"), "_")
        val file = File(exportDir, "$cleanName.m3u")

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:${playlist.name}\n")

        for (id in playlist.songIds) {
            val s = allSongs.find { it.id == id } ?: playlist.ftpSongs.find { it.id == id } ?: continue
            val secs = (s.duration / 1000).coerceAtLeast(0)
            sb.append("#EXTINF:$secs,${s.artist} - ${s.title}\n")
            if (s.isFtp) {
                sb.append("ftp://${s.ftpServerHost}${s.ftpPath}\n")
            } else {
                sb.append("${s.dataPath}\n")
            }
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        file
    }

    // Importar lista .M3U
    suspend fun importFromM3u(file: File, allSongs: List<Song>): Playlist? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val playlistName = file.nameWithoutExtension
            val matchedIds = mutableListOf<Long>()

            file.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    val song = allSongs.find {
                        it.dataPath.equals(line, ignoreCase = true) ||
                        it.title.equals(File(line).nameWithoutExtension, ignoreCase = true)
                    }
                    if (song != null && !matchedIds.contains(song.id)) {
                        matchedIds.add(song.id)
                    }
                }
            }

            val newPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                name = playlistName,
                songIds = matchedIds,
                ftpSongs = emptyList()
            )
            val current = getPlaylists().toMutableList()
            current.add(newPlaylist)
            savePlaylists(current)
            newPlaylist
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
