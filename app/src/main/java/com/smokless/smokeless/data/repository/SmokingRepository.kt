package com.smokless.smokeless.data.repository

import android.app.Application
import androidx.lifecycle.LiveData
import com.smokless.smokeless.bios.BiosClient
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.dao.SmokingSessionDao
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import java.util.concurrent.TimeUnit

class SmokingRepository(application: Application) {

    private val smokingDao: SmokingSessionDao
    private val allSessions: LiveData<List<SmokingSession>>
    private val biosClient: BiosClient = BiosClient(application)

    init {
        val db = AppDatabase.getInstance(application)
        smokingDao = db.smokingSessionDao()
        allSessions = smokingDao.getAllSessionsLive()
    }

    fun getAllSessions(): LiveData<List<SmokingSession>> = allSessions

    /** Newest-first stream for the data-entries screen. */
    fun getAllSessionsDesc(): LiveData<List<SmokingSession>> = smokingDao.getAllSessionsDescLive()

    /**
     * Add a session at a caller-chosen time — the manual "add entry" path.
     * The timestamp marks the *end* of the session (same convention as live
     * logging, which anchors on now + exposure window), so no offset is
     * applied here. Mirrors [insert] in also forwarding the event to Bios.
     */
    fun addManualSession(timestamp: Long, substance: Substance, quantity: Double) {
        AppDatabase.databaseExecutor.execute {
            smokingDao.insert(SmokingSession(timestamp, substance, quantity))
            biosClient.pushSmokingEvent(timestamp, substance)
        }
    }

    /**
     * Persist an edit to an existing session (time / substance / quantity).
     * Local-only — Bios has no retract/amend endpoint, matching how deletes
     * and undo already stay local.
     */
    fun updateSession(session: SmokingSession) {
        AppDatabase.databaseExecutor.execute {
            smokingDao.update(session)
        }
    }

    /** Async delete for the data-entries screen (off the main thread). */
    fun deleteSessionAsync(id: Long) {
        AppDatabase.databaseExecutor.execute {
            smokingDao.deleteById(id)
        }
    }

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

    fun backfillBios(): BiosClient.BackfillResult {
        val smokingEvents = smokingDao.getAllSessions().map { it.timestamp to it.substance }
        return biosClient.backfill(smokingEvents)
    }

    fun getLastTimestamp(): Long? = smokingDao.getLastTimestamp()

    fun getSessionsSince(startTime: Long): List<SmokingSession> = smokingDao.getSessionsSince(startTime)

    fun getAllSessionsSync(): List<SmokingSession> = smokingDao.getAllSessions()

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
