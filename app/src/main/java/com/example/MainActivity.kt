package com.casi.cashback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.casi.cashback.ui.screens.MainScreen
import com.casi.cashback.ui.theme.ShopeeCashbackTheme
import com.casi.cashback.viewmodel.CashbackViewModel

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
                val viewModel: CashbackViewModel = viewModel()
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
