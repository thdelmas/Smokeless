package com.smokless.smokeless.ui.main;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smokless.smokeless.data.AppDatabase;
import com.smokless.smokeless.data.entity.SmokingSession;
import com.smokless.smokeless.data.repository.SmokingRepository;
import com.smokless.smokeless.util.ScoreCalculator;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {
    
    private static final String PREF_NAME = "SmokelessPrefs";
    private static final String KEY_STRICT_MODE = "strictMode";
    private static final String KEY_DIFFICULTY = "difficultyLevel";
    
    private final SmokingRepository repository;
    private final SharedPreferences prefs;
    
    private final MutableLiveData<Long> currentScore = new MutableLiveData<>(0L);
    private final MutableLiveData<Double> currentPercentage = new MutableLiveData<>(0.0);
    private final MutableLiveData<Long> currentGoal = new MutableLiveData<>(0L);
    
    private final MutableLiveData<List<ScoreData>> allTimeScores = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScoreData>> yearScores = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScoreData>> monthScores = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScoreData>> weekScores = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScoreData>> dayScores = new MutableLiveData<>(new ArrayList<>());
    
    private final MutableLiveData<ScoreData> goalData = new MutableLiveData<>();
    
    private Long lastTimestamp = 0L;
    
    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new SmokingRepository(application);
        prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public LiveData<Long> getCurrentScore() {
        return currentScore;
    }
    
    public LiveData<Double> getCurrentPercentage() {
        return currentPercentage;
    }
    
    public LiveData<Long> getCurrentGoal() {
        return currentGoal;
    }
    
    public LiveData<List<ScoreData>> getAllTimeScores() {
        return allTimeScores;
    }
    
    public LiveData<List<ScoreData>> getYearScores() {
        return yearScores;
    }
    
    public LiveData<List<ScoreData>> getMonthScores() {
        return monthScores;
    }
    
    public LiveData<List<ScoreData>> getWeekScores() {
        return weekScores;
    }
    
    public LiveData<List<ScoreData>> getDayScores() {
        return dayScores;
    }
    
    public LiveData<ScoreData> getGoalData() {
        return goalData;
    }
    
    public void recordSmoke() {
        AppDatabase.databaseExecutor.execute(() -> {
            repository.recordSmoke();
            refreshData();
        });
    }
    
    public void refreshData() {
        AppDatabase.databaseExecutor.execute(() -> {
            Long timestamp = repository.getLastTimestamp();
            lastTimestamp = timestamp != null ? timestamp : 0L;
            
            long score = calculateTimeSinceLastSmoke();
            currentScore.postValue(score);
            
            calculateAllScores(score);
        });
    }
    
    private long calculateTimeSinceLastSmoke() {
        if (lastTimestamp == 0) {
            return 0;
        }
        return System.currentTimeMillis() - lastTimestamp;
    }
    
    private void calculateAllScores(long score) {
        boolean strictMode = prefs.getBoolean(KEY_STRICT_MODE, false);
        int difficulty = prefs.getInt(KEY_DIFFICULTY, 0);
        
        // All time scores
        List<SmokingSession> allSessions = repository.getAllSessionsSync();
        int allTimeCount = repository.getSessionCountForScope("all");
        allTimeScores.postValue(calculateScopeScores(allSessions, "all", score, strictMode, allTimeCount));
        
        // Year scores
        List<SmokingSession> yearSessions = repository.getSessionsForScope("year");
        int yearCount = repository.getSessionCountForScope("year");
        yearScores.postValue(calculateScopeScores(yearSessions, "year", score, strictMode, yearCount));
        
        // Month scores
        List<SmokingSession> monthSessions = repository.getSessionsForScope("month");
        int monthCount = repository.getSessionCountForScope("month");
        monthScores.postValue(calculateScopeScores(monthSessions, "month", score, strictMode, monthCount));
        
        // Week scores
        List<SmokingSession> weekSessions = repository.getSessionsForScope("week");
        int weekCount = repository.getSessionCountForScope("week");
        weekScores.postValue(calculateScopeScores(weekSessions, "week", score, strictMode, weekCount));
        
        // Day scores
        List<SmokingSession> daySessions = repository.getSessionsForScope("day");
        int dayCount = repository.getSessionCountForScope("day");
        dayScores.postValue(calculateScopeScores(daySessions, "day", score, strictMode, dayCount));
        
        // Goal
        double goal = ScoreCalculator.calculateGoal(allSessions, difficulty);
        double goalPercent = goal > 0 ? (score / goal) * 100 : 0;
        if (strictMode && goalPercent > 100) goalPercent = 100;
        
        currentGoal.postValue((long) goal);
        currentPercentage.postValue(goalPercent);
        goalData.postValue(new ScoreData("Goal", (long) goal, goalPercent));
    }
    
    private List<ScoreData> calculateScopeScores(List<SmokingSession> sessions, String scope, 
                                                   long currentScore, boolean strictMode, int count) {
        List<ScoreData> scores = new ArrayList<>();
        
        long best = ScoreCalculator.calculateBestTime(sessions, scope);
        long average = ScoreCalculator.calculateAverageTime(sessions);
        long median = ScoreCalculator.calculateMedianTime(sessions);
        
        double bestPercent = best > 0 ? (currentScore / (double) best) * 100 : 0;
        double avgPercent = average > 0 ? (currentScore / (double) average) * 100 : 0;
        double medPercent = median > 0 ? (currentScore / (double) median) * 100 : 0;
        
        if (strictMode) {
            if (bestPercent > 100) bestPercent = 100;
            if (avgPercent > 100) avgPercent = 100;
            if (medPercent > 100) medPercent = 100;
        }
        
        String scopeLabel = formatScopeLabel(scope);
        scores.add(new ScoreData("Best " + scopeLabel, best, bestPercent));
        scores.add(new ScoreData("Average " + scopeLabel, average, avgPercent));
        scores.add(new ScoreData("Median " + scopeLabel, median, medPercent));
        scores.add(new ScoreData("Count " + scopeLabel, count, 100, true)); // Count shown as number
        
        return scores;
    }
    
    private String formatScopeLabel(String scope) {
        switch (scope.toLowerCase()) {
            case "year": return "of Year";
            case "month": return "of Month";
            case "week": return "of Week";
            case "day": return "Today";
            default: return "of All";
        }
    }
}

