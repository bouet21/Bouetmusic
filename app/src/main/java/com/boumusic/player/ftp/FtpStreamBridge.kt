package com.boumusic.player.ftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
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

    fun getStreamUrl(remotePath: String): String {
        return "http://127.0.0.1:$port/stream?path=" + java.net.URLEncoder.encode(remotePath, "UTF-8")
    }

    private fun handleStreamingRequest(socket: Socket) {
        socket.soTimeout = 60000
        var ftpClient = ftpManager.openNewClient()
        var ftpStream: InputStream? = null

        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Leer línea de petición HTTP
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input, "UTF-8"))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val rawPath = parts[1]
            if (!rawPath.startsWith("/stream")) {
                sendNotFound(output)
                return
            }

            var remotePath = ""
            val query = rawPath.substringAfter("?", "")
            for (param in query.split("&")) {
                if (param.startsWith("path=")) {
                    remotePath = URLDecoder.decode(param.substringAfter("path="), "UTF-8")
                    break
                }
            }

            if (remotePath.isBlank() || ftpClient == null) {
                sendNotFound(output)
                return
            }

            ftpStream = ftpClient.retrieveFileStream(remotePath)
            if (ftpStream == null) {
                sendNotFound(output)
                return
            }

            val writer = PrintWriter(output)
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: audio/mpeg")
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
            // Cliente ExoPlayer desconectado o pausado
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
