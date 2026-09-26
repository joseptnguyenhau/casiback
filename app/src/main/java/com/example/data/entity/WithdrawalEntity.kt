package com.casi.cashback.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String = "user_001",
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val amount: Double,
    val status: String, // "pending" (Đang xử lý), "completed" (Đã chuyển khoản)
    val createdAt: Long = System.currentTimeMillis()
)
