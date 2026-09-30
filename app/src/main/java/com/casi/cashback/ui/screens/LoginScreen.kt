package com.casi.cashback.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casi.cashback.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

val CyberDark = Color(0xFF0D0E15)
val CyberSurface = Color(0xFF16192B)
val NeonCyan = Color(0xFF00F0FF)
val NeonPink = Color(0xFFFF007F)
val NeonGreen = Color(0xFF00FF66)

@Composable
fun LoginScreen(
    onSignInSuccess: () -> Unit,
    onSignInError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val authRepository = remember { AuthRepository() }

    // Cấu hình Google Sign-In Client
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("108296652405-apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val handleLoginSuccess: (com.google.firebase.auth.FirebaseUser?) -> Unit = { user ->
        if (user != null) {
            coroutineScope.launch {
                authRepository.syncUserToFirestore(user)
                isLoading = false
                onSignInSuccess()
            }
        } else {
            isLoading = false
            errorMessage = "Không thể lấy thông tin người dùng."
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    isLoading = true
                    authRepository.signInWithGoogle(idToken)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                handleLoginSuccess(authRepository.getCurrentUser())
                            } else {
                                isLoading = false
                                errorMessage = (authTask.exception?.localizedMessage ?: "Đăng nhập Firebase thất bại.") + "\n💡 Mẹo: Nhấn 'TRẢI NGHIỆM NGAY (DEMO LOGIN)' bên dưới để vào app ngay lập tức."
                            }
                        }
                } else {
                    isLoading = false
                    errorMessage = "Lỗi xác thực Google ID Token.\n💡 Mẹo: Nhấn 'TRẢI NGHIỆM NGAY (DEMO LOGIN)' bên dưới để vào app ngay lập tức."
                }
            } catch (e: ApiException) {
                isLoading = false
                errorMessage = "Google Sign-In lỗi (${e.statusCode}): ${e.message}\n💡 Mẹo: Nhấn 'TRẢI NGHIỆM NGAY (DEMO LOGIN)' bên dưới để vào app ngay lập tức."
            }
        } else {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Tiêu đề phong cách Cyberpunk
            Text(
                text = "CASI AI",
                color = NeonCyan,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "NEURAL CASHBACK PROTOCOL",
                color = NeonPink,
                    fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CHÀO MỪNG TRỞ LẠI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Đăng nhập để kết nối ví cashback, tự động tra cứu đơn hàng và rút tiền ngân hàng bảo mật.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = NeonCyan)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    val signInIntent = googleSignInClient.signInIntent
                                    launcher.launch(signInIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .border(1.5.dp, NeonCyan, RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = "ĐĂNG NHẬP BẰNG GOOGLE 🌐",
                                    color = NeonCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    authRepository.signInAsDemo()
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                handleLoginSuccess(authRepository.getCurrentUser())
                                            } else {
                                                // Fallback direct success so user can always enter app even if anonymous auth is disabled
                                                isLoading = false
                                                onSignInSuccess()
                                            }
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = "TRẢI NGHIỆM NGAY (DEMO LOGIN) 🚀",
                                    color = CyberDark,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = msg,
                            color = NeonPink,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
