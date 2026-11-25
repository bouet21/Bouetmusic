package com.boumusic.player.smb

import android.content.Context
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbManager(private val context: Context? = null) {

    private val smbConfig = SmbConfig.builder()
        .withTimeout(15000, TimeUnit.MILLISECONDS)
        .withSoTimeout(35000, TimeUnit.MILLISECONDS)
        .build()

    private fun getClient(): SMBClient = SMBClient(smbConfig)

    private fun getAuthContext(config: FtpConfig): AuthenticationContext {
        val user = config.username.trim()
        val pass = config.password
        return if (user.isBlank() || user.equals("anonymous", ignoreCase = true) || user.equals("guest", ignoreCase = true)) {
            AuthenticationContext.anonymous()
        } else {
            val domain = if (user.contains("\\")) user.substringBefore("\\") else ""
            val cleanUser = if (user.contains("\\")) user.substringAfter("\\") else user
            AuthenticationContext(cleanUser, pass.toCharArray(), domain)
        }
    }

    private fun extractSubPath(config: FtpConfig, remotePath: String): String {
        var path = remotePath.trim()
        val share = config.cleanSmbShare
        if (share.isNotBlank() && path.startsWith("/$share")) {
            path = path.removePrefix("/$share")
        }
        return path.trim('/').replace('/', '\\')
    }

    suspend fun testConnection(config: FtpConfig): Result<Unit> = withContext(Dispatchers.IO) {
        val client = getClient()
        var connection: Connection? = null
        var session: Session? = null
        try {
            val port = if (config.port > 0) config.port else 445
            connection = client.connect(config.host, port)
            val auth = getAuthContext(config)
            session = connection.authenticate(auth)

            if (config.cleanSmbShare.isNotBlank()) {
                val share = session.connectShare(config.cleanSmbShare) as? DiskShare
                    ?: return@withContext Result.failure(Exception("El recurso '${config.cleanSmbShare}' no es una carpeta compartida accesible."))
                share.close()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión SMB: ${e.message ?: "Servidor no accesible"}"))
        } finally {
            try { session?.close() } catch (ignored: Exception) {}
            try { connection?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun listFiles(config: FtpConfig, remotePath: String): List<FtpItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FtpItem>()
        val client = getClient()
        var connection: Connection? = null
        var session: Session? = null
        var diskShare: DiskShare? = null

        try {
            val port = if (config.port > 0) config.port else 445
            connection = client.connect(config.host, port)
            session = connection.authenticate(getAuthContext(config))

            val shareName = config.cleanSmbShare
            if (shareName.isBlank()) {
                return@withContext emptyList()
            }

            diskShare = session.connectShare(shareName) as? DiskShare ?: return@withContext emptyList()
            val subPath = extractSubPath(config, remotePath)

            val fileList = diskShare.list(subPath)
            for (f in fileList) {
                val name = f.fileName
                if (name == "." || name == ".." || name.startsWith(".")) continue

                val isDir = (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                val size = f.endOfFile

                val itemRelativePath = if (subPath.isBlank()) {
                    "/$shareName/$name"
                } else {
                    "/$shareName/${subPath.replace('\\', '/')}/$name"
                }

                items.add(
                    FtpItem(
                        name = name,
                        path = itemRelativePath,
                        isDirectory = isDir,
                        size = size
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { diskShare?.close() } catch (ignored: Exception) {}
            try { session?.close() } catch (ignored: Exception) {}
            try { connection?.close() } catch (ignored: Exception) {}
        }

        items.sortedWith(
            compareBy<FtpItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    fun openAudioStream(config: FtpConfig, remotePath: String, offset: Long = 0L): InputStream? {
        return try {
            val client = getClient()
            val port = if (config.port > 0) config.port else 445
            val connection = client.connect(config.host, port)
            val session = connection.authenticate(getAuthContext(config))
            val share = session.connectShare(config.cleanSmbShare) as DiskShare
            val subPath = extractSubPath(config, remotePath)

            val file = share.openFile(
                subPath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val stream = file.inputStream
            if (offset > 0) {
                stream.skip(offset)
            }
            // Retornar stream encapsulado que libere recursos al cerrarse
            object : InputStream() {
                override fun read(): Int = stream.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
                override fun available(): Int = stream.available()
                override fun close() {
                    try { stream.close() } catch (ignored: Exception) {}
                    try { file.close() } catch (ignored: Exception) {}
                    try { share.close() } catch (ignored: Exception) {}
                    try { session.close() } catch (ignored: Exception) {}
                    try { connection.close() } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadFile(
        config: FtpConfig,
        remotePath: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val client = getClient()
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            val port = if (config.port > 0) config.port else 445
            connection = client.connect(config.host, port)
            session = connection.authenticate(getAuthContext(config))
            share = session.connectShare(config.cleanSmbShare) as DiskShare
            val subPath = extractSubPath(config, remotePath)

            val file = share.openFile(
                subPath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val totalBytes = file.fileInformation.standardInformation.endOfFile
            val input = file.inputStream
            val output = FileOutputStream(destFile)

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
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
            file.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            false
        } finally {
            try { share?.close() } catch (ignored: Exception) {}
            try { session?.close() } catch (ignored: Exception) {}
            try { connection?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun uploadFile(
        config: FtpConfig,
        remoteDir: String,
        localFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val client = getClient()
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            val port = if (config.port > 0) config.port else 445
            connection = client.connect(config.host, port)
            session = connection.authenticate(getAuthContext(config))
            share = session.connectShare(config.cleanSmbShare) as DiskShare

            val subDirPath = extractSubPath(config, remoteDir)
            if (subDirPath.isNotBlank() && !share.folderExists(subDirPath)) {
                val parts = subDirPath.split('\\')
                var currentPath = ""
                for (p in parts) {
                    if (p.isBlank()) continue
                    currentPath = if (currentPath.isEmpty()) p else "$currentPath\\$p"
                    if (!share.folderExists(currentPath)) {
                        share.mkdir(currentPath)
                    }
                }
            }

            val remoteFilePath = if (subDirPath.isBlank()) localFile.name else "$subDirPath\\${localFile.name}"
            val smbFile = share.openFile(
                remoteFilePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )

            val totalBytes = localFile.length()
            val input = localFile.inputStream()
            val output = smbFile.outputStream

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
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
            smbFile.close()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }
}
