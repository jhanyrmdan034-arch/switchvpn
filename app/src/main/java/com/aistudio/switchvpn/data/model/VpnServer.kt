package com.aistudio.switchvpn.data.model

import com.aistudio.switchvpn.data.network.PingStatus

data class VpnServer(
    val id: String,
    val country: String,
    val flag: String,
    val config: String,
    val city: String = "Optimal",
    val pingStatus: PingStatus = PingStatus.Checking,
    val pingMs: Int? = null,
    val loadPercent: Int = 34,
    val isVip: Boolean = false,
    val protocol: String = "WireGuard"
) {
    companion object {
        const val LIVE_SERVERS_URL =
            "https://raw.githubusercontent.com/jhanyrmdan034-arch/Quali-/refs/heads/main/servers.json"

        // Initial default from the live online list
        val DEFAULT_SERVERS = listOf(
            VpnServer(
                id = "server_0_germany",
                country = "Germany",
                flag = "🇩🇪",
                config = "اینو پاک کنید و متن کامل و خام فایل ovpn یا وایرگارد خود را اینجا پیست کنید",
                city = "Frankfurt",
                pingStatus = PingStatus.Checking,
                pingMs = null,
                loadPercent = 32,
                isVip = false,
                protocol = "WireGuard"
            ),
            VpnServer(
                id = "server_1_finland",
                country = "Finland",
                flag = "🇫🇮",
                config = "متن کانفیگ سرور دوم شما",
                city = "Helsinki",
                pingStatus = PingStatus.Checking,
                pingMs = null,
                loadPercent = 45,
                isVip = false,
                protocol = "WireGuard"
            )
        )
    }
}
