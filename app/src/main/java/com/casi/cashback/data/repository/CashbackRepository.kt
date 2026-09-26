package com.casi.cashback.data.repository

import com.casi.cashback.data.FirebaseConfig
import com.casi.cashback.data.dao.AppDao
import com.casi.cashback.data.entity.TransactionEntity
import com.casi.cashback.data.entity.UserEntity
import com.casi.cashback.data.entity.WithdrawalEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class CashbackRepository(private val appDao: AppDao) {
    val currentUserId: String = "user_001"
    private val firestore = FirebaseConfig.firestore

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
     * Link Converter Engine & Firestore Real-time Sync
     * Khi người dùng dán link Shopee, hệ thống gọi saveTransactionToFirestore.
     */
    suspend fun convertAndSaveLink(originalUrl: String): Result<Pair<String, Double>> {
        return saveTransactionToFirestore(currentUserId, originalUrl)
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
     * Xử lý yêu cầu rút tiền và đồng bộ Firestore
     */
    suspend fun requestWithdrawal(
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        amount: Double
    ): Result<String> {
        if (amount < 20000.0) {
            return Result.failure(IllegalArgumentException("Số tiền rút tối thiểu là 20.000đ"))
        }

        val user = appDao.getUser(currentUserId)
            ?: return Result.failure(IllegalStateException("Không tìm thấy thông tin người dùng"))

        if (user.balanceAvailable < amount) {
            return Result.failure(IllegalArgumentException("Số dư khả dụng không đủ để rút số tiền này"))
        }

        // Cập nhật Room local
        appDao.updateUser(user.copy(balanceAvailable = user.balanceAvailable - amount))

        val withdrawalId = "WD${System.currentTimeMillis().toString().takeLast(8)}"
        val createdAtMillis = System.currentTimeMillis()

        val withdrawal = WithdrawalEntity(
            userId = currentUserId,
            bankName = bankName,
            accountNumber = accountNumber,
            accountHolder = accountHolder,
            amount = amount,
            status = "pending",
            createdAt = createdAtMillis
        )
        appDao.insertWithdrawal(withdrawal)

        // Đồng bộ Firestore
        try {
            val withdrawalData = hashMapOf(
                "userId" to currentUserId,
                "withdrawalId" to withdrawalId,
                "bankName" to bankName,
                "accountNumber" to accountNumber,
                "accountHolder" to accountHolder,
                "amount" to amount,
                "status" to "pending",
                "createdAt" to createdAtMillis
            )
            firestore.collection("withdrawals").document(withdrawalId).set(withdrawalData).await()

            val userDocRef = firestore.collection("users").document(currentUserId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)
                val currentAvailable = snapshot.getDouble("balanceAvailable") ?: 185000.0
                transaction.update(userDocRef, "balanceAvailable", (currentAvailable - amount).coerceAtLeast(0.0))
            }.await()
        } catch (e: Exception) {
            // Ignore offline sync errors gracefully
        }

        return Result.success("Yêu cầu rút tiền đã được gửi lên hệ thống bảo mật Casi AI!")
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
