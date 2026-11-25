package com.boumusic.player.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.boumusic.player.ftp.FtpManager
import com.boumusic.player.smb.SmbManager
import com.boumusic.player.sync.NetworkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AndroidTrackDownloaderEngine(
    private val context: Context,
    private val ftpManager: FtpManager,
    private val smbManager: SmbManager,
    private val pcDevicesProvider: () -> List<NetworkDevice> = { emptyList() }
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val fastClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val tempDir = File(context.cacheDir, "temp_downloads").apply { if (!exists()) mkdirs() }

    suspend fun executeDownload(
        task: DownloadTask,
        onUpdate: (DownloadTask) -> Unit
    ): Result<File?> = withContext(Dispatchers.IO) {
        val track = task.track
        val sanitizedBaseName = "${track.title} - ${track.artist}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val tempFile = File(tempDir, "$sanitizedBaseName.mp3")

        onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Localizando audio de alta fidelidad..."))

        try {
            // 1. Intentar descargar a través de un PC en red local con Boumusic Desktop / SpotDL si está conectado
            val pcDevice = pcDevicesProvider().firstOrNull { it.type.equals("PC", ignoreCase = true) }
            var downloaded = false

            if (pcDevice != null) {
                onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Conectando con PC (${pcDevice.name})..."))
                downloaded = tryDownloadViaPcCompanion(pcDevice, track, tempFile, onUpdate, task)
            }

            // 2. Si no hay PC conectado o falló, resolver mediante stream directo web
            if (!downloaded || !tempFile.exists() || tempFile.length() < 1024) {
                onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Resolviendo pista de audio..."))
                val streamUrl = resolveDirectStreamUrl(track)

                if (streamUrl.isNullOrBlank()) {
                    val err = "No se pudo obtener el enlace de descarga de la pista"
                    onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                    return@withContext Result.failure(Exception(err))
                }

                onUpdate(task.copy(stage = DownloadStage.DOWNLOADING, statusMessage = "Descargando stream de audio...", progress = 0.05f))
                val success = downloadStreamToFile(streamUrl, tempFile) { progress ->
                    onUpdate(
                        task.copy(
                            stage = DownloadStage.DOWNLOADING,
                            progress = 0.05f + (progress * 0.90f),
                            statusMessage = "Descargando: ${(progress * 100).toInt()}%"
                        )
                    )
                }

                if (!success || !tempFile.exists() || tempFile.length() < 1024) {
                    val err = "Error al descargar el archivo de audio"
                    onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                    return@withContext Result.failure(Exception(err))
                }
            }

            onUpdate(task.copy(stage = DownloadStage.CONVERTING, progress = 0.96f, statusMessage = "Verificando archivo..."))

            // 3. Destino LOCAL (Almacenamiento del Teléfono)
            if (task.destination.type == DestinationType.LOCAL) {
                val musicDir = getTargetMusicDirectory()
                if (!musicDir.exists()) musicDir.mkdirs()
                val targetFile = File(musicDir, tempFile.name)
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()

                // Notificar al escáner de medios de Android para que aparezca en el reproductor
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mpeg"),
                    null
                )

                onUpdate(
                    task.copy(
                        stage = DownloadStage.COMPLETED,
                        progress = 1.0f,
                        statusMessage = "Guardado en almacenamiento local",
                        outputFile = targetFile
                    )
                )
                return@withContext Result.success(targetFile)
            }

            // 4. Destino REMOTO (SMB, FTP o WebDAV)
            val serverConfig = task.destination.serverConfig
            if (serverConfig == null) {
                val err = "Servidor remoto no configurado"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }

            onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f, statusMessage = "Subiendo a ${serverConfig.name}..."))
            val remoteDir = task.destination.remoteSubPath.ifBlank { "/" }

            val uploadRes = if (serverConfig.isSmb) {
                smbManager.uploadFile(serverConfig, remoteDir, tempFile) { p ->
                    onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f + (p * 0.03f), statusMessage = "Subiendo a SMB: ${(p * 100).toInt()}%"))
                }
            } else {
                ftpManager.uploadSong(serverConfig, remoteDir, tempFile) { p ->
                    onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f + (p * 0.03f), statusMessage = "Subiendo a FTP: ${(p * 100).toInt()}%"))
                }
            }

            tempFile.delete()

            if (uploadRes.isSuccess) {
                onUpdate(
                    task.copy(
                        stage = DownloadStage.COMPLETED,
                        progress = 1.0f,
                        statusMessage = "Subido con éxito a ${serverConfig.name} ($remoteDir)"
                    )
                )
                return@withContext Result.success(null)
            } else {
                val err = "Error al subir a servidor: ${uploadRes.exceptionOrNull()?.message}"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }
        } catch (t: Throwable) {
            val err = "Error en descarga: ${t.message}"
            onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
            return@withContext Result.failure(t)
        }
    }

    private fun getTargetMusicDirectory(): File {
        val pubMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val boumusicFolder = File(pubMusic, "Boumusic")
        if (boumusicFolder.exists() || boumusicFolder.mkdirs()) {
            return boumusicFolder
        }
        val appMusic = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return appMusic ?: File(context.filesDir, "Music").apply { mkdirs() }
    }

    private suspend fun tryDownloadViaPcCompanion(
        pc: NetworkDevice,
        track: DownloadTrackItem,
        destFile: File,
        onUpdate: (DownloadTask) -> Unit,
        task: DownloadTask
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode("${track.artist} - ${track.title}", "UTF-8")
            val url = "http://${pc.ip}:${pc.port}/api/download?query=$q"
            val req = Request.Builder().url(url).build()
            val resp = fastClient.newCall(req).execute()
            if (resp.isSuccessful && resp.body != null) {
                val body = resp.body!!
                val total = body.contentLength()
                val inStream = body.byteStream()
                val outStream = FileOutputStream(destFile)
                inStream.use { inp ->
                    outStream.use { outp ->
                        val buf = ByteArray(32 * 1024)
                        var read: Int
                        var written = 0L
                        while (inp.read(buf).also { read = it } != -1) {
                            outp.write(buf, 0, read)
                            written += read
                            if (total > 0) {
                                onUpdate(
                                    task.copy(
                                        stage = DownloadStage.DOWNLOADING,
                                        progress = (written.toFloat() / total) * 0.95f,
                                        statusMessage = "Descargando desde PC: ${(written * 100 / total)}%"
                                    )
                                )
                            }
                        }
                    }
                }
                return@withContext true
            }
            resp.close()
        } catch (_: Exception) {}
        false
    }

    private fun resolveDirectStreamUrl(track: DownloadTrackItem): String? {
        // 1. Si la URL ya es un stream HTTP directo
        if (track.spotifyUrl.startsWith("http") && (track.spotifyUrl.endsWith(".mp3") || track.spotifyUrl.endsWith(".m4a"))) {
            return track.spotifyUrl
        }

        // 2. Método principal y ultra rápido: Extractor directo con conversión a MP3
        resolveViaLoader(track)?.let { return it }

        // 3. Método secundario: Piped API con timeout corto
        resolveViaPipedInstances(track)?.let { return it }

        // 4. Método terciario: Invidious API con timeout corto
        resolveViaInvidiousInstances(track)?.let { return it }

        return null
    }

    private fun resolveViaLoader(track: DownloadTrackItem): String? {
        val videoUrl = when {
            track.spotifyUrl.contains("youtube.com") || track.spotifyUrl.contains("youtu.be") -> {
                track.spotifyUrl
            }
            track.id.length in 10..12 && !track.id.contains("-") -> {
                "https://www.youtube.com/watch?v=${track.id}"
            }
            else -> {
                val vid = resolveYouTubeVideoId("${track.artist} - ${track.title} audio")
                if (vid != null) "https://www.youtube.com/watch?v=$vid" else null
            }
        } ?: return null

        try {
            val encodedYt = URLEncoder.encode(videoUrl, "UTF-8")
            val initUrl = "https://loader.to/ajax/download.php?format=mp3&url=$encodedYt"
            val initReq = Request.Builder()
                .url(initUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://loader.to/")
                .build()

            val initResp = fastClient.newCall(initReq).execute()
            if (!initResp.isSuccessful || initResp.body == null) {
                initResp.close()
                return null
            }
            val initJson = JSONObject(initResp.body!!.string())
            initResp.close()

            val progressUrl = initJson.optString("progress_url", "")
            if (progressUrl.isBlank()) return null

            // Esperar resolución del archivo MP3
            for (attempt in 0 until 8) {
                Thread.sleep(1100)
                val progReq = Request.Builder()
                    .url(progressUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val progResp = fastClient.newCall(progReq).execute()
                if (progResp.isSuccessful && progResp.body != null) {
                    val pJson = JSONObject(progResp.body!!.string())
                    progResp.close()
                    val downloadUrl = pJson.optString("download_url", "")
                    if (downloadUrl.isNotBlank() && downloadUrl.startsWith("http")) {
                        return downloadUrl
                    }
                    val prog = pJson.optInt("progress", 0)
                    if (prog >= 1000 && downloadUrl.isNotBlank()) {
                        return downloadUrl
                    }
                } else {
                    progResp.close()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveYouTubeVideoId(query: String): String? {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val sUrl = "https://www.youtube.com/results?search_query=$encoded"
            val req = Request.Builder()
                .url(sUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = fastClient.newCall(req).execute()
            if (resp.isSuccessful && resp.body != null) {
                val html = resp.body!!.string()
                resp.close()
                val matcher = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").matcher(html)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            } else {
                resp.close()
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveViaPipedInstances(track: DownloadTrackItem): String? {
        val query = "${track.artist} ${track.title} audio"
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        val endpoints = listOf(
            "https://cf.piped.video",
            "https://piped.video",
            "https://pipedapi.tokhmi.xyz"
        )

        for (ep in endpoints) {
            try {
                val sUrl = "$ep/search?q=$queryEncoded"
                val sReq = Request.Builder().url(sUrl).build()
                val sResp = fastClient.newCall(sReq).execute()
                if (sResp.isSuccessful && sResp.body != null) {
                    val sJson = JSONObject(sResp.body!!.string())
                    sResp.close()
                    val items = sJson.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val first = items.getJSONObject(0)
                        val vUrl = first.optString("url", "")
                        val videoId = if (vUrl.contains("v=")) vUrl.substringAfter("v=") else vUrl.substringAfterLast("/")

                        if (videoId.isNotBlank()) {
                            val streamReq = Request.Builder().url("$ep/streams/$videoId").build()
                            val streamResp = fastClient.newCall(streamReq).execute()
                            if (streamResp.isSuccessful && streamResp.body != null) {
                                val streamJson = JSONObject(streamResp.body!!.string())
                                streamResp.close()
                                val audioStreams = streamJson.optJSONArray("audioStreams")
                                if (audioStreams != null && audioStreams.length() > 0) {
                                    val best = audioStreams.getJSONObject(0)
                                    val directUrl = best.optString("url", "")
                                    if (directUrl.isNotBlank()) return directUrl
                                }
                            } else {
                                streamResp.close()
                            }
                        }
                    }
                } else {
                    sResp.close()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun resolveViaInvidiousInstances(track: DownloadTrackItem): String? {
        val query = "${track.artist} ${track.title}"
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        val instances = listOf(
            "https://yewtu.be",
            "https://invidious.nerdvpn.de",
            "https://inv.nadeko.net"
        )

        for (inst in instances) {
            try {
                val sUrl = "$inst/api/v1/search?q=$queryEncoded&type=video"
                val sReq = Request.Builder().url(sUrl).build()
                val sResp = fastClient.newCall(sReq).execute()
                if (sResp.isSuccessful && sResp.body != null) {
                    val arr = org.json.JSONArray(sResp.body!!.string())
                    sResp.close()
                    if (arr.length() > 0) {
                        val vid = arr.getJSONObject(0).optString("videoId", "")
                        if (vid.isNotBlank()) {
                            val vReq = Request.Builder().url("$inst/api/v1/videos/$vid").build()
                            val vResp = fastClient.newCall(vReq).execute()
                            if (vResp.isSuccessful && vResp.body != null) {
                                val vJson = JSONObject(vResp.body!!.string())
                                vResp.close()
                                val adaptive = vJson.optJSONArray("adaptiveFormats")
                                if (adaptive != null) {
                                    for (i in 0 until adaptive.length()) {
                                        val fmt = adaptive.getJSONObject(i)
                                        if (fmt.optString("type", "").contains("audio")) {
                                            val u = fmt.optString("url", "")
                                            if (u.isNotBlank()) return u
                                        }
                                    }
                                }
                            } else {
                                vResp.close()
                            }
                        }
                    }
                } else {
                    sResp.close()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun downloadStreamToFile(url: String, destFile: File, onProgress: (Float) -> Unit): Boolean {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful || resp.body == null) {
                resp.close()
                return false
            }

            val body = resp.body!!
            val totalBytes = body.contentLength()
            val input = body.byteStream()
            val output = FileOutputStream(destFile)

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            resp.close()
            return destFile.exists() && destFile.length() > 1024
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            return false
        }
    }
}
