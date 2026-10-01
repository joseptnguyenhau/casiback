package com.casi.cashback

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class CasiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val apps = FirebaseApp.getApps(this)
            val app = if (apps.isEmpty()) {
                FirebaseApp.initializeApp(this)
            } else {
                apps[0]
            }
            
            if (app != null) {
                val options = app.options
                val projectId = options.projectId ?: "unknown"
                val appId = options.applicationId ?: "unknown"
                val apiKey = options.apiKey ?: ""
                val maskedKey = if (apiKey.length > 8) {
                    "${apiKey.substring(0, 6)}...${apiKey.takeLast(4)}"
                } else {
                    "****"
                }
                
                Log.i("CasiApplication", "[FIREBASE] FirebaseApp initialized successfully")
                Log.i("CasiApplication", "[FIREBASE] Firebase projectId: $projectId")
                Log.i("CasiApplication", "[FIREBASE] Firebase applicationId: $appId")
                Log.i("CasiApplication", "[FIREBASE] API key masked: $maskedKey")
            } else {
                Log.e("CasiApplication", "[FIREBASE] FirebaseApp initialization resulted in null app")
            }
        } catch (e: Exception) {
            Log.e("CasiApplication", "[FIREBASE] Fatal Firebase initialization error", e)
        }
    }
}
