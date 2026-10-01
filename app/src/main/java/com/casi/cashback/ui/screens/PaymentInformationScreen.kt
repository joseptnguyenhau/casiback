package com.casi.cashback.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.ui.theme.*
import com.casi.cashback.viewmodel.CashbackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentInformationScreen(
    viewModel: CashbackViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val userProfile by viewModel.userProfileState.collectAsState()

    var bankName by remember { mutableStateOf(userProfile?.get("bankName") as? String ?: "") }
    var accountNumber by remember { mutableStateOf(userProfile?.get("accountNumber") as? String ?: "") }
    var accountHolder by remember { mutableStateOf(userProfile?.get("accountHolder") as? String ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val uiMessage by viewModel.uiMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông Tin Thanh Toán", color = Color.White) },
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

            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = NeonCyan)
                        Text("Tài Khoản Ngân Hàng Nhận Tiền", color = NeonCyan, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    Text(
                        text = "Vui lòng điền chính xác thông tin tài khoản ngân hàng để hệ thống thực hiện giải ngân cashback tự động và bảo mật.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Tên Ngân Hàng (VD: Vietcombank, Techcombank, MB...)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = accountHolder,
                        onValueChange = { accountHolder = it.uppercase() },
                        label = { Text("Tên Chủ Tài Khoản (IN HOA KHÔNG DẤU)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Số Tài Khoản Ngân Hàng", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Button(
                onClick = {
                    if (bankName.isBlank() || accountNumber.isBlank() || accountHolder.isBlank()) {
                        return@Button
                    }
                    isSaving = true
                    val data = mapOf(
                        "bankName" to bankName,
                        "accountNumber" to accountNumber,
                        "accountHolder" to accountHolder,
                        "paymentMethod" to "Bank Transfer"
                    )
                    viewModel.updateProfile(data) {
                        isSaving = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = CyberBackground, modifier = Modifier.size(24.dp))
                } else {
                    Text("Lưu Thông Tin Thanh Toán", color = CyberBackground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
