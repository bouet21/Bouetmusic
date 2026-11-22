package com.boumusic.player.ftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class FtpStreamBridge(
    private val ftpManager: FtpManager,
    val port: Int = 8082
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            job = scope.launch {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleStreamingRequest(client) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        job?.cancel()
        job = null
    }

    fun getStreamUrl(remotePath: String, size: Long = 0L): String {
        var url = "http://127.0.0.1:$port/stream?path=" + java.net.URLEncoder.encode(remotePath, "UTF-8")
        if (size > 0) {
            url += "&size=$size"
        }
        return url
    }

    private fun handleStreamingRequest(socket: Socket) {
        socket.soTimeout = 45000
        val isWebDav = ftpManager.currentConfig?.isWebDav == true
        var ftpClient: org.apache.commons.net.ftp.FTPClient? = if (!isWebDav) ftpManager.openNewClient() else null
        var ftpStream: InputStream? = null

        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, "UTF-8"))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val rawPath = parts[1]
            if (!rawPath.startsWith("/stream")) {
                sendNotFound(output)
                return
            }

            // Leer cabeceras HTTP (buscar Range para seeking / salto de audio)
            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine!!.isEmpty()) break
                val colon = headerLine!!.indexOf(":")
                if (colon > 0) {
                    headers[headerLine!!.substring(0, colon).trim().lowercase()] = headerLine!!.substring(colon + 1).trim()
                }
            }

            var remotePath = ""
            var totalSize = 0L
            val query = rawPath.substringAfter("?", "")
            for (param in query.split("&")) {
                if (param.startsWith("path=")) {
                    remotePath = URLDecoder.decode(param.substringAfter("path="), "UTF-8")
                } else if (param.startsWith("size=")) {
                    totalSize = param.substringAfter("size=").toLongOrNull() ?: 0L
                }
            }

            if (remotePath.isBlank()) {
                sendNotFound(output)
                return
            }

            // Soporte de Range Requests para saltar y avanzar en la canción
            val rangeHeader = headers["range"]
            var startByte = 0L
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val byteRange = rangeHeader.substringAfter("bytes=").substringBefore("-")
                startByte = byteRange.toLongOrNull() ?: 0L
            }

            if (isWebDav) {
                ftpStream = ftpManager.openWebDavStream(remotePath, startByte)
            } else {
                if (ftpClient == null) {
                    sendNotFound(output)
                    return
                }
                if (startByte > 0) {
                    ftpClient.restartOffset = startByte
                }
                ftpStream = ftpClient.retrieveFileStream(remotePath)
            }

            if (ftpStream == null) {
                sendNotFound(output)
                return
            }

            val writer = PrintWriter(output)
            val isPartial = (startByte > 0)

            if (isPartial) {
                writer.println("HTTP/1.1 206 Partial Content")
                if (totalSize > 0) {
                    val endByte = totalSize - 1
                    writer.println("Content-Range: bytes $startByte-$endByte/$totalSize")
                    writer.println("Content-Length: ${totalSize - startByte}")
                }
            } else {
                writer.println("HTTP/1.1 200 OK")
                if (totalSize > 0) {
                    writer.println("Content-Length: $totalSize")
                }
            }

            val ext = remotePath.substringAfterLast('.', "").lowercase()
            val contentType = when (ext) {
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "m4a", "aac" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg", "opus" -> "audio/ogg"
                else -> "audio/mpeg"
            }

            writer.println("Content-Type: $contentType")
            writer.println("Accept-Ranges: bytes")
            writer.println("Connection: close")
            writer.println()
            writer.flush()

            // Transmitir en streaming los bytes a ExoPlayer
            val buffer = ByteArray(32768)
            var read: Int
            while (ftpStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        } catch (e: Exception) {
            // Cliente ExoPlayer desconectado (por salto de canción o pausa)
        } finally {
            try { ftpStream?.close() } catch (ignored: Exception) {}
            try { ftpClient?.disconnect() } catch (ignored: Exception) {}
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun sendNotFound(output: OutputStream) {
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 404 Not Found")
        writer.println("Content-Type: text/plain")
        writer.println("Connection: close")
        writer.println()
        writer.println("Archivo no disponible")
        writer.flush()
    }
}
