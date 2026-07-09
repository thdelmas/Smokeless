package com.smokless.smokeless.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.smokless.smokeless.data.entity.SmokingSession

@Dao
interface SmokingSessionDao {

    @Insert
    fun insert(session: SmokingSession): Long

    /** Full-row edit (timestamp / substance / quantity). Matched by primary key. */
    @Update
    fun update(session: SmokingSession)

    @Query("SELECT * FROM smoking_sessions ORDER BY timestamp ASC")
    fun getAllSessionsLive(): LiveData<List<SmokingSession>>

    /** Newest-first — backs the history/data-entries screen. */
    @Query("SELECT * FROM smoking_sessions ORDER BY timestamp DESC")
    fun getAllSessionsDescLive(): LiveData<List<SmokingSession>>
    
    @Query("SELECT * FROM smoking_sessions ORDER BY timestamp ASC")
    fun getAllSessions(): List<SmokingSession>
    
    @Query("SELECT * FROM smoking_sessions WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getSessionsSince(startTime: Long): List<SmokingSession>
    
    @Query("SELECT MAX(timestamp) FROM smoking_sessions")
    fun getLastTimestamp(): Long?
    
    @Query("SELECT COUNT(*) FROM smoking_sessions")
    fun getSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM smoking_sessions WHERE timestamp >= :startTime")
    fun getSessionCountSince(startTime: Long): Int
    
    @Query("DELETE FROM smoking_sessions WHERE id = :id")
    fun deleteById(id: Long)

    @Query("UPDATE smoking_sessions SET quantity = :quantity WHERE id = :id")
    fun updateQuantity(id: Long, quantity: Double)

    @Query("DELETE FROM smoking_sessions")
    fun deleteAll()
}







