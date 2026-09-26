package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TransactionEntity
import com.example.ui.theme.*
import com.example.viewmodel.CashbackViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: CashbackViewModel) {
    val transactions by viewModel.transactionsState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Tất cả, 1: Chờ duyệt, 2: Đã duyệt, 3: Đã hủy

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")) }

    val filteredTransactions = when (selectedTab) {
        1 -> transactions.filter { it.status == "pending" }
        2 -> transactions.filter { it.status == "approved" }
        3 -> transactions.filter { it.status == "rejected" || it.status == "cancelled" }
        else -> transactions
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "LỊCH SỬ GIAO DỊCH",
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "NEURAL ORDER TRACKING",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBackground)
            )
        },
        containerColor = CyberBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBackground)
        ) {
            // Sleek Cyber Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberSurface,
                edgePadding = 16.dp,
                divider = { Divider(color = CyberCardBorder) },
                indicator = {}
            ) {
                val tabs = listOf("TẤT CẢ", "CHỜ DUYỆT", "ĐÃ DUYỆT", "ĐÃ HỦY")
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Tab(
                        selected = selected,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) NeonCyan else TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) NeonCyan.copy(alpha = 0.15f) else Color.Transparent)
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không tìm thấy giao dịch nào",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredTransactions) { tx ->
                        CyberTransactionCard(tx, currencyFormatter, dateFormatter)
                    }
                }
            }
        }
    }
}

@Composable
fun CyberTransactionCard(
    tx: TransactionEntity,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat
) {
    val isApproved = tx.status == "approved"
    val isCancelled = tx.status == "rejected" || tx.status == "cancelled"

    val statusColor = when {
        isApproved -> SuccessGreen
        isCancelled -> NeonPink
        else -> NeonCyan
    }

    val statusText = when {
        isApproved -> "ĐÃ DUYỆT"
        isCancelled -> "ĐÃ HỦY"
        else -> "CHỜ DUYỆT"
    }

    val statusIcon = when {
        isApproved -> Icons.Default.CheckCircle
        isCancelled -> Icons.Default.Cancel
        else -> Icons.Default.HourglassTop
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: #${tx.orderId}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = tx.productName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2
            )

            Divider(color = CyberCardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormatter.format(Date(tx.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "HOÀN TIỀN",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "+${currencyFormatter.format(tx.cashbackAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = GoldCash
                    )
                }
            }
        }
    }
}
