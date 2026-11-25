package com.boumusic.player.ftp

import android.content.Context
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.boumusic.player.smb.SmbManager
import com.boumusic.player.webdav.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

class FtpManager(private val context: Context? = null) {

    private var activeConfig: FtpConfig? = null
    val webDavManager = WebDavManager(context)
    val smbManager = SmbManager(context)

    val isConnected: Boolean
        get() = activeConfig != null

    val currentConfig: FtpConfig?
        get() = activeConfig

    fun disconnect() {
        activeConfig = null
    }

    private val profilesFile: File?
        get() = context?.let { File(it.filesDir, "ftp_profiles.json") }

    suspend fun getSavedConfigs(): List<FtpConfig> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FtpConfig>()
        val file = profilesFile ?: return@withContext list
        if (!file.exists()) return@withContext list

        try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun saveConfig(config: FtpConfig) = withContext(Dispatchers.IO) {
        val file = profilesFile ?: return@withContext
        val list = getSavedConfigs().filter { it.id != config.id }.toMutableList()
        list.add(0, config)

        try {
            val arr = JSONArray()
            for (c in list) {
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
                arr.put(o)
            }
            file.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteConfig(id: String) = withContext(Dispatchers.IO) {
        val file = profilesFile ?: return@withContext
        val list = getSavedConfigs().filter { it.id != id }
        try {
            val arr = JSONArray()
            for (c in list) {
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
                arr.put(o)
            }
            file.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createFtpClient(config: FtpConfig): FTPClient {
        return if (config.isFtps) {
            org.apache.commons.net.ftp.FTPSClient("TLS").apply {
                needClientAuth = false
                wantClientAuth = false
            }
        } else {
            FTPClient()
        }
    }

    private fun configurePostLogin(client: FTPClient) {
        if (client is org.apache.commons.net.ftp.FTPSClient) {
            try {
                client.execPBSZ(0)
                client.execPROT("P")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun connect(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        if (config.isSmb) {
            val res = smbManager.testConnection(config)
            return@withContext if (res.isSuccess) {
                activeConfig = config
                saveConfig(config)
                Result.success(true)
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("No se pudo conectar a la carpeta compartida SMB"))
            }
        }

        if (config.isWebDav) {
            val res = webDavManager.testConnection(config)
            return@withContext if (res.isSuccess) {
                activeConfig = config
                saveConfig(config)
                Result.success(true)
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("No se pudo conectar a WebDAV"))
            }
        }

        val client = createFtpClient(config)
        try {
            client.connectTimeout = 10000
            client.defaultTimeout = 15000
            client.connect(config.host, config.port)

            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect()
                return@withContext Result.failure(Exception("Servidor FTP rechazó la conexión (Código: $reply)"))
            }

            val user = config.username.ifBlank { "anonymous" }
            val pass = config.password
            val loggedIn = client.login(user, pass)
            if (!loggedIn) {
                client.disconnect()
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            activeConfig = config
            saveConfig(config)
            client.disconnect()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No hay servidor configurado"))
        if (config.isSmb) {
            return@withContext try {
                val list = smbManager.listFiles(config, path)
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (config.isWebDav) {
            return@withContext try {
                val list = webDavManager.listFiles(config, path)
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val client = createFtpClient(config)
        try {
            client.connectTimeout = 10000
            client.defaultTimeout = 15000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (path.isBlank()) "/" else path
            val files = client.listFiles(targetPath)
            val items = mutableListOf<FtpItem>()

            files?.forEach { f ->
                val name = f.name
                if (name != "." && name != "..") {
                    val fullPath = if (targetPath.endsWith("/")) "$targetPath$name" else "$targetPath/$name"
                    val isDir = f.isDirectory
                    items.add(FtpItem(name = name, path = fullPath, isDirectory = isDir, size = f.size))
                }
            }

            client.disconnect()
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSongs(
        query: String,
        startPath: String = "/",
        maxResults: Int = 250,
        maxDepth: Int = 10,
        onFoundBatch: ((List<FtpItem>) -> Unit)? = null
    ): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No hay servidor configurado"))
        val results = mutableListOf<FtpItem>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(Pair(if (startPath.isBlank()) "/" else startPath, 0))
        val lowerQuery = query.lowercase().trim()

        if (config.isSmb || config.isWebDav) {
            while (queue.isNotEmpty() && results.size < maxResults && coroutineContext.isActive) {
                val (currentDir, depth) = queue.removeFirst()
                if (depth > maxDepth) continue
                val items = try {
                    if (config.isSmb) smbManager.listFiles(config, currentDir) else webDavManager.listFiles(config, currentDir)
                } catch (e: Exception) {
                    emptyList()
                }

                var hasNewMatch = false
                for (item in items) {
                    if (!coroutineContext.isActive || results.size >= maxResults) break
                    if (item.isDirectory) {
                        if (depth < maxDepth) queue.add(Pair(item.path, depth + 1))
                    } else if (item.isAudio && item.name.lowercase().contains(lowerQuery)) {
                        results.add(item)
                        hasNewMatch = true
                    }
                }
                if (hasNewMatch) onFoundBatch?.invoke(results.toList())
            }
            return@withContext Result.success(results)
        }

        val client = createFtpClient(config)
        try {
            client.connectTimeout = 10000
            client.defaultTimeout = 15000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            while (queue.isNotEmpty() && results.size < maxResults && coroutineContext.isActive) {
                val (currentDir, depth) = queue.removeFirst()
                if (depth > maxDepth) continue

                val files = try {
                    client.listFiles(currentDir)
                } catch (e: Exception) {
                    null
                } ?: continue

                var hasNewMatch = false

                for (f in files) {
                    if (!coroutineContext.isActive || results.size >= maxResults) break
                    val name = f.name ?: continue
                    if (name == "." || name == ".." || name.startsWith(".")) continue

                    val fullPath = if (currentDir.endsWith("/")) "$currentDir$name" else "$currentDir/$name"

                    if (f.isDirectory) {
                        if (depth < maxDepth) {
                            queue.add(Pair(fullPath, depth + 1))
                        }
                    } else {
                        val item = FtpItem(name = name, path = fullPath, isDirectory = false, size = f.size)
                        if (item.isAudio && name.lowercase().contains(lowerQuery)) {
                            results.add(item)
                            hasNewMatch = true
                        }
                    }
                }

                if (hasNewMatch) {
                    onFoundBatch?.invoke(results.toList())
                }
            }

            try { client.disconnect() } catch (ignored: Exception) {}
            Result.success(results)
        } catch (e: Exception) {
            try { client.disconnect() } catch (ignored: Exception) {}
            Result.failure(e)
        }
    }

    suspend fun listAllAudioFilesRecursively(
        startPath: String = "/",
        maxResults: Int = 3000,
        maxDepth: Int = 10,
        onProgress: ((Int) -> Unit)? = null
    ): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No hay servidor configurado"))
        val results = mutableListOf<FtpItem>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val rootPath = if (startPath.isBlank()) "/" else startPath
        queue.add(Pair(rootPath, 0))

        if (config.isSmb || config.isWebDav) {
            while (queue.isNotEmpty() && results.size < maxResults && coroutineContext.isActive) {
                val (currentDir, depth) = queue.removeFirst()
                if (depth > maxDepth) continue
                val items = try {
                    if (config.isSmb) smbManager.listFiles(config, currentDir) else webDavManager.listFiles(config, currentDir)
                } catch (e: Exception) {
                    emptyList()
                }

                val countBefore = results.size
                for (item in items) {
                    if (!coroutineContext.isActive || results.size >= maxResults) break
                    if (item.isDirectory) {
                        if (depth < maxDepth) queue.add(Pair(item.path, depth + 1))
                    } else if (item.isAudio) {
                        results.add(item)
                    }
                }
                if (results.size > countBefore) {
                    onProgress?.invoke(results.size)
                }
            }
            val sorted = results.sortedWith(
                compareBy({ it.path.substringBeforeLast("/") }, { it.name.lowercase() })
            )
            return@withContext Result.success(sorted)
        }

        val client = createFtpClient(config)
        try {
            client.connectTimeout = 10000
            client.defaultTimeout = 15000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            while (queue.isNotEmpty() && results.size < maxResults && coroutineContext.isActive) {
                val (currentDir, depth) = queue.removeFirst()
                if (depth > maxDepth) continue

                val files = try {
                    client.listFiles(currentDir)
                } catch (e: Exception) {
                    null
                } ?: continue

                val countBefore = results.size
                for (f in files) {
                    if (!coroutineContext.isActive || results.size >= maxResults) break
                    val name = f.name ?: continue
                    if (name == "." || name == ".." || name.startsWith(".")) continue

                    val fullPath = if (currentDir.endsWith("/")) "$currentDir$name" else "$currentDir/$name"

                    if (f.isDirectory) {
                        if (depth < maxDepth) {
                            queue.add(Pair(fullPath, depth + 1))
                        }
                    } else {
                        val item = FtpItem(name = name, path = fullPath, isDirectory = false, size = f.size)
                        if (item.isAudio) {
                            results.add(item)
                        }
                    }
                }

                if (results.size > countBefore) {
                    onProgress?.invoke(results.size)
                }
            }

            try { client.disconnect() } catch (ignored: Exception) {}
            val sorted = results.sortedWith(
                compareBy({ it.path.substringBeforeLast("/") }, { it.name.lowercase() })
            )
            Result.success(sorted)
        } catch (e: Exception) {
            try { client.disconnect() } catch (ignored: Exception) {}
            Result.failure(e)
        }
    }

    fun openNewClient(): FTPClient? {
        val config = activeConfig ?: return null
        if (config.isWebDav || config.isSmb) return null
        return try {
            val client = createFtpClient(config)
            client.connectTimeout = 10000
            client.defaultTimeout = 30000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openWebDavStream(remotePath: String, offset: Long = 0L): InputStream? {
        val config = activeConfig ?: return null
        return webDavManager.openAudioStream(config, remotePath, offset)
    }

    fun openSmbStream(remotePath: String, offset: Long = 0L): InputStream? {
        val config = activeConfig ?: return null
        return smbManager.openAudioStream(config, remotePath, offset)
    }

    suspend fun downloadSong(remotePath: String, destFile: File, onProgress: (Int) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No conectado a servidor"))
        if (config.isSmb) {
            val success = smbManager.downloadFile(config, remotePath, destFile) { progress ->
                onProgress((progress * 100).toInt())
            }
            return@withContext if (success) Result.success(true) else Result.failure(Exception("Error al descargar archivo de SMB"))
        }

        if (config.isWebDav) {
            val success = webDavManager.downloadFile(config, remotePath, destFile) { progress ->
                onProgress((progress * 100).toInt())
            }
            return@withContext if (success) Result.success(true) else Result.failure(Exception("Error al descargar archivo de WebDAV"))
        }

        val client = createFtpClient(config)
        try {
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val inputStream: InputStream = client.retrieveFileStream(remotePath) ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo remoto"))
            val outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(32768)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            client.completePendingCommand()
            client.disconnect()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveConfig(): FtpConfig? = activeConfig

    suspend fun uploadSong(
        config: FtpConfig,
        remoteDir: String,
        localFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.isSmb) {
            return@withContext smbManager.uploadFile(config, remoteDir, localFile, onProgress)
        }
        if (config.isWebDav) {
            return@withContext webDavManager.uploadFile(config, remoteDir, localFile, onProgress)
        }

        var client: FTPClient? = null
        try {
            client = createFtpClient(config)
            client.connectTimeout = 15000
            client.defaultTimeout = 20000
            client.connect(config.host, config.port)

            val loginOk = if (config.username.isBlank()) {
                client.login("anonymous", "")
            } else {
                client.login(config.username, config.password)
            }
            if (!loginOk) {
                return@withContext Result.failure(Exception("Fallo de autenticación FTP: ${client.replyString}"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val dir = remoteDir.trim().ifBlank { "/" }
            if (dir != "/" && dir.isNotBlank()) {
                val parts = dir.split('/').filter { it.isNotBlank() }
                var curr = ""
                for (p in parts) {
                    curr = "$curr/$p"
                    client.makeDirectory(curr)
                }
                client.changeWorkingDirectory(dir)
            }

            val targetName = localFile.name
            val totalBytes = localFile.length()
            val input = localFile.inputStream()
            val output = client.storeFileStream(targetName)
                ?: return@withContext Result.failure(Exception("No se pudo iniciar la subida a FTP (${client.replyString})"))

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalWritten = 0L
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalWritten.toFloat() / totalBytes)
                        }
                    }
                }
            }
            val completed = client.completePendingCommand()
            if (completed) Result.success(Unit) else Result.failure(Exception("Subida incompleta: ${client.replyString}"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                if (client != null && client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            } catch (_: Exception) {}
        }
    }
}
