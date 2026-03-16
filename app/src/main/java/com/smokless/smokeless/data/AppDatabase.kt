package com.smokless.smokeless.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smokless.smokeless.data.dao.CravingDao
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(entities = [SmokingSession::class, Craving::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun smokingSessionDao(): SmokingSessionDao
    abstract fun cravingDao(): CravingDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val databaseExecutor: ExecutorService = Executors.newFixedThreadPool(4)
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cravings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
                )
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smokeless_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            databaseExecutor.execute {
                                val dao = INSTANCE?.smokingSessionDao()
                                if (dao != null && dao.getSessionCount() == 0) {
                                    dao.insert(SmokingSession(System.currentTimeMillis()))
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}






