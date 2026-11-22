package com.boumusic.player.data

import android.content.Context
import com.boumusic.player.model.Playlist
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
}
