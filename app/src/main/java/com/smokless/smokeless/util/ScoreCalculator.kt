package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.SmokingSession
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ScoreCalculator {
    
    /**
     * Statistics for a given time period
     */
    data class PeriodStats(
        val totalCigarettes: Int,
        val averagePerDay: Double,
        val cleanDays: Int,
        val totalDays: Int,
        val currentStreak: Int,
        val bestStreak: Int,
        val trend: Double,  // Positive = improving (fewer cigs), Negative = worsening
        val frequency: Double,  // Cigarettes per day (including clean days)
        val bestDay: Int,  // Lowest cigarette count
        val worstDay: Int  // Highest cigarette count
    )
    
    /**
     * Calculate comprehensive statistics for a period
     */
    fun calculatePeriodStats(sessions: List<SmokingSession>, scope: String): PeriodStats {
        if (sessions.isEmpty()) {
            // When there's no data, return zeros with proper context
            // Note: totalDays = 0 signals "no data" vs "perfect period"
            return PeriodStats(
                totalCigarettes = 0,
                averagePerDay = 0.0,
                cleanDays = 0,
                totalDays = 0,  // Key indicator: 0 days means no tracking started
                currentStreak = 0,
                bestStreak = 0,
                trend = 0.0,
                frequency = 0.0,
                bestDay = 0,
                worstDay = 0
            )
        }
        
        val dailyCounts = getDailyCountsForScope(sessions, scope)
        val totalDays = dailyCounts.size
        
        // If we have tracking days but no cigarettes, that's actually perfect!
        val totalCigarettes = dailyCounts.values.sum()
        val cleanDays = dailyCounts.count { it.value == 0 }
        
        // Calculate streaks
        val currentStreak = calculateCurrentStreak(dailyCounts)
        val bestStreak = calculateBestStreak(dailyCounts)
        
        // Calculate average (total cigarettes / total days)
        val averagePerDay = if (totalDays > 0) {
            totalCigarettes.toDouble() / totalDays
        } else {
            0.0
        }
        
        // Calculate trend (comparing first half to second half)
        val trend = calculateTrend(dailyCounts)
        
        // Best and worst days (among days with cigarettes)
        val counts = dailyCounts.values.filter { it > 0 }
        val bestDay = counts.minOrNull() ?: 0
        val worstDay = counts.maxOrNull() ?: 0
        
        return PeriodStats(
            totalCigarettes = totalCigarettes,
            averagePerDay = averagePerDay,
            cleanDays = cleanDays,
            totalDays = totalDays,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            trend = trend,
            frequency = averagePerDay,
            bestDay = bestDay,
            worstDay = worstDay
        )
    }
    
    /**
     * Get daily cigarette counts for a scope, including days with 0 cigarettes
     */
    fun getDailyCountsForScope(sessions: List<SmokingSession>, scope: String): LinkedHashMap<String, Int> {
        val dailyCounts = LinkedHashMap<String, Int>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        if (sessions.isEmpty()) {
            return dailyCounts
        }
        
        // Find the earliest session to start counting from
        val firstSessionTime = sessions.minOf { it.timestamp }
        
        // Determine the start date (either first session or scope start, whichever is later)
        val scopeStartTime = System.currentTimeMillis() - when (scope.lowercase()) {
            "year" -> TimeUnit.DAYS.toMillis(365)
            "month" -> TimeUnit.DAYS.toMillis(30)
            "week" -> TimeUnit.DAYS.toMillis(7)
            "day" -> TimeUnit.DAYS.toMillis(1)
            else -> Long.MAX_VALUE  // For "all", use first session
        }
        
        val startTime = if (scope.lowercase() == "all") firstSessionTime else max(firstSessionTime, scopeStartTime)
        
        // Calculate the start date
        calendar.timeInMillis = startTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        
        // Calculate end date (today)
        val endCalendar = Calendar.getInstance()
        endCalendar.set(Calendar.HOUR_OF_DAY, 0)
        endCalendar.set(Calendar.MINUTE, 0)
        endCalendar.set(Calendar.SECOND, 0)
        endCalendar.set(Calendar.MILLISECOND, 0)
        
        // Initialize all days from start to today with 0 count
        calendar.time = startDate
        while (!calendar.after(endCalendar)) {
            val dayKey = dateFormat.format(calendar.time)
            dailyCounts[dayKey] = 0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        // Count cigarettes for each day
        for (session in sessions) {
            val dayKey = dateFormat.format(Date(session.timestamp))
            if (dailyCounts.containsKey(dayKey)) {
                dailyCounts[dayKey] = dailyCounts[dayKey]!! + 1
            }
        }
        
        return dailyCounts
    }
    
    /**
     * Calculate current streak of clean days (from today backwards)
     */
    private fun calculateCurrentStreak(dailyCounts: LinkedHashMap<String, Int>): Int {
        var streak = 0
        val values = dailyCounts.values.toList()
        
        // Count from the end (most recent day) backwards
        for (i in values.size - 1 downTo 0) {
            if (values[i] == 0) {
                streak++
            } else {
                break
            }
        }
        
        return streak
    }
    
    /**
     * Calculate best streak of clean days in the period
     */
    private fun calculateBestStreak(dailyCounts: LinkedHashMap<String, Int>): Int {
        var bestStreak = 0
        var currentStreak = 0
        
        for (count in dailyCounts.values) {
            if (count == 0) {
                currentStreak++
                bestStreak = max(bestStreak, currentStreak)
            } else {
                currentStreak = 0
            }
        }
        
        return bestStreak
    }
    
    /**
     * Calculate trend: positive = improving (fewer cigarettes), negative = worsening
     * Returns percentage change: +50 means 50% reduction, -50 means 50% increase
     * 
     * Uses weighted comparison giving more importance to recent data
     */
    private fun calculateTrend(dailyCounts: LinkedHashMap<String, Int>): Double {
        if (dailyCounts.size < 4) {
            return 0.0  // Not enough data to determine meaningful trend
        }
        
        val values = dailyCounts.values.toList()
        
        // Split at 60% point to give more weight to recent performance
        val splitPoint = (values.size * 0.6).toInt().coerceIn(1, values.size - 1)
        
        val earlierPeriod = values.subList(0, splitPoint)
        val recentPeriod = values.subList(splitPoint, values.size)
        
        val earlierAvg = earlierPeriod.average()
        val recentAvg = recentPeriod.average()
        
        // Both periods smoke-free = maintaining perfection
        if (earlierAvg < 0.01 && recentAvg < 0.01) {
            return 100.0  // Perfect maintenance
        }
        
        // Started smoking from clean slate = worst trend
        if (earlierAvg < 0.01 && recentAvg >= 0.01) {
            return -100.0
        }
        
        // Quit completely from smoking = best trend
        if (earlierAvg >= 0.01 && recentAvg < 0.01) {
            return 100.0
        }
        
        // Calculate percentage change
        // Positive result = improvement (reduction), Negative = worsening (increase)
        val percentChange = ((earlierAvg - recentAvg) / earlierAvg) * 100
        
        return percentChange
    }
    
    /**
     * Calculate goal based on historical average with difficulty modifier
     */
    fun calculateGoal(sessions: List<SmokingSession>, scope: String, difficultyLevel: Int): Double {
        if (sessions.isEmpty()) {
            return 0.0  // Goal is 0 if no history
        }
        
        val stats = calculatePeriodStats(sessions, scope)
        val currentAvg = stats.averagePerDay
        
        // Goal is to reduce by difficulty percentage
        // Difficulty 0 = maintain current, 1 = reduce by 10%, 2 = reduce by 20%, etc.
        val reductionFactor = 1.0 - (difficultyLevel * 0.1)
        val goal = currentAvg * reductionFactor
        
        return max(0.0, goal)
    }
    
    /**
     * Calculate percentage towards goal
     * Returns 0-100 where 100 = meeting or exceeding goal
     */
    fun calculateGoalProgress(currentAvg: Double, goalAvg: Double): Double {
        if (goalAvg == 0.0) {
            return if (currentAvg == 0.0) 100.0 else 0.0
        }
        
        // If current is better than goal, 100%
        if (currentAvg <= goalAvg) {
            return 100.0
        }
        
        // Calculate how far off we are
        // The further from goal, the lower the percentage
        val difference = currentAvg - goalAvg
        val percentOff = (difference / goalAvg) * 100
        
        // Max out at 0% if we're way off
        return max(0.0, 100.0 - percentOff)
    }
    
    /**
     * Calculate time-aware daily progress
     * For "today" view, consider both cigarette count AND time elapsed
     * This prevents showing 100% too early in the day
     */
    fun calculateDailyProgress(cigarettesToday: Int, goalCount: Double, lastTimestamp: Long): Double {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Get start of today
        calendar.time = Date()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        
        // Calculate what portion of the day has passed (0.0 to 1.0)
        val dayElapsedFraction = (currentTime - startOfToday).toDouble() / TimeUnit.DAYS.toMillis(1)
        
        // Expected cigarettes by now = goal * fraction of day elapsed
        val expectedByNow = goalCount * dayElapsedFraction
        
        // If we haven't smoked yet
        if (cigarettesToday == 0) {
            // Progress based on how much time has passed without smoking
            // At start of day (0%): 0% progress
            // At end of day (100%): 100% progress
            // This creates a gradual increase throughout the day
            return min(100.0, dayElapsedFraction * 100.0)
        }
        
        // If we have smoked, compare actual vs expected
        if (cigarettesToday <= expectedByNow) {
            // Doing better than expected for this time of day
            val ratio = 1.0 - (cigarettesToday / max(expectedByNow, 1.0))
            return min(100.0, 50.0 + (ratio * 50.0)) // 50-100% range
        } else {
            // Doing worse than expected
            val ratio = cigarettesToday / max(expectedByNow, 1.0)
            return max(0.0, 50.0 / ratio) // 0-50% range
        }
    }
    
    /**
     * Get total days for a scope
     */
    private fun getTotalDaysInScope(scope: String): Int {
        return when (scope.lowercase()) {
            "day" -> 1
            "week" -> 7
            "month" -> 30
            "year" -> 365
            "all" -> 365  // Cap at 1 year for "all time" to keep it manageable
            else -> 30
        }
    }
    
    /**
     * Get hourly cigarette counts for today
     * Returns a map of hour (0-23) to count
     * Optimized to show relevant time range
     */
    fun getHourlyCountsForToday(sessions: List<SmokingSession>): LinkedHashMap<String, Int> {
        val hourlyCounts = LinkedHashMap<String, Int>()
        val calendar = Calendar.getInstance()
        
        // Get start of today
        calendar.time = Date()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        
        // Find today's sessions and their hour range
        val todayHours = mutableSetOf<Int>()
        for (session in sessions) {
            if (session.timestamp >= startOfToday) {
                calendar.time = Date(session.timestamp)
                todayHours.add(calendar.get(Calendar.HOUR_OF_DAY))
            }
        }
        
        // Determine display range
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val startHour: Int
        val endHour: Int
        
        if (todayHours.isEmpty()) {
            // No sessions today - show from midnight to current hour
            startHour = 0
            endHour = currentHour
        } else {
            // Show from earliest session to current hour (or latest session, whichever is later)
            val minHour = todayHours.minOrNull() ?: 0
            val maxHour = todayHours.maxOrNull() ?: currentHour
            startHour = max(0, minHour - 1)  // Include one hour before first session
            endHour = max(currentHour, maxHour)  // Show up to current hour or latest session
        }
        
        // Initialize hours in range
        for (hour in startHour..endHour) {
            val hourKey = String.format("%02d:00", hour)
            hourlyCounts[hourKey] = 0
        }
        
        // Count cigarettes for each hour
        for (session in sessions) {
            if (session.timestamp >= startOfToday) {
                calendar.time = Date(session.timestamp)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val hourKey = String.format("%02d:00", hour)
                if (hourlyCounts.containsKey(hourKey)) {
                    hourlyCounts[hourKey] = hourlyCounts[hourKey]!! + 1
                }
            }
        }
        
        return hourlyCounts
    }
    
    /**
     * Calculate time since last cigarette
     */
    fun calculateTimeSinceLastSmoke(lastTimestamp: Long): Long {
        return if (lastTimestamp == 0L) {
            0L
        } else {
            System.currentTimeMillis() - lastTimestamp
        }
    }

    /**
     * Calculate average interval between smoking sessions (in milliseconds).
     * Uses the last 30 days of data for a stable baseline.
     * Returns 0 if fewer than 2 sessions exist.
     */
    fun calculateAverageInterval(sessions: List<SmokingSession>): Long {
        if (sessions.size < 2) return 0L

        val sorted = sessions.sortedBy { it.timestamp }
        var totalGap = 0L
        var gapCount = 0

        for (i in 1 until sorted.size) {
            val gap = sorted[i].timestamp - sorted[i - 1].timestamp
            // Ignore gaps longer than 24 hours (likely sleep or days off)
            if (gap <= TimeUnit.HOURS.toMillis(24)) {
                totalGap += gap
                gapCount++
            }
        }

        return if (gapCount > 0) totalGap / gapCount else 0L
    }

    /**
     * Calculate target interval the user should wait before next cigarette.
     * Target = average interval * (1 + difficulty * 0.1)
     * Difficulty 0 = maintain current pace, 1 = wait 10% longer, etc.
     */
    fun calculateTargetInterval(sessions: List<SmokingSession>, difficultyLevel: Int): Long {
        val avgInterval = calculateAverageInterval(sessions)
        if (avgInterval == 0L) return 0L

        val stretchFactor = 1.0 + (difficultyLevel * 0.1)
        return (avgInterval * stretchFactor).toLong()
    }

    /**
     * Calculate countdown progress towards target interval.
     * Returns 0-100 where 100 = waited long enough (reached target).
     * Can exceed 100 if user waits beyond target (bonus time).
     */
    fun calculateIntervalProgress(timeSinceLastSmoke: Long, targetInterval: Long): Double {
        if (targetInterval <= 0L) return 0.0
        return (timeSinceLastSmoke.toDouble() / targetInterval) * 100.0
    }
}





