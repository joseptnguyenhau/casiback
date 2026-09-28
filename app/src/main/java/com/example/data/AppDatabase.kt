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
                // Default user (bắt đầu với 0 hoặc giá trị khởi tạo thực tế)
                dao.insertUser(
                    UserEntity(
                        userId = "user_001",
                        name = "Nguyễn Văn Hậu",
                        email = "nguyenvana@gmail.com",
                        phone = "0901234567",
                        balanceAvailable = 0L,
                        balancePending = 0L
                    )
                )
            }
        }
    }
}
