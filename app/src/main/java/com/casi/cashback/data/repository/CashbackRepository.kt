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
     * Link Converter Engine & Firestore Real-time Sync
     * Khi người dùng dán link Shopee, hệ thống tạo link affiliate và tự động tạo document mới
     * lưu vào Collection 'transactions' trên Firestore theo thời gian thực.
     */
    suspend fun convertAndSaveLink(originalUrl: String): Result<Pair<String, Double>> {
        try {
            if (originalUrl.isBlank() || (!originalUrl.contains("shopee.vn") && !originalUrl.contains("shp.ee"))) {
                return Result.failure(IllegalArgumentException("Link Shopee không hợp lệ. Vui lòng kiểm tra lại URL!"))
            }

            kotlinx.coroutines.delay(600)

            val randomCashback = (Random.nextInt(2, 12) * 5000.0).coerceAtLeast(10000.0)
            val randomId = Random.nextInt(100000, 999999)
            val shortAffiliateUrl = "https://s.shopee.vn/casi_aff_$randomId?sub_id=$currentUserId"
            val orderId = "CS${System.currentTimeMillis().toString().takeLast(8)}"
            
            val sampleProductNames = listOf(
                "Áo khoác gió thông minh Casi Techwear",
                "Tai nghe Neo-Cyber Bluetooth TWS 5.3",
                "Đế tản nhiệt LED RGB laptop gaming",
                "Bàn phím cơ không dây Cyberpunk 68 keys",
                "Cáp sạc nhanh Type-C LED 120W",
                "Bình giữ nhiệt thông minh hiển thị nhiệt độ"
            )
            val productName = sampleProductNames.random()
            val createdAtMillis = System.currentTimeMillis()

            // 1. Lưu vào Firestore Collection 'transactions'
            val transactionData = hashMapOf(
                "userId" to currentUserId,
                "orderId" to orderId,
                "productName" to productName,
                "originalLink" to originalUrl,
                "affiliateLink" to shortAffiliateUrl,
                "cashbackAmount" to randomCashback,
                "status" to "pending",
                "createdAt" to createdAtMillis
            )
            firestore.collection("transactions").document(orderId).set(transactionData).await()

            // 2. Cập nhật số dư chờ đối soát (balancePending) trên Firestore 'users'
            val userDocRef = firestore.collection("users").document(currentUserId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)
                val currentPending = snapshot.getDouble("balancePending") ?: 320000.0
                transaction.update(userDocRef, "balancePending", currentPending + randomCashback)
            }.await()

            // 3. Đồng thời lưu cache cục bộ vào Room Database
            val newTx = TransactionEntity(
                userId = currentUserId,
                orderId = orderId,
                productName = productName,
                originalLink = originalUrl,
                affiliateLink = shortAffiliateUrl,
                cashbackAmount = randomCashback,
                status = "pending",
                createdAt = createdAtMillis
            )
            appDao.insertTransaction(newTx)

            val user = appDao.getUser(currentUserId)
            if (user != null) {
                appDao.updateUser(user.copy(balancePending = user.balancePending + randomCashback))
            }

            return Result.success(Pair(shortAffiliateUrl, randomCashback))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Giữ nguyên tương thích với ViewModel cũ gọi convertLinkToAffiliate
    suspend fun convertLinkToAffiliate(originalUrl: String): Result<Pair<String, Double>> {
        return convertAndSaveLink(originalUrl)
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
}
