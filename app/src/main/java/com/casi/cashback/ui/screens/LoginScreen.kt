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

    // Cấu hình Google Sign-In Client sử dụng chính xác Web application Client ID
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("108296652405-33o16ra8m4d1nl2tcv0lbot14iaiarf4.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val handleLoginSuccess: (com.google.firebase.auth.FirebaseUser?) -> Unit = { user ->
        if (user != null) {
            coroutineScope.launch {
                try {
                    authRepository.syncUserToFirestore(user)
                } catch (e: Exception) {
                    android.util.Log.e("LoginScreen", "[FIRESTORE] User sync error ignored for navigation: ${e.message}")
                }
                isLoading = false
                android.util.Log.i("LoginScreen", "[NAVIGATION] Opening main application")
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
                    android.util.Log.i("LoginScreen", "[AUTH] Google ID token received")
                    authRepository.signInWithGoogle(idToken)
                        .addOnCompleteListener { authTask ->
                            isLoading = false
                            if (authTask.isSuccessful) {
                                android.util.Log.i("LoginScreen", "[AUTH] Firebase signInWithCredential SUCCESS")
                                handleLoginSuccess(authRepository.getCurrentUser())
                            } else {
                                val errorMsg = authTask.exception?.localizedMessage ?: "Đăng nhập Firebase thất bại."
                                android.util.Log.e("LoginScreen", "[AUTH] Firebase signInWithCredential FAILED: $errorMsg", authTask.exception)
                                errorMessage = "Đăng nhập Google thất bại: $errorMsg"
                            }
                        }
                } else {
                    isLoading = false
                    errorMessage = "Không thể lấy Google ID Token (idToken is null). Vui lòng kiểm tra lại cấu hình SHA-1 và OAuth client."
                }
            } catch (e: ApiException) {
                isLoading = false
                errorMessage = "Google Sign-In lỗi (${e.statusCode}): ${e.message}"
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
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                val user = authRepository.getCurrentUser()
                                                if (user != null) {
                                                    android.util.Log.i("LoginScreen", "[DEMO] Anonymous sign-in success, handling user sync and navigation")
                                                    handleLoginSuccess(user)
                                                } else {
                                                    errorMessage = "Không tìm thấy user ẩn danh sau khi đăng nhập."
                                                }
                                            } else {
                                                val error = task.exception
                                                android.util.Log.e("LoginScreen", "[DEMO] Anonymous sign-in failed: ${error?.message}", error)
                                                errorMessage = "Đăng nhập Demo thất bại: ${error?.localizedMessage ?: "Vui lòng bật Anonymous Authentication trong Firebase Console."}"
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
