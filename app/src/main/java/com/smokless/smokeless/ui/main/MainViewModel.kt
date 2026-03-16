package com.smokless.smokeless.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.repository.SmokingRepository
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.NotificationHelper
import com.smokless.smokeless.util.ScoreCalculator
import com.smokless.smokeless.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_DIFFICULTY = "difficultyLevel"
        private const val KEY_PACK_PRICE = "packPrice"
        private const val KEY_CIGS_PER_PACK = "cigsPerPack"
        private const val KEY_CURRENCY = "currency"
        private const val DEFAULT_PACK_PRICE = 10.0f
        private const val DEFAULT_CIGS_PER_PACK = 20
        private const val DEFAULT_CURRENCY = "$"
    }
    
    private val repository = SmokingRepository(application)
    private val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // Current time since last smoke (for hero timer)
    private val _currentScore = MutableLiveData(0L)
    val currentScore: LiveData<Long> = _currentScore
    
    // Hero section metrics (contextual to selected period)
    private val _heroValue = MutableLiveData(0.0)
    val heroValue: LiveData<Double> = _heroValue
    
    private val _heroLabel = MutableLiveData("Clean Streak")
    val heroLabel: LiveData<String> = _heroLabel
    
    private val _heroUnit = MutableLiveData("hours")
    val heroUnit: LiveData<String> = _heroUnit
    
    // Goal progress percentage
    private val _currentPercentage = MutableLiveData(0.0)
    val currentPercentage: LiveData<Double> = _currentPercentage
    
    // Goal (cigarettes per day target)
    private val _currentGoal = MutableLiveData(0.0)
    val currentGoal: LiveData<Double> = _currentGoal
    
    // Statistics for different time periods
    private val _allTimeScores = MutableLiveData<List<ScoreData>>(emptyList())
    val allTimeScores: LiveData<List<ScoreData>> = _allTimeScores
    
    private val _yearScores = MutableLiveData<List<ScoreData>>(emptyList())
    val yearScores: LiveData<List<ScoreData>> = _yearScores
    
    private val _monthScores = MutableLiveData<List<ScoreData>>(emptyList())
    val monthScores: LiveData<List<ScoreData>> = _monthScores
    
    private val _weekScores = MutableLiveData<List<ScoreData>>(emptyList())
    val weekScores: LiveData<List<ScoreData>> = _weekScores
    
    private val _dayScores = MutableLiveData<List<ScoreData>>(emptyList())
    val dayScores: LiveData<List<ScoreData>> = _dayScores
    
    // Chart data
    private val _chartData = MutableLiveData<ChartData?>()
    val chartData: LiveData<ChartData?> = _chartData
    
    // Money saved data
    private val _moneySaved = MutableLiveData<Float>()
    val moneySaved: LiveData<Float> = _moneySaved
    
    private val _moneySavedFormatted = MutableLiveData<String>()
    val moneySavedFormatted: LiveData<String> = _moneySavedFormatted
    
    private var lastTimestamp = 0L
    private var currentChartPeriod = "month"
    private var currentGoalPeriod = "month"  // Track period for goal calculation
    private var lastNotifiedHours = 0L  // Track last milestone notification
    
    /**
     * Record a cigarette smoke
     */
    fun recordSmoke() {
        AppDatabase.databaseExecutor.execute {
            repository.recordSmoke()
            refreshData()
        }
    }
    
    /**
     * Record a craving resisted
     */
    fun recordCravingResisted() {
        AppDatabase.databaseExecutor.execute {
            repository.recordCraving()
            // No need to refresh all data, just record the event
        }
    }
    
    /**
     * Refresh all data and statistics
     */
    fun refreshData() {
        AppDatabase.databaseExecutor.execute {
            val timestamp = repository.getLastTimestamp()
            lastTimestamp = timestamp ?: 0L
            
            // Calculate time since last smoke (for hero display)
            val score = ScoreCalculator.calculateTimeSinceLastSmoke(lastTimestamp)
            _currentScore.postValue(score)
            
            // Calculate all period statistics
            calculateAllScores()
        }
    }
    
    /**
     * Update only the timer (called frequently)
     */
    fun updateTimer() {
        val score = ScoreCalculator.calculateTimeSinceLastSmoke(lastTimestamp)
        _currentScore.postValue(score)
        
        // Check for milestone achievements
        checkMilestones(score)
        
        // Update hero metrics and progress for day view
        if (currentGoalPeriod == "day") {
            val hoursSinceLastSmoke = if (lastTimestamp > 0L) {
                (System.currentTimeMillis() - lastTimestamp) / (1000.0 * 60.0 * 60.0)
            } else {
                0.0
            }
            _heroValue.postValue(hoursSinceLastSmoke)
            
            // Update progress as time passes (so it gradually increases throughout the day)
            // Run database queries on background thread
            AppDatabase.databaseExecutor.execute {
                val difficulty = prefs.getInt(KEY_DIFFICULTY, 0)
                val allSessions = repository.getAllSessionsSync()
                val goal = ScoreCalculator.calculateGoal(allSessions, "month", difficulty)
                val todaySessions = repository.getSessionsForScope("day")
                val todayStats = ScoreCalculator.calculatePeriodStats(todaySessions, "day")
                val goalProgress = ScoreCalculator.calculateDailyProgress(
                    todayStats.totalCigarettes,
                    goal,
                    lastTimestamp
                )
                _currentPercentage.postValue(goalProgress)
            }
        }
    }
    
    /**
     * Check if user reached a health milestone and send notification
     */
    private fun checkMilestones(score: Long) {
        val hours = score / 3600
        
        // Only check every hour to avoid spam
        if (hours > lastNotifiedHours) {
            val milestone = HealthBenefits.getCurrentMilestone(hours)
            if (milestone != null && milestone.hours.toLong() == hours) {
                NotificationHelper.showMilestoneNotification(
                    getApplication<Application>().applicationContext,
                    milestone
                )
            }
            
            // Check for major time milestones
            when (hours.toInt()) {
                24, 72, 168, 720, 2160, 4320, 8760 -> {
                    NotificationHelper.showEncouragementNotification(
                        getApplication<Application>().applicationContext,
                        hours.toInt()
                    )
                }
            }
            
            lastNotifiedHours = hours
        }
    }
    
    /**
     * Set the chart period
     */
    fun setChartPeriod(period: String) {
        currentChartPeriod = period
        AppDatabase.databaseExecutor.execute {
            val sessions = repository.getSessionsForScope(period)
            calculateChartData(sessions, period)
        }
    }
    
    /**
     * Set the goal period - updates goal and progress calculation
     */
    fun setGoalPeriod(period: String) {
        currentGoalPeriod = period
        AppDatabase.databaseExecutor.execute {
            updateGoalForPeriod()
        }
    }
    
    /**
     * Calculate statistics for all time periods
     */
    private fun calculateAllScores() {
        // Get all sessions for goal calculation
        val allSessions = repository.getAllSessionsSync()
        
        // Calculate goal and progress based on current goal period
        updateGoalForPeriod()
        
        // Calculate scores for each period
        _allTimeScores.postValue(calculateScopeScores(allSessions, "all"))
        _yearScores.postValue(calculateScopeScores(repository.getSessionsForScope("year"), "year"))
        _monthScores.postValue(calculateScopeScores(repository.getSessionsForScope("month"), "month"))
        _weekScores.postValue(calculateScopeScores(repository.getSessionsForScope("week"), "week"))
        _dayScores.postValue(calculateScopeScores(repository.getSessionsForScope("day"), "day"))
        
        // Calculate chart data
        val chartSessions = repository.getSessionsForScope(currentChartPeriod)
        calculateChartData(chartSessions, currentChartPeriod)
        
        // Calculate money saved
        calculateMoneySaved(allSessions)
    }
    
    /**
     * Calculate total money saved based on avoided cigarettes
     */
    private fun calculateMoneySaved(allSessions: List<com.smokless.smokeless.data.entity.SmokingSession>) {
        val packPrice = prefs.getFloat(KEY_PACK_PRICE, DEFAULT_PACK_PRICE)
        val cigsPerPack = prefs.getInt(KEY_CIGS_PER_PACK, DEFAULT_CIGS_PER_PACK)
        val currency = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        
        val costPerCig = packPrice / cigsPerPack
        
        // Calculate expected cigarettes (baseline before quitting)
        val stats = ScoreCalculator.calculatePeriodStats(allSessions, "all")
        
        // Estimate cigarettes avoided: assume user was smoking at average rate
        // For each clean day, they saved their daily average cigarettes
        val baselineDaily = if (stats.averagePerDay > 0) stats.averagePerDay else 10.0 // default estimate
        val cigarettesAvoided = stats.cleanDays * baselineDaily
        
        val totalSaved = (cigarettesAvoided * costPerCig).toFloat()
        _moneySaved.postValue(totalSaved)
        _moneySavedFormatted.postValue(String.format("%s%.2f", currency, totalSaved))
    }
    
    /**
     * Update goal and progress for the current goal period
     */
    private fun updateGoalForPeriod() {
        val difficulty = prefs.getInt(KEY_DIFFICULTY, 0)
        val allSessions = repository.getAllSessionsSync()
        
        // Calculate goal based on longer historical period for stability
        // Use 30-day rolling average as baseline, regardless of viewing period
        val baselinePeriod = when (currentGoalPeriod) {
            "day" -> "month"  // For daily view, use monthly baseline
            "week" -> "month" // For weekly view, use monthly baseline
            else -> currentGoalPeriod // For month/year/all, use selected period
        }
        val goal = ScoreCalculator.calculateGoal(allSessions, baselinePeriod, difficulty)
        _currentGoal.postValue(goal)
        
        // Calculate progress towards goal using current period's actual performance
        val periodSessions = repository.getSessionsForScope(currentGoalPeriod)
        val periodStats = ScoreCalculator.calculatePeriodStats(periodSessions, currentGoalPeriod)
        
        // For daily view, use time-aware progress calculation
        val goalProgress = if (currentGoalPeriod == "day") {
            ScoreCalculator.calculateDailyProgress(
                periodStats.totalCigarettes, 
                goal,
                lastTimestamp
            )
        } else {
            ScoreCalculator.calculateGoalProgress(periodStats.averagePerDay, goal)
        }
        _currentPercentage.postValue(goalProgress)
        
        // Update hero metrics based on current period
        updateHeroMetrics(periodStats)
    }
    
    /**
     * Update hero section metrics based on selected period
     */
    private fun updateHeroMetrics(stats: ScoreCalculator.PeriodStats) {
        when (currentGoalPeriod) {
            "day" -> {
                // For today: show current streak in hours
                val hoursSinceLastSmoke = if (lastTimestamp > 0L) {
                    (System.currentTimeMillis() - lastTimestamp) / (1000 * 60 * 60)
                } else {
                    0L
                }
                _heroValue.postValue(hoursSinceLastSmoke.toDouble())
                _heroLabel.postValue("SMOKE-FREE FOR")
                _heroUnit.postValue(if (hoursSinceLastSmoke == 1L) "hour" else "hours")
            }
            "week", "month", "year", "all" -> {
                // For other periods: show average per day
                _heroValue.postValue(stats.averagePerDay)
                _heroLabel.postValue("AVERAGE PER DAY")
                _heroUnit.postValue("cigs/day")
            }
        }
    }
    
    /**
     * Calculate comprehensive statistics for a scope
     */
    private fun calculateScopeScores(sessions: List<com.smokless.smokeless.data.entity.SmokingSession>, scope: String): List<ScoreData> {
        val stats = ScoreCalculator.calculatePeriodStats(sessions, scope)
        val scores = mutableListOf<ScoreData>()
        
        // Only show meaningful stats if we have data
        if (stats.totalDays == 0) {
            scores.add(ScoreData(
                label = "No Data Yet",
                value = 0L,
                percentage = 0.0,
                unit = "Start tracking to see insights",
                type = ScoreData.StatType.COUNT
            ))
            return scores
        }
        
        // 1. Success Rate (Clean Days Percentage) - Most motivating metric
        val successRate = if (stats.totalDays > 0) {
            (stats.cleanDays.toDouble() / stats.totalDays) * 100
        } else 0.0
        scores.add(ScoreData(
            label = "Success Rate",
            value = stats.cleanDays.toLong(),
            decimalValue = successRate,
            percentage = successRate,
            unit = "clean days (${stats.cleanDays}/${stats.totalDays})",
            type = ScoreData.StatType.DAYS
        ))
        
        // 2. Average per day - Clear and actionable
        val avgPercentage = calculateAverageQuality(stats.averagePerDay)
        scores.add(ScoreData(
            label = "Daily Average",
            value = stats.averagePerDay.toLong(),
            decimalValue = stats.averagePerDay,
            percentage = avgPercentage,
            unit = "cigs/day",
            type = ScoreData.StatType.AVERAGE
        ))
        
        // 3. Current streak - Shows current momentum
        val streakPercentage = calculateStreakPercentage(stats.currentStreak, stats.bestStreak)
        scores.add(ScoreData(
            label = "Current Streak",
            value = stats.currentStreak.toLong(),
            percentage = streakPercentage,
            unit = if (stats.currentStreak == 1) "day" else "days",
            type = ScoreData.StatType.STREAK
        ))
        
        // 4. Best streak - Personal record
        scores.add(ScoreData(
            label = "Best Streak",
            value = stats.bestStreak.toLong(),
            percentage = 100.0,  // Best is always 100%
            unit = if (stats.bestStreak == 1) "day" else "days",
            type = ScoreData.StatType.STREAK
        ))
        
        // 5. Total smoked - For context
        scores.add(ScoreData(
            label = "Total Smoked",
            value = stats.totalCigarettes.toLong(),
            decimalValue = stats.totalCigarettes.toDouble(),
            percentage = 0.0,  // No percentage needed for raw total
            unit = if (stats.totalCigarettes == 1) "cigarette" else "cigarettes",
            type = ScoreData.StatType.COUNT
        ))
        
        // 6. Trend - Show if improving
        if (stats.totalDays >= 4) {  // Only show trend if enough data
            val trendText = when {
                stats.trend >= 30 -> "Much better!"
                stats.trend >= 10 -> "Improving"
                stats.trend >= -10 -> "Steady"
                stats.trend >= -30 -> "Needs focus"
                else -> "Increasing"
            }
            scores.add(ScoreData(
                label = "Progress Trend",
                value = 0L,
                percentage = stats.trend,
                unit = trendText,
                type = ScoreData.StatType.TREND
            ))
        }
        
        return scores
    }
    
    /**
     * Calculate quality score for average cigarettes per day
     * Uses health-based benchmarks:
     * - 0 cigs/day = 100% (perfect)
     * - 1-2 cigs/day = 90% (excellent)
     * - 3-5 cigs/day = 70% (good)
     * - 6-10 cigs/day = 50% (moderate)
     * - 11-15 cigs/day = 30% (concerning)
     * - 16-20 cigs/day = 10% (poor)
     * - 20+ cigs/day = 0% (pack-a-day smoker)
     */
    private fun calculateAverageQuality(average: Double): Double {
        return when {
            average == 0.0 -> 100.0
            average <= 2.0 -> 90.0 - (average * 5.0)      // 90-80%
            average <= 5.0 -> 80.0 - ((average - 2.0) * 10.0 / 3.0)  // 80-70%
            average <= 10.0 -> 70.0 - ((average - 5.0) * 4.0)  // 70-50%
            average <= 15.0 -> 50.0 - ((average - 10.0) * 4.0)  // 50-30%
            average <= 20.0 -> 30.0 - ((average - 15.0) * 4.0)  // 30-10%
            else -> max(0.0, 10.0 - ((average - 20.0) * 0.5))  // 10-0%
        }
    }
    
    /**
     * Calculate percentage for streak relative to best
     */
    private fun calculateStreakPercentage(currentStreak: Int, bestStreak: Int): Double {
        return when {
            bestStreak == 0 && currentStreak == 0 -> 0.0
            bestStreak == 0 -> 100.0  // First streak is 100%
            currentStreak == 0 -> 0.0
            else -> (currentStreak.toDouble() / bestStreak) * 100
        }
    }
    
    /**
     * Format scope label for display
     */
    private fun formatScopeLabel(scope: String): String {
        return when (scope.lowercase()) {
            "year" -> "This Year"
            "month" -> "This Month"
            "week" -> "This Week"
            "day" -> "Today"
            else -> "All Time"
        }
    }
    
    /**
     * Calculate chart data with daily counts and moving average
     */
    private fun calculateChartData(sessions: List<com.smokless.smokeless.data.entity.SmokingSession>, period: String) {
        if (sessions.isEmpty() && period != "day") {
            _chartData.postValue(null)
            return
        }
        
        // For daily view, group by hours; for other views, group by days
        val countsMap = if (period == "day") {
            ScoreCalculator.getHourlyCountsForToday(sessions)
        } else {
            ScoreCalculator.getDailyCountsForScope(sessions, period)
        }
        
        val dailyCounts = countsMap.values.toList()
        val dateKeys = countsMap.keys.toList()
        
        // Skip if no data at all
        if (dailyCounts.isEmpty()) {
            _chartData.postValue(null)
            return
        }
        
        // Generate labels (limit display for readability)
        val labels = generateLabels(dateKeys, period)
        
        // Calculate adaptive moving average based on period
        val windowSize = getMovingAverageWindow(period, dailyCounts.size)
        val movingAverage = calculateMovingAverage(dailyCounts, windowSize)
        
        // Calculate statistics
        val avgDailyCount = dailyCounts.average()
        
        val bestDay = dailyCounts.filter { it > 0 }.minOrNull() ?: 0
        val worstDay = dailyCounts.maxOrNull() ?: 0
        val cleanDays = dailyCounts.count { it == 0 }
        
        // Improved trend calculation using linear regression
        val (isImproving, trendPercentage) = calculateTrendImproved(dailyCounts)
        
        _chartData.postValue(ChartData(
            dailyCounts = dailyCounts,
            labels = labels,
            movingAverage = movingAverage,
            avgDailyCount = avgDailyCount,
            isImproving = isImproving,
            bestDay = bestDay,
            worstDay = worstDay,
            cleanDays = cleanDays,
            trendPercentage = trendPercentage
        ))
    }
    
    /**
     * Get adaptive moving average window size based on period
     */
    private fun getMovingAverageWindow(period: String, dataSize: Int): Int {
        val baseWindow = when (period.lowercase()) {
            "day" -> 3      // 3-hour window for hourly data
            "week" -> 3     // 3-day window for weekly data
            "month" -> 7    // 7-day window for monthly data
            "year" -> 30    // 30-day window for yearly data
            "all" -> 30     // 30-day window for all-time data
            else -> 7
        }
        
        // Ensure window is not larger than available data
        return kotlin.math.min(baseWindow, kotlin.math.max(1, dataSize / 3))
    }
    
    /**
     * Calculate trend using improved method
     * Returns Pair(isImproving, trendPercentage)
     */
    private fun calculateTrendImproved(data: List<Int>): Pair<Boolean, Double> {
        if (data.size < 3) {
            return Pair(false, 0.0)
        }
        
        // Use weighted comparison: recent data matters more
        val size = data.size
        val splitPoint = (size * 0.6).toInt() // Split at 60% to give more weight to recent data
        
        if (splitPoint < 1 || splitPoint >= size) {
            // Fallback to simple comparison
            val mid = size / 2
            val firstHalfAvg = data.subList(0, mid).average()
            val secondHalfAvg = data.subList(mid, size).average()
            
            val improving = secondHalfAvg < firstHalfAvg
            val percentage = if (firstHalfAvg > 0.01) {
                ((firstHalfAvg - secondHalfAvg) / firstHalfAvg) * 100
            } else if (secondHalfAvg > 0.01) {
                -100.0  // Worsening from near-zero
            } else {
                0.0  // Both are near-zero
            }
            
            return Pair(improving, percentage)
        }
        
        val earlierAvg = data.subList(0, splitPoint).average()
        val recentAvg = data.subList(splitPoint, size).average()
        
        val improving = recentAvg < earlierAvg
        
        // Calculate percentage change
        val percentage = when {
            earlierAvg > 0.01 -> {
                ((earlierAvg - recentAvg) / earlierAvg) * 100
            }
            recentAvg > 0.01 -> {
                -100.0  // Worsening from near-zero
            }
            else -> {
                0.0  // Both periods are clean
            }
        }
        
        return Pair(improving, percentage)
    }
    
    /**
     * Calculate moving average with proper weighting
     */
    private fun calculateMovingAverage(data: List<Int>, windowSize: Int): List<Double> {
        if (data.isEmpty()) return emptyList()
        if (windowSize < 1) return data.map { it.toDouble() }
        
        val result = mutableListOf<Double>()
        
        for (i in data.indices) {
            val start = max(0, i - windowSize + 1)
            val window = data.subList(start, i + 1)
            
            // Use simple average for consistency
            result.add(window.average())
        }
        
        return result
    }
    
    /**
     * Generate display labels for chart
     */
    private fun generateLabels(dateKeys: List<String>, period: String): List<String> {
        // For daily (hourly) view, keys are already in "HH:00" format
        if (period == "day") {
            return dateKeys
        }
        
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = when (period.lowercase()) {
            "week" -> SimpleDateFormat("EEE", Locale.getDefault())      // Mon, Tue, etc.
            "month" -> SimpleDateFormat("MMM d", Locale.getDefault())   // Jan 1, Jan 2, etc.
            "year" -> SimpleDateFormat("MMM", Locale.getDefault())      // Jan, Feb, etc.
            "all" -> SimpleDateFormat("MMM yy", Locale.getDefault())    // Jan 24, Feb 24, etc.
            else -> SimpleDateFormat("MMM d", Locale.getDefault())
        }
        
        // For year and all-time views, consolidate labels to avoid overcrowding
        val labels = dateKeys.map { dateKey ->
            try {
                val date = inputFormat.parse(dateKey)
                if (date != null) outputFormat.format(date) else dateKey
            } catch (e: Exception) {
                dateKey
            }
        }
        
        // For year/all-time: group by month to reduce label count
        if (period.lowercase() in listOf("year", "all")) {
            return consolidateMonthlyLabels(labels, dateKeys)
        }
        
        return labels
    }
    
    /**
     * Consolidate labels by month for yearly/all-time views
     * Shows first occurrence of each month
     */
    private fun consolidateMonthlyLabels(labels: List<String>, @Suppress("UNUSED_PARAMETER") dateKeys: List<String>): List<String> {
        val seenMonths = mutableSetOf<String>()
        return labels.mapIndexed { _, label ->
            // Extract month part (e.g., "Jan" from "Jan 24")
            val monthPart = label.split(" ")[0]
            if (seenMonths.add(monthPart)) {
                label
            } else {
                ""  // Return empty string for duplicate months
            }
        }
    }
}
