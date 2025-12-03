package com.smokless.smokeless.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.smokless.smokeless.data.AppDatabase;
import com.smokless.smokeless.data.dao.SmokingSessionDao;
import com.smokless.smokeless.data.entity.SmokingSession;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmokingRepository {
    
    private final SmokingSessionDao dao;
    private final LiveData<List<SmokingSession>> allSessions;
    
    public SmokingRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        dao = db.smokingSessionDao();
        allSessions = dao.getAllSessionsLive();
    }
    
    public LiveData<List<SmokingSession>> getAllSessions() {
        return allSessions;
    }
    
    public void insert(SmokingSession session) {
        AppDatabase.databaseExecutor.execute(() -> dao.insert(session));
    }
    
    public void recordSmoke() {
        insert(new SmokingSession(System.currentTimeMillis()));
    }
    
    public Long getLastTimestamp() {
        return dao.getLastTimestamp();
    }
    
    public List<SmokingSession> getSessionsSince(long startTime) {
        return dao.getSessionsSince(startTime);
    }
    
    public List<SmokingSession> getAllSessionsSync() {
        return dao.getAllSessions();
    }
    
    public List<SmokingSession> getSessionsForScope(String scope) {
        long startTime = getStartTimeForScope(scope);
        return dao.getSessionsSince(startTime);
    }
    
    public int getSessionCountForScope(String scope) {
        long startTime = getStartTimeForScope(scope);
        if (startTime == 0) {
            return dao.getSessionCount();
        }
        return dao.getSessionCountSince(startTime);
    }
    
    private long getStartTimeForScope(String scope) {
        long currentTime = System.currentTimeMillis();
        
        switch (scope.toLowerCase()) {
            case "year":
                return currentTime - TimeUnit.DAYS.toMillis(365);
            case "month":
                return currentTime - TimeUnit.DAYS.toMillis(30);
            case "week":
                return currentTime - TimeUnit.DAYS.toMillis(7);
            case "day":
                return currentTime - TimeUnit.DAYS.toMillis(1);
            default:
                return 0;
        }
    }
}

