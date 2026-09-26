package com.casi.cashback

import android.app.Application
import com.google.firebase.FirebaseApp

class CasiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
