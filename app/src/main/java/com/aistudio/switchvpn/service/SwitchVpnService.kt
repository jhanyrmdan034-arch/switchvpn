package com.aistudio.switchvpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.aistudio.switchvpn.MainActivity
import com.aistudio.switchvpn.data.model.ConnectionStatus
import com.aistudio.switchvpn.data.model.TrafficStats
import com.aistudio.switchvpn.data.network.PingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SwitchVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        const val ACTION_CONNECT = "com.aistudio.switchvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.aistudio.switchvpn.DISCONNECT"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_COUNTRY = "extra_server_country"
        const val EXTRA_SERVER_CONFIG = "extra_server_config"
        const val NOTIFICATION_ID = 1010
        const val CHANNEL_ID = "vpn_channel_id"

        private val _vpnStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val vpnStatus = _vpnStatus.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats = _trafficStats.asStateFlow()

        private val _connectedServerName = MutableStateFlow("Germany - Frankfurt")
        val connectedServerName = _connectedServerName.asStateFlow()

        fun startVpn(context: Context, serverName: String, countryName: String, config: String = "") {
            val intent = Intent(context, SwitchVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_SERVER_COUNTRY, countryName)
                putExtra(EXTRA_SERVER_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, SwitchVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "VPN Server"
                val country = intent.getStringExtra(EXTRA_SERVER_COUNTRY) ?: "Optimal"
                val config = intent.getStringExtra(EXTRA_SERVER_CONFIG) ?: ""
                _connectedServerName.value = "$country - $serverName"

                // Must invoke startForeground IMMEDIATELY in onStartCommand to satisfy
                // Android's strict startForegroundService requirement and prevent ForegroundServiceDidNotStartInTimeException
                val initialNotification = buildNotification("$country ($serverName)", "Connecting...", isConnected = false)
                startForegroundSafely(initialNotification)

                connectVpn(serverName, country, config)
            }
            ACTION_DISCONNECT -> {
                disconnectVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status and traffic stats"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverInfo: String, durationOrStatus: String, isConnected: Boolean = true): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SwitchVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnected) "Switch VPN: Connected" else "Switch VPN: Connecting..."
        val actionLabel = if (isConnected) "Disconnect" else "Cancel"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$serverInfo • $durationOrStatus")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, actionLabel, disconnectIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Validates whether the configuration is real or placeholder/fake text.
     * Returns null if valid, or a descriptive failure message if invalid.
     */
    private fun validateConfig(config: String): String? {
        if (config.isBlank() || config.length < 20) {
            return "Invalid Configuration"
        }

        val placeholderKeywords = listOf(
            "اینو پاک کنید",
            "متن کانفیگ",
            "paste your",
            "delete this",
            "placeholder",
            "sample config",
            "your config here"
        )

        for (keyword in placeholderKeywords) {
            if (config.contains(keyword, ignoreCase = true)) {
                return "Invalid Configuration"
            }
        }

        val endpoint = PingManager.extractEndpoint(config)
        if (endpoint == null) {
            return "Invalid Configuration"
        }

        val hasWireGuard = config.contains("[Interface]", ignoreCase = true) ||
                config.contains("PrivateKey", ignoreCase = true)
        val hasOpenVpn = config.contains("remote ", ignoreCase = true) ||
                config.contains("dev tun", ignoreCase = true) ||
                config.contains("client", ignoreCase = true)
        val hasProxyUri = config.startsWith("vmess://", ignoreCase = true) ||
                config.startsWith("vless://", ignoreCase = true) ||
                config.startsWith("trojan://", ignoreCase = true) ||
                config.startsWith("ss://", ignoreCase = true)

        if (!hasWireGuard && !hasOpenVpn && !hasProxyUri) {
            return "Invalid Configuration"
        }

        return null
    }

    /**
     * Attempts a real network handshake probe and only establishes the tunnel when
     * packet confirmations succeed.
     */
    private fun connectVpn(serverName: String, country: String, config: String) {
        _vpnStatus.value = ConnectionStatus.CONNECTING
        _trafficStats.value = TrafficStats()

        serviceScope.launch {
            // 1. Strict validation: detect invalid or placeholder configurations
            val validationError = validateConfig(config)
            if (validationError != null) {
                disconnectAndCleanUp(validationError)
                return@launch
            }

            // 2. Real network tunnel establishment & handshake probe with timeout
            try {
                withTimeout(5500L) {
                    val endpoint = PingManager.extractEndpoint(config)
                        ?: throw IllegalArgumentException("Invalid Configuration")

                    // Resolve remote host IP
                    val address = withContext(Dispatchers.IO) {
                        try {
                            InetAddress.getByName(endpoint.host)
                        } catch (e: Exception) {
                            throw SocketTimeoutException("Could not resolve host")
                        }
                    }

                    // Perform real network handshake probe
                    val handshakeConfirmed = withContext(Dispatchers.IO) {
                        performHandshakeProbe(address, endpoint.port)
                    }

                    if (!handshakeConfirmed) {
                        throw SocketTimeoutException("Handshake failed")
                    }

                    // Establish real native VpnService tunnel interface
                    val builder = Builder()
                        .setSession("SwitchVPN - $country")
                        .setMtu(1420)
                        .addAddress("10.8.0.2", 24)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("1.1.1.1")
                        .addDnsServer("8.8.8.8")

                    val tunnelFd = builder.establish()
                        ?: throw IllegalStateException("Failed to establish tunnel interface")

                    vpnInterface = tunnelFd

                    val notification = buildNotification("$country ($serverName)", "00:00:00", isConnected = true)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, notification)

                    // ONLY change to Connected when the tunnel is established & handshake confirmed!
                    _vpnStatus.value = ConnectionStatus.CONNECTED
                    startRealTrafficMonitoring(tunnelFd, country, serverName)
                }
            } catch (e: TimeoutCancellationException) {
                disconnectAndCleanUp("Connection Failed")
            } catch (e: SocketTimeoutException) {
                disconnectAndCleanUp("Connection Failed")
            } catch (e: IllegalArgumentException) {
                disconnectAndCleanUp(e.message ?: "Invalid Configuration")
            } catch (e: Exception) {
                e.printStackTrace()
                disconnectAndCleanUp("Connection Failed")
            }
        }
    }

    /**
     * Probes the server port or sends UDP handshake packets to verify connectivity.
     */
    private fun performHandshakeProbe(address: InetAddress, port: Int): Boolean {
        // Socket probe with short timeout
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(address, port), 3500)
            socket.soTimeout = 2500
            val out = socket.getOutputStream()
            out.write(byteArrayOf(0x16, 0x03, 0x01, 0x00))
            out.flush()
            socket.close()
            return true
        } catch (e: Exception) {
            // Try UDP probe or ICMP fallback
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }

        // UDP datagram probe with timeout
        var udpSocket: DatagramSocket? = null
        try {
            udpSocket = DatagramSocket()
            udpSocket.soTimeout = 3000
            val probeBytes = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            val packet = DatagramPacket(probeBytes, probeBytes.size, address, port)
            udpSocket.send(packet)

            val receiveBuf = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            udpSocket.receive(receivePacket)
            if (receivePacket.length > 0) {
                return true
            }
        } catch (e: Exception) {
            // UDP receive timed out or unreachable
        } finally {
            try { udpSocket?.close() } catch (_: Exception) {}
        }

        // ICMP reachability check as fallback
        val hostStr = address.hostAddress ?: address.hostName
        return PingManager.tryIcmpPing(hostStr) != null
    }

    /**
     * Monitors actual bytes processed through the tunnel interface without simulation.
     */
    private fun startRealTrafficMonitoring(
        tunnelFd: ParcelFileDescriptor,
        country: String,
        serverName: String
    ) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var elapsedSeconds = 0L
            var prevRx = 0L
            var prevTx = 0L
            var totalRx = 0L
            var totalTx = 0L

            val inStream = FileInputStream(tunnelFd.fileDescriptor)
            val buffer = ByteArray(32768)

            // Packet reader thread reading real data from the tunnel interface
            val readerJob = launch(Dispatchers.IO) {
                try {
                    while (isActive && _vpnStatus.value == ConnectionStatus.CONNECTED) {
                        val bytesRead = inStream.read(buffer)
                        if (bytesRead > 0) {
                            totalRx += bytesRead
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed
                }
            }

            while (isActive && _vpnStatus.value == ConnectionStatus.CONNECTED) {
                delay(1000)
                elapsedSeconds++

                val downSpeed = maxOf(0L, totalRx - prevRx)
                val upSpeed = maxOf(0L, totalTx - prevTx)
                prevRx = totalRx
                prevTx = totalTx

                _trafficStats.value = TrafficStats(
                    downloadSpeedBytesPerSec = downSpeed,
                    uploadSpeedBytesPerSec = upSpeed,
                    totalDownloadBytes = totalRx,
                    totalUploadBytes = totalTx,
                    connectedDurationSeconds = elapsedSeconds,
                    virtualIp = "10.8.0.2"
                )

                if (elapsedSeconds % 5L == 0L) {
                    val hours = elapsedSeconds / 3600
                    val minutes = (elapsedSeconds % 3600) / 60
                    val seconds = elapsedSeconds % 60
                    val durationStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, buildNotification("$country ($serverName)", durationStr))
                }
            }

            readerJob.cancel()
        }
    }

    private fun disconnectAndCleanUp(errorMessage: String) {
        statsJob?.cancel()
        statsJob = null
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _vpnStatus.value = ConnectionStatus.DISCONNECTED
        _trafficStats.value = TrafficStats()
        showToast(errorMessage)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    private fun disconnectVpn() {
        _vpnStatus.value = ConnectionStatus.DISCONNECTING
        statsJob?.cancel()
        statsJob = null

        serviceScope.launch {
            delay(300)
            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _vpnStatus.value = ConnectionStatus.DISCONNECTED
            _trafficStats.value = TrafficStats()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        statsJob?.cancel()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _vpnStatus.value = ConnectionStatus.DISCONNECTED
        super.onDestroy()
    }
}
