package com.casi.cashback.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String = "user_001",
    val name: String = "Nguyễn Văn A",
    val email: String = "nguyenvana@gmail.com",
    val phone: String = "0901234567",
    val balanceAvailable: Double = 185000.0, // Số dư khả dụng (VND)
    val balancePending: Double = 320000.0   // Số dư chờ đối soát (VND)
)
