package com.boumusic.player.ftp

import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FtpManager {

    private var activeConfig: FtpConfig? = null

    suspend fun connect(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = FTPClient()
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

            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            activeConfig = config
            client.disconnect()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No hay servidor FTP configurado"))
        val client = FTPClient()
        try {
            client.connectTimeout = 10000
            client.defaultTimeout = 15000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
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
            // Ordenar: primero carpetas y luego archivos de audio
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openNewClient(): FTPClient? {
        val config = activeConfig ?: return null
        return try {
            val client = FTPClient()
            client.connectTimeout = 10000
            client.defaultTimeout = 30000
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadSong(remotePath: String, destFile: File, onProgress: (Int) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = activeConfig ?: return@withContext Result.failure(Exception("No conectado a FTP"))
        val client = FTPClient()
        try {
            client.connect(config.host, config.port)
            client.login(config.username.ifBlank { "anonymous" }, config.password)
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
}
