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
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
                android.util.Log.i("MainActivity", "[FIREBASE] FirebaseApp initialized successfully")
            } else {
                android.util.Log.i("MainActivity", "[FIREBASE] FirebaseApp already initialized")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "[FIREBASE] FirebaseApp initialization failed", e)
        }
        enableEdgeToEdge()
        setContent {
            ShopeeCashbackTheme {
                var currentUser by remember {
                    mutableStateOf(
                        try {
                            if (com.google.firebase.FirebaseApp.getApps(this@MainActivity).isNotEmpty()) {
                                FirebaseAuth.getInstance().currentUser
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "[FIREBASE] Failed to get current user", e)
                            null
                        }
                    )
                }

                DisposableEffect(Unit) {
                    val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        currentUser = firebaseAuth.currentUser
                    }
                    val auth = FirebaseAuth.getInstance()
                    auth.addAuthStateListener(authListener)
                    onDispose {
                        auth.removeAuthStateListener(authListener)
                    }
                }

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
