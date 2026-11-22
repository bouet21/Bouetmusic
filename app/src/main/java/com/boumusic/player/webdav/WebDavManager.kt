package com.boumusic.player.webdav

import android.content.Context
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.FtpItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebDavManager(private val context: Context? = null) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildUrl(config: FtpConfig, path: String): String {
        val scheme = if (config.port == 443 || config.isFtps) "https" else "http"
        val cleanHost = config.host.removePrefix("http://").removePrefix("https://").trimEnd('/')
        val portPart = if ((scheme == "http" && config.port == 80) || (scheme == "https" && config.port == 443)) {
            ""
        } else {
            ":${config.port}"
        }

        val basePath = if (config.webDavPath.isNotBlank()) {
            "/" + config.webDavPath.trim('/')
        } else {
            ""
        }

        val subPath = if (path.isNotBlank()) {
            "/" + path.trim('/')
        } else {
            ""
        }

        val fullPath = if (basePath == "/" && subPath.isNotBlank()) {
            subPath
        } else if (subPath.isNotBlank()) {
            "$basePath$subPath"
        } else if (basePath.isNotBlank()) {
            basePath
        } else {
            ""
        }

        return "$scheme://$cleanHost$portPart$fullPath"
    }

    suspend fun testConnection(config: FtpConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(config, "/")
            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop><D:resourcetype/></D:prop>
                </D:propfind>
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindXml.toRequestBody(null))
                .addHeader("Depth", "0")
                .addHeader("Content-Type", "application/xml; charset=utf-8")

            if (config.username.isNotBlank()) {
                requestBuilder.addHeader(
                    "Authorization",
                    Credentials.basic(config.username, config.password)
                )
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.code in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error de conexión WebDAV: HTTP ${response.code} ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(config: FtpConfig, remotePath: String): List<FtpItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FtpItem>()
        try {
            val url = buildUrl(config, remotePath)
            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:displayname/>
                        <D:resourcetype/>
                        <D:getcontentlength/>
                    </D:prop>
                </D:propfind>
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindXml.toRequestBody(null))
                .addHeader("Depth", "1")
                .addHeader("Content-Type", "application/xml; charset=utf-8")

            if (config.username.isNotBlank()) {
                requestBuilder.addHeader(
                    "Authorization",
                    Credentials.basic(config.username, config.password)
                )
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val xmlBody = response.body?.string() ?: return@withContext emptyList()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlBody))

            var eventType = parser.eventType
            var inResponse = false
            var currentHref = ""
            var currentDisplayName = ""
            var currentIsCollection = false
            var currentContentLength = 0L

            var requestedPathClean = remotePath.trim('/')
            if (requestedPathClean.isNotEmpty()) requestedPathClean = "/$requestedPathClean"

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "response" -> {
                                inResponse = true
                                currentHref = ""
                                currentDisplayName = ""
                                currentIsCollection = false
                                currentContentLength = 0L
                            }
                            "href" -> {
                                if (inResponse) {
                                    currentHref = parser.nextText().trim()
                                }
                            }
                            "displayname" -> {
                                if (inResponse) {
                                    currentDisplayName = parser.nextText().trim()
                                }
                            }
                            "collection" -> {
                                if (inResponse) {
                                    currentIsCollection = true
                                }
                            }
                            "getcontentlength" -> {
                                if (inResponse) {
                                    currentContentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "response" && inResponse) {
                            inResponse = false
                            val decodedHref = try {
                                URLDecoder.decode(currentHref, "UTF-8")
                            } catch (e: Exception) {
                                currentHref
                            }
                            val cleanHref = decodedHref.trimEnd('/')

                            // Extraer nombre del archivo o carpeta
                            val name = if (currentDisplayName.isNotBlank()) {
                                currentDisplayName
                            } else {
                                cleanHref.substringAfterLast('/')
                            }

                            // Ignorar la propia carpeta consultada
                            val isSelf = cleanHref.endsWith(requestedPathClean) ||
                                    cleanHref.isEmpty() ||
                                    (requestedPathClean.isEmpty() && (cleanHref.endsWith(config.webDavPath.trimEnd('/')) || cleanHref == "/"))

                            if (!isSelf && name.isNotBlank()) {
                                val itemPath = if (remotePath.isBlank() || remotePath == "/") {
                                    "/$name"
                                } else {
                                    "/${remotePath.trim('/')}/$name"
                                }

                                items.add(
                                    FtpItem(
                                        name = name,
                                        path = itemPath,
                                        isDirectory = currentIsCollection,
                                        size = currentContentLength
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Ordenar carpetas primero y luego canciones
        items.sortedWith(
            compareBy<FtpItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    fun openAudioStream(config: FtpConfig, remotePath: String, offset: Long = 0L): InputStream? {
        return try {
            val url = buildUrl(config, remotePath)
            val requestBuilder = Request.Builder().url(url)
            if (config.username.isNotBlank()) {
                requestBuilder.addHeader(
                    "Authorization",
                    Credentials.basic(config.username, config.password)
                )
            }
            if (offset > 0) {
                requestBuilder.addHeader("Range", "bytes=$offset-")
            }
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()
            } else {
                null
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
        try {
            val url = buildUrl(config, remotePath)
            val requestBuilder = Request.Builder().url(url)
            if (config.username.isNotBlank()) {
                requestBuilder.addHeader(
                    "Authorization",
                    Credentials.basic(config.username, config.password)
                )
            }
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val input = body.byteStream()
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            false
        }
    }
}
