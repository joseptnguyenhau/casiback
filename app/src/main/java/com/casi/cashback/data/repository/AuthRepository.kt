package com.casi.cashback.data.repository

import android.util.Log
import com.casi.cashback.data.FirebaseConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseConfig.firestore
    private val TAG = "AuthRepository"

    fun signInWithGoogle(idToken: String): Task<AuthResult> {
        Log.i(TAG, "[AUTH] Firebase signInWithCredential started")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                Log.i(TAG, "[AUTH] Firebase signInWithCredential SUCCESS")
                Log.i(TAG, "[AUTH] Firebase UID = $uid")
            } else {
                Log.e(TAG, "[AUTH] Firebase signInWithCredential FAILED", task.exception)
            }
        }
    }

    fun signInAsDemo(): Task<AuthResult> {
        Log.i(TAG, "[AUTH] Firebase signInAnonymously (Demo) started")
        return auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                Log.i(TAG, "[AUTH] Firebase signInAnonymously SUCCESS")
                Log.i(TAG, "[AUTH] Firebase UID = $uid")
            } else {
                Log.e(TAG, "[AUTH] Firebase signInAnonymously FAILED", task.exception)
            }
        }
    }

    suspend fun syncUserToFirestore(firebaseUser: FirebaseUser): Result<Unit> {
        return try {
            val uid = firebaseUser.uid
            Log.i(TAG, "[FIRESTORE] User sync started for UID = $uid")
            val userRef = firestore.collection("users").document(uid)

            val userData = hashMapOf<String, Any>(
                "userId" to uid,
                "uid" to uid,
                "name" to (firebaseUser.displayName ?: "Casi Cyber User"),
                "displayName" to (firebaseUser.displayName ?: "Casi Cyber User"),
                "email" to (firebaseUser.email ?: ""),
                "photoURL" to (firebaseUser.photoUrl?.toString() ?: ""),
                "avatarUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
                "provider" to "google",
                "updatedAt" to Timestamp.now(),
                "lastLoginAt" to Timestamp.now()
            )

            val snapshot = userRef.get().await()
            if (!snapshot.exists()) {
                userData["createdAt"] = Timestamp.now()
                userData["balance_available"] = 0.0
                userData["balance_pending"] = 0.0
            }

            userRef.set(userData, SetOptions.merge()).await()
            Log.i(TAG, "[FIRESTORE] User sync SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[FIRESTORE] User sync FAILED", e)
            Result.failure(e)
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
    }
}
