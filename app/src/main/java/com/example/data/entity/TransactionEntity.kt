package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String = "user_001",
    val orderId: String,
    val productName: String,
    val originalLink: String,
    val affiliateLink: String,
    val cashbackAmount: Double,
    val status: String, // "pending" (Chờ duyệt), "approved" (Đã duyệt), "rejected" (Đã hủy)
    val createdAt: Long = System.currentTimeMillis()
)
