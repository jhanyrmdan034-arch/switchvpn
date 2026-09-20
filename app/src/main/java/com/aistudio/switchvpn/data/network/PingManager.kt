package com.aistudio.switchvpn.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class PingStatus {
    object Checking : PingStatus()
    data class Success(val rttMs: Int) : PingStatus()
    object Timeout : PingStatus()
    object Unavailable : PingStatus()
}

object PingManager {

    data class ServerEndpoint(val host: String, val port: Int)

    /**
     * Dynamically parses and resolves the server's IP address or hostname and port from the raw configuration text.
     * Supports:
     * - WireGuard configs: Endpoint = host:port
     * - OpenVPN configs: remote host [port]
     * - V2Ray / Shadowsocks / Trojan URI schemes: proto://user@host:port
     * - Direct IPv4 addresses: 123.45.67.89
     * - Domain names: server.example.com
     */
    fun extractEndpoint(config: String): ServerEndpoint? {
        if (config.isBlank()) return null

        val lines = config.lines()

        // 1. WireGuard Endpoint directive
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Endpoint", ignoreCase = true) && trimmed.contains("=")) {
                val value = trimmed.substringAfter("=").trim()
                val parts = value.split(":")
                if (parts.isNotEmpty()) {
                    val host = parts[0].trim()
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 51820
                    if (isValidHost(host)) {
                        return ServerEndpoint(host, port)
                    }
                }
            }
        }

        // 2. OpenVPN remote directive
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("remote ", ignoreCase = true)) {
                val tokens = trimmed.split("\\s+".toRegex())
                if (tokens.size >= 2) {
                    val host = tokens[1].trim()
                    val port = tokens.getOrNull(2)?.toIntOrNull() ?: 1194
                    if (isValidHost(host)) {
                        return ServerEndpoint(host, port)
                    }
                }
            }
        }

        // 3. URI schemes (vless://, vmess://, trojan://, ss://)
        if (config.contains("://")) {
            val uriPattern = Regex("""[a-zA-Z0-9+.-]+://(?:[^@]+@)?([a-zA-Z0-9.-]+)(?::(\d+))?""")
            val match = uriPattern.find(config)
            if (match != null) {
                val host = match.groupValues[1]
                val port = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 443
                if (isValidHost(host)) {
                    return ServerEndpoint(host, port)
                }
            }
        }

        // 4. Raw IPv4 address in configuration body
        val ipRegex = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
        val ipMatch = ipRegex.find(config)
        if (ipMatch != null) {
            val host = ipMatch.value
            return ServerEndpoint(host, 443)
        }

        // 5. Domain name in configuration body
        val domainRegex = Regex("""\b[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\.(?:com|org|net|io|de|fi|uk|us|nl|fr|ru|co|info|biz|me|app)\b""")
        val domainMatch = domainRegex.find(config)
        if (domainMatch != null) {
            val host = domainMatch.value
            return ServerEndpoint(host, 443)
        }

        return null
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host.contains(" ") || host.contains("\n") || host.contains("\t") || host.contains("\"")) return false
        return host.contains(".") && !host.startsWith(".") && !host.endsWith(".")
    }

    /**
     * Executes a live network probe to calculate the actual round-trip time (RTT in ms).
     * Uses ICMP ping or direct TCP socket connection tests to the server's port.
     */
    suspend fun measurePing(config: String): PingStatus = withContext(Dispatchers.IO) {
        val endpoint = extractEndpoint(config)
        if (endpoint == null) {
            // No valid IP address or host could be extracted from configuration text
            return@withContext PingStatus.Unavailable
        }

        val host = endpoint.host
        val port = endpoint.port

        // Step 1: Attempt ICMP ping via system ping probe
        val icmpRtt = tryIcmpPing(host)
        if (icmpRtt != null) {
            return@withContext PingStatus.Success(icmpRtt)
        }

        // Step 2: Attempt TCP socket connection test to destination port
        var timeoutOccurred = false
        val socketRtt = trySocketPing(host, port, timeoutMs = 2500) { isTimeout ->
            if (isTimeout) timeoutOccurred = true
        }
        if (socketRtt != null) {
            return@withContext PingStatus.Success(socketRtt)
        }

        // Step 3: If port was non-standard UDP (e.g. WireGuard 51820), test standard probe port 443 / 80 / 53
        if (port != 443 && port != 80 && port != 53) {
            val fallbackRtt = trySocketPing(host, 443, timeoutMs = 2000)
                ?: trySocketPing(host, 80, timeoutMs = 2000)
                ?: trySocketPing(host, 53, timeoutMs = 2000)
            if (fallbackRtt != null) {
                return@withContext PingStatus.Success(fallbackRtt)
            }
        }

        // Step 4: Verify host reachability or DNS resolution
        return@withContext try {
            val address = InetAddress.getByName(host)
            if (timeoutOccurred) {
                PingStatus.Timeout
            } else {
                PingStatus.Unavailable
            }
        } catch (e: UnknownHostException) {
            PingStatus.Unavailable
        } catch (e: Exception) {
            if (timeoutOccurred) PingStatus.Timeout else PingStatus.Unavailable
        }
    }

    fun tryIcmpPing(host: String): Int? {
        return try {
            val process = ProcessBuilder("ping", "-c", "1", "-w", "2", host)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            if (exit == 0) {
                val timeRegex = Regex("""time=([0-9.]+)\s*ms""")
                val match = timeRegex.find(output)
                if (match != null) {
                    val rtt = match.groupValues[1].toFloatOrNull()?.toInt()
                    if (rtt != null && rtt > 0) return rtt
                }
                val avgRegex = Regex("""rtt\s+min/avg/max/mdev\s*=\s*[0-9.]+/([0-9.]+)/""")
                val avgMatch = avgRegex.find(output)
                if (avgMatch != null) {
                    val rtt = avgMatch.groupValues[1].toFloatOrNull()?.toInt()
                    if (rtt != null && rtt > 0) return rtt
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun trySocketPing(
        host: String,
        port: Int,
        timeoutMs: Int = 2000,
        onTimeout: (Boolean) -> Unit = {}
    ): Int? {
        var socket: Socket? = null
        return try {
            val startTime = System.nanoTime()
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val durationMs = ((System.nanoTime() - startTime) / 1_000_000).toInt()
            socket.close()
            maxOf(1, durationMs)
        } catch (e: SocketTimeoutException) {
            onTimeout(true)
            try { socket?.close() } catch (_: Exception) {}
            null
        } catch (e: Exception) {
            try { socket?.close() } catch (_: Exception) {}
            null
        }
    }
}
