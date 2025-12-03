package com.smokless.smokeless.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.smokless.smokeless.data.entity.SmokingSession;

import java.util.List;

@Dao
public interface SmokingSessionDao {
    
    @Insert
    long insert(SmokingSession session);
    
    @Query("SELECT * FROM smoking_sessions ORDER BY timestamp ASC")
    LiveData<List<SmokingSession>> getAllSessionsLive();
    
    @Query("SELECT * FROM smoking_sessions ORDER BY timestamp ASC")
    List<SmokingSession> getAllSessions();
    
    @Query("SELECT * FROM smoking_sessions WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<SmokingSession> getSessionsSince(long startTime);
    
    @Query("SELECT MAX(timestamp) FROM smoking_sessions")
    Long getLastTimestamp();
    
    @Query("SELECT COUNT(*) FROM smoking_sessions")
    int getSessionCount();
    
    @Query("SELECT COUNT(*) FROM smoking_sessions WHERE timestamp >= :startTime")
    int getSessionCountSince(long startTime);
    
    @Query("DELETE FROM smoking_sessions")
    void deleteAll();
}

