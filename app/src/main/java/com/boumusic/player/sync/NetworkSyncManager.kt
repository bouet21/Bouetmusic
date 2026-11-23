package com.boumusic.player.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import com.boumusic.player.data.BackupManager
import com.boumusic.player.model.FtpConfig
import com.boumusic.player.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
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

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    var actualHttpPort: Int = httpPort
        private set

    val localIp: String?
        get() = getLocalWifiIp()

    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var broadcastJob: Job? = null
    private val lastSyncTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fastProbeClient = OkHttpClient.Builder()
        .connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
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
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("boumusic_sync")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
        startHttpServer()
        startUdpDiscovery()

        scope.launch {
            delay(1000)
            scanSubnet()
        }
    }

    fun stop() {
        discoveryJob?.cancel()
        broadcastJob?.cancel()
        serverJob?.cancel()
        try { udpSocket?.close() } catch (ignored: Exception) {}
        try { serverSocket?.close() } catch (ignored: Exception) {}
        try { multicastLock?.release() } catch (ignored: Exception) {}
        multicastLock = null
    }

    fun toggleAutoSync(targetDeviceId: String) {
        val current = _autoSyncDeviceIds.value.toMutableSet()
        if (current.contains(targetDeviceId)) {
            current.remove(targetDeviceId)
        } else {
            current.add(targetDeviceId)
            _discoveredDevices.value.find { it.id == targetDeviceId }?.let { dev ->
                syncWithDevice(dev)
            }
        }
        _autoSyncDeviceIds.value = current
        prefs.edit().putStringSet("auto_sync_devices", current).apply()
    }

    fun getLocalWifiIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = (iface.name + " " + iface.displayName).lowercase()
                if (name.contains("tun") || name.contains("tap") || name.contains("p2p") || name.contains("tailscale")) continue

                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.") && !host.startsWith("100.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun startHttpServer() {
        serverJob = scope.launch {
            try {
                var port = httpPort
                while (port < httpPort + 10) {
                    try {
                        serverSocket = ServerSocket(port)
                        actualHttpPort = port
                        break
                    } catch (_: Exception) {
                        port++
                    }
                }

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
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

            // Descubrir automáticamente al cliente que nos contacta
            val clientIp = socket.inetAddress?.hostAddress
            if (clientIp != null && clientIp != localIp && !clientIp.startsWith("127.")) {
                scope.launch { probeAndAddDevice(clientIp) }
            }

            if (path.startsWith("/api/info")) {
                val json = """{"app":"Boumusic","platform":"Android","id":"$deviceId","name":"$deviceName","port":$actualHttpPort}"""
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.write(jsonBytes)
                output.flush()
                return
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

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val b = addr.broadcast
                    if (b != null) list.add(b)
                }
            }
        } catch (_: Exception) {}
        try { list.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
        return list.distinct()
    }

    private fun startUdpDiscovery() {
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(udpPort))
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
                    } catch (ignored: Exception) {}
                }
            }

            broadcastJob = scope.launch {
                while (isActive) {
                    try {
                        val ping = "BOUMUSIC_DISCOVERY:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"ANDROID\",\"port\":$actualHttpPort}"
                        val bytes = ping.toByteArray(Charsets.UTF_8)
                        for (bAddr in getBroadcastAddresses()) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, bAddr, udpPort)
                                udpSocket?.send(packet)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    val now = System.currentTimeMillis()
                    _discoveredDevices.value = _discoveredDevices.value.filter { now - it.lastSeen < 25000 }
                    delay(5000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun probeAndAddDevice(ip: String, portCandidate: Int? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val portsToTry = if (portCandidate != null) listOf(portCandidate) else listOf(actualHttpPort, 8089, 8090, 8091)
            for (p in portsToTry) {
                try {
                    val req = Request.Builder()
                        .url("http://$ip:$p/api/info")
                        .get()
                        .build()
                    val resp = fastProbeClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        if (body.contains("Boumusic")) {
                            val o = JSONObject(body)
                            val rId = o.optString("id", "direct-$ip")
                            val rName = o.optString("name", "Dispositivo ($ip)")
                            val rType = o.optString("platform", "Móvil / PC")
                            val rPort = o.optInt("port", p)

                            if (rId != deviceId) {
                                val dev = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = ip,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(dev)
                                return@withContext true
                            }
                        }
                    } else {
                        resp.close()
                    }
                } catch (_: Exception) {}
            }
            false
        }
    }

    fun scanSubnet(onDone: ((Int) -> Unit)? = null) {
        val ip = getLocalWifiIp() ?: return
        val prefix = ip.substringBeforeLast(".") + "."
        _isScanning.value = true
        _syncStatus.value = "Buscando en red local ($prefix*)..."
        scope.launch(Dispatchers.IO) {
            var found = 0
            val jobs = (1..254).map { i ->
                async {
                    val target = "$prefix$i"
                    if (target != ip) {
                        if (probeAndAddDevice(target)) found++
                    }
                }
            }
            jobs.awaitAll()
            _isScanning.value = false
            _syncStatus.value = if (found > 0) "¡Se encontraron $found dispositivo(s)!" else null
            onDone?.invoke(found)
        }
    }

    fun connectDirectly(targetIp: String, port: Int = actualHttpPort, onDone: (Boolean, String) -> Unit) {
        val cleanIp = targetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
        if (cleanIp.isBlank()) {
            onDone(false, "Introduce una dirección IP válida")
            return
        }
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = "Conectando a $cleanIp..."
            val ok = probeAndAddDevice(cleanIp, port)
            if (ok) {
                val dev = _discoveredDevices.value.find { it.ip == cleanIp }
                if (dev != null) {
                    syncWithDevice(dev)
                    onDone(true, "¡Conectado y sincronizado con ${dev.name}!")
                } else {
                    onDone(true, "¡Dispositivo encontrado en $cleanIp!")
                }
            } else {
                _syncStatus.value = null
                onDone(false, "No se encontró ningún Boumusic activo en $cleanIp:$port. Comprueba la IP y que la app esté abierta.")
            }
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
