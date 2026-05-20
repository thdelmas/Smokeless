package com.smokless.smokeless.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.dao.CravingDao
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
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
            biosClient.pushSmokingEvent(session.timestamp, session.substance)
        }
    }

    fun recordSmoke(
        substance: Substance = Substance.DEFAULT,
        quantity: Double = 1.0,
    ) {
        insert(SmokingSession(System.currentTimeMillis(), substance, quantity))
    }

    fun recordSmokeSync(
        exposureOffsetMs: Long = 0L,
        substance: Substance = Substance.DEFAULT,
        quantity: Double = 1.0,
    ): Long {
        val timestamp = System.currentTimeMillis() + exposureOffsetMs
        val id = smokingDao.insert(SmokingSession(timestamp, substance, quantity))
        biosClient.pushSmokingEvent(timestamp, substance)
        return id
    }

    fun deleteSession(id: Long) {
        smokingDao.deleteById(id)
    }

    /**
     * Update the quantity on an already-logged session — backs the snackbar
     * "modify size" action and the long-press sized picker's post-commit
     * adjust path. Idempotent.
     */
    fun updateQuantity(id: Long, quantity: Double) {
        smokingDao.updateQuantity(id, quantity)
    }

    fun recordCraving() {
        AppDatabase.databaseExecutor.execute {
            val timestamp = System.currentTimeMillis()
            cravingDao.insert(Craving(timestamp))
            biosClient.pushCravingEvent(timestamp)
        }
    }

    fun backfillBios(): BiosClient.BackfillResult {
        val smokingEvents = smokingDao.getAllSessions().map { it.timestamp to it.substance }
        val cravingTimestamps = cravingDao.getAllCravings().map { it.timestamp }
        return biosClient.backfill(smokingEvents, cravingTimestamps)
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
