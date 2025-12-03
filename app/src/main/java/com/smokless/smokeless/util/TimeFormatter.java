package com.smokless.smokeless.util;

import java.time.Duration;
import java.time.Instant;

public class TimeFormatter {
    
    public static String format(long milliseconds) {
        if (milliseconds <= 0) {
            return "00";
        }
        
        Duration duration = Duration.ofMillis(milliseconds);
        
        long totalDays = duration.toDays();
        long years = totalDays / 365;
        long remainingDaysAfterYears = totalDays % 365;
        long months = remainingDaysAfterYears / 30;
        long days = remainingDaysAfterYears % 30;
        
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        if (years > 0) {
            return String.format("%dY %dM %dD %02d:%02d:%02d", years, months, days, hours, minutes, seconds);
        } else if (months > 0) {
            return String.format("%dM %dD %02d:%02d:%02d", months, days, hours, minutes, seconds);
        } else if (days > 0) {
            return String.format("%dD %02d:%02d:%02d", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02d:%02d", minutes, seconds);
        } else {
            return String.format("%02d", seconds);
        }
    }
    
    public static String formatShort(long milliseconds) {
        if (milliseconds <= 0) {
            return "0s";
        }
        
        Duration duration = Duration.ofMillis(milliseconds);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
}

