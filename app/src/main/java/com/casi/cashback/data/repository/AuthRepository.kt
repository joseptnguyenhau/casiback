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
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val firestore by lazy {
        FirebaseConfig.firestore
    }
    private val TAG = "AuthRepository"

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

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
        Log.i(TAG, "[DEMO] Starting anonymous login")
        return auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                Log.i(TAG, "[DEMO] Anonymous login SUCCESS")
                Log.i(TAG, "[DEMO] UID: ${user?.uid}")
                Log.i(TAG, "[DEMO] isAnonymous: ${user?.isAnonymous}")
                Log.i(TAG, "[DEMO] providerData: ${user?.providerData}")
            } else {
                val error = task.exception
                Log.e(TAG, "[DEMO] Anonymous login FAILED")
                Log.e(TAG, "[DEMO] code: ${error?.javaClass?.simpleName}")
                Log.e(TAG, "[DEMO] message: ${error?.message}")
                Log.e(TAG, "[DEMO] full error: $error")
            }
        }
    }

    suspend fun syncUserToFirestore(firebaseUser: FirebaseUser): Result<Unit> {
        return try {
            val uid = firebaseUser.uid
            Log.i(TAG, "[FIRESTORE] User sync started for UID = $uid")
            val userRef = firestore.collection("users").document(uid)

            val providerName = if (firebaseUser.isAnonymous) "anonymous" else "google"
            val userData = hashMapOf<String, Any>(
                "uid" to uid,
                "email" to (firebaseUser.email ?: "anonymous@casi.ai"),
                "displayName" to (firebaseUser.displayName ?: "Casi Cyber User"),
                "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
                "provider" to providerName,
                "lastLogin" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )

            userRef.set(userData, SetOptions.merge()).await()
            Log.i(TAG, "[FIRESTORE] User sync SUCCESS for UID = $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[FIRESTORE] User sync FAILED", e)
            Result.failure(e)
        }
    }
}
