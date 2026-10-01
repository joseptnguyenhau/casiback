package com.casi.cashback.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.ui.theme.*
import com.casi.cashback.viewmodel.CashbackViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInformationScreen(
    viewModel: CashbackViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val userProfile by viewModel.userProfileState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    var fullName by remember { mutableStateOf(userProfile?.get("displayName") as? String ?: currentUser?.displayName ?: "") }
    var phone by remember { mutableStateOf(userProfile?.get("phone") as? String ?: currentUser?.phoneNumber ?: "") }
    var birthday by remember { mutableStateOf(userProfile?.get("birthday") as? String ?: "") }
    var address by remember { mutableStateOf(userProfile?.get("address") as? String ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    val email = currentUser?.email ?: (userProfile?.get("email") as? String ?: "Chưa cập nhật email")

    val uiMessage by viewModel.uiMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông Tin Cá Nhân", color = Color.White) },
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
                    Text("Thông tin định danh", color = NeonCyan, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Họ và tên", color = TextSecondary) },
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
                        value = email,
                        onValueChange = {},
                        enabled = false,
                        label = { Text("Email (Không thể thay đổi)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = CyberCardBorder,
                            disabledTextColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Số điện thoại", color = TextSecondary) },
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
                        value = birthday,
                        onValueChange = { birthday = it },
                        label = { Text("Ngày sinh (VD: DD/MM/YYYY)", color = TextSecondary) },
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
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Địa chỉ nhận hàng / thanh toán", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }

            Button(
                onClick = {
                    isSaving = true
                    val data = mapOf(
                        "displayName" to fullName,
                        "phone" to phone,
                        "birthday" to birthday,
                        "address" to address,
                        "email" to email
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
                    Text("Lưu Thay Đổi", color = CyberBackground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
