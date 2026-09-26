package com.casi.cashback.data

import com.google.firebase.firestore.FirebaseFirestore

object FirebaseConfig {
    /**
     * Khởi tạo và cung cấp Firebase Firestore instance chính thức.
     * Cấu hình kết nối thời gian thực và đồng bộ cache ngoại tuyến.
     */
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }
}
