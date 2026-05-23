package com.smokless.smokeless.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.SmokingSession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(entities = [SmokingSession::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smokingSessionDao(): SmokingSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val databaseExecutor: ExecutorService = Executors.newFixedThreadPool(4)

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cravings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE smoking_sessions " +
                    "ADD COLUMN substance TEXT NOT NULL DEFAULT 'TOBACCO'"
                )
            }
        }

        // Adds the per-session quantity (dose) column. All pre-existing rows
        // default to 1.0 — every legacy session was a "full smoke" under the
        // binary count model, so backfilling at full is honest, not lossy.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE smoking_sessions " +
                    "ADD COLUMN quantity REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        // Drops the cravings table — the "I Resisted" feature has been removed
        // in favor of letting the per-substance pace hero answer "did I smoke
        // today, and how much" directly. Existing craving rows are gone with
        // the table; the feature has no replacement that would consume them.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cravings")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smokeless_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
