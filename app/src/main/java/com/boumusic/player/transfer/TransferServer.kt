package com.boumusic.player.transfer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.boumusic.player.model.Playlist
import com.boumusic.player.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TransferServer(
    private val context: Context,
    val port: Int = 8080,
    var songProvider: () -> List<Song> = { emptyList() },
    var playlistProvider: () -> List<Playlist> = { emptyList() },
    private val onFileReceived: (filename: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var watchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var pin: String? = null
    var autoStopMinutes: Int = 15
    private var lastActivityTime: Long = System.currentTimeMillis()
    var onAutoStopped: (() -> Unit)? = null

    var isRunning: Boolean = false
        private set

    fun start(onSuccess: (url: String) -> Unit, onError: (Exception) -> Unit) {
        if (isRunning) return

        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            lastActivityTime = System.currentTimeMillis()

            val ip = NetworkUtils.getLocalIpAddress(context) ?: "127.0.0.1"
            val serverUrl = "http://$ip:$port"

            serverJob = scope.launch {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }

            watchdogJob = scope.launch {
                while (isRunning) {
                    kotlinx.coroutines.delay(30000)
                    if (autoStopMinutes > 0 && (System.currentTimeMillis() - lastActivityTime) > (autoStopMinutes * 60 * 1000L)) {
                        stop()
                        onAutoStopped?.invoke()
                        break
                    }
                }
            }

            onSuccess(serverUrl)
        } catch (e: Exception) {
            isRunning = false
            onError(e)
        }
    }

    fun stop() {
        isRunning = false
        watchdogJob?.cancel()
        watchdogJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 60000 // 60 segundos de timeout por conexión
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Leer cabeceras HTTP byte a byte exactamente hasta \r\n\r\n
            val headerBytes = ByteArrayOutputStream()
            var matchCount = 0
            var b: Int
            while (input.read().also { b = it } != -1) {
                headerBytes.write(b)
                if ((matchCount == 0 || matchCount == 2) && b == '\r'.code) {
                    matchCount++
                } else if ((matchCount == 1 || matchCount == 3) && b == '\n'.code) {
                    matchCount++
                    if (matchCount == 4) break
                } else {
                    matchCount = 0
                }
            }

            if (headerBytes.size() == 0) return

            val headerString = headerBytes.toString("UTF-8")
            val lines = headerString.split("\r\n")
            if (lines.isEmpty()) return

            val requestLine = lines[0].split(" ")
            if (requestLine.size < 2) return

            val method = requestLine[0].uppercase()
            val rawPath = requestLine[1]

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(":")
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
            }

            when {
                method == "OPTIONS" -> {
                    sendCorsPreflight(output)
                }
                method == "GET" && (rawPath == "/" || rawPath.startsWith("/index")) -> {
                    serveWebPage(output)
                }
                method == "POST" && rawPath.startsWith("/upload") -> {
                    handleFileUpload(rawPath, headers, input, output)
                }
                method == "GET" && (rawPath.startsWith("/share/song") || (rawPath.startsWith("/share") && (rawPath.contains("type=song") || rawPath.contains("songId=")))) -> {
                    handleShareSong(rawPath, output)
                }
                method == "GET" && (rawPath.startsWith("/share/playlist") || (rawPath.startsWith("/share") && (rawPath.contains("type=playlist") || rawPath.contains("playlistId=")))) -> {
                    handleSharePlaylist(rawPath, output)
                }
                method == "GET" && (rawPath.startsWith("/stream/song") || rawPath.startsWith("/stream")) -> {
                    handleAudioStream(rawPath, headers, output, isDownload = false)
                }
                method == "GET" && (rawPath.startsWith("/download/song") || (rawPath.startsWith("/download") && rawPath.contains("songId="))) -> {
                    handleAudioStream(rawPath, headers, output, isDownload = true)
                }
                method == "GET" && (rawPath.startsWith("/download/playlist.zip") || (rawPath.startsWith("/download") && rawPath.contains("format=zip"))) -> {
                    handleDownloadPlaylistZip(rawPath, output)
                }
                method == "GET" && (rawPath.startsWith("/download/playlist.m3u") || (rawPath.startsWith("/download") && rawPath.contains("format=m3u"))) -> {
                    handleDownloadPlaylistM3u(rawPath, output)
                }
                else -> {
                    sendNotFound(output)
                }
            }

            output.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun sendCorsPreflight(output: OutputStream) {
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 204 No Content")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Access-Control-Allow-Methods: GET, POST, OPTIONS")
        writer.println("Access-Control-Allow-Headers: *")
        writer.println("Connection: close")
        writer.println()
        writer.flush()
    }

    private fun getQueryParam(path: String, key: String): String? {
        val query = path.substringAfter("?", "")
        if (query.isBlank()) return null
        for (param in query.split("&")) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == key) {
                return try {
                    URLDecoder.decode(parts[1].trim(), "UTF-8")
                } catch (e: Exception) {
                    parts[1].trim()
                }
            }
        }
        return null
    }

    // ==========================================
    // COMPARTIR Y DESCARGAR CANCIÓN INDIVIDUAL
    // ==========================================

    private fun handleShareSong(path: String, output: OutputStream) {
        val songIdStr = getQueryParam(path, "id") ?: getQueryParam(path, "songId")
        val songId = songIdStr?.toLongOrNull()
        val song = songProvider().firstOrNull { it.id == songId }

        if (song == null) {
            sendNotFound(output)
            return
        }

        val html = """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${escapeHtml(song.title)} — Boumusic</title>
    <style>
        :root {
            --bg: #0C0A17;
            --surface: #16132B;
            --border: #2A254C;
            --primary: #00F5D4;
            --secondary: #7B2CBF;
            --text: #F8F9FA;
            --text-sec: #9D9BAE;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 24px 16px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 24px; max-width: 480px; width: 100%; padding: 32px 24px; box-shadow: 0 20px 60px rgba(0,0,0,0.6); text-align: center; }
        .badge-app { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 700; color: var(--primary); background: rgba(0, 245, 212, 0.12); padding: 6px 14px; border-radius: 20px; margin-bottom: 24px; }
        .vinyl-art { width: 140px; height: 140px; border-radius: 24px; background: linear-gradient(135deg, var(--secondary), var(--primary)); margin: 0 auto 20px auto; display: flex; align-items: center; justify-content: center; font-size: 64px; box-shadow: 0 12px 30px rgba(123, 44, 191, 0.35); }
        h1 { font-size: 22px; font-weight: 800; line-height: 1.3; margin-bottom: 6px; }
        p.artist { color: var(--text-sec); font-size: 15px; margin-bottom: 20px; }
        .chip { display: inline-block; background: rgba(255,255,255,0.06); padding: 4px 12px; border-radius: 12px; font-size: 13px; color: var(--text-sec); margin-bottom: 24px; }
        audio { width: 100%; border-radius: 30px; margin-bottom: 24px; filter: invert(0.9) hue-rotate(180deg); outline: none; }
        .btn-download { display: block; width: 100%; background: linear-gradient(135deg, var(--primary), #00BBF9); color: #07060F; font-size: 16px; font-weight: 800; text-decoration: none; padding: 16px; border-radius: 18px; box-shadow: 0 8px 25px rgba(0, 245, 212, 0.35); transition: transform 0.15s ease; }
        .btn-download:active { transform: scale(0.98); }
        .footer-note { font-size: 12px; color: var(--text-sec); margin-top: 18px; line-height: 1.5; }
    </style>
</head>
<body>
    <div class="card">
        <div class="badge-app">🎵 Compartido desde Boumusic</div>
        <div class="vinyl-art">🎶</div>
        <h1>${escapeHtml(song.title)}</h1>
        <p class="artist">${escapeHtml(song.artist)} • ${escapeHtml(song.album)}</p>
        <span class="chip">⏱️ ${song.formattedDuration}</span>

        <audio controls preload="metadata" src="/stream/song?id=${song.id}"></audio>

        <a href="/download/song?id=${song.id}" download class="btn-download">⬇️ Descargar Canción Directamente</a>

        <p class="footer-note">Transferencia directa por Wi-Fi sin compresión, sin anuncios y sin intermediarios.</p>
    </div>
</body>
</html>
        """.trimIndent()

        sendHtml(output, html)
    }

    // ==========================================
    // COMPARTIR Y DESCARGAR LISTA COMPLETA
    // ==========================================

    private fun handleSharePlaylist(path: String, output: OutputStream) {
        val playlistId = getQueryParam(path, "id") ?: getQueryParam(path, "playlistId")
        val playlist = playlistProvider().firstOrNull { it.id == playlistId }

        if (playlist == null) {
            sendNotFound(output)
            return
        }

        val allSongs = songProvider()
        val songs = allSongs.filter { playlist.songIds.contains(it.id) }

        val songsHtml = StringBuilder()
        for ((index, song) in songs.withIndex()) {
            songsHtml.append("""
            <div class="track-row">
                <span class="track-num">${index + 1}</span>
                <div class="track-info">
                    <div class="track-title">${escapeHtml(song.title)}</div>
                    <div class="track-artist">${escapeHtml(song.artist)}</div>
                </div>
                <span class="track-duration">${song.formattedDuration}</span>
                <a href="/download/song?id=${song.id}" download class="track-btn" title="Descargar">⬇️</a>
            </div>
            """.trimIndent())
        }

        val html = """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Lista: ${escapeHtml(playlist.name)} — Boumusic</title>
    <style>
        :root {
            --bg: #0C0A17;
            --surface: #16132B;
            --surface-hover: #1E1A3A;
            --border: #2A254C;
            --primary: #00F5D4;
            --secondary: #7B2CBF;
            --text: #F8F9FA;
            --text-sec: #9D9BAE;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 24px 16px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 24px; max-width: 600px; width: 100%; padding: 28px 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.6); }
        .badge-app { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 700; color: var(--primary); background: rgba(0, 245, 212, 0.12); padding: 6px 14px; border-radius: 20px; margin-bottom: 16px; }
        .header { display: flex; align-items: center; gap: 18px; margin-bottom: 24px; }
        .playlist-art { width: 72px; height: 72px; border-radius: 18px; background: linear-gradient(135deg, var(--secondary), var(--primary)); display: flex; align-items: center; justify-content: center; font-size: 34px; box-shadow: 0 8px 25px rgba(123, 44, 191, 0.35); flex-shrink: 0; }
        h1 { font-size: 22px; font-weight: 800; }
        p.track-count { color: var(--text-sec); font-size: 14px; margin-top: 4px; }
        .actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; }
        .btn-main { background: linear-gradient(135deg, var(--primary), #00BBF9); color: #07060F; font-size: 14px; font-weight: 800; text-decoration: none; padding: 14px; border-radius: 16px; text-align: center; box-shadow: 0 6px 20px rgba(0, 245, 212, 0.3); display: flex; align-items: center; justify-content: center; gap: 6px; }
        .btn-sec { background: var(--border); color: var(--text); font-size: 14px; font-weight: 700; text-decoration: none; padding: 14px; border-radius: 16px; text-align: center; display: flex; align-items: center; justify-content: center; gap: 6px; }
        .btn-main:active, .btn-sec:active { transform: scale(0.98); }
        .track-list { display: flex; flex-direction: column; gap: 8px; max-height: 440px; overflow-y: auto; padding-right: 4px; }
        .track-row { display: flex; align-items: center; gap: 12px; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); padding: 10px 14px; border-radius: 14px; transition: background 0.15s ease; }
        .track-row:hover { background: var(--surface-hover); }
        .track-num { font-size: 13px; font-weight: 700; color: var(--primary); width: 22px; text-align: center; }
        .track-info { flex: 1; min-width: 0; }
        .track-title { font-size: 14px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .track-artist { font-size: 12px; color: var(--text-sec); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
        .track-duration { font-size: 12px; color: var(--text-sec); }
        .track-btn { background: rgba(0,245,212,0.15); color: var(--primary); text-decoration: none; padding: 6px 10px; border-radius: 10px; font-size: 13px; font-weight: bold; }
        .footer-note { font-size: 12px; color: var(--text-sec); margin-top: 20px; text-align: center; }
    </style>
</head>
<body>
    <div class="card">
        <div class="badge-app">📋 Lista Compartida — Boumusic</div>
        <div class="header">
            <div class="playlist-art">📑</div>
            <div>
                <h1>${escapeHtml(playlist.name)}</h1>
                <p class="track-count">${songs.size} canciones en total</p>
            </div>
        </div>

        <div class="actions">
            <a href="/download/playlist.zip?id=${playlist.id}" class="btn-main">📦 Descargar Todo (.ZIP)</a>
            <a href="/download/playlist.m3u?id=${playlist.id}" class="btn-sec">📄 Descargar Lista (.M3U)</a>
        </div>

        <div class="track-list">
            $songsHtml
        </div>

        <p class="footer-note">Transferencia directa entre dispositivos en la misma red Wi-Fi o Hotspot.</p>
    </div>
</body>
</html>
        """.trimIndent()

        sendHtml(output, html)
    }

    // ==========================================
    // STREAMING Y DESCARGA DE AUDIO
    // ==========================================

    private fun handleAudioStream(path: String, headers: Map<String, String>, output: OutputStream, isDownload: Boolean) {
        val songIdStr = getQueryParam(path, "id") ?: getQueryParam(path, "songId")
        val songId = songIdStr?.toLongOrNull()
        val song = songProvider().firstOrNull { it.id == songId }

        if (song == null) {
            sendNotFound(output)
            return
        }

        serveAudioFile(song, headers, output, isDownload)
    }

    private fun openSongInputStream(song: Song): InputStream? {
        return try {
            context.contentResolver.openInputStream(song.contentUri)
        } catch (e: Exception) {
            if (song.dataPath.isNotBlank()) {
                val f = File(song.dataPath)
                if (f.exists() && f.canRead()) f.inputStream() else null
            } else null
        }
    }

    private fun getSongFileLength(song: Song): Long {
        try {
            if (song.dataPath.isNotBlank()) {
                val f = File(song.dataPath)
                if (f.exists()) return f.length()
            }
            context.contentResolver.openFileDescriptor(song.contentUri, "r")?.use { pfd ->
                return pfd.statSize
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return -1L
    }

    private fun serveAudioFile(song: Song, headers: Map<String, String>, output: OutputStream, isDownload: Boolean) {
        val totalLength = getSongFileLength(song)
        val mimeType = getMimeType(song.dataPath.ifBlank { "${song.title}.mp3" })

        val rangeHeader = headers["range"]
        var start = 0L
        var end = if (totalLength > 0) totalLength - 1 else -1L

        val isPartial = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.substringAfter("bytes=").trim()
            val parts = rangeSpec.split("-")
            start = parts[0].toLongOrNull() ?: 0L
            if (parts.size > 1 && parts[1].isNotBlank()) {
                end = parts[1].toLongOrNull() ?: end
            }
            true
        } else false

        val contentLength = if (totalLength > 0 && end >= start) (end - start + 1) else -1L
        val safeFilename = "${song.artist} - ${song.title}.mp3".replace("[/\\\\:*?\"<>|]".toRegex(), "_")
        val disposition = if (isDownload) "attachment; filename=\"$safeFilename\"" else "inline"

        val writer = PrintWriter(output)
        if (isPartial) {
            writer.println("HTTP/1.1 206 Partial Content")
            writer.println("Content-Range: bytes $start-$end/${if (totalLength > 0) totalLength else "*"}")
        } else {
            writer.println("HTTP/1.1 200 OK")
        }
        writer.println("Content-Type: $mimeType")
        if (contentLength > 0) {
            writer.println("Content-Length: $contentLength")
        }
        writer.println("Accept-Ranges: bytes")
        writer.println("Content-Disposition: $disposition")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.flush()

        val inputStream = openSongInputStream(song) ?: return
        inputStream.use { input ->
            if (start > 0) {
                input.skip(start)
            }
            val buffer = ByteArray(32768)
            var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
            while (remaining > 0) {
                val toRead = buffer.size.toLong().coerceAtMost(remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.flush()
        }
    }

    // ==========================================
    // DESCARGAR ZIP DE LISTA COMPLETA
    // ==========================================

    private fun handleDownloadPlaylistZip(path: String, output: OutputStream) {
        val playlistId = getQueryParam(path, "id") ?: getQueryParam(path, "playlistId")
        val playlist = playlistProvider().firstOrNull { it.id == playlistId }

        if (playlist == null) {
            sendNotFound(output)
            return
        }

        val allSongs = songProvider()
        val songs = allSongs.filter { playlist.songIds.contains(it.id) }

        val safeZipName = "${playlist.name}.zip".replace("[/\\\\:*?\"<>|]".toRegex(), "_")
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: application/zip")
        writer.println("Content-Disposition: attachment; filename=\"$safeZipName\"")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.flush()

        val zipOut = ZipOutputStream(output)
        val buffer = ByteArray(32768)
        var index = 1

        for (song in songs) {
            val entryName = String.format("%02d - %s - %s.mp3", index++, song.artist, song.title)
                .replace("[/\\\\:*?\"<>|]".toRegex(), "_")
            try {
                val inStream = openSongInputStream(song) ?: continue
                zipOut.putNextEntry(ZipEntry(entryName))
                inStream.use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                    }
                }
                zipOut.closeEntry()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        zipOut.finish()
        zipOut.flush()
    }

    // ==========================================
    // DESCARGAR M3U DE LISTA
    // ==========================================

    private fun handleDownloadPlaylistM3u(path: String, output: OutputStream) {
        val playlistId = getQueryParam(path, "id") ?: getQueryParam(path, "playlistId")
        val playlist = playlistProvider().firstOrNull { it.id == playlistId }

        if (playlist == null) {
            sendNotFound(output)
            return
        }

        val allSongs = songProvider()
        val songs = allSongs.filter { playlist.songIds.contains(it.id) }

        val m3uContent = StringBuilder()
        m3uContent.append("#EXTM3U\n")
        m3uContent.append("#PLAYLIST:${playlist.name}\n\n")

        for (song in songs) {
            val sec = (song.duration / 1000).toInt()
            m3uContent.append("#EXTINF:$sec,${song.artist} - ${song.title}\n")
            val filename = if (song.dataPath.isNotBlank()) File(song.dataPath).name else "${song.title}.mp3"
            m3uContent.append("$filename\n")
        }

        val bytes = m3uContent.toString().toByteArray(StandardCharsets.UTF_8)
        val safeM3uName = "${playlist.name}.m3u".replace("[/\\\\:*?\"<>|]".toRegex(), "_")
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: audio/x-mpegurl; charset=UTF-8")
        writer.println("Content-Length: ${bytes.size}")
        writer.println("Content-Disposition: attachment; filename=\"$safeM3uName\"")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.flush()
        output.write(bytes)
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun sendHtml(output: OutputStream, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: text/html; charset=UTF-8")
        writer.println("Content-Length: ${bytes.size}")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.flush()
        output.write(bytes)
    }

    // ==========================================
    // SUBIDA DESDE PC (PORTAL EXISTENTE)
    // ==========================================

    private fun handleFileUpload(path: String, headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L

        var filename = "cancion_${System.currentTimeMillis()}.mp3"
        if (path.contains("filename=")) {
            val queryParams = path.substringAfter("?", "")
            for (param in queryParams.split("&")) {
                if (param.startsWith("filename=")) {
                    filename = URLDecoder.decode(param.substringAfter("filename="), "UTF-8")
                    break
                }
            }
        } else if (headers.containsKey("x-filename")) {
            filename = URLDecoder.decode(headers["x-filename"]!!, "UTF-8")
        }

        filename = File(filename).name.ifBlank { "cancion_${System.currentTimeMillis()}.mp3" }

        val success = saveAudioFile(filename, input, contentLength)

        val writer = PrintWriter(output)
        if (success) {
            onFileReceived(filename)
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println("Connection: close")
            writer.println()
            writer.println("{\"status\": \"success\", \"filename\": \"$filename\"}")
        } else {
            writer.println("HTTP/1.1 500 Internal Server Error")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println("Connection: close")
            writer.println()
            writer.println("{\"status\": \"error\", \"message\": \"Error al guardar archivo\"}")
        }
        writer.flush()
    }

    private fun saveAudioFile(filename: String, input: InputStream, length: Long): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Audio.Media.MIME_TYPE, getMimeType(filename))
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Boumusic/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

                resolver.openOutputStream(uri)?.use { out ->
                    copyStream(input, out, length)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                true
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "Boumusic")
                if (!targetDir.exists()) targetDir.mkdirs()

                val targetFile = File(targetDir, filename)
                FileOutputStream(targetFile).use { out ->
                    copyStream(input, out, length)
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(getMimeType(filename)),
                    null
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, length: Long) {
        val buffer = ByteArray(32768)
        var totalRead = 0L
        while (length < 0 || totalRead < length) {
            val toRead = if (length < 0) buffer.size else (length - totalRead).coerceAtMost(buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            totalRead += read
        }
        output.flush()
    }

    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "audio/*"
        }
    }

    private fun serveWebPage(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Boumusic Transfer — Subida por lotes</title>
    <style>
        :root {
            --bg: #0F0F13;
            --surface: #181820;
            --surface-hover: #22222D;
            --border: #2E2E3E;
            --primary: #00E5FF;
            --secondary: #7C4DFF;
            --text: #F6F6F9;
            --text-sec: #A2A2B2;
            --success: #00E676;
            --error: #FF5252;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 30px 16px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 20px; max-width: 680px; width: 100%; padding: 32px; box-shadow: 0 16px 50px rgba(0,0,0,0.6); }
        .logo { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
        .badge { background: linear-gradient(135deg, var(--primary), var(--secondary)); border-radius: 14px; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; font-size: 26px; }
        h1 { font-size: 24px; font-weight: 800; }
        p.subtitle { color: var(--text-sec); font-size: 14px; margin-top: 4px; }
        .dest-chip { display: inline-flex; align-items: center; gap: 6px; background: rgba(0, 229, 255, 0.12); color: var(--primary); padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; margin-bottom: 20px; }
        .drop-zone { border: 2px dashed var(--border); border-radius: 16px; padding: 36px 20px; text-align: center; cursor: pointer; transition: all 0.2s ease; background: rgba(255,255,255,0.015); }
        .drop-zone:hover, .drop-zone.dragover { border-color: var(--primary); background: rgba(0,229,255,0.06); transform: scale(1.005); }
        .drop-icon { font-size: 44px; margin-bottom: 10px; }
        .btn-select { background: linear-gradient(135deg, var(--primary), var(--secondary)); color: #000; font-weight: 800; border: none; padding: 12px 28px; border-radius: 25px; margin-top: 14px; cursor: pointer; font-size: 15px; box-shadow: 0 4px 15px rgba(0,229,255,0.3); }
        .btn-select:hover { opacity: 0.92; }
        input[type="file"] { display: none; }
        .progress-box { margin-top: 24px; display: none; }
        .progress-bar-bg { width: 100%; height: 10px; background: var(--surface-hover); border-radius: 5px; overflow: hidden; margin-top: 8px; }
        .progress-bar-fill { height: 100%; width: 0%; background: linear-gradient(90deg, var(--secondary), var(--primary)); transition: width 0.1s linear; }
        .stats-row { display: flex; justify-content: space-between; font-size: 13px; color: var(--text-sec); margin-top: 6px; }
        .file-list { margin-top: 20px; max-height: 240px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
        .file-item { display: flex; align-items: center; justify-content: space-between; background: var(--surface-hover); padding: 10px 14px; border-radius: 10px; font-size: 13px; }
        .file-item.done { border-left: 3px solid var(--success); }
        .file-item.error { border-left: 3px solid var(--error); }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo">
            <div class="badge">🎵</div>
            <div>
                <h1>Boumusic Wi-Fi Transfer</h1>
                <p class="subtitle">Sube canciones desde tu PC directamente a tu móvil sin cables</p>
            </div>
        </div>

        <div class="dest-chip">📁 Guardando en: Music/Boumusic</div>

        <div class="drop-zone" id="dropZone">
            <div class="drop-icon">📂</div>
            <h3>Arrastra aquí tus canciones</h3>
            <p style="color: var(--text-sec); font-size: 14px; margin-top: 4px;">Soporta selección de múltiples archivos a la vez (MP3, FLAC, M4A, WAV)</p>
            <button class="btn-select" id="btnSelect">Elegir canciones del PC</button>
            <input type="file" id="fileInput" multiple accept="audio/*,.mp3,.flac,.m4a,.aac,.wav,.ogg">
        </div>

        <div class="progress-box" id="progressBox">
            <div class="stats-row">
                <span id="currentFileName">Subiendo archivos...</span>
                <span id="fileCountText">0 / 0</span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar-fill" id="progressBar"></div>
            </div>
        </div>

        <div class="file-list" id="fileList"></div>
    </div>

    <script>
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');
        const btnSelect = document.getElementById('btnSelect');
        const progressBox = document.getElementById('progressBox');
        const progressBar = document.getElementById('progressBar');
        const currentFileName = document.getElementById('currentFileName');
        const fileCountText = document.getElementById('fileCountText');
        const fileList = document.getElementById('fileList');

        btnSelect.addEventListener('click', (e) => { e.stopPropagation(); fileInput.click(); });
        dropZone.addEventListener('click', () => fileInput.click());

        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => { e.preventDefault(); e.stopPropagation(); dropZone.classList.add('dragover'); }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => { e.preventDefault(); e.stopPropagation(); dropZone.classList.remove('dragover'); }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            if (dt && dt.files && dt.files.length > 0) handleFiles(dt.files);
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) handleFiles(fileInput.files);
        });

        async function handleFiles(files) {
            const queue = Array.from(files).filter(f => f.type.startsWith('audio/') || /\.(mp3|flac|m4a|aac|wav|ogg)$/i.test(f.name));
            if (queue.length === 0) {
                alert('Por favor selecciona archivos de música válidos (MP3, FLAC, M4A, etc.)');
                return;
            }

            progressBox.style.display = 'block';
            let uploadedCount = 0;

            for (let i = 0; i < queue.length; i++) {
                const file = queue[i];
                fileCountText.textContent = (i + 1) + ' / ' + queue.length;
                currentFileName.textContent = file.name;
                progressBar.style.width = '0%';

                const itemDiv = document.createElement('div');
                itemDiv.className = 'file-item';
                itemDiv.innerHTML = '<span>' + file.name + ' (' + (file.size / 1024 / 1024).toFixed(1) + ' MB)</span><span id="status-' + i + '">⏳ Subiendo...</span>';
                fileList.prepend(itemDiv);

                try {
                    await uploadSingleFile(file, (percent) => {
                        progressBar.style.width = percent + '%';
                    });
                    document.getElementById('status-' + i).textContent = '✅ Completado';
                    itemDiv.classList.add('done');
                    uploadedCount++;
                } catch (err) {
                    document.getElementById('status-' + i).textContent = '❌ Error';
                    itemDiv.classList.add('error');
                }
            }

            currentFileName.textContent = '¡Listo! Se subieron ' + uploadedCount + ' de ' + queue.length + ' canciones.';
            progressBar.style.width = '100%';
        }

        function uploadSingleFile(file, onProgress) {
            return new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                const uploadUrl = '/upload?filename=' + encodeURIComponent(file.name);
                xhr.open('POST', uploadUrl, true);
                xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
                xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable) {
                        const percent = Math.round((e.loaded / e.total) * 100);
                        onProgress(percent);
                    }
                };

                xhr.onload = () => {
                    if (xhr.status >= 200 && xhr.status < 300) resolve();
                    else reject(new Error(xhr.responseText));
                };

                xhr.onerror = () => reject(new Error('Fallo de red'));
                xhr.send(file);
            });
        }
    </script>
</body>
</html>
        """.trimIndent()

        sendHtml(output, html)
    }

    private fun sendNotFound(output: OutputStream) {
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 404 Not Found")
        writer.println("Content-Type: text/plain; charset=UTF-8")
        writer.println("Connection: close")
        writer.println()
        writer.println("404 Not Found")
        writer.flush()
    }
}
