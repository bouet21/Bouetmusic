package com.boumusic.player.transfer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TransferServer(
    private val context: Context,
    val port: Int = 8080,
    private val onFileReceived: (filename: String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isRunning: Boolean = false
        private set

    fun start(onSuccess: (url: String) -> Unit, onError: (Exception) -> Unit) {
        if (isRunning) return

        try {
            serverSocket = ServerSocket(port)
            isRunning = true

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

            onSuccess(serverUrl)
        } catch (e: Exception) {
            isRunning = false
            onError(e)
        }
    }

    fun stop() {
        isRunning = false
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
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]

            // Leer cabeceras HTTP
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "GET" && (rawPath == "/" || rawPath.startsWith("/index"))) {
                serveWebPage(output)
            } else if (method == "POST" && rawPath.startsWith("/upload")) {
                handleFileUpload(rawPath, headers, input, output)
            } else {
                sendNotFound(output)
            }

            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleFileUpload(path: String, headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L

        // Extraer nombre del archivo del parámetro de consulta (?filename=...) o cabecera X-Filename
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

        // Limpiar el nombre de archivo para evitar rutas inseguras
        filename = File(filename).name.ifBlank { "cancion_${System.currentTimeMillis()}.mp3" }

        val success = saveAudioFile(filename, input, contentLength)

        val writer = PrintWriter(output)
        if (success) {
            onFileReceived(filename)
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Connection: close")
            writer.println()
            writer.println("{\"status\": \"success\", \"message\": \"Archivo recibido correctamente\", \"filename\": \"$filename\"}")
        } else {
            writer.println("HTTP/1.1 500 Internal Server Error")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Connection: close")
            writer.println()
            writer.println("{\"status\": \"error\", \"message\": \"No se pudo guardar el archivo\"}")
        }
        writer.flush()
    }

    private fun saveAudioFile(filename: String, input: InputStream, length: Long): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : Guardar en Music/Boumusic mediante MediaStore
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
                // Android 9 o inferior : Guardar en carpeta física Music/Boumusic
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "Boumusic")
                if (!targetDir.exists()) targetDir.mkdirs()

                val targetFile = File(targetDir, filename)
                FileOutputStream(targetFile).use { out ->
                    copyStream(input, out, length)
                }

                // Escanear para que el sistema lo indexe inmediatamente
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
        val buffer = ByteArray(8192)
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
    <title>Boumusic Transfer — Transmitir canciones desde PC</title>
    <style>
        :root {
            --bg: #0F0F13;
            --surface: #181820;
            --border: #2E2E3E;
            --primary: #00E5FF;
            --secondary: #7C4DFF;
            --text: #F6F6F9;
            --text-sec: #A2A2B2;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
        body { background: var(--bg); color: var(--text); display: flex; flex-direction: column; align-items: center; min-height: 100vh; padding: 30px 20px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 20px; max-width: 620px; width: 100%; padding: 32px; box-shadow: 0 10px 40px rgba(0,0,0,0.5); }
        .logo { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
        .badge { background: linear-gradient(135deg, var(--primary), var(--secondary)); border-radius: 12px; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
        h1 { font-size: 24px; font-weight: 800; }
        p.subtitle { color: var(--text-sec); font-size: 14px; margin-top: 4px; line-height: 1.5; }
        .drop-zone { border: 2px dashed var(--border); border-radius: 16px; padding: 40px 20px; text-align: center; margin-top: 24px; cursor: pointer; transition: all 0.2s ease; background: rgba(255,255,255,0.02); }
        .drop-zone:hover, .drop-zone.dragover { border-color: var(--primary); background: rgba(0,229,255,0.05); }
        .drop-icon { font-size: 48px; margin-bottom: 12px; }
        .btn-select { background: linear-gradient(135deg, var(--primary), var(--secondary)); color: #000; font-weight: bold; border: none; padding: 12px 24px; border-radius: 25px; margin-top: 16px; cursor: pointer; font-size: 15px; }
        .folder-badge { display: inline-flex; align-items: center; gap: 6px; background: rgba(124, 77, 255, 0.15); color: #B388FF; padding: 6px 14px; border-radius: 20px; font-size: 12px; margin-top: 20px; font-weight: 600; }
        #progress-container { margin-top: 24px; display: none; }
        .progress-bar-bg { width: 100%; height: 8px; background: var(--border); border-radius: 4px; overflow: hidden; margin-top: 8px; }
        .progress-bar { height: 100%; width: 0%; background: linear-gradient(90deg, var(--primary), var(--secondary)); transition: width 0.15s ease; }
        .file-list { margin-top: 24px; max-height: 220px; overflow-y: auto; }
        .file-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: 10px; background: rgba(255,255,255,0.03); margin-bottom: 8px; font-size: 14px; }
        .success-tag { color: var(--primary); font-size: 12px; font-weight: bold; }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo">
            <div class="badge">🎵</div>
            <div>
                <h1>Boumusic Transfer</h1>
                <p class="subtitle">Transfiere música directamente de tu ordenador al móvil sin cables</p>
            </div>
        </div>

        <div class="folder-badge">
            📁 Destino en tu teléfono: <strong>Music/Boumusic/</strong>
        </div>

        <div class="drop-zone" id="dropZone">
            <div class="drop-icon">📂</div>
            <h3>Arrastra aquí tus canciones MP3, FLAC, M4A o WAV</h3>
            <p class="subtitle" style="margin-top: 6px;">o haz clic en el botón para seleccionarlas</p>
            <button class="btn-select" onclick="document.getElementById('fileInput').click()">Elegir archivos</button>
            <input type="file" id="fileInput" multiple accept="audio/*,.mp3,.flac,.m4a,.wav,.ogg" style="display: none;">
        </div>

        <div id="progress-container">
            <div style="display: flex; justify-content: space-between; font-size: 13px;">
                <span id="current-file-text">Subiendo...</span>
                <span id="progress-percent">0%</span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar" id="progressBar"></div>
            </div>
        </div>

        <div class="file-list" id="fileList"></div>
    </div>

    <script>
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');
        const progressContainer = document.getElementById('progress-container');
        const progressBar = document.getElementById('progressBar');
        const currentFileText = document.getElementById('current-file-text');
        const progressPercent = document.getElementById('progress-percent');
        const fileList = document.getElementById('fileList');

        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => { e.preventDefault(); dropZone.classList.add('dragover'); }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if (files.length > 0) uploadFiles(files);
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) uploadFiles(fileInput.files);
        });

        async function uploadFiles(files) {
            progressContainer.style.display = 'block';
            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                currentFileText.textContent = `Transfiriendo (` + (i+1) + `/` + files.length + `): ` + file.name;
                progressBar.style.width = '0%';
                progressPercent.textContent = '0%';

                await uploadSingleFile(file);

                const item = document.createElement('div');
                item.className = 'file-item';
                item.innerHTML = '<span>🎵 ' + file.name + '</span><span class="success-tag">✓ Guardada</span>';
                fileList.prepend(item);
            }

            currentFileText.textContent = '¡Todas las canciones han sido transferidas a Boumusic!';
            progressBar.style.width = '100%';
            progressPercent.textContent = '100%';
            setTimeout(() => {
                progressContainer.style.display = 'none';
            }, 3000);
        }

        function uploadSingleFile(file) {
            return new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload?filename=' + encodeURIComponent(file.name), true);

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable) {
                        const pct = Math.round((e.loaded / e.total) * 100);
                        progressBar.style.width = pct + '%';
                        progressPercent.textContent = pct + '%';
                    }
                };

                xhr.onload = () => {
                    if (xhr.status === 200) resolve();
                    else reject(new Error(xhr.responseText));
                };

                xhr.onerror = () => reject(new Error('Error de red al transferir'));
                xhr.send(file);
            });
        }
    </script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val writer = PrintWriter(output)
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: text/html; charset=UTF-8")
        writer.println("Content-Length: ${bytes.size}")
        writer.println("Connection: close")
        writer.println()
        writer.flush()
        output.write(bytes)
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
