package com.aistudio.switchvpn.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.switchvpn.data.model.AppPreferences
import com.aistudio.switchvpn.data.model.ConnectionStatus
import com.aistudio.switchvpn.data.model.TrafficStats
import com.aistudio.switchvpn.data.model.VpnServer
import com.aistudio.switchvpn.data.network.PingManager
import com.aistudio.switchvpn.data.network.PingStatus
import com.aistudio.switchvpn.service.SwitchVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("switch_vpn_prefs", Context.MODE_PRIVATE)

    val vpnStatus: StateFlow<ConnectionStatus> = SwitchVpnService.vpnStatus
    val trafficStats: StateFlow<TrafficStats> = SwitchVpnService.trafficStats

    private val _servers = MutableStateFlow<List<VpnServer>>(VpnServer.DEFAULT_SERVERS)
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow(VpnServer.DEFAULT_SERVERS.first())
    val selectedServer: StateFlow<VpnServer> = _selectedServer.asStateFlow()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    private val _appPreferences = MutableStateFlow(
        AppPreferences(
            language = prefs.getString("language", "fa") ?: "fa",
            hasAcceptedPrivacy = prefs.getBoolean("has_accepted_privacy", false),
            killSwitchEnabled = prefs.getBoolean("kill_switch", false),
            autoConnect = prefs.getBoolean("auto_connect", false),
            dnsProtection = prefs.getBoolean("dns_protection", true),
            protocol = prefs.getString("protocol", "WireGuard") ?: "WireGuard"
        )
    )
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    val searchQuery = MutableStateFlow("")
    val selectedServerTab = MutableStateFlow("ALL") // "ALL", "FASTEST", "FREE", "VIP"

    val filteredServers: StateFlow<List<VpnServer>> = combine(
        _servers,
        searchQuery,
        selectedServerTab
    ) { list, query, tab ->
        var result = list
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.country.lowercase().contains(q) ||
                it.city.lowercase().contains(q)
            }
        }
        when (tab) {
            "FASTEST" -> result.sortedWith(
                compareBy<VpnServer> {
                    when (it.pingStatus) {
                        is PingStatus.Success -> 0
                        PingStatus.Checking -> 1
                        PingStatus.Timeout -> 2
                        PingStatus.Unavailable -> 3
                    }
                }.thenBy { it.pingMs ?: Int.MAX_VALUE }
            )
            "FREE" -> result.filter { !it.isVip }
            "VIP" -> result.filter { it.isVip }
            else -> result
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnServer.DEFAULT_SERVERS)

    init {
        // First, check if we have cached servers from the live list
        val cachedJson = prefs.getString("cached_servers_json", null)
        if (!cachedJson.isNullOrBlank()) {
            val parsedCache = parseServersJson(cachedJson)
            if (parsedCache.isNotEmpty()) {
                _servers.value = parsedCache
            }
        }

        val savedServerId = prefs.getString("selected_server_id", null)
        if (savedServerId != null) {
            val found = _servers.value.find { it.id == savedServerId }
            if (found != null) {
                _selectedServer.value = found
            }
        } else if (_servers.value.isNotEmpty()) {
            _selectedServer.value = _servers.value.first()
        }

        // Fetch live from GitHub raw URL and measure real pings
        fetchLiveServers()
    }

    fun fetchLiveServers() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingServers.value = true
            try {
                val url = URL(VpnServer.LIVE_SERVERS_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SwitchVPN-Client/1.0")
                }

                if (connection.responseCode in 200..299) {
                    val rawJson = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsedServers = parseServersJson(rawJson)
                    if (parsedServers.isNotEmpty()) {
                        prefs.edit().putString("cached_servers_json", rawJson).apply()
                        _servers.value = parsedServers

                        val currentId = _selectedServer.value.id
                        val match = parsedServers.find { it.id == currentId }
                            ?: parsedServers.find { it.country.equals(_selectedServer.value.country, true) }
                            ?: parsedServers.first()
                        _selectedServer.value = match

                        // Trigger real live ping measurement across all servers in parallel
                        measureAllServersPing(parsedServers)
                    }
                } else {
                    // If network fetch failed, measure pings on current servers
                    measureAllServersPing(_servers.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                measureAllServersPing(_servers.value)
            } finally {
                _isLoadingServers.value = false
            }
        }
    }

    /**
     * Sends real network probes (ICMP ping or TCP socket connection) to calculate real RTT
     */
    fun measureAllServersPing(serverList: List<VpnServer> = _servers.value) {
        viewModelScope.launch(Dispatchers.IO) {
            // Mark all as checking
            _servers.update { list ->
                list.map { it.copy(pingStatus = PingStatus.Checking) }
            }

            // Probe servers in parallel
            val tasks = serverList.map { server ->
                async {
                    val status = PingManager.measurePing(server.config)
                    val rtt = (status as? PingStatus.Success)?.rttMs
                    server.id to Pair(status, rtt)
                }
            }

            val results = tasks.awaitAll().toMap()

            _servers.update { list ->
                list.map { server ->
                    val res = results[server.id]
                    if (res != null) {
                        server.copy(pingStatus = res.first, pingMs = res.second)
                    } else {
                        server
                    }
                }
            }

            val cur = _selectedServer.value
            val res = results[cur.id]
            if (res != null) {
                _selectedServer.value = cur.copy(pingStatus = res.first, pingMs = res.second)
            }
        }
    }

    private fun parseServersJson(jsonStr: String): List<VpnServer> {
        val serverList = mutableListOf<VpnServer>()
        try {
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("servers") ?: JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val country = obj.optString("country", "Server ${i + 1}").trim()
                val flag = obj.optString("flag", "🌐").trim()
                val config = obj.optString("config", "").trim()

                val cityName = when (country.lowercase()) {
                    "germany" -> "Frankfurt"
                    "finland" -> "Helsinki"
                    "united states", "usa" -> "New York"
                    "united kingdom", "uk" -> "London"
                    "france" -> "Paris"
                    "netherlands" -> "Amsterdam"
                    "canada" -> "Toronto"
                    "japan" -> "Tokyo"
                    "singapore" -> "Singapore"
                    else -> "Optimal"
                }

                val isWireGuard = config.contains("wireguard", ignoreCase = true) ||
                        config.contains("[Interface]", ignoreCase = true)

                // No fake ping! Starts as Checking, then measured via live network probe
                serverList.add(
                    VpnServer(
                        id = "server_${i}_${country.lowercase().replace(" ", "_")}",
                        country = country,
                        flag = flag,
                        config = config,
                        city = cityName,
                        pingStatus = PingStatus.Checking,
                        pingMs = null,
                        loadPercent = 35,
                        isVip = false,
                        protocol = if (isWireGuard) "WireGuard" else "OpenVPN"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return serverList
    }

    fun requestToggleConnection(
        context: Context,
        onRequirePermission: () -> Unit
    ) {
        val currentStatus = vpnStatus.value
        if (currentStatus == ConnectionStatus.CONNECTED || currentStatus == ConnectionStatus.CONNECTING) {
            SwitchVpnService.stopVpn(context)
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                onRequirePermission()
            } else {
                startConnection(context)
            }
        }
    }

    fun startConnection(context: Context) {
        val server = _selectedServer.value
        SwitchVpnService.startVpn(context, server.city, server.country, server.config)
    }

    fun disconnect(context: Context) {
        SwitchVpnService.stopVpn(context)
    }

    fun selectServer(server: VpnServer, context: Context) {
        _selectedServer.value = server
        prefs.edit().putString("selected_server_id", server.id).apply()
        if (vpnStatus.value == ConnectionStatus.CONNECTED) {
            SwitchVpnService.stopVpn(context)
            viewModelScope.launch {
                kotlinx.coroutines.delay(600)
                SwitchVpnService.startVpn(context, server.city, server.country, server.config)
            }
        }
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _appPreferences.value = _appPreferences.value.copy(language = lang)
    }

    fun acceptPrivacyAndContinue() {
        prefs.edit().putBoolean("has_accepted_privacy", true).apply()
        _appPreferences.value = _appPreferences.value.copy(hasAcceptedPrivacy = true)
    }

    fun toggleKillSwitch(enabled: Boolean) {
        prefs.edit().putBoolean("kill_switch", enabled).apply()
        _appPreferences.value = _appPreferences.value.copy(killSwitchEnabled = enabled)
    }

    fun toggleAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        _appPreferences.value = _appPreferences.value.copy(autoConnect = enabled)
    }

    fun toggleDnsProtection(enabled: Boolean) {
        prefs.edit().putBoolean("dns_protection", enabled).apply()
        _appPreferences.value = _appPreferences.value.copy(dnsProtection = enabled)
    }

    fun setProtocol(proto: String) {
        prefs.edit().putString("protocol", proto).apply()
        _appPreferences.value = _appPreferences.value.copy(protocol = proto)
    }

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> String.format("%d KB/s", bytesPerSec / 1000)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatDataSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%d KB", bytes / 1000)
            else -> "$bytes B"
        }
    }
}
