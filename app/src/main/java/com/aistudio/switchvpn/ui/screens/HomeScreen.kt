package com.aistudio.switchvpn.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.switchvpn.R
import com.aistudio.switchvpn.data.localization.AppStrings
import com.aistudio.switchvpn.data.model.AppPreferences
import com.aistudio.switchvpn.data.model.ConnectionStatus
import com.aistudio.switchvpn.data.model.TrafficStats
import com.aistudio.switchvpn.data.model.VpnServer
import com.aistudio.switchvpn.data.network.PingStatus
import com.aistudio.switchvpn.ui.theme.AccentAmber
import com.aistudio.switchvpn.ui.theme.AccentCyan
import com.aistudio.switchvpn.ui.theme.AccentGreen
import com.aistudio.switchvpn.ui.theme.AccentRed
import com.aistudio.switchvpn.ui.theme.BgCard
import com.aistudio.switchvpn.ui.theme.BgCardElevated
import com.aistudio.switchvpn.ui.theme.BgMidnight
import com.aistudio.switchvpn.ui.theme.PrimaryBlue
import com.aistudio.switchvpn.ui.theme.PrimaryLightBlue
import com.aistudio.switchvpn.ui.theme.TextMuted
import com.aistudio.switchvpn.ui.theme.TextPrimary
import com.aistudio.switchvpn.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    vpnStatus: ConnectionStatus,
    trafficStats: TrafficStats,
    selectedServer: VpnServer,
    preferences: AppPreferences,
    onToggleConnection: () -> Unit,
    onNavigateToServerSelect: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleDnsProtection: (Boolean) -> Unit,
    formatDuration: (Long) -> String,
    formatSpeed: (Long) -> String,
    formatDataSize: (Long) -> String
) {
    val lang = preferences.language
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "powerPulse")
    val pulseRingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRingScale"
    )
    val pulseRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRingAlpha"
    )

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = {
                Text(
                    text = AppStrings.get("disconnect_confirm_title", lang),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.get("disconnect_confirm_msg", lang),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        onToggleConnection()
                    }
                ) {
                    Text(
                        text = AppStrings.get("disconnect", lang),
                        color = AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(text = AppStrings.get("cancel", lang), color = TextSecondary)
                }
            },
            containerColor = BgCardElevated
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMidnight)
            .testTag("home_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(PrimaryBlue, AccentCyan)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = AppStrings.get("app_title", lang),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Language Switcher Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCardElevated)
                            .clickable { onToggleLanguage() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("lang_toggle_chip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lang.equals("fa", ignoreCase = true)) "فارسی" else "EN",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // STATUS & POWER BUTTON AREA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status text pill
                val (statusText, statusColor) = when (vpnStatus) {
                    ConnectionStatus.CONNECTED -> Pair(AppStrings.get("status_connected", lang), AccentGreen)
                    ConnectionStatus.CONNECTING -> Pair(AppStrings.get("status_connecting", lang), AccentAmber)
                    ConnectionStatus.DISCONNECTING -> Pair(AppStrings.get("status_disconnecting", lang), AccentAmber)
                    ConnectionStatus.DISCONNECTED -> Pair(AppStrings.get("status_disconnected", lang), TextMuted)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Big Circular Power Button with Outer Glow Waves
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Outer Rings when connected or connecting
                    if (vpnStatus == ConnectionStatus.CONNECTED || vpnStatus == ConnectionStatus.CONNECTING) {
                        val waveColor = if (vpnStatus == ConnectionStatus.CONNECTED) AccentGreen else AccentAmber
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .scale(pulseRingScale)
                                .background(waveColor.copy(alpha = pulseRingAlpha), CircleShape)
                        )
                    }

                    // Button Container
                    val buttonBrush = when (vpnStatus) {
                        ConnectionStatus.CONNECTED -> Brush.radialGradient(
                            listOf(AccentGreen, Color(0xFF008947))
                        )
                        ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTING -> Brush.radialGradient(
                            listOf(AccentAmber, Color(0xFFC67D00))
                        )
                        ConnectionStatus.DISCONNECTED -> Brush.radialGradient(
                            listOf(PrimaryLightBlue, PrimaryBlue, Color(0xFF0F1B4E))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(buttonBrush)
                            .clickable {
                                if (vpnStatus == ConnectionStatus.CONNECTED) {
                                    showDisconnectDialog = true
                                } else {
                                    onToggleConnection()
                                }
                            }
                            .testTag("vpn_power_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vpnStatus == ConnectionStatus.CONNECTING || vpnStatus == ConnectionStatus.DISCONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp),
                                color = Color.White,
                                strokeWidth = 4.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "VPN Power Switch",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Connection Duration Timer
                if (vpnStatus == ConnectionStatus.CONNECTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("connection_timer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = AccentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatDuration(trafficStats.connectedDurationSeconds),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "IP: ${trafficStats.virtualIp} • ${selectedServer.protocol}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = AppStrings.get("tap_to_connect", lang),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SELECTED SERVER CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onNavigateToServerSelect() }
                    .testTag("selected_server_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, BgCardElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedServer.flag.ifBlank { "🌐" },
                                fontSize = 26.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "${selectedServer.country} • ${selectedServer.city}",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val (pingLabel, pingBadgeColor) = when (val st = selectedServer.pingStatus) {
                                is PingStatus.Success -> "${st.rttMs} ms" to AccentGreen
                                PingStatus.Checking -> "Pinging..." to TextMuted
                                PingStatus.Timeout -> "Timeout" to Color(0xFFFFB300)
                                PingStatus.Unavailable -> "Unavailable" to Color(0xFFFF5252)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(pingBadgeColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$pingLabel • ${selectedServer.protocol}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = AppStrings.get("change_server", lang),
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Change",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // REAL-TIME TRAFFIC STATISTICS MATRIX
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("traffic_stats_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, BgCardElevated)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Download Stats
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(AccentGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Download",
                                    tint = AccentGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("download_speed", lang),
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatSpeed(trafficStats.downloadSpeedBytesPerSec),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${AppStrings.get("total_download", lang)}: ${formatDataSize(trafficStats.totalDownloadBytes)}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(60.dp)
                            .background(BgCardElevated)
                    )

                    // Upload Stats
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(AccentCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Upload",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("upload_speed", lang),
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatSpeed(trafficStats.uploadSpeedBytesPerSec),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${AppStrings.get("total_upload", lang)}: ${formatDataSize(trafficStats.totalUploadBytes)}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QUICK TOGGLES CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, BgCardElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Kill Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Kill Switch",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("kill_switch", lang),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = AppStrings.get("kill_switch_desc", lang),
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = preferences.killSwitchEnabled,
                            onCheckedChange = onToggleKillSwitch,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryBlue,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BgCardElevated
                            ),
                            modifier = Modifier.testTag("kill_switch_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // DNS Protection Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "DNS Protection",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("dns_protection", lang),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "1.1.1.1 & 8.8.8.8 Encrypted DNS",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = preferences.dnsProtection,
                            onCheckedChange = onToggleDnsProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentCyan,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BgCardElevated
                            ),
                            modifier = Modifier.testTag("dns_protection_toggle")
                        )
                    }
                }
            }
        }
    }
}
