package com.boumusic.player.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.boumusic.player.data.BackupManager
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NetworkDevice(
    val id: String,
    val name: String,
    val type: String, // "PC" o "ANDROID"
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

class NetworkSyncManager(
    private val context: Context,
    val httpPort: Int = 8089,
    val udpPort: Int = 42424,
    private val serverProvider: () -> List<FtpConfig>,
    private val playlistProvider: () -> List<Playlist>,
    private val onDataReceived: (servers: List<FtpConfig>, playlists: List<Playlist>) -> Unit
) {
    val deviceId: String
    val deviceName: String = run {
        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        "$brand $model"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("boumusic_sync_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _discoveredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NetworkDevice>> = _discoveredDevices.asStateFlow()

    private val _autoSyncDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val autoSyncDeviceIds: StateFlow<Set<String>> = _autoSyncDeviceIds.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var actualHttpPort: Int = httpPort
    private var udpSocket: DatagramSocket? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var broadcastJob: Job? = null
    private val lastSyncTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "android-" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_id", id).apply()
        }
        deviceId = id

        val savedAutoSync = prefs.getStringSet("auto_sync_devices", emptySet()) ?: emptySet()
        _autoSyncDeviceIds.value = savedAutoSync
    }

    fun start() {
        startHttpServer()
        startUdpDiscovery()
    }

    fun stop() {
        discoveryJob?.cancel()
        broadcastJob?.cancel()
        serverJob?.cancel()
        try { udpSocket?.close() } catch (ignored: Exception) {}
        try { serverSocket?.close() } catch (ignored: Exception) {}
    }

    fun toggleAutoSync(targetDeviceId: String) {
        val current = _autoSyncDeviceIds.value.toMutableSet()
        if (current.contains(targetDeviceId)) {
            current.remove(targetDeviceId)
        } else {
            current.add(targetDeviceId)
            // Sincronizar inmediatamente al activar
            _discoveredDevices.value.find { it.id == targetDeviceId }?.let { dev ->
                syncWithDevice(dev)
            }
        }
        _autoSyncDeviceIds.value = current
        prefs.edit().putStringSet("auto_sync_devices", current).apply()
    }

    private fun startHttpServer() {
        serverJob = scope.launch {
            try {
                // Intentar en httpPort o el siguiente puerto libre
                var port = httpPort
                while (port < httpPort + 10) {
                    try {
                        serverSocket = ServerSocket(port)
                        actualHttpPort = port
                        break
                    } catch (e: Exception) {
                        port++
                    }
                }

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Leer cabecera HTTP
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

            if (headerBytes.size() == 0) {
                socket.close()
                return
            }

            val headerStr = headerBytes.toString("UTF-8")
            val lines = headerStr.split("\r\n")
            if (lines.isEmpty()) {
                socket.close()
                return
            }

            val reqLine = lines[0].split(" ")
            if (reqLine.size < 2) {
                socket.close()
                return
            }

            val method = reqLine[0].uppercase()
            val path = reqLine[1]

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val colon = lines[i].indexOf(':')
                if (colon > 0) {
                    headers[lines[i].substring(0, colon).trim().lowercase()] = lines[i].substring(colon + 1).trim()
                }
            }

            if (path.startsWith("/api/sync")) {
                if (method == "GET") {
                    val json = BackupManager.generateBackupJson(serverProvider(), playlistProvider(), emptyMap())
                    val jsonBytes = json.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${jsonBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(response.toByteArray(Charsets.UTF_8))
                    output.write(jsonBytes)
                    output.flush()
                } else if (method == "POST") {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val bodyBytes = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = input.read(bodyBytes, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    val body = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
                    val data = BackupManager.parseBackupJson(body)
                    onDataReceived(data.servers, data.playlists)

                    val respJson = """{"success":true,"servers":${data.servers.size},"playlists":${data.playlists.size}}"""
                    val respBytes = respJson.toByteArray(Charsets.UTF_8)
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${respBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(response.toByteArray(Charsets.UTF_8))
                    output.write(respBytes)
                    output.flush()
                } else {
                    val resp = "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n"
                    output.write(resp.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } else {
                val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                output.write(resp.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun startUdpDiscovery() {
        try {
            udpSocket = DatagramSocket(udpPort).apply {
                broadcast = true
                soTimeout = 2000
            }

            discoveryJob = scope.launch {
                val buffer = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val senderIp = packet.address.hostAddress ?: continue

                        if (text.startsWith("BOUMUSIC_DISCOVERY:")) {
                            val jsonStr = text.removePrefix("BOUMUSIC_DISCOVERY:")
                            val o = JSONObject(jsonStr)
                            val rId = o.optString("id", "")
                            val rName = o.optString("name", "Dispositivo")
                            val rType = o.optString("type", "DESCONOCIDO")
                            val rPort = o.optInt("port", 8089)

                            if (rId != deviceId) {
                                val device = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = senderIp,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(device)

                                // Responder con PONG
                                val pong = "BOUMUSIC_PONG:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"ANDROID\",\"port\":$actualHttpPort}"
                                val pongBytes = pong.toByteArray(Charsets.UTF_8)
                                val pongPacket = DatagramPacket(pongBytes, pongBytes.size, packet.address, packet.port)
                                udpSocket?.send(pongPacket)
                            }
                        } else if (text.startsWith("BOUMUSIC_PONG:")) {
                            val jsonStr = text.removePrefix("BOUMUSIC_PONG:")
                            val o = JSONObject(jsonStr)
                            val rId = o.optString("id", "")
                            val rName = o.optString("name", "Dispositivo")
                            val rType = o.optString("type", "DESCONOCIDO")
                            val rPort = o.optInt("port", 8089)

                            if (rId != deviceId) {
                                val device = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = senderIp,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(device)
                            }
                        }
                    } catch (ignored: Exception) {
                        // Timeout esperado para chequear cancelación
                    }
                }
            }

            broadcastJob = scope.launch {
                while (isActive) {
                    try {
                        val ping = "BOUMUSIC_DISCOVERY:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"ANDROID\",\"port\":$actualHttpPort}"
                        val bytes = ping.toByteArray(Charsets.UTF_8)
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, udpPort)
                        udpSocket?.send(packet)
                    } catch (e: Exception) {
                        // Puede fallar si no hay Wi-Fi activo
                    }

                    // Limpiar dispositivos no vistos en > 15s
                    val now = System.currentTimeMillis()
                    _discoveredDevices.value = _discoveredDevices.value.filter { now - it.lastSeen < 15000 }

                    delay(5000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateDiscoveredDevice(device: NetworkDevice) {
        val list = _discoveredDevices.value.toMutableList()
        val index = list.indexOfFirst { it.id == device.id || it.ip == device.ip }
        if (index >= 0) {
            list[index] = device
        } else {
            list.add(device)
        }
        _discoveredDevices.value = list

        if (_autoSyncDeviceIds.value.contains(device.id)) {
            val lastSync = lastSyncTimes[device.id] ?: 0L
            if (System.currentTimeMillis() - lastSync > 30000L) {
                syncWithDevice(device)
            }
        }
    }

    fun syncWithDevice(device: NetworkDevice) {
        scope.launch {
            try {
                _syncStatus.value = "Sincronizando con ${device.name}..."

                // 1. Enviar datos locales (POST)
                val localJson = BackupManager.generateBackupJson(serverProvider(), playlistProvider(), emptyMap())
                val postReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .post(localJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(postReq).execute().close()

                // 2. Obtener datos remotos (GET)
                val getReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .get()
                    .build()
                val resp = httpClient.newCall(getReq).execute()
                val body = resp.body?.string() ?: ""
                resp.close()

                if (body.isNotBlank()) {
                    val remoteData = BackupManager.parseBackupJson(body)
                    onDataReceived(remoteData.servers, remoteData.playlists)
                }

                lastSyncTimes[device.id] = System.currentTimeMillis()
                _syncStatus.value = "✓ Sincronizado con ${device.name}"
                delay(4000)
                _syncStatus.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Error al sincronizar: ${e.message}"
            }
        }
    }
}
