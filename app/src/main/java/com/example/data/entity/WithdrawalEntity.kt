package com.casi.cashback.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val amount: Long, // VND integer
    val status: String, // "pending", "completed", "cancelled"
    val createdAt: Long = System.currentTimeMillis()
)
