package com.smokless.smokeless.util

import java.time.Duration

object TimeFormatter {
    
    fun format(milliseconds: Long): String {
        if (milliseconds <= 0) {
            return "00"
        }
        
        val duration = Duration.ofMillis(milliseconds)
        
        val totalDays = duration.toDays()
        val years = totalDays / 365
        val remainingDaysAfterYears = totalDays % 365
        val months = remainingDaysAfterYears / 30
        val days = remainingDaysAfterYears % 30
        
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        
        return when {
            years > 0 -> String.format("%dY %dM %dD %02d:%02d:%02d", years, months, days, hours, minutes, seconds)
            months > 0 -> String.format("%dM %dD %02d:%02d:%02d", months, days, hours, minutes, seconds)
            days > 0 -> String.format("%dD %02d:%02d:%02d", days, hours, minutes, seconds)
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("%02d", seconds)
        }
    }
    
    fun formatShort(milliseconds: Long): String {
        if (milliseconds <= 0) {
            return "0s"
        }
        
        val duration = Duration.ofMillis(milliseconds)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        
        return when {
            days > 0 -> String.format("%dd %dh", days, hours)
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            else -> String.format("%dm", minutes)
        }
    }
}







