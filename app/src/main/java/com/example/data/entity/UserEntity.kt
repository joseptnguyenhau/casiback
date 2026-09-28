package com.casi.cashback.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val balanceAvailable: Long = 0L, // Số dư khả dụng (VND)
    val balancePending: Long = 0L   // Số dư chờ đối soát (VND)
)
