package com.casi.cashback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.casi.cashback.ui.screens.LoginScreen
import com.casi.cashback.ui.screens.MainScreen
import com.casi.cashback.ui.theme.ShopeeCashbackTheme
import com.casi.cashback.viewmodel.CashbackViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Ignored if already initialized
        }
        enableEdgeToEdge()
        setContent {
            ShopeeCashbackTheme {
                var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

                if (currentUser == null) {
                    LoginScreen(
                        onSignInSuccess = {
                            currentUser = FirebaseAuth.getInstance().currentUser
                        },
                        onSignInError = { _ -> }
                    )
                } else {
                    val viewModel: CashbackViewModel = viewModel()
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
