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
        socket.soTimeout = 60000 // 60 segundos de timeout por conexión
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Leer cabeceras HTTP byte a byte exactamente hasta \r\n\r\n
            // Esto evita que BufferedReader se trague los primeros bytes del archivo binario
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
        #overall-status { margin-top: 24px; display: none; background: rgba(255,255,255,0.03); padding: 16px; border-radius: 14px; border: 1px solid var(--border); }
        .status-header { display: flex; justify-content: space-between; font-size: 14px; font-weight: 600; margin-bottom: 8px; }
        .progress-bar-bg { width: 100%; height: 10px; background: var(--border); border-radius: 6px; overflow: hidden; }
        .progress-bar { height: 100%; width: 0%; background: linear-gradient(90deg, var(--primary), var(--secondary)); transition: width 0.2s ease; }
        .queue-summary { font-size: 12px; color: var(--text-sec); margin-top: 8px; }
        .file-list { margin-top: 20px; max-height: 280px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding-right: 4px; }
        .file-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-radius: 12px; background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05); font-size: 14px; }
        .file-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 75%; }
        .tag-waiting { color: var(--text-sec); font-size: 12px; }
        .tag-uploading { color: var(--primary); font-size: 12px; font-weight: bold; }
        .tag-done { color: var(--success); font-size: 12px; font-weight: bold; }
        .tag-error { color: var(--error); font-size: 12px; font-weight: bold; }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo">
            <div class="badge">🎵</div>
            <div>
                <h1>Boumusic Transfer</h1>
                <p class="subtitle">Transfiere todas tus canciones a la vez desde tu ordenador</p>
            </div>
        </div>

        <div class="dest-chip">
            📁 Guardando directamente en: <strong>Music/Boumusic/</strong>
        </div>

        <div class="drop-zone" id="dropZone">
            <div class="drop-icon">📦</div>
            <h3>Arrastra aquí carpetas o múltiples archivos MP3, FLAC, M4A</h3>
            <p class="subtitle">Soporta selección masiva simultánea sin límite</p>
            <button class="btn-select" onclick="document.getElementById('fileInput').click()">Seleccionar canciones</button>
            <input type="file" id="fileInput" multiple accept="audio/*,.mp3,.flac,.m4a,.wav,.ogg,.aac" style="display: none;">
        </div>

        <div id="overall-status">
            <div class="status-header">
                <span id="overall-title">Subiendo canciones...</span>
                <span id="overall-percent">0%</span>
            </div>
            <div class="progress-bar-bg">
                <div class="progress-bar" id="overallBar"></div>
            </div>
            <div class="queue-summary" id="queueSummary">0 de 0 archivos completados</div>
        </div>

        <div class="file-list" id="fileList"></div>
    </div>

    <script>
        const dropZone = document.getElementById('dropZone');
        const fileInput = document.getElementById('fileInput');
        const overallStatus = document.getElementById('overall-status');
        const overallTitle = document.getElementById('overall-title');
        const overallPercent = document.getElementById('overall-percent');
        const overallBar = document.getElementById('overallBar');
        const queueSummary = document.getElementById('queueSummary');
        const fileList = document.getElementById('fileList');

        let uploadQueue = [];
        let completedCount = 0;
        let totalCount = 0;
        let isProcessing = false;
        const CONCURRENCY = 3; // 3 subidas en paralelo para máxima velocidad y estabilidad

        ['dragenter', 'dragover'].forEach(eName => {
            dropZone.addEventListener(eName, (e) => { e.preventDefault(); dropZone.classList.add('dragover'); }, false);
        });
        ['dragleave', 'drop'].forEach(eName => {
            dropZone.addEventListener(eName, (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const files = Array.from(e.dataTransfer.files).filter(isAudioFile);
            if (files.length > 0) enqueueFiles(files);
        });

        fileInput.addEventListener('change', () => {
            const files = Array.from(fileInput.files).filter(isAudioFile);
            if (files.length > 0) enqueueFiles(files);
            fileInput.value = '';
        });

        function isAudioFile(file) {
            return file.name.match(/\.(mp3|flac|m4a|wav|ogg|aac|opus)$/i) || file.type.startsWith('audio/');
        }

        function enqueueFiles(files) {
            overallStatus.style.display = 'block';
            totalCount += files.length;
            updateOverallProgress();

            files.forEach(file => {
                const itemElem = document.createElement('div');
                itemElem.className = 'file-item';
                itemElem.innerHTML = '<span class="file-name">🎵 ' + file.name + '</span><span class="tag-waiting" id="tag-' + file.name.replace(/[^a-zA-Z0-9]/g, '_') + '">En cola</span>';
                fileList.prepend(itemElem);

                uploadQueue.push({ file: file, elemId: 'tag-' + file.name.replace(/[^a-zA-Z0-9]/g, '_') });
            });

            processQueue();
        }

        async function processQueue() {
            if (isProcessing) return;
            isProcessing = true;

            const workers = [];
            for (let i = 0; i < CONCURRENCY; i++) {
                workers.push(runWorker());
            }

            await Promise.all(workers);
            isProcessing = false;

            overallTitle.textContent = '🎉 ¡Todas las canciones transferidas con éxito a Boumusic!';
            overallBar.style.width = '100%';
            overallPercent.textContent = '100%';
        }

        async function runWorker() {
            while (uploadQueue.length > 0) {
                const task = uploadQueue.shift();
                const tagElem = document.getElementById(task.elemId);
                if (tagElem) {
                    tagElem.className = 'tag-uploading';
                    tagElem.textContent = 'Subiendo...';
                }

                try {
                    await uploadSingleFile(task.file, (pct) => {
                        if (tagElem) tagElem.textContent = pct + '%';
                    });
                    if (tagElem) {
                        tagElem.className = 'tag-done';
                        tagElem.textContent = '✓ Guardada';
                    }
                } catch (err) {
                    if (tagElem) {
                        tagElem.className = 'tag-error';
                        tagElem.textContent = '✗ Error';
                    }
                }

                completedCount++;
                updateOverallProgress();
            }
        }

        function updateOverallProgress() {
            if (totalCount === 0) return;
            const pct = Math.round((completedCount / totalCount) * 100);
            overallBar.style.width = pct + '%';
            overallPercent.textContent = pct + '%';
            queueSummary.textContent = completedCount + ' de ' + totalCount + ' canciones transferidas (' + pct + '%)';
            overallTitle.textContent = 'Subiendo canciones en lote...';
        }

        function uploadSingleFile(file, onProgress) {
            return new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload?filename=' + encodeURIComponent(file.name), true);

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable && onProgress) {
                        const pct = Math.round((e.loaded / e.total) * 100);
                        onProgress(pct);
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
