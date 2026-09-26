package com.casi.cashback

import android.app.Application
import com.google.firebase.FirebaseApp

class CasiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            // Ignore initialization exceptions if already initialized or resource missing
        }
    }
}
