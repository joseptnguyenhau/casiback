package com.casi.cashback.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.casi.cashback.data.dao.AppDao
import com.casi.cashback.data.entity.TransactionEntity
import com.casi.cashback.data.entity.UserEntity
import com.casi.cashback.data.entity.WithdrawalEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [UserEntity::class, TransactionEntity::class, WithdrawalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shopee_cashback_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database.appDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: AppDao) {
                // Default user
                dao.insertUser(
                    UserEntity(
                        userId = "user_001",
                        name = "Nguyễn Văn Hậu",
                        email = "nguyenvana@gmail.com",
                        phone = "0901234567",
                        balanceAvailable = 185000.0,
                        balancePending = 320000.0
                    )
                )

                // Initial sample transactions
                dao.insertTransaction(
                    TransactionEntity(
                        userId = "user_001",
                        orderId = "SPVN20260925881",
                        productName = "Áo khoác gió nam thể thao chống nước cao cấp",
                        originalLink = "https://shopee.vn/product/123/456",
                        affiliateLink = "https://s.shopee.vn/an_001?sub_id=user_001",
                        cashbackAmount = 25000.0,
                        status = "approved",
                        createdAt = System.currentTimeMillis() - 86400000L * 2
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        userId = "user_001",
                        orderId = "SPVN20260924992",
                        productName = "Tai nghe Bluetooth True Wireless Gaming TWS",
                        originalLink = "https://shopee.vn/product/456/789",
                        affiliateLink = "https://s.shopee.vn/an_002?sub_id=user_001",
                        cashbackAmount = 45000.0,
                        status = "pending",
                        createdAt = System.currentTimeMillis() - 86400000L
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        userId = "user_001",
                        orderId = "SPVN20260922334",
                        productName = "Nồi chiên không dầu điện tử 6L thông minh",
                        originalLink = "https://shopee.vn/product/789/123",
                        affiliateLink = "https://s.shopee.vn/an_003?sub_id=user_001",
                        cashbackAmount = 115000.0,
                        status = "approved",
                        createdAt = System.currentTimeMillis() - 86400000L * 4
                    )
                )
            }
        }
    }
}
