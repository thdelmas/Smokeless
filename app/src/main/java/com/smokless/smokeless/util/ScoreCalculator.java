package com.smokless.smokeless.util;

import com.smokless.smokeless.data.entity.SmokingSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ScoreCalculator {
    
    public static long calculateBestTime(List<SmokingSession> sessions, String scope) {
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        
        long timeUnitMillis = getScopeMillis(scope);
        long biggestDifference = 0;
        
        for (int i = 1; i < sessions.size(); i++) {
            long diff = sessions.get(i).getTimestamp() - sessions.get(i - 1).getTimestamp();
            if (diff > biggestDifference) {
                biggestDifference = diff;
            }
        }
        
        // Check current ongoing period
        if (!sessions.isEmpty()) {
            long lastDiff = System.currentTimeMillis() - sessions.get(sessions.size() - 1).getTimestamp();
            if (lastDiff > biggestDifference) {
                biggestDifference = lastDiff;
            }
        }
        
        // Cap at scope limit
        if (timeUnitMillis > 0 && biggestDifference > timeUnitMillis) {
            biggestDifference = timeUnitMillis;
        }
        
        return biggestDifference;
    }
    
    public static long calculateAverageTime(List<SmokingSession> sessions) {
        if (sessions == null || sessions.size() < 1) {
            return 0;
        }
        
        List<Long> differences = getTimeDifferences(sessions);
        if (differences.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (Long diff : differences) {
            sum += diff;
        }
        
        return sum / differences.size();
    }
    
    public static long calculateMedianTime(List<SmokingSession> sessions) {
        if (sessions == null || sessions.size() < 1) {
            return 0;
        }
        
        List<Long> differences = getTimeDifferences(sessions);
        if (differences.isEmpty()) {
            return 0;
        }
        
        long[] array = differences.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(array);
        
        int n = array.length;
        if (n % 2 == 0) {
            return (array[n / 2 - 1] + array[n / 2]) / 2;
        } else {
            return array[n / 2];
        }
    }
    
    public static double calculateStandardDeviation(List<SmokingSession> sessions) {
        if (sessions == null || sessions.size() < 2) {
            return 0;
        }
        
        List<Long> differences = getTimeDifferences(sessions);
        if (differences.size() < 2) {
            return 0;
        }
        
        double mean = differences.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = differences.stream()
                .mapToDouble(diff -> Math.pow(diff - mean, 2))
                .average()
                .orElse(0);
        
        return Math.sqrt(variance);
    }
    
    public static double calculateGoal(List<SmokingSession> sessions, int difficultyLevel) {
        long median = calculateMedianTime(sessions);
        double stdDev = calculateStandardDeviation(sessions);
        return median + stdDev * difficultyLevel;
    }
    
    private static List<Long> getTimeDifferences(List<SmokingSession> sessions) {
        List<Long> differences = new ArrayList<>();
        
        for (int i = 1; i < sessions.size(); i++) {
            differences.add(sessions.get(i).getTimestamp() - sessions.get(i - 1).getTimestamp());
        }
        
        // Include current ongoing period
        if (!sessions.isEmpty()) {
            differences.add(System.currentTimeMillis() - sessions.get(sessions.size() - 1).getTimestamp());
        }
        
        return differences;
    }
    
    private static long getScopeMillis(String scope) {
        switch (scope.toLowerCase()) {
            case "year":
                return TimeUnit.DAYS.toMillis(365);
            case "month":
                return TimeUnit.DAYS.toMillis(30);
            case "week":
                return TimeUnit.DAYS.toMillis(7);
            case "day":
                return TimeUnit.DAYS.toMillis(1);
            default:
                return 0;
        }
    }
}

