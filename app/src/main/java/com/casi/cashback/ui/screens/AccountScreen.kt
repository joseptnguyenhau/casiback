package com.casi.cashback.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.ui.theme.*
import com.casi.cashback.viewmodel.CashbackViewModel
import com.google.firebase.auth.FirebaseAuth

enum class AccountSubScreen {
    MAIN,
    PERSONAL_INFO,
    PAYMENT_INFO,
    WITHDRAWAL_REQUEST,
    WITHDRAWAL_HISTORY
}

@Composable
fun AccountScreen(viewModel: CashbackViewModel) {
    var currentSubScreen by remember { mutableStateOf(AccountSubScreen.MAIN) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val userProfile by viewModel.userProfileState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    val isAnonymous = currentUser?.isAnonymous == true
    val displayName = userProfile?.get("displayName") as? String 
        ?: currentUser?.displayName 
        ?: if (isAnonymous) "Tài Khoản Khách (Demo)" else "Thành Viên Casi"
    val email = currentUser?.email ?: (userProfile?.get("email") as? String ?: (if (isAnonymous) "guest@casi.ai" else "Chưa cập nhật email"))

    when (currentSubScreen) {
        AccountSubScreen.PERSONAL_INFO -> {
            PersonalInformationScreen(viewModel = viewModel, onBack = { currentSubScreen = AccountSubScreen.MAIN })
        }
        AccountSubScreen.PAYMENT_INFO -> {
            PaymentInformationScreen(viewModel = viewModel, onBack = { currentSubScreen = AccountSubScreen.MAIN })
        }
        AccountSubScreen.WITHDRAWAL_REQUEST -> {
            WithdrawalRequestScreen(
                viewModel = viewModel,
                onBack = { currentSubScreen = AccountSubScreen.MAIN },
                onViewHistory = { currentSubScreen = AccountSubScreen.WITHDRAWAL_HISTORY }
            )
        }
        AccountSubScreen.WITHDRAWAL_HISTORY -> {
            WithdrawalHistoryScreen(viewModel = viewModel, onBack = { currentSubScreen = AccountSubScreen.WITHDRAWAL_REQUEST })
        }
        AccountSubScreen.MAIN -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = CyberBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Tài Khoản",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Profile Header Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                                color = NeonCyan.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = displayName,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = email,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    color = if (isAnonymous) PendingYellow.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isAnonymous) "Demo / Guest Account" else "Google Account",
                                        color = if (isAnonymous) PendingYellow else SuccessGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Menu Options
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            MenuItemRow(
                                icon = Icons.Default.Person,
                                title = "Thông Tin Cá Nhân",
                                subtitle = "Họ tên, SĐT, Ngày sinh, Địa chỉ",
                                onClick = { currentSubScreen = AccountSubScreen.PERSONAL_INFO }
                            )
                            HorizontalDivider(color = CyberCardBorder)
                            MenuItemRow(
                                icon = Icons.Default.Payment,
                                title = "Thông Tin Thanh Toán",
                                subtitle = "Ngân hàng nhận tiền, số tài khoản",
                                onClick = { currentSubScreen = AccountSubScreen.PAYMENT_INFO }
                            )
                            HorizontalDivider(color = CyberCardBorder)
                            MenuItemRow(
                                icon = Icons.Default.AccountBalanceWallet,
                                title = "Yêu Cầu Thanh Toán",
                                subtitle = "Rút tiền cashback về ngân hàng",
                                onClick = { currentSubScreen = AccountSubScreen.WITHDRAWAL_REQUEST }
                            )
                            HorizontalDivider(color = CyberCardBorder)
                            MenuItemRow(
                                icon = Icons.Default.History,
                                title = "Lịch Sử Thanh Toán",
                                subtitle = "Theo dõi trạng thái PENDING / COMPLETED",
                                onClick = { currentSubScreen = AccountSubScreen.WITHDRAWAL_HISTORY }
                            )
                        }
                    }

                    // Logout Button
                    Button(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed)
                            Text("Đăng Xuất (Logout)", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = CyberSurface,
            title = { Text("Xác Nhận Đăng Xuất", color = Color.White) },
            text = { Text("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        FirebaseAuth.getInstance().signOut()
                    }
                ) {
                    Text("Đăng Xuất", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = NeonCyan.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
        }

        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
    }
}
