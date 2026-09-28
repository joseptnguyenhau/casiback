package com.casi.cashback.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val orderId: String,
    val productName: String,
    val originalLink: String,
    val affiliateLink: String,
    val cashbackAmount: Long, // VND integer
    val status: String, // "pending", "approved", "cancelled"
    val createdAt: Long = System.currentTimeMillis()
)
