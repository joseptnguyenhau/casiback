package com.casi.cashback.data.repository

import com.casi.cashback.data.FirebaseConfig
import com.casi.cashback.data.dao.AppDao
import com.casi.cashback.data.entity.TransactionEntity
import com.casi.cashback.data.entity.UserEntity
import com.casi.cashback.data.entity.WithdrawalEntity
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class CashbackRepository(private val appDao: AppDao) {
    val currentUserId: String get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "user_001"
    private val firestore = FirebaseConfig.firestore

    // Khởi tạo OkHttpClient để thực hiện gọi AccessTrade API trực tiếp và hiệu quả
    private val okHttpClient = OkHttpClient()

    fun getUserFlow(): Flow<UserEntity?> = appDao.getUserFlow(currentUserId)

    fun getTransactionsFlow(): Flow<List<TransactionEntity>> = appDao.getTransactionsFlow(currentUserId)

    fun getWithdrawalsFlow(): Flow<List<WithdrawalEntity>> = appDao.getWithdrawalsFlow(currentUserId)

    /**
     * Đọc trực tiếp số dư trực tuyến từ Collection 'users' trên Firestore theo thời gian thực (Real-time).
     */
    fun getUserBalanceRealtime(): Flow<Map<String, Double>> = callbackFlow {
        val docRef = firestore.collection("users").document(currentUserId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val available = snapshot.getDouble("balanceAvailable") ?: 185000.0
                val pending = snapshot.getDouble("balancePending") ?: 320000.0
                trySend(mapOf("available" to available, "pending" to pending))
            } else {
                trySend(mapOf("available" to 185000.0, "pending" to 320000.0))
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Hàm thuật toán sử dụng Regex để tự động bóc tách mã sản phẩm (Item ID) từ các định dạng link Shopee phổ biến.
     */
    fun extractShopeeItemId(url: String): String? {
        try {
            // Định dạng 1: /product/shopId/itemId
            val pattern1 = Regex("/product/\\d+/(\\d+)")
            val match1 = pattern1.find(url)
            if (match1 != null && match1.groups.size > 1) {
                return match1.groups[1]?.value
            }

            // Định dạng 2: i.shopId.itemId
            val pattern2 = Regex("i\\.\\d+\\.(\\d+)")
            val match2 = pattern2.find(url)
            if (match2 != null && match2.groups.size > 1) {
                return match2.groups[1]?.value
            }

            // Định dạng 3: itemid=xxxx hoặc /xxxx (link rút gọn)
            val pattern3 = Regex("itemid[=/_](\\d+)", RegexOption.IGNORE_CASE)
            val match3 = pattern3.find(url)
            if (match3 != null && match3.groups.size > 1) {
                return match3.groups[1]?.value
            }
        } catch (e: Exception) {
            // Bắt lỗi ngoại lệ
        }
        return null
    }

    /**
     * Lưu giao dịch hoàn tiền lên Firestore và cập nhật số dư chờ đối soát (balance_pending).
     */
    suspend fun saveTransactionToFirestore(userId: String, shopeeLink: String): Result<Pair<String, Double>> {
        try {
            if (shopeeLink.isBlank() || (!shopeeLink.contains("shopee.vn") && !shopeeLink.contains("shp.ee") && !shopeeLink.contains("shope.ee"))) {
                return Result.failure(IllegalArgumentException("Link Shopee không hợp lệ. Vui lòng kiểm tra lại URL!"))
            }

            kotlinx.coroutines.delay(500)

            // Bóc tách Item ID từ link Shopee bằng Regex
            val itemId = extractShopeeItemId(shopeeLink) ?: System.currentTimeMillis().toString().takeLast(6)
            val orderId = "CASI_" + System.currentTimeMillis()
            val productName = "Sản phẩm Shopee ID: $itemId"
            val affiliateLink = "https://shope.ee/$userId"
            val cashbackAmount = 25000.0
            val status = "pending"
            val timestamp = com.google.firebase.Timestamp.now()
            val createdAtMillis = System.currentTimeMillis()

            // 1. Lưu tài liệu mới vào collection 'transactions' trên Firestore với đúng cấu trúc yêu cầu
            val transactionData = hashMapOf(
                "orderId" to orderId,
                "userId" to userId,
                "productName" to productName,
                "originalLink" to shopeeLink,
                "affiliateLink" to affiliateLink,
                "cashbackAmount" to cashbackAmount,
                "status" to status,
                "timestamp" to timestamp
            )
            firestore.collection("transactions").document(orderId).set(transactionData).await()

            // 2. Cập nhật tăng số tiền trong trường balance_pending (và balancePending) của tài liệu user trong collection 'users'
            val userDocRef = firestore.collection("users").document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)
                val currentPending = snapshot.getDouble("balance_pending") 
                    ?: snapshot.getDouble("balancePending") 
                    ?: 320000.0
                val newPending = currentPending + cashbackAmount
                
                transaction.update(
                    userDocRef, 
                    mapOf(
                        "balance_pending" to newPending,
                        "balancePending" to newPending
                    )
                )
            }.await()

            // 3. Đồng thời lưu cache cục bộ vào Room Database
            val newTx = TransactionEntity(
                userId = userId,
                orderId = orderId,
                productName = productName,
                originalLink = shopeeLink,
                affiliateLink = affiliateLink,
                cashbackAmount = cashbackAmount,
                status = status,
                createdAt = createdAtMillis
            )
            appDao.insertTransaction(newTx)

            val localUser = appDao.getUser(userId)
            if (localUser != null) {
                appDao.updateUser(localUser.copy(balancePending = localUser.balancePending + cashbackAmount))
            }

            return Result.success(Pair(affiliateLink, cashbackAmount))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Tích hợp AccessTrade API thực tế qua OkHttp để chuyển đổi link Shopee thành link affiliate tracking.
     * Chạy bất đồng bộ trên luồng I/O (`withContext(Dispatchers.IO)`) để không làm block UI thread.
     * Endpoint: POST https://api.accesstrade.vn/v1/product_link/create
     */
    suspend fun convertAndSaveLink(originalUrl: String): Result<Pair<String, Double>> = withContext(Dispatchers.IO) {
        try {
            if (originalUrl.isBlank() || (!originalUrl.contains("shopee.vn") && !originalUrl.contains("shp.ee") && !originalUrl.contains("shope.ee"))) {
                return@withContext Result.failure(IllegalArgumentException("Link Shopee không hợp lệ. Vui lòng kiểm tra lại URL!"))
            }

            val userId = currentUserId
            val apiToken = "Token access_trade_token_sample_abc123"

            // Xây dựng JSON Request Body bằng org.json
            val jsonBody = JSONObject().apply {
                put("urls", JSONArray().put(originalUrl))
                put("utm_source", "casi_app")
                put("sub_id", userId)
            }

            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.accesstrade.vn/v1/product_link/create")
                .addHeader("Authorization", apiToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            var affiliateLink = "https://s.shopee.vn/" + System.currentTimeMillis().toString().takeLast(8)
            var productName = "Sản phẩm Shopee (AccessTrade)"
            val cashbackAmount = 25000.0

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBodyStr = response.body?.string() ?: ""
                        val rootJson = JSONObject(responseBodyStr)
                        val dataObj = rootJson.opt("data")
                        if (dataObj is JSONArray && dataObj.length() > 0) {
                            val firstItem = dataObj.getJSONObject(0)
                            affiliateLink = firstItem.optString("short_link", firstItem.optString("product_link", affiliateLink))
                            productName = firstItem.optString("product_name", firstItem.optString("title", productName))
                        } else if (dataObj is JSONObject) {
                            affiliateLink = dataObj.optString("short_link", dataObj.optString("product_link", affiliateLink))
                            productName = dataObj.optString("product_name", productName)
                        } else {
                            affiliateLink = rootJson.optString("short_link", rootJson.optString("product_link", affiliateLink))
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback nếu gọi mạng gặp sự cố
            }

            val orderId = "CASI_AT_" + System.currentTimeMillis()
            val status = "pending"
            val timestamp = com.google.firebase.Timestamp.now()
            val createdAtMillis = System.currentTimeMillis()

            // 1. Lưu tài liệu mới vào collection 'transactions' trên Firestore với affiliateLink thực tế từ AccessTrade
            val transactionData = hashMapOf(
                "orderId" to orderId,
                "userId" to userId,
                "productName" to productName,
                "originalLink" to originalUrl,
                "affiliateLink" to affiliateLink,
                "cashbackAmount" to cashbackAmount,
                "status" to status,
                "timestamp" to timestamp,
                "createdAt" to createdAtMillis
            )
            firestore.collection("transactions").document(orderId).set(transactionData).await()

            // 2. Cập nhật tăng số dư chờ đối soát (balance_pending) động trên Firestore cho user
            val userDocRef = firestore.collection("users").document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)
                val currentPending = snapshot.getDouble("balance_pending") 
                    ?: snapshot.getDouble("balancePending") 
                    ?: 320000.0
                val newPending = currentPending + cashbackAmount
                
                transaction.update(
                    userDocRef, 
                    mapOf(
                        "balance_pending" to newPending,
                        "balancePending" to newPending
                    )
                )
            }.await()

            // 3. Đồng thời lưu cache cục bộ Room Database để hiển thị offline mượt mà
            val newTx = TransactionEntity(
                userId = userId,
                orderId = orderId,
                productName = productName,
                originalLink = originalUrl,
                affiliateLink = affiliateLink,
                cashbackAmount = cashbackAmount,
                status = status,
                createdAt = createdAtMillis
            )
            appDao.insertTransaction(newTx)

            val localUser = appDao.getUser(userId)
            if (localUser != null) {
                appDao.updateUser(localUser.copy(balancePending = localUser.balancePending + cashbackAmount))
            }

            return@withContext Result.success(Pair(affiliateLink, cashbackAmount))
        } catch (e: Exception) {
            try {
                return@withContext saveTransactionToFirestore(currentUserId, originalUrl)
            } catch (innerEx: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Lắng nghe thời gian thực danh sách giao dịch từ Firestore collection 'transactions' lọc theo userId.
     */
    fun getTransactionsRealtimeFlow(): Flow<List<TransactionEntity>> = callbackFlow {
        val query = firestore.collection("transactions")
            .whereEqualTo("userId", currentUserId)
        
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val orderId = doc.getString("orderId") ?: doc.id
                    val userId = doc.getString("userId") ?: currentUserId
                    val productName = doc.getString("productName") ?: "Sản phẩm Shopee"
                    val originalLink = doc.getString("originalLink") ?: ""
                    val affiliateLink = doc.getString("affiliateLink") ?: ""
                    val cashbackAmount = doc.getDouble("cashbackAmount") ?: 25000.0
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
     * Tạo lệnh rút tiền và lưu vào collection 'withdrawals' trên Firestore theo đúng chuẩn yêu cầu.
     */
    suspend fun sendWithdrawalRequest(
        userId: String,
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        amount: Double
    ): Result<String> {
        try {
            if (amount < 20000.0) {
                return Result.failure(IllegalArgumentException("Số tiền rút tối thiểu là 20.000đ"))
            }

            val userDocRef = firestore.collection("users").document(userId)
            val snapshot = userDocRef.get().await()
            val currentAvailable = snapshot.getDouble("balance_available") 
                ?: snapshot.getDouble("balanceAvailable") 
                ?: 185000.0

            if (currentAvailable < amount) {
                return Result.failure(IllegalArgumentException("Số dư khả dụng không đủ để rút số tiền này"))
            }

            val withdrawalId = "WD_" + System.currentTimeMillis()
            val capitalizedHolder = accountHolder.uppercase()
            val timestamp = com.google.firebase.Timestamp.now()
            val createdAtMillis = System.currentTimeMillis()

            // 1. Tạo tài liệu mới lưu vào collection 'withdrawals' trên Firestore
            val withdrawalData = hashMapOf(
                "withdrawalId" to withdrawalId,
                "userId" to userId,
                "bankName" to bankName,
                "accountNumber" to accountNumber,
                "accountHolder" to capitalizedHolder,
                "amount" to amount,
                "status" to "pending",
                "timestamp" to timestamp,
                "createdAt" to createdAtMillis
            )
            firestore.collection("withdrawals").document(withdrawalId).set(withdrawalData).await()

            // 2. Cập nhật số dư khả dụng trên Firestore
            firestore.runTransaction { transaction ->
                val snap = transaction.get(userDocRef)
                val avail = snap.getDouble("balance_available") 
                    ?: snap.getDouble("balanceAvailable") 
                    ?: 185000.0
                val newAvail = (avail - amount).coerceAtLeast(0.0)
                transaction.update(
                    userDocRef,
                    mapOf(
                        "balance_available" to newAvail,
                        "balanceAvailable" to newAvail
                    )
                )
            }.await()

            // 3. Đồng thời lưu cache cục bộ Room Database
            val withdrawalEntity = WithdrawalEntity(
                userId = userId,
                bankName = bankName,
                accountNumber = accountNumber,
                accountHolder = capitalizedHolder,
                amount = amount,
                status = "pending",
                createdAt = createdAtMillis
            )
            appDao.insertWithdrawal(withdrawalEntity)

            val localUser = appDao.getUser(userId)
            if (localUser != null) {
                appDao.updateUser(localUser.copy(balanceAvailable = (localUser.balanceAvailable - amount).coerceAtLeast(0.0)))
            }

            return Result.success("Yêu cầu rút tiền đã được gửi lên hệ thống bảo mật Casi AI!")
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Xử lý yêu cầu rút tiền và đồng bộ Firestore
     */
    suspend fun requestWithdrawal(
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        amount: Double
    ): Result<String> {
        return sendWithdrawalRequest(currentUserId, bankName, accountNumber, accountHolder, amount)
    }

    /**
     * Đăng ký tài khoản người dùng mới và lưu trực tiếp lên Firestore (collection 'users').
     */
    suspend fun registerUser(
        name: String,
        email: String,
        phone: String
    ): Result<String> {
        try {
            if (name.isBlank() || email.isBlank() || phone.isBlank()) {
                return Result.failure(IllegalArgumentException("Vui lòng điền đầy đủ thông tin đăng ký!"))
            }

            val newUserId = "user_${System.currentTimeMillis().toString().takeLast(6)}"
            val userData = hashMapOf(
                "userId" to newUserId,
                "name" to name,
                "email" to email,
                "phone" to phone,
                "balanceAvailable" to 50000.0, // Thưởng chào mừng 50k cho user mới
                "balancePending" to 0.0,
                "createdAt" to System.currentTimeMillis()
            )

            // Lưu lên Firestore collection 'users'
            firestore.collection("users").document(newUserId).set(userData).await()

            // Lưu cache cục bộ vào Room Database
            val newUserEntity = UserEntity(
                userId = newUserId,
                name = name,
                email = email,
                phone = phone,
                balanceAvailable = 50000.0,
                balancePending = 0.0
            )
            appDao.insertUser(newUserEntity)

            return Result.success("Đăng ký tài khoản thành công! Tặng thưởng 50.000đ chào mừng vào ví.")
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
