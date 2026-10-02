package com.casi.cashback.data.repository

import com.casi.cashback.data.FirebaseConfig
import com.casi.cashback.data.dao.AppDao
import com.casi.cashback.data.entity.TransactionEntity
import com.casi.cashback.data.entity.UserEntity
import com.casi.cashback.data.entity.WithdrawalEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CashbackRepository(private val appDao: AppDao) {
    val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

    private val firestore = FirebaseConfig.firestore
    private val client = OkHttpClient()

    // Base URL của trusted backend (Render / Railway / Production server)
    private val backendBaseUrl = "https://casi-webhook.onrender.com"

    fun getUserFlow(): Flow<UserEntity?> = appDao.getUserFlow(currentUserId)

    fun getTransactionsFlow(): Flow<List<TransactionEntity>> = appDao.getTransactionsFlow(currentUserId)

    fun getWithdrawalsFlow(): Flow<List<WithdrawalEntity>> = appDao.getWithdrawalsFlow(currentUserId)

    /**
     * Đọc trực tiếp số dư trực tuyến từ Collection 'users' trên Firestore theo thời gian thực (Real-time).
     * Sử dụng Long cho số tiền (VND integer), không dùng Double và không dùng giá trị giả.
     */
    fun getUserBalanceRealtime(): Flow<Map<String, Long>> = callbackFlow {
        val uid = try { currentUserId } catch (e: Exception) { null }
        if (uid == null) {
            trySend(mapOf("available" to 0L, "pending" to 0L))
            awaitClose {}
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val available = snapshot.getLong("balanceAvailable") 
                    ?: snapshot.getLong("balance_available") 
                    ?: 0L
                val pending = snapshot.getLong("balancePending") 
                    ?: snapshot.getLong("balance_pending") 
                    ?: 0L
                trySend(mapOf("available" to available, "pending" to pending))
            } else {
                trySend(mapOf("available" to 0L, "pending" to 0L))
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Gọi trusted backend endpoint '/api/create-affiliate-link' xác thực bằng Firebase ID Token.
     * Android KHÔNG gọi trực tiếp AccessTrade API và KHÔNG chứa AccessTrade token.
     */
    suspend fun convertAndSaveLink(originalUrl: String): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        try {
            if (originalUrl.isBlank() || (!originalUrl.contains("shopee.vn") && !originalUrl.contains("shp.ee") && !originalUrl.contains("shope.ee"))) {
                return@withContext Result.failure(IllegalArgumentException("Link Shopee không hợp lệ. Vui lòng kiểm tra lại URL!"))
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: return@withContext Result.failure(IllegalStateException("Vui lòng đăng nhập lại tài khoản"))
            val idToken = currentUser.getIdToken(true).await().token
                ?: return@withContext Result.failure(IllegalStateException("Không thể xác thực phiên đăng nhập"))

            val jsonBody = JSONObject().apply {
                put("originalUrl", originalUrl)
            }
            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$backendBaseUrl/api/create-affiliate-link")
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorStr = response.body?.string() ?: "Lỗi từ server"
                    return@withContext Result.failure(Exception("Lỗi: $errorStr"))
                }
                val responseBodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(responseBodyStr)
                val affiliateLink = json.optString("affiliateLink", "")
                val estimatedCashback = json.optLong("estimatedCashback", 25000L)

                if (affiliateLink.isBlank()) {
                    return@withContext Result.failure(Exception("Không nhận được link affiliate từ server"))
                }

                return@withContext Result.success(Pair(affiliateLink, estimatedCashback))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Lắng nghe thời gian thực danh sách giao dịch từ Firestore collection 'transactions' lọc theo userId.
     */
    fun getTransactionsRealtimeFlow(): Flow<List<TransactionEntity>> = callbackFlow {
        val uid = try { currentUserId } catch (e: Exception) { null }
        if (uid == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val query = firestore.collection("transactions")
            .whereEqualTo("userId", uid)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val orderId = doc.getString("orderId") ?: doc.id
                    val userId = doc.getString("userId") ?: uid
                    val productName = doc.getString("productName") ?: "Sản phẩm Shopee"
                    val originalLink = doc.getString("originalLink") ?: ""
                    val affiliateLink = doc.getString("affiliateLink") ?: ""
                    val cashbackAmount = doc.getLong("cashbackAmount") ?: 0L
                    val status = doc.getString("status") ?: "pending"
                    val timestamp = doc.getTimestamp("timestamp")
                    val createdAt = timestamp?.toDate()?.time ?: doc.getLong("createdAt") ?: System.currentTimeMillis()

                    TransactionEntity(
                        userId = userId,
                        orderId = orderId,
                        productName = productName,
                        originalLink = originalLink,
                        affiliateLink = affiliateLink,
                        cashbackAmount = cashbackAmount,
                        status = status,
                        createdAt = createdAt
                    )
                }.sortedByDescending { it.createdAt }
                trySend(list)
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Gọi trusted backend endpoint '/api/request-withdrawal' xác thực bằng Firebase ID Token.
     */
    suspend fun requestWithdrawal(
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        amount: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (amount < 20000L) {
                return@withContext Result.failure(IllegalArgumentException("Số tiền rút tối thiểu là 20.000đ"))
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
                ?: return@withContext Result.failure(IllegalStateException("Vui lòng đăng nhập lại tài khoản"))
            val idToken = currentUser.getIdToken(true).await().token
                ?: return@withContext Result.failure(IllegalStateException("Không thể xác thực phiên đăng nhập"))

            val jsonBody = JSONObject().apply {
                put("bankName", bankName)
                put("accountNumber", accountNumber)
                put("accountHolder", accountHolder.uppercase())
                put("amount", amount)
            }
            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$backendBaseUrl/api/request-withdrawal")
                .addHeader("Authorization", "Bearer $idToken")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorStr = response.body?.string() ?: "Lỗi yêu cầu rút tiền"
                    return@withContext Result.failure(Exception("Lỗi: $errorStr"))
                }
                val responseBodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(responseBodyStr)
                val message = json.optString("message", "Yêu cầu rút tiền đã được gửi thành công!")
                return@withContext Result.success(message)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    fun getUserProfileFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val uid = try { currentUserId } catch (e: Exception) { null }
        if (uid == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                trySend(snapshot.data)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateUserProfile(data: Map<String, Any>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = currentUserId
            val userRef = firestore.collection("users").document(uid)
            val updateMap = data.toMutableMap()
            updateMap["updatedAt"] = com.google.firebase.Timestamp.now()
            userRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getWithdrawalRequestsFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        val uid = try { currentUserId } catch (e: Exception) { null }
        if (uid == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val query = firestore.collection("withdrawals")
            .whereEqualTo("userId", uid)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val requests = snapshot.documents.mapNotNull { doc ->
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    map["requestId"] = doc.id
                    map
                }.sortedByDescending { doc ->
                    val ts = doc["createdAt"] as? com.google.firebase.Timestamp
                    ts?.toDate()?.time ?: 0L
                }
                trySend(requests)
            } else {
                trySend(emptyList())
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun createWithdrawalRequest(
        amount: Long,
        bankName: String,
        accountNumber: String,
        accountHolder: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            return@withContext requestWithdrawal(bankName, accountNumber, accountHolder, amount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
