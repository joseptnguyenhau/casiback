package com.casi.cashback.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.ui.theme.*
import com.casi.cashback.viewmodel.CashbackViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawalHistoryScreen(
    viewModel: CashbackViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val requests by viewModel.withdrawalRequestsState.collectAsState()
    val currencyFormatter = remember { NumberFormat.getInstance(Locale("vi", "VN")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lịch Sử Yêu Cầu Thanh Toán", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberSurface)
            )
        },
        containerColor = CyberBackground
    ) { paddingValues ->
        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Text("Chưa có lịch sử yêu cầu rút tiền nào", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests) { item ->
                    val amount = (item["amount"] as? Number)?.toLong() ?: 0L
                    val status = (item["status"] as? String) ?: "PENDING"
                    val timestampObj = item["createdAt"]
                    val dateStr = when (timestampObj) {
                        is com.google.firebase.Timestamp -> dateFormat.format(timestampObj.toDate())
                        is Number -> dateFormat.format(Date(timestampObj.toLong()))
                        else -> "Vừa xong"
                    }
                    val paymentSnapshot = item["paymentAccountSnapshot"] as? Map<*, *>
                    val bankName = paymentSnapshot?.get("bankName") as? String ?: "Ngân hàng"
                    val accountNumber = paymentSnapshot?.get("accountNumber") as? String ?: ""

                    val statusColor = when (status.uppercase()) {
                        "COMPLETED" -> SuccessGreen
                        "PENDING" -> PendingYellow
                        "PROCESSING" -> NeonCyan
                        "REJECTED", "CANCELLED" -> ErrorRed
                        else -> TextSecondary
                    }

                    val statusText = when (status.uppercase()) {
                        "COMPLETED" -> "ĐÃ HOÀN TẤT"
                        "PENDING" -> "ĐANG CHỜ XỬ LÝ (PENDING)"
                        "PROCESSING" -> "ĐANG XỬ LÝ"
                        "REJECTED" -> "BỊ TỪ CHỐI"
                        "CANCELLED" -> "ĐÃ HỦY"
                        else -> status
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currencyFormatter.format(amount)} đ",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = CyberCardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Phương thức:", color = TextSecondary, fontSize = 12.sp)
                                Text("Bank Transfer ($bankName)", color = Color.White, fontSize = 12.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Thời gian yêu cầu:", color = TextSecondary, fontSize = 12.sp)
                                Text(dateStr, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
