package com.smokless.smokeless.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smokless.smokeless.data.AppDatabase
import com.smokless.smokeless.data.repository.SmokingRepository
import com.smokless.smokeless.data.entity.Substance
import com.smokless.smokeless.util.HealthBenefits
import com.smokless.smokeless.util.NotificationHelper
import com.smokless.smokeless.util.ScoreCalculator
import com.smokless.smokeless.util.SubstanceCopy
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
        // Cursor for craving-victory detection: timestamp of the latest craving
        // whose 30-min outcome window has been evaluated and acknowledged to
        // the user. Initialized to "now" on first run so historical cravings
        // don't all surface as victories at once.
        private const val KEY_LAST_VICTORY_CURSOR = "lastVictoryCursorTs"
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

    // Interval-based countdown
    private val _targetInterval = MutableLiveData(0L) // target wait time in ms
    val targetInterval: LiveData<Long> = _targetInterval

    private val _timeRemaining = MutableLiveData(0L) // countdown remaining in ms (negative = bonus)
    val timeRemaining: LiveData<Long> = _timeRemaining
    
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

    // Reduction trend (reduce-don't-quit hero signal)
    private val _reductionStats = MutableLiveData<ScoreCalculator.ReductionStats>()
    val reductionStats: LiveData<ScoreCalculator.ReductionStats> = _reductionStats

    // Lifetime banked smoke-free time. Never resets on a slip — its job is to
    // remove the perverse incentive to skip logging to preserve a streak.
    private val _bankedSmokeFreeMs = MutableLiveData(0L)
    val bankedSmokeFreeMs: LiveData<Long> = _bankedSmokeFreeMs

    // One-shot signal: number of cravings that verifiably held (no smoke
    // within 30 min) since the last time the UI acknowledged them. Activity
    // calls [dismissNewVictories] after showing.
    private val _newCravingVictories = MutableLiveData(0)
    val newCravingVictories: LiveData<Int> = _newCravingVictories

    // "How am I doing right now" — today vs typical pace, time-of-day aware.
    private val _todayPace = MutableLiveData<ScoreCalculator.TodayPace>()
    val todayPace: LiveData<ScoreCalculator.TodayPace> = _todayPace

    // Per-substance pace (tobacco vs cannabis comparisons separately).
    private val _perSubstancePace = MutableLiveData<List<ScoreCalculator.SubstancePace>>(emptyList())
    val perSubstancePace: LiveData<List<ScoreCalculator.SubstancePace>> = _perSubstancePace

    // First-smoke-of-day timing: today vs typical first-smoke hour.
    private val _firstSmokeOfDay = MutableLiveData<ScoreCalculator.FirstSmokeOfDay>()
    val firstSmokeOfDay: LiveData<ScoreCalculator.FirstSmokeOfDay> = _firstSmokeOfDay

    // Half-life decay estimate per substance.
    private val _substanceLevels = MutableLiveData<List<ScoreCalculator.SubstanceLevel>>(emptyList())
    val substanceLevels: LiveData<List<ScoreCalculator.SubstanceLevel>> = _substanceLevels

    // Hour-of-day distribution & peak windows per substance.
    private val _triggerWindows = MutableLiveData<List<ScoreCalculator.TriggerWindow>>(emptyList())
    val triggerWindows: LiveData<List<ScoreCalculator.TriggerWindow>> = _triggerWindows

    // Resistance signal: verified-held cravings vs. smokes, last 7 days.
    private val _resistanceStats = MutableLiveData<ScoreCalculator.ResistanceStats>()
    val resistanceStats: LiveData<ScoreCalculator.ResistanceStats> = _resistanceStats

    // Sunday-recap-shaped rollup of the last 7 days.
    private val _weeklyDigest = MutableLiveData<ScoreCalculator.WeeklyDigest>()
    val weeklyDigest: LiveData<ScoreCalculator.WeeklyDigest> = _weeklyDigest

    // Snapshot taken on each DB refresh so the per-second timer can tick the
    // banked counter without touching the database.
    private var firstSessionTimestamp = 0L
    private var totalExposureMs = 0L

    // Snapshot inputs for the today-pace ticker so updateTimer can re-evaluate
    // the pace verdict as the day progresses, without hitting the DB.
    private var paceBaselineDailyAvg = 0.0
    private var paceActualToday = 0
    private var paceStartOfToday = 0L
    private var paceHasBaseline = false

    // Per-substance last-smoke timestamps snapshotted on refresh. The decay
    // ticker re-evaluates each second from these without touching the DB.
    private var lastSubstanceTimestamps: Map<Substance, Long> = emptyMap()

    // Trigger windows snapshot — peak hours don't move minute-to-minute, but
    // the nearPeakNow flag flips as wall-clock advances, so the ticker
    // recomputes it from the cached peakHours.
    private var triggerWindowsSnapshot: List<ScoreCalculator.TriggerWindow> = emptyList()
    private var lastNearPeakHour: Int = -1

    // Dominant substance for headline copy. Drives unit nouns and the
    // "smoke-free / clean" labeling per ROADMAP §2.2.
    private val _primarySubstance = MutableLiveData(Substance.DEFAULT)
    val primarySubstance: LiveData<Substance> = _primarySubstance
    
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

    fun recordSmokeWithId(
        exposureOffsetMs: Long = 0L,
        substance: com.smokless.smokeless.data.entity.Substance =
            com.smokless.smokeless.data.entity.Substance.DEFAULT,
        quantity: Double = 1.0,
        callback: (Long) -> Unit,
    ) {
        AppDatabase.databaseExecutor.execute {
            val id = repository.recordSmokeSync(exposureOffsetMs, substance, quantity)
            refreshData()
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(id) }
        }
    }

    fun undoSmoke(id: Long) {
        AppDatabase.databaseExecutor.execute {
            repository.deleteSession(id)
            refreshData()
        }
    }
    
    /**
     * Record a craving resisted
     */
    fun recordCravingResisted() {
        AppDatabase.databaseExecutor.execute {
            repository.recordCraving()
        }
    }

    fun getResistedCravingsCount(callback: (Int) -> Unit) {
        AppDatabase.databaseExecutor.execute {
            val count = repository.getAllCravings().size
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(count) }
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

            // Detect verified craving victories since last acknowledgement.
            detectCravingVictories()
        }
    }

    private fun detectCravingVictories() {
        var cursor = prefs.getLong(KEY_LAST_VICTORY_CURSOR, 0L)
        if (cursor == 0L) {
            // First run: anchor at "now" so historical cravings don't all
            // surface at once. Their windows are already long-past, so this
            // is a one-shot reset; future cravings will be evaluated normally.
            cursor = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_VICTORY_CURSOR, cursor).apply()
            return
        }
        val cravings = repository.getAllCravings()
        val sessions = repository.getAllSessionsSync()
        val result = ScoreCalculator.detectCravingVictories(cravings, sessions, cursor)
        if (result.newCursor != cursor) {
            prefs.edit().putLong(KEY_LAST_VICTORY_CURSOR, result.newCursor).apply()
        }
        if (result.newCount > 0) {
            _newCravingVictories.postValue(result.newCount)
        }
    }

    fun dismissNewVictories() {
        _newCravingVictories.value = 0
    }
    
    /**
     * Update only the timer (called frequently)
     */
    fun updateTimer() {
        val timeSinceLastSmoke = ScoreCalculator.calculateTimeSinceLastSmoke(lastTimestamp)
        _currentScore.postValue(timeSinceLastSmoke)

        // Check for milestone achievements
        checkMilestones(timeSinceLastSmoke)

        // Update countdown: remaining = target - elapsed (negative = bonus time)
        val target = _targetInterval.value ?: 0L
        if (target > 0L) {
            _timeRemaining.postValue(target - timeSinceLastSmoke)
        }

        // Update interval progress (fills towards 100% as countdown reaches 0)
        if (target > 0L) {
            val progress = ScoreCalculator.calculateIntervalProgress(timeSinceLastSmoke, target)
            _currentPercentage.postValue(progress)
        }

        // Tick banked smoke-free counter using the snapshot from last refresh
        if (firstSessionTimestamp > 0L) {
            val banked = max(0L, System.currentTimeMillis() - firstSessionTimestamp - totalExposureMs)
            _bankedSmokeFreeMs.postValue(banked)
        }

        // Re-evaluate today-pace verdict from the cached baseline. The day's
        // expected count grows with elapsed time, so this can flip
        // BEHIND → ON_PACE → AHEAD without a DB hit.
        if (paceHasBaseline && paceStartOfToday > 0L) {
            val nowMs = System.currentTimeMillis()
            val dayMs = java.util.concurrent.TimeUnit.DAYS.toMillis(1)
            val dayFraction = ((nowMs - paceStartOfToday).toDouble() / dayMs).coerceIn(0.0, 1.0)
            val typicalByNow = paceBaselineDailyAvg * dayFraction
            val state = when {
                paceBaselineDailyAvg < 0.5 ->
                    if (paceActualToday == 0) ScoreCalculator.PaceState.CLEAN_TODAY
                    else ScoreCalculator.PaceState.CLEAN_BREAK
                paceActualToday <= typicalByNow * 0.75 -> ScoreCalculator.PaceState.AHEAD
                paceActualToday <= typicalByNow * 1.25 -> ScoreCalculator.PaceState.ON_PACE
                else -> ScoreCalculator.PaceState.BEHIND
            }
            _todayPace.postValue(
                ScoreCalculator.TodayPace(state, paceActualToday, typicalByNow, paceBaselineDailyAvg)
            )
        }

        // Re-evaluate nearPeakNow when the wall-clock hour changes. Cheap —
        // only recomputes flags from the cached peak-hour lists. Avoids
        // re-emitting the LiveData every second.
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (nowHour != lastNearPeakHour && triggerWindowsSnapshot.isNotEmpty()) {
            lastNearPeakHour = nowHour
            val refreshed = triggerWindowsSnapshot.map { tw ->
                val near = tw.peakHours.any { peak ->
                    val diff = ((peak - nowHour + 36) % 24) - 12
                    kotlin.math.abs(diff) <= 1
                }
                if (near == tw.nearPeakNow) tw else tw.copy(nearPeakNow = near)
            }
            triggerWindowsSnapshot = refreshed
            _triggerWindows.postValue(refreshed)
        }

        // Decay substance levels from the snapshot. Cheap math, smooths
        // the percent-remaining as time passes.
        if (lastSubstanceTimestamps.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            val levels = lastSubstanceTimestamps.entries
                .sortedBy { it.key.ordinal }
                .map { (sub, ts) ->
                    val halfLife = ScoreCalculator.halfLifeHours(sub)
                    val hours = max(
                        0.0,
                        (nowMs - ts).toDouble() / java.util.concurrent.TimeUnit.HOURS.toMillis(1),
                    )
                    val pct = 100.0 * Math.pow(0.5, hours / halfLife)
                    ScoreCalculator.SubstanceLevel(sub, pct, hours, halfLife, ts)
                }
            _substanceLevels.postValue(levels)
        }
    }
    
    /**
     * Check if user reached a health milestone and send notification
     */
    private fun checkMilestones(score: Long) {
        val hours = score / 3_600_000
        
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
                    val copy = SubstanceCopy.forSubstance(_primarySubstance.value ?: Substance.DEFAULT)
                    NotificationHelper.showEncouragementNotification(
                        getApplication<Application>().applicationContext,
                        hours.toInt(),
                        copy,
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
            val sessions = repository.getSessionsForScope(period)
            calculateMoneySaved(sessions, period)
        }
    }
    
    /**
     * Calculate statistics for all time periods
     */
    private fun calculateAllScores() {
        // Get all sessions for goal calculation
        val allSessions = repository.getAllSessionsSync()

        // Reduction trend — independent of selected period, uses all sessions
        _reductionStats.postValue(ScoreCalculator.calculateReductionStats(allSessions))

        // Snapshot inputs for the banked-hours ticker; updateTimer() will
        // recompute from these without re-querying the DB each second.
        firstSessionTimestamp = if (allSessions.isEmpty()) 0L else allSessions.minOf { it.timestamp }
        totalExposureMs = allSessions.sumOf { it.substance.exposureMs }
        _bankedSmokeFreeMs.postValue(ScoreCalculator.calculateBankedSmokeFreeMs(allSessions))

        // Snapshot today-pace inputs so the per-second tick can re-evaluate
        // the verdict as the day's expected count grows.
        val pace = ScoreCalculator.calculateTodayPace(allSessions)
        _todayPace.postValue(pace)
        paceBaselineDailyAvg = pace.baselineDailyAvg
        paceActualToday = pace.actualToday
        paceHasBaseline = pace.state != ScoreCalculator.PaceState.CALIBRATING
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        paceStartOfToday = cal.timeInMillis

        // Pedagogical "today vs before" panel inputs.
        _perSubstancePace.postValue(ScoreCalculator.calculatePerSubstancePace(allSessions))
        _firstSmokeOfDay.postValue(ScoreCalculator.calculateFirstSmokeOfDay(allSessions))
        _substanceLevels.postValue(ScoreCalculator.estimateSubstanceLevels(allSessions))
        _triggerWindows.postValue(ScoreCalculator.calculateTriggerWindows(allSessions))
        val cravings = repository.getAllCravings()
        _resistanceStats.postValue(
            ScoreCalculator.calculateResistanceStats(cravings, allSessions)
        )
        lastSubstanceTimestamps = allSessions
            .groupBy { it.substance }
            .mapValues { (_, list) -> list.maxOf { it.timestamp } }
        triggerWindowsSnapshot = ScoreCalculator.calculateTriggerWindows(allSessions)

        // Primary substance drives headline copy (units, clean label).
        val primary = SubstanceCopy.primarySubstance(allSessions)
        _primarySubstance.postValue(primary)
        val copy = SubstanceCopy.forSubstance(primary)
        _weeklyDigest.postValue(
            ScoreCalculator.calculateWeeklyDigest(allSessions, cravings, primary)
        )

        // Calculate goal and progress based on current goal period
        updateGoalForPeriod()

        // Calculate scores for each period
        _allTimeScores.postValue(calculateScopeScores(allSessions, "all", copy))
        _yearScores.postValue(calculateScopeScores(repository.getSessionsForScope("year"), "year", copy))
        _monthScores.postValue(calculateScopeScores(repository.getSessionsForScope("month"), "month", copy))
        _weekScores.postValue(calculateScopeScores(repository.getSessionsForScope("week"), "week", copy))
        _dayScores.postValue(calculateScopeScores(repository.getSessionsForScope("day"), "day", copy))
        
        // Calculate chart data
        val chartSessions = repository.getSessionsForScope(currentChartPeriod)
        calculateChartData(chartSessions, currentChartPeriod)
        
        // Calculate money saved for current period
        val moneySessions = repository.getSessionsForScope(currentGoalPeriod)
        calculateMoneySaved(moneySessions, currentGoalPeriod)
    }
    
    /**
     * Calculate total money saved based on avoided cigarettes
     */
    private fun calculateMoneySaved(sessions: List<com.smokless.smokeless.data.entity.SmokingSession>, scope: String) {
        val packPrice = prefs.getFloat(KEY_PACK_PRICE, DEFAULT_PACK_PRICE)
        val cigsPerPack = prefs.getInt(KEY_CIGS_PER_PACK, DEFAULT_CIGS_PER_PACK)
        val currency = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY

        val costPerCig = packPrice / cigsPerPack

        val stats = ScoreCalculator.calculatePeriodStats(sessions, scope)

        val baselineDaily = if (stats.averagePerDay > 0) stats.averagePerDay else 10.0
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

        // Calculate interval-based target (how long to wait before next smoke)
        val target = ScoreCalculator.calculateTargetInterval(allSessions, difficulty)
        _targetInterval.postValue(target)

        // Calculate countdown progress
        val timeSinceLastSmoke = ScoreCalculator.calculateTimeSinceLastSmoke(lastTimestamp)
        if (target > 0L) {
            _timeRemaining.postValue(target - timeSinceLastSmoke)
            val progress = ScoreCalculator.calculateIntervalProgress(timeSinceLastSmoke, target)
            _currentPercentage.postValue(progress)
        }

        // Also keep legacy goal for period stats display
        val baselinePeriod = when (currentGoalPeriod) {
            "day" -> "month"
            "week" -> "month"
            else -> currentGoalPeriod
        }
        val goal = ScoreCalculator.calculateGoal(allSessions, baselinePeriod, difficulty)
        _currentGoal.postValue(goal)

        updateHeroMetrics()
    }

    /**
     * Update hero section metrics based on selected period
     */
    private fun updateHeroMetrics() {
        when (currentGoalPeriod) {
            "day" -> {
                _heroLabel.postValue("WAIT BEFORE NEXT")
                _heroUnit.postValue("")
            }
            "week", "month", "year", "all" -> {
                // For other periods: show average interval
                val allSessions = repository.getAllSessionsSync()
                val avgInterval = ScoreCalculator.calculateAverageInterval(allSessions)
                val avgHours = avgInterval / (1000.0 * 60.0 * 60.0)
                _heroValue.postValue(avgHours)
                _heroLabel.postValue("AVG INTERVAL")
                _heroUnit.postValue(if (avgHours >= 1.0) "hours" else "minutes")
            }
        }
    }
    
    /**
     * Calculate comprehensive statistics for a scope
     */
    private fun calculateScopeScores(
        sessions: List<com.smokless.smokeless.data.entity.SmokingSession>,
        scope: String,
        copy: SubstanceCopy = SubstanceCopy.TOBACCO,
    ): List<ScoreData> {
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
            unit = copy.perDay,
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
        
        // 5. Total smoked - dose-weighted ("3.5 cigs" = three full plus a half).
        // value (Long) carries the rounded integer for legacy callers; the
        // adapter prefers decimalValue when set.
        val totalRounded = kotlin.math.round(stats.totalCigarettes).toLong()
        scores.add(ScoreData(
            label = "Total Smoked",
            value = totalRounded,
            decimalValue = stats.totalCigarettes,
            percentage = 0.0,  // No percentage needed for raw total
            unit = copy.unitFor(totalRounded),
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
