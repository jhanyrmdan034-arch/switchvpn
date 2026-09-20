package com.aistudio.switchvpn

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aistudio.switchvpn.ui.screens.HomeScreen
import com.aistudio.switchvpn.ui.screens.LanguagePrivacyScreen
import com.aistudio.switchvpn.ui.screens.ServerSelectionScreen
import com.aistudio.switchvpn.ui.screens.SettingsDialog
import com.aistudio.switchvpn.ui.screens.SplashScreen
import com.aistudio.switchvpn.ui.theme.BgMidnight
import com.aistudio.switchvpn.ui.theme.SwitchVpnTheme
import com.aistudio.switchvpn.ui.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SwitchVpnTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()
    val preferences by viewModel.appPreferences.collectAsState()
    val filteredServers by viewModel.filteredServers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedServerTab.collectAsState()
    val isLoadingServers by viewModel.isLoadingServers.collectAsState()

    var currentScreen by remember { mutableStateOf("splash") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startConnection(context)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = BgMidnight
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgMidnight)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (currentScreen) {
                "splash" -> {
                    SplashScreen(
                        currentLanguage = preferences.language,
                        hasAcceptedPrivacy = preferences.hasAcceptedPrivacy,
                        onNavigateNext = { accepted ->
                            currentScreen = if (accepted) "home" else "language_privacy"
                        }
                    )
                }

                "language_privacy" -> {
                    LanguagePrivacyScreen(
                        currentLanguage = preferences.language,
                        onLanguageChange = { newLang ->
                            viewModel.setLanguage(newLang)
                        },
                        onAcceptAndContinue = {
                            viewModel.acceptPrivacyAndContinue()
                            currentScreen = "home"
                        }
                    )
                }

                "home" -> {
                    HomeScreen(
                        vpnStatus = vpnStatus,
                        trafficStats = trafficStats,
                        selectedServer = selectedServer,
                        preferences = preferences,
                        onToggleConnection = {
                            viewModel.requestToggleConnection(
                                context = context,
                                onRequirePermission = {
                                    val prepareIntent = VpnService.prepare(context)
                                    if (prepareIntent != null) {
                                        vpnPermissionLauncher.launch(prepareIntent)
                                    }
                                }
                            )
                        },
                        onNavigateToServerSelect = {
                            currentScreen = "server_select"
                        },
                        onOpenSettings = {
                            showSettingsDialog = true
                        },
                        onToggleLanguage = {
                            val nextLang = if (preferences.language.equals("fa", ignoreCase = true)) "en" else "fa"
                            viewModel.setLanguage(nextLang)
                        },
                        onToggleKillSwitch = { enabled ->
                            viewModel.toggleKillSwitch(enabled)
                        },
                        onToggleDnsProtection = { enabled ->
                            viewModel.toggleDnsProtection(enabled)
                        },
                        formatDuration = viewModel::formatDuration,
                        formatSpeed = viewModel::formatSpeed,
                        formatDataSize = viewModel::formatDataSize
                    )
                }

                "server_select" -> {
                    ServerSelectionScreen(
                        servers = filteredServers,
                        selectedServer = selectedServer,
                        currentLanguage = preferences.language,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { q -> viewModel.searchQuery.value = q },
                        selectedTab = selectedTab,
                        onTabSelected = { tab -> viewModel.selectedServerTab.value = tab },
                        onServerSelected = { server ->
                            viewModel.selectServer(server, context)
                            currentScreen = "home"
                        },
                        onBack = {
                            currentScreen = "home"
                        },
                        isLoading = isLoadingServers,
                        onRefresh = {
                            viewModel.fetchLiveServers()
                        }
                    )
                }
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    preferences = preferences,
                    onDismiss = { showSettingsDialog = false },
                    onLanguageChange = { newLang ->
                        viewModel.setLanguage(newLang)
                    },
                    onToggleKillSwitch = { enabled ->
                        viewModel.toggleKillSwitch(enabled)
                    },
                    onToggleAutoConnect = { enabled ->
                        viewModel.toggleAutoConnect(enabled)
                    },
                    onToggleDnsProtection = { enabled ->
                        viewModel.toggleDnsProtection(enabled)
                    },
                    onProtocolChange = { proto ->
                        viewModel.setProtocol(proto)
                    }
                )
            }
        }
    }
}
