package com.boumusic.player.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupData(
    val servers: List<FtpConfig>,
    val playlists: List<Playlist>,
    val settings: Map<String, Any>,
    val statsJson: JSONObject? = null
)

object BackupManager {

    fun generateBackupJson(
        servers: List<FtpConfig>,
        playlists: List<Playlist>,
        settings: Map<String, Any>,
        statsJson: JSONObject? = null
    ): String {
        val root = JSONObject()
        root.put("app", "Boumusic")
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Servidores
        val serversArr = JSONArray()
        for (c in servers) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("host", c.host)
            o.put("port", c.port)
            o.put("username", c.username)
            o.put("password", c.password)
            o.put("isFtps", c.isFtps)
            o.put("isWebDav", c.isWebDav)
            o.put("webDavPath", c.webDavPath)
            o.put("isSmb", c.isSmb)
            o.put("smbShare", c.smbShare)
            serversArr.put(o)
        }
        root.put("servers", serversArr)

        // Listas de reproducción
        val playlistsArr = JSONArray()
        for (p in playlists) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("createdAt", p.createdAt)

            val songIdsArr = JSONArray()
            p.songIds.forEach { songIdsArr.put(it) }
            pObj.put("songIds", songIdsArr)

            val ftpArr = JSONArray()
            for (f in p.ftpSongs) {
                val fObj = JSONObject()
                fObj.put("id", f.id)
                fObj.put("title", f.title)
                fObj.put("artist", f.artist)
                fObj.put("album", f.album)
                fObj.put("ftpPath", f.ftpPath)
                fObj.put("ftpServerHost", f.ftpServerHost)
                fObj.put("contentUri", f.contentUri.toString())
                ftpArr.put(fObj)
            }
            pObj.put("ftpSongs", ftpArr)

