package com.aistudio.switchvpn.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.switchvpn.data.localization.AppStrings
import com.aistudio.switchvpn.data.model.VpnServer
import com.aistudio.switchvpn.data.network.PingStatus
import com.aistudio.switchvpn.ui.theme.AccentAmber
import com.aistudio.switchvpn.ui.theme.AccentCyan
import com.aistudio.switchvpn.ui.theme.AccentGreen
import com.aistudio.switchvpn.ui.theme.BgCard
import com.aistudio.switchvpn.ui.theme.BgCardElevated
import com.aistudio.switchvpn.ui.theme.BgMidnight
import com.aistudio.switchvpn.ui.theme.PrimaryBlue
import com.aistudio.switchvpn.ui.theme.TextMuted
import com.aistudio.switchvpn.ui.theme.TextPrimary
import com.aistudio.switchvpn.ui.theme.TextSecondary

@Composable
fun ServerSelectionScreen(
    servers: List<VpnServer>,
    selectedServer: VpnServer,
    currentLanguage: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onServerSelected: (VpnServer) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val lang = currentLanguage

    val tabs = listOf(
        "ALL" to AppStrings.get("all_servers", lang),
        "FASTEST" to AppStrings.get("fastest", lang),
        "FREE" to AppStrings.get("free", lang),
        "VIP" to AppStrings.get("vip", lang)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMidnight)
            .testTag("server_selection_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = AppStrings.get("select_server", lang),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_servers_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_servers_input"),
                placeholder = {
                    Text(
                        text = AppStrings.get("search_servers", lang),
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextMuted
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BgCardElevated,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // CATEGORY TABS ROW
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tabs) { (key, label) ->
                    val isSelected = selectedTab == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryBlue else BgCardElevated)
                            .clickable { onTabSelected(key) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                            .testTag("tab_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SERVERS LIST
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("server_list")
            ) {
                items(servers, key = { it.id }) { server ->
                    val isSelected = server.id == selectedServer.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onServerSelected(server) }
                            .testTag("server_item_${server.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) BgCardElevated else BgCard
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) PrimaryBlue else BgCardElevated
                        )
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
                                // Dynamic Flag Emoji Box
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgMidnight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = server.flag.ifBlank { "🌐" },
                                        fontSize = 24.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = server.country,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (server.isVip) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        AccentAmber.copy(alpha = 0.2f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "VIP",
                                                    color = AccentAmber,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "${server.city} • ${server.protocol}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Server load or offline indicator
                                    if (server.pingStatus is PingStatus.Unavailable || server.pingStatus is PingStatus.Timeout) {
                                        Text(
                                            text = if (server.pingStatus is PingStatus.Timeout) "Connection Timed Out" else "Server Offline / Unavailable",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            val loadColor = when {
                                                server.loadPercent > 75 -> AccentAmber
                                                server.loadPercent > 50 -> AccentCyan
                                                else -> AccentGreen
                                            }
                                            LinearProgressIndicator(
                                                progress = { server.loadPercent / 100f },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = loadColor,
                                                trackColor = BgMidnight
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${server.loadPercent}%",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Real Live Ping badge and Selection mark
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val (pingText, pingColor) = when (val st = server.pingStatus) {
                                    is PingStatus.Success -> {
                                        val color = when {
                                            st.rttMs < 60 -> AccentGreen
                                            st.rttMs < 120 -> AccentCyan
                                            st.rttMs < 220 -> AccentAmber
                                            else -> Color(0xFFFF5252)
                                        }
                                        "${st.rttMs} ms" to color
                                    }
                                    PingStatus.Checking -> "Pinging..." to TextMuted
                                    PingStatus.Timeout -> "Timeout" to Color(0xFFFFB300)
                                    PingStatus.Unavailable -> "Unavailable" to Color(0xFFFF5252)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (server.pingStatus is PingStatus.Checking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(8.dp),
                                            color = AccentCyan,
                                            strokeWidth = 1.5.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(pingColor, CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pingText,
                                        color = pingColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, TextMuted, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
