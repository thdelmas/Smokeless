package com.smokless.smokeless.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "smoking_sessions")
public class SmokingSession {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private long timestamp;
    
    public SmokingSession(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