            playlistsArr.put(pObj)
        }
        root.put("playlists", playlistsArr)

        // Ajustes
        val settingsObj = JSONObject()
        for ((k, v) in settings) {
            settingsObj.put(k, v)
        }
        root.put("settings", settingsObj)

        if (statsJson != null) {
            root.put("stats", statsJson)
        }

        return root.toString(2)
    }

    suspend fun saveBackupToFile(context: Context, json: String): File = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(downloadDir, "Boumusic_Backup_$dateStr.json")
        file.writeText(json, Charsets.UTF_8)

        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("application/json"), null)
        file
    }

    fun parseBackupJson(jsonStr: String): BackupData {
        val root = JSONObject(jsonStr)

        val servers = mutableListOf<FtpConfig>()
        val serversArr = root.optJSONArray("servers")
        if (serversArr != null) {
            for (i in 0 until serversArr.length()) {
                val o = serversArr.getJSONObject(i)
                servers.add(
                    FtpConfig(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Servidor"),
                        host = o.optString("host", ""),
                        port = o.optInt("port", 21),
                        username = o.optString("username", "anonymous"),
                        password = o.optString("password", ""),
                        isFtps = o.optBoolean("isFtps", false),
                        isWebDav = o.optBoolean("isWebDav", false),
                        webDavPath = o.optString("webDavPath", "/"),
                        isSmb = o.optBoolean("isSmb", false),
                        smbShare = o.optString("smbShare", "")
                    )
                )
            }
        }

        val playlists = mutableListOf<Playlist>()
        val playlistsArr = root.optJSONArray("playlists")
        if (playlistsArr != null) {
            for (i in 0 until playlistsArr.length()) {
                val pObj = playlistsArr.getJSONObject(i)
                val id = pObj.optString("id", UUID.randomUUID().toString())
                val name = pObj.optString("name", "Lista")
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                val songIds = mutableListOf<Long>()
                val sArr = pObj.optJSONArray("songIds")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        songIds.add(sArr.getLong(j))
                    }
                }

                val ftpSongs = mutableListOf<Song>()
                val fArr = pObj.optJSONArray("ftpSongs")
                if (fArr != null) {
                    for (k in 0 until fArr.length()) {
                        val fObj = fArr.getJSONObject(k)
                        ftpSongs.add(
                            Song(
                                id = fObj.optLong("id", System.currentTimeMillis()),
                                title = fObj.optString("title", "Canción"),
                                artist = fObj.optString("artist", "Remoto"),
                                album = fObj.optString("album", ""),
                                duration = 0L,
                                contentUri = Uri.parse(fObj.optString("contentUri", "")),
                                albumArtUri = null,
                                dataPath = fObj.optString("ftpPath", ""),
                                isFtp = true,
                                ftpPath = fObj.optString("ftpPath", ""),
                                ftpServerHost = fObj.optString("ftpServerHost", "")
                            )
                        )
                    }
                }

                playlists.add(Playlist(id, name, songIds, ftpSongs, createdAt))
            }
        }

        val settings = mutableMapOf<String, Any>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                settings[key] = settingsObj.get(key)
            }
        }

        val statsObj = root.optJSONObject("stats")
        return BackupData(servers, playlists, settings, statsObj)
    }

    fun exportPlaylistToM3u8(playlist: Playlist, allSongs: List<Song>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:${playlist.name}")

        val songMap = allSongs.associateBy { it.id }
        for (id in playlist.songIds) {
            val song = songMap[id] ?: continue
            val durationSec = (song.duration / 1000).coerceAtLeast(0)
            sb.appendLine("#EXTINF:$durationSec,${song.artist} - ${song.title}")
            val path = if (song.dataPath.isNotBlank()) song.dataPath else song.contentUri.toString()
            sb.appendLine(path)
        }

        for (fSong in playlist.ftpSongs) {
            sb.appendLine("#EXTINF:0,${fSong.artist} - ${fSong.title}")
            sb.appendLine(fSong.contentUri.toString())
        }

        return sb.toString()
    }

    suspend fun savePlaylistM3u8ToFile(context: Context, playlist: Playlist, allSongs: List<Song>): File = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val safeName = playlist.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Playlist" }
        val file = File(downloadDir, "$safeName.m3u8")
        file.writeText(exportPlaylistToM3u8(playlist, allSongs), Charsets.UTF_8)

        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("audio/x-mpegurl"), null)
        file
    }

    fun parseM3u8(m3u8Content: String, fallbackName: String, allSongs: List<Song>): Playlist {
        var playlistName = fallbackName
        val lines = m3u8Content.lines()
        val matchedIds = mutableListOf<Long>()
        val ftpSongs = mutableListOf<Song>()

        val songsByFilename = allSongs.associateBy { File(it.dataPath).name.lowercase() }
        val songsByTitle = allSongs.associateBy { it.title.trim().lowercase() }

        var nextExtInfTitle: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#PLAYLIST:")) {
                playlistName = trimmed.removePrefix("#PLAYLIST:").trim().ifBlank { fallbackName }
            } else if (trimmed.startsWith("#EXTINF:")) {
                val afterComma = trimmed.substringAfter(",", "")
                if (afterComma.isNotBlank()) {
                    nextExtInfTitle = afterComma.trim()
                }
            } else if (!trimmed.startsWith("#")) {
                // Es una ruta o URL
                if (trimmed.startsWith("ftp://") || trimmed.startsWith("http://") || trimmed.startsWith("smb://")) {
                    val uri = Uri.parse(trimmed)
                    val filename = uri.lastPathSegment ?: "Canción Remota"
                    val host = uri.host ?: ""
                    ftpSongs.add(
                        Song(
                            id = System.currentTimeMillis() + ftpSongs.size,
                            title = nextExtInfTitle?.substringAfter(" - ") ?: filename,
                            artist = nextExtInfTitle?.substringBefore(" - ") ?: host,
                            album = "",
                            duration = 0L,
                            contentUri = uri,
                            albumArtUri = null,
                            dataPath = uri.path ?: "",
                            isFtp = true,
                            ftpPath = uri.path ?: "",
                            ftpServerHost = host
                        )
                    )
                } else {
                    val file = File(trimmed)
                    val filename = file.name.lowercase()
                    val match = songsByFilename[filename]
                        ?: nextExtInfTitle?.let { titleHint ->
                            val t = titleHint.substringAfter(" - ").lowercase()
                            songsByTitle[t]
                        }
                    if (match != null) {
                        matchedIds.add(match.id)
                    }
                }
                nextExtInfTitle = null
            }
        }

        return Playlist(
            id = UUID.randomUUID().toString(),
            name = playlistName,
            songIds = matchedIds,
            ftpSongs = ftpSongs,
            createdAt = System.currentTimeMillis()
        )
    }
}
