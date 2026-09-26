package com.casi.cashback.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirebaseConfig {
    /**
     * Khởi tạo và cung cấp Firebase Firestore instance chính thức.
     * Cấu hình kết nối thời gian thực và đồng bộ cache ngoại tuyến.
     */
    val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db.firestoreSettings = settings
        db
    }
}
