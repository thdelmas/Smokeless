package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
     * Rolling-window reduction stats. Headline metric for the "reduce, don't quit"
     * frame: dominant signal is gradient (7d avg, change vs prior period), not binary
     * abstinence. See docs/design-notes/2026-05-13-streak-vs-reduce.md.
     */
    data class ReductionStats(
        val rollingAverage7d: Double,
        val velocityPercent: Double, // positive = reducing, negative = increasing
        val hasEnoughData: Boolean,
        // False when the prior-30 vs last-7 comparison would be misleading
        // (untracked gap between windows, or sparse logging in either). When
        // false, the UI suppresses the "% more/less than 30 days ago" copy
        // and shows a neutral baseline instead. See gh #19.
        val velocityComparable: Boolean,
        // Number of distinct days in the last 7 that have at least one
        // logged session. The UI uses this to disclose coverage so the
        // headline number can't smuggle a relapse past the user. See gh #21.
        val loggedDaysLast7: Int,
    )

    fun calculateReductionStats(sessions: List<SmokingSession>): ReductionStats {
        if (sessions.isEmpty()) {
            return ReductionStats(
                rollingAverage7d = 0.0,
                velocityPercent = 0.0,
                hasEnoughData = false,
                velocityComparable = false,
                loggedDaysLast7 = 0,
            )
        }

        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val start7d = now - 7 * day
        val start30d = now - 30 * day
        val startPrior = now - 60 * day // prior 30-day window: [60d, 30d) ago

        val sessionsLast7 = sessions.filter { it.timestamp >= start7d }
        val sessionsLast30 = sessions.filter { it.timestamp >= start30d }
        val sessionsPrior30 = sessions.filter { it.timestamp in startPrior until start30d }

        val avg7d = sessionsLast7.size / 7.0
        val avgPrior = sessionsPrior30.size / 30.0

        val firstSession = sessions.minOf { it.timestamp }
        val trackedDays = ((now - firstSession) / day).toInt() + 1
        // Calendar age alone isn't enough — a user with one session 14+ days
        // ago would still pass. Require some density of actual logging too.
        // See gh #20.
        val loggedDaysLast30 = sessionsLast30.map { it.timestamp / day }.distinct().size
        val hasEnoughData = trackedDays >= 14 && loggedDaysLast30 >= 5

        // velocityComparable: each window needs enough logged days, and there
        // must be no multi-week silence anywhere across the 60-day span (which
        // would mean the comparison spans a returning-user gap).
        val loggedDays = { ss: List<SmokingSession> -> ss.map { it.timestamp / day }.distinct().size }
        val loggedLast7 = loggedDays(sessionsLast7)
        val loggedPrior30 = loggedDays(sessionsPrior30)
        val sortedLast60 = sessions
            .filter { it.timestamp >= startPrior }
            .sortedBy { it.timestamp }
        var maxGapDays = 0L
        for (i in 1 until sortedLast60.size) {
            val gap = (sortedLast60[i].timestamp - sortedLast60[i - 1].timestamp) / day
            if (gap > maxGapDays) maxGapDays = gap
        }
        val velocityComparable = hasEnoughData &&
            loggedLast7 >= 4 &&
            loggedPrior30 >= 14 &&
            maxGapDays <= 14

        val velocity = when {
            !velocityComparable -> 0.0
            avgPrior < 0.01 && avg7d < 0.01 -> 0.0
            avgPrior < 0.01 -> -100.0 // started smoking from clean baseline
            else -> ((avgPrior - avg7d) / avgPrior) * 100.0
        }

        return ReductionStats(
            rollingAverage7d = avg7d,
            velocityPercent = velocity,
            hasEnoughData = hasEnoughData,
            velocityComparable = velocityComparable,
            loggedDaysLast7 = loggedLast7,
        )
    }

    /** Window after a craving log in which a smoke "cancels" the victory. */
    const val CRAVING_VICTORY_WINDOW_MS = 30L * 60 * 1000

    /**
     * The "moments of decision" view of recent activity. Per design note
     * 2026-05-13 (streak-vs-reduce), the cleanest reduction signal available
     * in current data is the ratio of held cravings to smokes — it answers
     * "when the urge hit, what did I do?" without resetting on slips.
     *
     * - [resistedCount] is verified-held cravings: window of 30 min fully
     *   elapsed, no real smoke landed inside it. Conservative on purpose.
     * - [smokedCount] is every session in the window. No verification needed.
     * - [resistancePercent] is resisted / (resisted + smoked), bounded 0–100.
     * - [vsPriorPercent] is the change vs. the immediately prior window of
     *   the same length. Positive = improving (more held, fewer smoked).
     *   null when there isn't enough prior-window data to compare honestly.
     */
    data class ResistanceStats(
        val resistedCount: Int,
        val smokedCount: Int,
        val resistancePercent: Double,
        val vsPriorPercent: Double?,
        val lookbackDays: Int,
    )

    fun calculateResistanceStats(
        cravings: List<Craving>,
        sessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
        lookbackDays: Int = 7,
    ): ResistanceStats {
        val day = TimeUnit.DAYS.toMillis(1)
        val windowMs = lookbackDays * day
        val windowStart = nowMs - windowMs
        val priorStart = nowMs - 2 * windowMs

        fun resistedIn(rangeStart: Long, rangeEnd: Long): Int {
            // A craving counts only if its 30-min outcome window is fully past.
            return cravings.count { c ->
                val inRange = c.timestamp in rangeStart until rangeEnd
                val windowElapsed = nowMs - c.timestamp >= CRAVING_VICTORY_WINDOW_MS
                if (!inRange || !windowElapsed) return@count false
                val windowEnd = c.timestamp + CRAVING_VICTORY_WINDOW_MS
                val smokedWithin = sessions.any { s ->
                    val realSmoke = s.timestamp - s.substance.exposureMs
                    realSmoke in c.timestamp..windowEnd
                }
                !smokedWithin
            }
        }

        fun smokedIn(rangeStart: Long, rangeEnd: Long): Int =
            sessions.count { it.timestamp in rangeStart until rangeEnd }

        val resisted = resistedIn(windowStart, nowMs)
        val smoked = smokedIn(windowStart, nowMs)
        val total = resisted + smoked
        val pct = if (total == 0) 0.0 else (resisted.toDouble() / total) * 100.0

        // Prior window: only compute delta if there's any prior activity at all.
        val priorResisted = resistedIn(priorStart, windowStart)
        val priorSmoked = smokedIn(priorStart, windowStart)
        val priorTotal = priorResisted + priorSmoked
        val priorPct = if (priorTotal == 0) null
            else (priorResisted.toDouble() / priorTotal) * 100.0
        val delta = if (priorPct == null) null else pct - priorPct

        return ResistanceStats(
            resistedCount = resisted,
            smokedCount = smoked,
            resistancePercent = pct,
            vsPriorPercent = delta,
            lookbackDays = lookbackDays,
        )
    }

    /** "How am I doing right now" — one-glance verdict for today vs typical pace. */
    enum class PaceState {
        CALIBRATING, // not enough history to compare against
        AHEAD,       // notably below typical-by-now
        ON_PACE,     // within ±25% of typical-by-now
        BEHIND,      // notably above typical-by-now
        CLEAN_TODAY, // baseline is near-zero and today is also clean
        CLEAN_BREAK, // baseline is near-zero but smoked today
    }

    data class TodayPace(
        val state: PaceState,
        val actualToday: Int,
        val typicalByNow: Double,
        val baselineDailyAvg: Double,
    )

    /**
     * Compute today's pace against a rolling 14-day baseline, time-of-day
     * aware: typical-by-now scales with the fraction of the day elapsed. The
     * baseline excludes today (so a heavy morning doesn't reset its own bar).
     * Requires at least 3 prior days of tracking — fewer than that and we
     * return CALIBRATING rather than a misleading verdict.
     */
    fun calculateTodayPace(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
    ): TodayPace {
        if (allSessions.isEmpty()) return TodayPace(PaceState.CALIBRATING, 0, 0.0, 0.0)

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val actualToday = allSessions.count { it.timestamp >= startOfToday }

        val day = TimeUnit.DAYS.toMillis(1)
        val firstSession = allSessions.minOf { it.timestamp }
        val trackedDays = ((nowMs - firstSession) / day).toInt() + 1
        val priorDays = (trackedDays - 1).coerceAtMost(14)
        if (priorDays < 3) return TodayPace(PaceState.CALIBRATING, actualToday, 0.0, 0.0)

        val priorStart = startOfToday - priorDays * day
        val priorCount = allSessions.count { it.timestamp in priorStart until startOfToday }
        val baselineDailyAvg = priorCount.toDouble() / priorDays

        val dayFraction = ((nowMs - startOfToday).toDouble() / day).coerceIn(0.0, 1.0)
        val typicalByNow = baselineDailyAvg * dayFraction

        val state = when {
            baselineDailyAvg < 0.5 -> if (actualToday == 0) PaceState.CLEAN_TODAY else PaceState.CLEAN_BREAK
            actualToday <= typicalByNow * 0.75 -> PaceState.AHEAD
            actualToday <= typicalByNow * 1.25 -> PaceState.ON_PACE
            else -> PaceState.BEHIND
        }
        return TodayPace(state, actualToday, typicalByNow, baselineDailyAvg)
    }

    data class CravingVictories(
        /** Number of confirmed victories beyond the cursor (verified: window elapsed, no smoke landed). */
        val newCount: Int,
        /** Updated cursor — advance past every craving whose window has fully elapsed, win or not. */
        val newCursor: Long,
    )

    /**
     * Detect craving victories: a logged craving counts when its 30-min window
     * has fully elapsed AND no real smoke landed inside it. Smokes are matched
     * against the actual smoke moment (timestamp minus the exposure offset
     * already baked into [SmokingSession.timestamp]).
     *
     * Caller should initialize the cursor to "now" on first run to avoid
     * surfacing a backlog of historical victories.
     */
    fun detectCravingVictories(
        cravings: List<Craving>,
        sessions: List<SmokingSession>,
        cursor: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): CravingVictories {
        val confirmable = cravings.filter {
            it.timestamp > cursor && nowMs - it.timestamp >= CRAVING_VICTORY_WINDOW_MS
        }
        if (confirmable.isEmpty()) return CravingVictories(0, cursor)
        var victories = 0
        var newCursor = cursor
        for (craving in confirmable) {
            val windowEnd = craving.timestamp + CRAVING_VICTORY_WINDOW_MS
            val smokedWithin = sessions.any { s ->
                val realSmokeTime = s.timestamp - s.substance.exposureMs
                realSmokeTime in craving.timestamp..windowEnd
            }
            if (!smokedWithin) victories++
            if (craving.timestamp > newCursor) newCursor = craving.timestamp
        }
        return CravingVictories(victories, newCursor)
    }

    /**
     * Lifetime smoke-free time, additive and never reset by a slip.
     * = (now - first logged session) - sum of per-session exposure windows.
     * Counterweight to clean-day streaks so honest logging doesn't cost the
     * user their headline number.
     */
    fun calculateBankedSmokeFreeMs(sessions: List<SmokingSession>): Long {
        if (sessions.isEmpty()) return 0L
        val firstTimestamp = sessions.minOf { it.timestamp }
        val now = System.currentTimeMillis()
        val tracked = now - firstTimestamp
        val exposure = sessions.sumOf { it.substance.exposureMs }
        return max(0L, tracked - exposure)
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

    /**
     * Per-substance "today vs typical pace" entry: a [TodayPace] computed over
     * the subset of sessions for a single substance. Used to give the user
     * separate verdicts for tobacco and cannabis instead of one blended number.
     */
    data class SubstancePace(
        val substance: Substance,
        val pace: TodayPace,
    )

    /**
     * Compute [TodayPace] independently for each substance the user has logged
     * in the lookback window. Substances with zero history in that window are
     * omitted — there's no useful comparison to draw.
     */
    fun calculatePerSubstancePace(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SubstancePace> {
        if (allSessions.isEmpty()) return emptyList()
        val day = TimeUnit.DAYS.toMillis(1)
        val lookbackStart = nowMs - 14 * day
        val recentSubstances = allSessions
            .filter { it.timestamp >= lookbackStart }
            .map { it.substance }
            .toSet()
        // Include substances seen today even if recent window is sparse.
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val todaySubstances = allSessions
            .filter { it.timestamp >= startOfToday }
            .map { it.substance }
            .toSet()
        val substances = (recentSubstances + todaySubstances).sortedBy { it.ordinal }
        return substances.map { sub ->
            val subset = allSessions.filter { it.substance == sub }
            SubstancePace(sub, calculateTodayPace(subset, nowMs))
        }
    }

    /**
     * "When did you first smoke today vs. what time you usually do?" — a
     * morning-anchor signal. Pushing the first cigarette later in the day is a
     * well-known reduction lever: each delayed hour is real exposure avoided.
     */
    data class FirstSmokeOfDay(
        /** Milliseconds since start-of-today for the first smoke; null if none yet today. */
        val todayFirstMsFromStartOfDay: Long?,
        /** Hour-of-day (0..24) of the typical first smoke over the lookback window; null if too little data. */
        val typicalFirstHour: Double?,
        /** today minus typical, in minutes; positive = later than usual (better). null if either side missing. */
        val deltaMinutes: Long?,
        /** Number of prior days that contributed to the typical estimate. */
        val daysContributing: Int,
    )

    fun calculateFirstSmokeOfDay(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
        lookbackDays: Int = 14,
    ): FirstSmokeOfDay {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val day = TimeUnit.DAYS.toMillis(1)
        val firstTodayTs = allSessions
            .filter { it.timestamp >= startOfToday }
            .minOfOrNull { it.timestamp }
        val todayOffsetMs = firstTodayTs?.let { it - startOfToday }

        // Bucket prior sessions by day and take the earliest each day.
        val priorStart = startOfToday - lookbackDays * day
        val priorByDay = allSessions
            .filter { it.timestamp in priorStart until startOfToday }
            .groupBy { (it.timestamp - priorStart) / day }
        val firstHours = priorByDay.values.mapNotNull { dayList ->
            val earliest = dayList.minOfOrNull { it.timestamp } ?: return@mapNotNull null
            val c = Calendar.getInstance().apply { timeInMillis = earliest }
            c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
        }
        val typicalHour = if (firstHours.size >= 3) firstHours.average() else null

        val delta: Long? = if (typicalHour != null && todayOffsetMs != null) {
            val todayHour = todayOffsetMs.toDouble() / TimeUnit.HOURS.toMillis(1)
            ((todayHour - typicalHour) * 60.0).toLong()
        } else null

        return FirstSmokeOfDay(
            todayFirstMsFromStartOfDay = todayOffsetMs,
            typicalFirstHour = typicalHour,
            deltaMinutes = delta,
            daysContributing = firstHours.size,
        )
    }

    /**
     * Plasma half-life (in hours) for the body's response to each substance.
     * These are short-cycle approximations the user can feel — not legal-test
     * windows. Sources:
     *  - Nicotine plasma half-life: ~2h (Hukkanen et al., 2005;
     *    PMID 16968948). Cotinine is longer (~16h) but nicotine itself drives
     *    the acute "still wired" sensation we're modeling here.
     *  - Δ9-THC plasma half-life in occasional users: ~24–30h after a
     *    single dose (Huestis, 2007). Chronic heavy use extends this; we
     *    pick 25h as a midpoint usable for self-reporting.
     */
    fun halfLifeHours(substance: Substance): Double = when (substance) {
        Substance.TOBACCO -> 2.0
        Substance.CANNABIS -> 25.0
    }

    /**
     * Estimated remaining substance level in plasma, modeled as simple
     * first-order decay from the last logged exposure: pct = 100 * 0.5^(t/h).
     * This is a back-of-the-envelope teaching aid, not a clinical
     * measurement — its purpose is to show the user *why* spacing matters.
     */
    data class SubstanceLevel(
        val substance: Substance,
        val percentRemaining: Double,
        val hoursSinceLast: Double,
        val halfLifeHours: Double,
        val lastTimestamp: Long?,
    )

    /**
     * Per-substance hour-of-day distribution over a lookback window. The
     * `hourCounts` field has exactly 24 entries (index 0 = midnight..00:59,
     * index 23 = 23:00..23:59). `peakHours` lists hours whose count is
     * meaningfully above the user's baseline rate — the "trigger windows"
     * worth flagging in copy. `nearPeakNow` is true when the current hour is
     * within ±1h of any peak, used to drive a heads-up banner.
     *
     * Designed for the "anticipate, don't just react" frame: the existing
     * surface only describes what already happened. This one says *when* the
     * craving is most likely to fire.
     */
    data class TriggerWindow(
        val substance: Substance,
        val hourCounts: IntArray,
        val peakHours: List<Int>,
        val totalSessions: Int,
        val nearPeakNow: Boolean,
    ) {
        // Auto-generated equals/hashCode would compare IntArray by reference;
        // override so tests and diffing behave predictably.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TriggerWindow) return false
            return substance == other.substance &&
                hourCounts.contentEquals(other.hourCounts) &&
                peakHours == other.peakHours &&
                totalSessions == other.totalSessions &&
                nearPeakNow == other.nearPeakNow
        }
        override fun hashCode(): Int {
            var result = substance.hashCode()
            result = 31 * result + hourCounts.contentHashCode()
            result = 31 * result + peakHours.hashCode()
            result = 31 * result + totalSessions
            result = 31 * result + nearPeakNow.hashCode()
            return result
        }
    }

    /**
     * Compute trigger windows per substance over the last [lookbackDays] days.
     * A peak is any hour whose share of total sessions is ≥ [peakMultiplier]
     * times the uniform-distribution baseline (1/24). Default multiplier of
     * 1.8 means an hour has to carry at least 1.8× its "fair share" to count.
     * Returns at most 4 peaks per substance, sorted by hour ascending for
     * display.
     */
    fun calculateTriggerWindows(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
        lookbackDays: Int = 30,
        peakMultiplier: Double = 1.8,
        minSessionsToReport: Int = 5,
    ): List<TriggerWindow> {
        if (allSessions.isEmpty()) return emptyList()
        val day = TimeUnit.DAYS.toMillis(1)
        val lookbackStart = nowMs - lookbackDays * day
        val windowSessions = allSessions.filter { it.timestamp >= lookbackStart }
        if (windowSessions.isEmpty()) return emptyList()

        val nowHour = Calendar.getInstance().apply { timeInMillis = nowMs }
            .get(Calendar.HOUR_OF_DAY)

        val grouped = windowSessions.groupBy { it.substance }
        return grouped.entries
            .sortedBy { it.key.ordinal }
            .map { (substance, sessions) ->
                val counts = IntArray(24)
                val cal = Calendar.getInstance()
                for (s in sessions) {
                    cal.timeInMillis = s.timestamp
                    counts[cal.get(Calendar.HOUR_OF_DAY)]++
                }
                val total = sessions.size
                val peaks = if (total >= minSessionsToReport) {
                    val threshold = (total / 24.0) * peakMultiplier
                    (0..23)
                        .filter { counts[it] >= threshold && counts[it] >= 2 }
                        .sortedByDescending { counts[it] }
                        .take(4)
                        .sorted()
                } else emptyList()
                val nearPeak = peaks.any { peakHour ->
                    val diff = ((peakHour - nowHour + 36) % 24) - 12
                    abs(diff) <= 1
                }
                TriggerWindow(
                    substance = substance,
                    hourCounts = counts,
                    peakHours = peaks,
                    totalSessions = total,
                    nearPeakNow = nearPeak,
                )
            }
            // Drop substances with zero useful signal — they'd render as empty rows.
            .filter { it.totalSessions > 0 }
    }

    fun estimateSubstanceLevels(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SubstanceLevel> {
        if (allSessions.isEmpty()) return emptyList()
        val seen = allSessions.map { it.substance }.toSet().sortedBy { it.ordinal }
        return seen.map { sub ->
            val last = allSessions.filter { it.substance == sub }.maxOfOrNull { it.timestamp }
            val halfLife = halfLifeHours(sub)
            if (last == null) {
                SubstanceLevel(sub, 0.0, Double.POSITIVE_INFINITY, halfLife, null)
            } else {
                val hours = max(0.0, (nowMs - last).toDouble() / TimeUnit.HOURS.toMillis(1))
                val pct = 100.0 * 0.5.pow(hours / halfLife)
                SubstanceLevel(sub, pct, hours, halfLife, last)
            }
        }
    }
}





