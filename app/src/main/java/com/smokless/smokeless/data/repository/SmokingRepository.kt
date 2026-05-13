package com.smokless.smokeless.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.dao.CravingDao
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import java.util.concurrent.TimeUnit

class SmokingRepository(application: Application) {

    private val smokingDao: SmokingSessionDao
    private val cravingDao: CravingDao
    private val allSessions: LiveData<List<SmokingSession>>
    private val biosClient: BiosClient = BiosClient(application)

    init {
        val db = AppDatabase.getInstance(application)
        smokingDao = db.smokingSessionDao()
        cravingDao = db.cravingDao()
        allSessions = smokingDao.getAllSessionsLive()
    }

    fun getAllSessions(): LiveData<List<SmokingSession>> = allSessions

    fun insert(session: SmokingSession) {
        AppDatabase.databaseExecutor.execute {
            smokingDao.insert(session)
            biosClient.pushSmokingEvent(session.timestamp)
        }
    }

    fun recordSmoke() {
        insert(SmokingSession(System.currentTimeMillis()))
    }

    fun recordSmokeSync(exposureOffsetMs: Long = 0L): Long {
        val timestamp = System.currentTimeMillis() + exposureOffsetMs
        val id = smokingDao.insert(SmokingSession(timestamp))
        biosClient.pushSmokingEvent(timestamp)
        return id
    }

    fun deleteSession(id: Long) {
        smokingDao.deleteById(id)
    }

    fun recordCraving() {
        AppDatabase.databaseExecutor.execute {
            val timestamp = System.currentTimeMillis()
            cravingDao.insert(Craving(timestamp))
            biosClient.pushCravingEvent(timestamp)
        }
    }

    fun backfillBios(): BiosClient.BackfillResult {
        val sessionTimestamps = smokingDao.getAllSessions().map { it.timestamp }
        val cravingTimestamps = cravingDao.getAllCravings().map { it.timestamp }
        return biosClient.backfill(sessionTimestamps, cravingTimestamps)
    }

    fun getLastTimestamp(): Long? = smokingDao.getLastTimestamp()

    fun getSessionsSince(startTime: Long): List<SmokingSession> = smokingDao.getSessionsSince(startTime)

    fun getAllSessionsSync(): List<SmokingSession> = smokingDao.getAllSessions()

    fun getAllCravings(): List<Craving> = cravingDao.getAllCravings()

    fun getCravingsForScope(scope: String): List<Craving> {
        val startTime = getStartTimeForScope(scope)
        return if (startTime == 0L) {
            cravingDao.getAllCravings()
        } else {
            cravingDao.getCravingsSince(startTime)
        }
    }

    fun getSessionsForScope(scope: String): List<SmokingSession> {
        val startTime = getStartTimeForScope(scope)
        return smokingDao.getSessionsSince(startTime)
    }

    fun getSessionCountForScope(scope: String): Int {
        val startTime = getStartTimeForScope(scope)
        return if (startTime == 0L) {
            smokingDao.getSessionCount()
        } else {
            smokingDao.getSessionCountSince(startTime)
        }
    }

    private fun getStartTimeForScope(scope: String): Long {
        val currentTime = System.currentTimeMillis()

        return when (scope.lowercase()) {
            "year" -> currentTime - TimeUnit.DAYS.toMillis(365)
            "month" -> currentTime - TimeUnit.DAYS.toMillis(30)
            "week" -> currentTime - TimeUnit.DAYS.toMillis(7)
            "day" -> currentTime - TimeUnit.DAYS.toMillis(1)
            else -> 0L
        }
    }
}
