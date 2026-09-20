package com.aistudio.switchvpn.data.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class TrafficStats(
    val downloadSpeedBytesPerSec: Long = 0,
    val uploadSpeedBytesPerSec: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalUploadBytes: Long = 0,
    val connectedDurationSeconds: Long = 0,
    val virtualIp: String = "10.8.0.2"
)

data class AppPreferences(
    val language: String = "en", // "en" or "fa"
    val hasAcceptedPrivacy: Boolean = false,
    val killSwitchEnabled: Boolean = false,
    val autoConnect: Boolean = false,
    val dnsProtection: Boolean = true,
    val protocol: String = "WireGuard"
)
