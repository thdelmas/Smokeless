package com.smokless.smokeless.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smokless.smokeless.data.entity.Craving

@Dao
interface CravingDao {
    
    @Insert
    fun insert(craving: Craving): Long
    
    @Query("SELECT * FROM cravings ORDER BY timestamp DESC")
    fun getAllCravings(): List<Craving>
    
    @Query("SELECT * FROM cravings WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getCravingsSince(startTime: Long): List<Craving>
    
    @Query("SELECT COUNT(*) FROM cravings")
    fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM cravings WHERE timestamp >= :startTime")
    fun getCountSince(startTime: Long): Int
    
    @Query("DELETE FROM cravings")
    fun deleteAll()
}

