package com.casi.cashback.data.repository

import com.casi.cashback.data.FirebaseConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseConfig.firestore

    /**
     * Đăng nhập Firebase Auth bằng Google ID Token.
     */
    fun signInWithGoogle(idToken: String): Task<AuthResult> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential)
    }

    /**
     * Đăng nhập ẩn danh / Demo để test app khi Google Sign-In chưa cấu hình client ID.
     */
    fun signInAsDemo(): Task<AuthResult> {
        return auth.signInAnonymously()
    }

    /**
     * Đồng bộ thông tin người dùng lên collection 'users' trên Firestore nếu chưa tồn tại.
     */
    suspend fun syncUserToFirestore(firebaseUser: FirebaseUser): Result<Unit> {
        return try {
            val uid = firebaseUser.uid
            val userRef = firestore.collection("users").document(uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val userData = hashMapOf(
                    "userId" to uid,
                    "name" to (firebaseUser.displayName ?: "Casi Cyber User"),
                    "email" to (firebaseUser.email ?: ""),
                    "avatarUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
                    "balance_available" to 0.0,
                    "balance_pending" to 0.0,
                    "createdAt" to Timestamp.now()
                )
                userRef.set(userData).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
    }
}
