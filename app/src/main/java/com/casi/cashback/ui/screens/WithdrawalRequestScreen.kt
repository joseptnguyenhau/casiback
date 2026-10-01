package com.casi.cashback.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.ui.theme.*
import com.casi.cashback.viewmodel.CashbackViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawalRequestScreen(
    viewModel: CashbackViewModel,
    onBack: () -> Unit,
    onViewHistory: () -> Unit
) {
    BackHandler { onBack() }

    val userProfile by viewModel.userProfileState.collectAsState()
    val balanceMap by viewModel.repositoryBalanceRealtimeState().collectAsState(initial = mapOf("available" to 0L, "pending" to 0L))
    val availableBalance = balanceMap["available"] ?: (userProfile?.get("balanceAvailable") as? Long ?: 0L)

    var amountInput by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val bankName = userProfile?.get("bankName") as? String ?: ""
    val accountNumber = userProfile?.get("accountNumber") as? String ?: ""
    val accountHolder = userProfile?.get("accountHolder") as? String ?: ""
    val hasPaymentInfo = bankName.isNotBlank() && accountNumber.isNotBlank() && accountHolder.isNotBlank()

    val uiMessage by viewModel.uiMessage.collectAsState()
    val currencyFormatter = remember { NumberFormat.getInstance(Locale("vi", "VN")) }

    val parsedAmount = amountInput.toLongOrNull() ?: 0L
    val isValid = hasPaymentInfo && parsedAmount >= 20000L && parsedAmount <= availableBalance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yêu Cầu Thanh Toán", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = NeonCyan)
                    }
                },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.Default.History, contentDescription = "Lịch sử", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberSurface)
            )
        },
        containerColor = CyberBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = NeonCyan,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Balance Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Số Dư Khả Dụng Rút Tiền", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = "${currencyFormatter.format(availableBalance)} đ",
                        color = SuccessGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Tối thiểu rút: 20,000 đ", color = TextSecondary, fontSize = 11.sp)
                }
            }

            // Payment Account Summary
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tài Khoản Nhận Tiền", color = NeonCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (hasPaymentInfo) {
                        Text("Ngân hàng: $bankName", color = Color.White, fontSize = 14.sp)
                        Text("Chủ tài khoản: $accountHolder", color = Color.White, fontSize = 14.sp)
                        Text("Số tài khoản: •••• ${if (accountNumber.length > 4) accountNumber.takeLast(4) else accountNumber}", color = TextSecondary, fontSize = 14.sp)
                    } else {
                        Text("Chưa có thông tin thanh toán!", color = ErrorRed, fontSize = 14.sp)
                        Text("Vui lòng cập nhật thông tin thanh toán trước khi yêu cầu rút tiền.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // Amount Input Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Nhập Số Tiền Muốn Rút", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Số tiền (VND)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(50000L, 100000L, 500000L, availableBalance).forEach { preset ->
                            if (preset > 0 && preset <= availableBalance) {
                                OutlinedButton(
                                    onClick = { amountInput = preset.toString() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${preset / 1000}k", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showConfirmDialog = true },
                enabled = isValid && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, disabledContainerColor = CyberCardBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = CyberBackground, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "Yêu Cầu Thanh Toán",
                        color = if (isValid) CyberBackground else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = CyberSurface,
            title = { Text("Xác Nhận Yêu Cầu Rút Tiền", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Số tiền rút: ${currencyFormatter.format(parsedAmount)} đ", color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text("Ngân hàng: $bankName", color = Color.White)
                    Text("Số TK: $accountNumber", color = Color.White)
                    Text("Chủ TK: $accountHolder", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trạng thái yêu cầu ban đầu sẽ là PENDING chờ xử lý từ hệ thống.", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        isSubmitting = true
                        viewModel.submitWithdrawalRequest(
                            amount = parsedAmount,
                            bankName = bankName,
                            accountNumber = accountNumber,
                            accountHolder = accountHolder
                        ) {
                            isSubmitting = false
                            amountInput = ""
                        }
                    }
                ) {
                    Text("Xác Nhận", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun CashbackViewModel.repositoryBalanceRealtimeState(): kotlinx.coroutines.flow.Flow<Map<String, Long>> {
    val repo = remember { com.casi.cashback.data.repository.CashbackRepository(com.casi.cashback.data.AppDatabase.getDatabase(getApplication()).appDao()) }
    return repo.getUserBalanceRealtime()
}
