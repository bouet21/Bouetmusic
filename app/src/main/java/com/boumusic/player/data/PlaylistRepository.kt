package com.boumusic.player.data

import android.content.Context
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
                list.add(Playlist(id, name, songIds, createdAt))
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
                jsonArray.put(obj)
            }
            playlistFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val current = getPlaylists().toMutableList()
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Nueva lista" },
            songIds = emptyList()
        )
        current.add(newPlaylist)
        savePlaylists(current)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        val current = getPlaylists().filter { it.id != playlistId }
        savePlaylists(current)
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
            val updatedList = target.songIds.filter { it != songId }
            current[idx] = target.copy(songIds = updatedList)
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
            val s = allSongs.find { it.id == id } ?: continue
            val secs = (s.duration / 1000).coerceAtLeast(0)
            sb.append("#EXTINF:$secs,${s.artist} - ${s.title}\n")
            sb.append("${s.dataPath}\n")
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
                songIds = matchedIds
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
