package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CashbackViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Trang Chủ", Icons.Default.Home)
    object History : Screen("history", "Lịch Sử", Icons.Default.ReceiptLong)
    object Wallet : Screen("wallet", "Ví Tiền", Icons.Default.AccountBalanceWallet)
}

@Composable
fun MainScreen(viewModel: CashbackViewModel) {
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }

    val items = listOf(Screen.Home, Screen.History, Screen.Wallet)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CyberSurface
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentTab == screen,
                        onClick = { currentTab = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                Screen.Home -> HomeScreen(viewModel = viewModel, onNavigateToWallet = { currentTab = Screen.Wallet })
                Screen.History -> HistoryScreen(viewModel = viewModel)
                Screen.Wallet -> WalletScreen(viewModel = viewModel)
            }
        }
    }
}
