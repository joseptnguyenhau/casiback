package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.entity.TransactionEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.WithdrawalEntity
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class CashbackRepository(private val appDao: AppDao) {
    val currentUserId: String = "user_001"

    fun getUserFlow(): Flow<UserEntity?> = appDao.getUserFlow(currentUserId)

    fun getTransactionsFlow(): Flow<List<TransactionEntity>> = appDao.getTransactionsFlow(currentUserId)

    fun getWithdrawalsFlow(): Flow<List<WithdrawalEntity>> = appDao.getWithdrawalsFlow(currentUserId)

    /**
     * Link Converter Engine (Mô phỏng Shopee Affiliate API / AccessTrade API)
     * Nhận vào link gốc (originalUrl) và tạo link affiliate rút gọn có đính kèm SubID = userId.
     */
    suspend fun convertLinkToAffiliate(originalUrl: String): Result<Pair<String, Double>> {
        try {
            // Kiểm tra link cơ bản
            if (originalUrl.isBlank() || (!originalUrl.contains("shopee.vn") && !originalUrl.contains("shp.ee"))) {
                return Result.failure(IllegalArgumentException("Link Shopee không hợp lệ. Vui lòng kiểm tra lại URL!"))
            }

            // Giả lập độ trễ mạng gọi API Shopee Affiliate
            kotlinx.coroutines.delay(800)

            // Tạo mã đơn ngẫu nhiên và tính toán số tiền hoàn dự kiến (ví dụ: từ 5% đến 15 giá trị hoặc ngẫu nhiên từ 10k đến 150k)
            val randomCashback = (Random.nextInt(2, 12) * 5000.0).coerceAtLeast(10000.0)
            val randomId = Random.nextInt(100000, 999999)
            val shortAffiliateUrl = "https://s.shopee.vn/vn_aff_${randomId}?sub_id=$currentUserId"
            val orderId = "SPVN${System.currentTimeMillis().toString().takeLast(8)}"
            val sampleProductNames = listOf(
                "Áo thun cotton unisex form rộng thời trang",
                "Son dưỡng môi dâu tây thiên nhiên",
                "Giáp thể thao chạy bộ thoáng khí",
                "Đế tản nhiệt laptop nhôm cao cấp",
                "Cáp sạc nhanh Type-C bọc dù 100W",
                "Bình giữ nhiệt inox 316 cao cấp 500ml"
            )
            val productName = sampleProductNames.random()

            // Tự động ghi nhận một giao dịch mới ở trạng thái "pending" (Chờ đối soát)
            val newTx = TransactionEntity(
                userId = currentUserId,
                orderId = orderId,
                productName = productName,
                originalLink = originalUrl,
                affiliateLink = shortAffiliateUrl,
                cashbackAmount = randomCashback,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )
            appDao.insertTransaction(newTx)

            // Cập nhật số dư chờ đối soát (balancePending) của user
            val user = appDao.getUser(currentUserId)
            if (user != null) {
                appDao.updateUser(user.copy(balancePending = user.balancePending + randomCashback))
            }

            return Result.success(Pair(shortAffiliateUrl, randomCashback))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Xử lý yêu cầu rút tiền
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

        // Trừ số dư khả dụng và tạo lệnh rút tiền
        appDao.updateUser(user.copy(balanceAvailable = user.balanceAvailable - amount))

        val withdrawal = WithdrawalEntity(
            userId = currentUserId,
            bankName = bankName,
            accountNumber = accountNumber,
            accountHolder = accountHolder,
            amount = amount,
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        appDao.insertWithdrawal(withdrawal)

        return Result.success("Gửi yêu cầu rút tiền thành công! Tiền sẽ được chuyển về tài khoản của bạn trong vòng 24h.")
    }
}
