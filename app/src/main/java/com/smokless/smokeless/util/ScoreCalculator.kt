package com.smokless.smokeless.util

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
     * Statistics for a given time period.
     *
     * Dose-weighted: [totalCigarettes], [averagePerDay], [bestDay], [worstDay]
     * sum SmokingSession.quantity rather than event counts. A user who logs
     * five drags (0.25 each) registers as 1.25 cig-equivalents, not 5.
     * [cleanDays], [totalDays], and the streak fields stay event-based — a
     * day with any logged session, even a drag, is not "clean."
     */
    data class PeriodStats(
        val totalCigarettes: Double,
        val averagePerDay: Double,
        val cleanDays: Int,
        val totalDays: Int,
        val currentStreak: Int,
        val bestStreak: Int,
        val trend: Double,  // Positive = improving (fewer cigs), Negative = worsening
        val frequency: Double,  // Cig-equivalents per day (including clean days)
        val bestDay: Double,  // Lowest non-zero daily dose
        val worstDay: Double  // Highest daily dose
    )
    
    /**
     * Calculate comprehensive statistics for a period
     */
    fun calculatePeriodStats(sessions: List<SmokingSession>, scope: String): PeriodStats {
        if (sessions.isEmpty()) {
            // When there's no data, return zeros with proper context
            // Note: totalDays = 0 signals "no data" vs "perfect period"
            return PeriodStats(
                totalCigarettes = 0.0,
                averagePerDay = 0.0,
                cleanDays = 0,
                totalDays = 0,  // Key indicator: 0 days means no tracking started
                currentStreak = 0,
                bestStreak = 0,
                trend = 0.0,
                frequency = 0.0,
                bestDay = 0.0,
                worstDay = 0.0
            )
        }

        // Two parallel daily breakdowns:
        //  - dailyCounts (Int): event counts. Drives streaks, clean-day
        //    counting, and the trend calculation (where a slip is a slip
        //    regardless of dose).
        //  - dailyDoses (Double): summed quantity. Drives every dose-weighted
        //    headline (totalCigarettes, averagePerDay, bestDay/worstDay).
        val dailyCounts = getDailyCountsForScope(sessions, scope)
        val dailyDoses = getDailyQuantityForScope(sessions, scope)
        val totalDays = dailyCounts.size

        val totalCigarettes = dailyDoses.values.sum()
        val cleanDays = dailyCounts.count { it.value == 0 }

        val currentStreak = calculateCurrentStreak(dailyCounts)
        val bestStreak = calculateBestStreak(dailyCounts)

        val averagePerDay = if (totalDays > 0) totalCigarettes / totalDays else 0.0

        val trend = calculateTrend(dailyCounts)

        val doses = dailyDoses.values.filter { it > 0.0 }
        val bestDay = doses.minOrNull() ?: 0.0
        val worstDay = doses.maxOrNull() ?: 0.0

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
     * Parallel to [getDailyCountsForScope] but summing SmokingSession.quantity
     * instead of counting events. Days with zero sessions appear with 0.0.
     */
    fun getDailyQuantityForScope(
        sessions: List<SmokingSession>,
        scope: String,
    ): LinkedHashMap<String, Double> {
        val daily = LinkedHashMap<String, Double>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        if (sessions.isEmpty()) return daily

        val firstSessionTime = sessions.minOf { it.timestamp }
        val scopeStartTime = System.currentTimeMillis() - when (scope.lowercase()) {
            "year" -> TimeUnit.DAYS.toMillis(365)
            "month" -> TimeUnit.DAYS.toMillis(30)
            "week" -> TimeUnit.DAYS.toMillis(7)
            "day" -> TimeUnit.DAYS.toMillis(1)
            else -> Long.MAX_VALUE
        }
        val startTime = if (scope.lowercase() == "all") firstSessionTime
            else max(firstSessionTime, scopeStartTime)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = startTime
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (!calendar.after(endCalendar)) {
            daily[dateFormat.format(calendar.time)] = 0.0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        for (session in sessions) {
            val dayKey = dateFormat.format(Date(session.timestamp))
            if (daily.containsKey(dayKey)) {
                daily[dayKey] = daily[dayKey]!! + session.quantity
            }
        }
        return daily
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
     * Chart bucketing by time range. Each range picks the bucket size that
     * gives a readable bar count:
     *
     *  - "week"  → last 7 daily buckets, keys "yyyy-MM-dd"
     *  - "month" → last 30 daily buckets, keys "yyyy-MM-dd"
     *  - "year"  → last 12 monthly buckets, keys "yyyy-MM"
     *
     * Zero-fills empty buckets so the chart shows continuity.
     */
    fun getCountsByRange(
        sessions: List<SmokingSession>,
        range: String,
    ): LinkedHashMap<String, Int> = when (range.lowercase()) {
        "year" -> getMonthlyCounts(sessions, monthsBack = 12)
        "week" -> getDailyCountsBack(sessions, daysBack = 7)
        else -> getDailyCountsBack(sessions, daysBack = 30)
    }

    private fun getDailyCountsBack(
        sessions: List<SmokingSession>,
        daysBack: Int,
    ): LinkedHashMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(daysBack - 1))
        }
        val endCal = Calendar.getInstance()
        while (!cal.after(endCal)) {
            counts[keyFormat.format(cal.time)] = 0
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        for (session in sessions) {
            val key = keyFormat.format(Date(session.timestamp))
            if (counts.containsKey(key)) {
                counts[key] = counts[key]!! + 1
            }
        }
        return counts
    }

    private fun getMonthlyCounts(
        sessions: List<SmokingSession>,
        monthsBack: Int,
    ): LinkedHashMap<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(monthsBack - 1))
        }
        val endCal = Calendar.getInstance()
        while (!cal.after(endCal)) {
            counts[keyFormat.format(cal.time)] = 0
            cal.add(Calendar.MONTH, 1)
        }
        for (session in sessions) {
            val key = keyFormat.format(Date(session.timestamp))
            if (counts.containsKey(key)) {
                counts[key] = counts[key]!! + 1
            }
        }
        return counts
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

        // Dose-weighted averages: reduction signal should reflect exposure,
        // not just event count. A user who replaces three full smokes with
        // three drags has reduced 75%, not 0%.
        val avg7d = sessionsLast7.sumOf { it.quantity } / 7.0
        val avgPrior = sessionsPrior30.sumOf { it.quantity } / 30.0

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

    /**
     * "How am I doing right now" — one-glance verdict for the current period vs
     * typical pace. The three "better than typical" tiers are graded by how big
     * a cushion you still have, measured in whole doses you could take now and
     * remain better than typical-by-now (see [paceVerdict]):
     *   AHEAD          → cushion > 2 doses (green)
     *   SLIGHTLY_AHEAD → cushion 1–2 doses (blue): one more stays better, two flips
     *   ON_PACE        → cushion 0–1 doses (yellow): even one more flips to worse
     */
    enum class PaceState {
        CALIBRATING,    // not enough history to compare against
        AHEAD,          // more than two doses below typical-by-now
        SLIGHTLY_AHEAD, // one-to-two-dose cushion below typical-by-now
        ON_PACE,        // below typical-by-now but within one dose
        BEHIND,         // at or above typical-by-now
        CLEAN_TODAY,    // baseline is near-zero and today is also clean
        CLEAN_BREAK,    // baseline is near-zero but smoked today
    }

    data class TodayPace(
        val state: PaceState,
        /**
         * Today's smoking expressed as dose-weighted "cigarette-equivalents"
         * — sum of [SmokingSession.quantity] over the window, NOT a raw
         * event count. A user who replaces three full smokes with three
         * drags shows 0.75 here, not 3, so the verdict correctly registers
         * the reduction.
         */
        val actualToday: Double,
        val typicalByNow: Double,
        val baselineDailyAvg: Double,
        /**
         * Effective start-of-today anchor used by this computation — either
         * the wake-time the caller supplied, or calendar midnight as a
         * fallback. Exposed so per-second UI ticks can re-evaluate the verdict
         * against the same anchor without re-querying the DB.
         */
        val dayStartMs: Long = 0L,
        /**
         * Empirical rhythm CDF: flat list of alternating (offsetHours,
         * cumulativeFraction) pairs, sorted by offset. Encodes the share of
         * typical-day dose that the user has consumed by each successive
         * hour-since-wake in the prior window. Empty when the prior pool was
         * too sparse and the linear projection was used instead. Per-second
         * UI ticks pass this back to [applyRhythmCdf] so they don't re-walk
         * the session list every second.
         */
        val rhythmCdf: List<Double> = emptyList(),
    )

    /**
     * Lookup helper for the rhythm CDF stored in [TodayPace.rhythmCdf]. Walks
     * the (offset, cumulative-fraction) pairs and returns the cumulative
     * fraction at the largest offset ≤ [elapsedHours], or 0 if elapsed is
     * before the first event. An empty CDF means "fall back to linear" — the
     * caller should use [elapsedHours]/24 instead.
     */
    fun applyRhythmCdf(cdf: List<Double>, elapsedHours: Double): Double {
        if (cdf.isEmpty()) return 0.0
        var frac = 0.0
        var i = 0
        while (i < cdf.size) {
            if (cdf[i] > elapsedHours) break
            frac = cdf[i + 1]
            i += 2
        }
        return frac
    }

    /** Clock hour-of-day in [0, 24), e.g. 8.5 for 08:30 — local timezone. */
    private fun hourOfDay(timestampMs: Long): Double {
        val c = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
    }

    /**
     * Build an empirical rhythm CDF from the prior-window sessions, expressed
     * as offsets from the caller's wake-hour-of-day. An event at clock hour
     * `h` becomes offset `(h - wakeHour + 24) mod 24` — so events past wake
     * are early in the awake stretch and pre-wake events sit at the tail
     * (interpreted as "still up from the previous awake stretch").
     *
     * Returns a flat list of pairs (offset, cumulativeFraction) sorted by
     * offset. Empty if prior data is too sparse for a stable estimate — the
     * caller should fall back to a linear projection.
     */
    private fun buildRhythmCdf(
        priorSessions: List<SmokingSession>,
        wakeAnchorMs: Long,
    ): List<Double> {
        if (priorSessions.size < 14) return emptyList()
        val totalDose = priorSessions.sumOf { it.quantity }
        if (totalDose <= 0.0) return emptyList()
        val wakeHour = hourOfDay(wakeAnchorMs)
        val pairs = priorSessions
            .map { (hourOfDay(it.timestamp) - wakeHour + 24.0) % 24.0 to it.quantity }
            .sortedBy { it.first }
        val out = ArrayList<Double>(pairs.size * 2)
        var cum = 0.0
        for ((offset, dose) in pairs) {
            cum += dose
            out.add(offset)
            out.add(cum / totalDose)
        }
        return out
    }

    /**
     * Compute today's pace against a rolling 14-day baseline, time-of-day
     * aware: typical-by-now scales with the fraction of the day elapsed. The
     * baseline excludes today (so a heavy morning doesn't reset its own bar).
     * Requires at least 3 prior days of tracking — fewer than that and we
     * return CALIBRATING rather than a misleading verdict.
     *
     * All comparisons are dose-weighted (sum of [SmokingSession.quantity]),
     * not event counts. This matches [calculateReductionStats] and the
     * substance app's reduction thesis: replacing full smokes with drags
     * counts as progress, and the pace verdict reflects that.
     *
     * @param dayStartMs anchor for "today's window" — typically the user's
     *   wake-up time pulled from Bios via [BiosClient.getWakeTimeMs]. When
     *   null, falls back to calendar midnight (the prior behaviour). The wake
     *   anchor matters at the day boundary: a 01:00 cigarette from someone
     *   still up from the evening before should count in that prior waking
     *   stretch, and the day-fraction scaling should reflect how long the
     *   user has actually been awake.
     */
    fun calculateTodayPace(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
        dayStartMs: Long? = null,
    ): TodayPace {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val midnight = cal.timeInMillis
        // Wake anchor controls today's session window and the day-fraction
        // scaling. Prior-day bucketing stays on calendar midnight — we don't
        // have historical wake-times, and the 14-day average absorbs any
        // boundary noise (same rationale as calculateFirstSmokeOfDay).
        val todayAnchor = dayStartMs ?: midnight

        if (allSessions.isEmpty()) return TodayPace(PaceState.CALIBRATING, 0.0, 0.0, 0.0, todayAnchor)

        val actualToday = allSessions
            .filter { it.timestamp >= todayAnchor }
            .sumOf { it.quantity }

        val day = TimeUnit.DAYS.toMillis(1)
        val firstSession = allSessions.minOf { it.timestamp }
        val trackedDays = ((nowMs - firstSession) / day).toInt() + 1
        val priorDays = (trackedDays - 1).coerceAtMost(14)
        if (priorDays < 3) return TodayPace(PaceState.CALIBRATING, actualToday, 0.0, 0.0, todayAnchor)

        val priorStart = midnight - priorDays * day
        val priorSessions = allSessions.filter { it.timestamp in priorStart until midnight }
        val priorDose = priorSessions.sumOf { it.quantity }
        val baselineDailyAvg = priorDose / priorDays

        // Rhythm-aware projection: empirical CDF of typical-day dose by
        // hour-since-wake. Falls back to linear when prior data is sparse
        // (< 14 events) so a calibrating user keeps the simpler behaviour.
        // Morning-heavy and evening-heavy smokers see verdicts that respect
        // their actual pattern, not a uniform-day assumption.
        val hourMs = TimeUnit.HOURS.toMillis(1).toDouble()
        val elapsedHours = (nowMs - todayAnchor).toDouble() / hourMs
        val dayFractionLinear = (elapsedHours / 24.0).coerceIn(0.0, 1.0)
        val rhythmCdf = buildRhythmCdf(priorSessions, todayAnchor)
        val effectiveFraction = if (rhythmCdf.isNotEmpty()) {
            applyRhythmCdf(rhythmCdf, elapsedHours.coerceAtMost(24.0))
        } else {
            dayFractionLinear
        }
        val typicalByNow = baselineDailyAvg * effectiveFraction

        val state = paceVerdict(actualToday, typicalByNow, baselineDailyAvg)
        return TodayPace(state, actualToday, typicalByNow, baselineDailyAvg, todayAnchor, rhythmCdf)
    }

    /**
     * Single source of truth for the pace verdict's colour thresholds, framed
     * by a one-full-dose margin. Shared by [calculateTodayPace] and
     * [calculateScopedBaselines] (and mirrored in MainViewModel's per-second
     * ticker — keep all three in sync).
     *
     * Grade the cushion below typical-by-now by whole doses you could take now
     * and stay better than typical:
     *  - BEHIND (red): not better than usual — at or above typical-by-now.
     *  - ON_PACE (yellow): better, but no cushion — one more dose tips you
     *    to/over typical (cushion ≤ 1).
     *  - SLIGHTLY_AHEAD (blue): one more dose stays better, but two would flip
     *    (cushion in (1, 2]).
     *  - AHEAD (green): comfortably better — even two more doses stay better
     *    (cushion > 2).
     *
     * The reduction thesis is "use strictly less than your typical period", so
     * equality with typical-by-now is not a win and reads RED. When the
     * baseline is effectively zero, a clean period reads CLEAN_TODAY and a
     * fresh slip against that clean baseline reads CLEAN_BREAK.
     *
     * @param actual dose-weighted amount in the current period so far.
     * @param typicalByNow baseline prorated to how far the period has elapsed.
     * @param baselinePerPeriod the full-period baseline (used only to detect a
     *   near-zero "clean" baseline).
     */
    fun paceVerdict(actual: Double, typicalByNow: Double, baselinePerPeriod: Double): PaceState = when {
        baselinePerPeriod < 0.5 -> if (actual < 0.001) PaceState.CLEAN_TODAY else PaceState.CLEAN_BREAK
        actual >= typicalByNow -> PaceState.BEHIND
        actual >= typicalByNow - 1.0 -> PaceState.ON_PACE
        actual >= typicalByNow - 2.0 -> PaceState.SLIGHTLY_AHEAD
        else -> PaceState.AHEAD
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
        dayStartMs: Long? = null,
    ): List<SubstancePace> {
        if (allSessions.isEmpty()) return emptyList()
        val day = TimeUnit.DAYS.toMillis(1)
        val lookbackStart = nowMs - 14 * day
        val recentSubstances = allSessions
            .filter { it.timestamp >= lookbackStart }
            .map { it.substance }
            .toSet()
        // Include substances seen since the wake anchor (or midnight fallback)
        // even if the 14-day window is sparse.
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayAnchor = dayStartMs ?: cal.timeInMillis
        val todaySubstances = allSessions
            .filter { it.timestamp >= todayAnchor }
            .map { it.substance }
            .toSet()
        val substances = (recentSubstances + todaySubstances).sortedBy { it.ordinal }
        return substances.map { sub ->
            val subset = allSessions.filter { it.substance == sub }
            SubstancePace(sub, calculateTodayPace(subset, nowMs, dayStartMs))
        }
    }

    /**
     * One scope's "current period vs typical" comparison.
     *
     * The baseline is the trailing per-period average over completed periods
     * (the in-progress period is excluded so a heavy start doesn't inflate its
     * own reference), prorated to how far the current period has elapsed
     * ([typicalByNow]). The verdict applies the shared one-full-dose margin via
     * [paceVerdict]. Dose-weighted throughout (sum of SmokingSession.quantity).
     */
    data class ScopeComparison(
        /** Dose logged in the current period so far. */
        val current: Double,
        /** Baseline prorated to the elapsed fraction of the current period. */
        val typicalByNow: Double,
        /** Full-period baseline — the trailing per-period average. */
        val baselinePerPeriod: Double,
        val state: PaceState,
    )

    /**
     * Per-substance "current pace vs typical" at three calendar granularities,
     * each comparing the in-progress period against a trailing baseline:
     *
     *   day   → today      vs the average day over the last 7 days
     *   week  → this week  vs the average week over the last 4 weeks
     *   month → this month vs the average month over the last 12 months
     *
     * Periods are calendar-anchored (midnight / start-of-week / first-of-month).
     * Each trailing window excludes the current period and is clamped to the
     * user's tracked span; a scope with no completed prior period reports
     * CALIBRATING.
     */
    data class ScopedBaseline(
        val substance: Substance,
        val day: ScopeComparison,
        val week: ScopeComparison,
        val month: ScopeComparison,
    )

    fun calculateScopedBaselines(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ScopedBaseline> {
        if (allSessions.isEmpty()) return emptyList()
        val day = TimeUnit.DAYS.toMillis(1)
        val week = 7L * day
        val firstSession = allSessions.minOf { it.timestamp }

        val dayStart = startOfDay(nowMs)
        val weekStart = startOfWeek(nowMs)
        val monthStart = startOfMonth(nowMs)
        val monthLenMs = daysInMonth(nowMs).toLong() * day

        // Completed prior periods available to average over, clamped to the
        // window length. Anchor the first session to its own period start so a
        // user who began logging partway through a day/week still counts that
        // calendar period as completed prior history.
        val priorDays = ((dayStart - startOfDay(firstSession)) / day).toInt().coerceIn(0, 7)
        val priorWeeks = ((weekStart - startOfWeek(firstSession)) / week).toInt().coerceIn(0, 4)
        val priorMonths = monthsBetween(firstSession, monthStart).coerceIn(0, 12)

        val dayWindowStart = dayStart - priorDays.toLong() * day
        val weekWindowStart = weekStart - priorWeeks.toLong() * week
        val monthWindowStart = shiftMonths(monthStart, -priorMonths)

        fun comp(
            subset: List<SmokingSession>,
            periodStart: Long,
            periodLenMs: Double,
            windowStart: Long,
            priorPeriods: Int,
        ): ScopeComparison {
            val current = subset.filter { it.timestamp >= periodStart }.sumOf { it.quantity }
            if (priorPeriods <= 0) {
                return ScopeComparison(current, 0.0, 0.0, PaceState.CALIBRATING)
            }
            val windowDose = subset.filter { it.timestamp in windowStart until periodStart }.sumOf { it.quantity }
            val baseline = windowDose / priorPeriods
            val fraction = ((nowMs - periodStart).toDouble() / periodLenMs).coerceIn(0.0, 1.0)
            val typicalByNow = baseline * fraction
            return ScopeComparison(current, typicalByNow, baseline, paceVerdict(current, typicalByNow, baseline))
        }

        val substances = allSessions.map { it.substance }.toSet().sortedBy { it.ordinal }
        return substances.mapNotNull { sub ->
            val subset = allSessions.filter { it.substance == sub }
            if (subset.isEmpty()) return@mapNotNull null
            ScopedBaseline(
                substance = sub,
                day = comp(subset, dayStart, day.toDouble(), dayWindowStart, priorDays),
                week = comp(subset, weekStart, week.toDouble(), weekWindowStart, priorWeeks),
                month = comp(subset, monthStart, monthLenMs.toDouble(), monthWindowStart, priorMonths),
            )
        }
    }

    /** Calendar midnight of the day containing [ms], local time. */
    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Start of the locale's week containing [ms], at midnight. */
    private fun startOfWeek(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // DAY_OF_WEEK can roll forward past `ms` when firstDayOfWeek is later
        // in the week than today; back up a week if so.
        if (timeInMillis > ms) add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis

    /** Midnight on the first day of the month containing [ms]. */
    private fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysInMonth(ms: Long): Int = Calendar.getInstance().apply {
        timeInMillis = ms
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    /** [ms] shifted back/forward by [months] calendar months (from its month start). */
    private fun shiftMonths(ms: Long, months: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        add(Calendar.MONTH, months)
    }.timeInMillis

    /** Whole calendar months between [fromMs] and [toMs] (>= 0 when to ≥ from). */
    private fun monthsBetween(fromMs: Long, toMs: Long): Int {
        val from = Calendar.getInstance().apply { timeInMillis = fromMs }
        val to = Calendar.getInstance().apply { timeInMillis = toMs }
        val months = (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12 +
            (to.get(Calendar.MONTH) - from.get(Calendar.MONTH))
        return months.coerceAtLeast(0)
    }

    /**
     * "When did you first smoke today vs. what time you usually do?" — a
     * morning-anchor signal. Pushing the first cigarette later in the day is a
     * well-known reduction lever: each delayed hour is real exposure avoided.
     */
    data class FirstSmokeOfDay(
        /** Clock hour-of-day (0..24, e.g. 8.5 for 08:30) of the first smoke since [dayStartMs]. */
        val todayFirstClockHour: Double?,
        /** Hour-of-day (0..24) of the typical first smoke over the lookback window; null if too little data. */
        val typicalFirstHour: Double?,
        /** today minus typical, in minutes; positive = later than usual (better). null if either side missing. */
        val deltaMinutes: Long?,
        /** Number of prior days that contributed to the typical estimate. */
        val daysContributing: Int,
    )

    /**
     * @param dayStartMs anchor for "today's first smoke" — typically the user's
     *   wake-up time pulled from Bios via [BiosClient.getWakeTimeMs]. When null,
     *   falls back to calendar midnight (the prior behaviour). The wake anchor
     *   matters because a smoke at 00:15 by someone still up from the previous
     *   evening should count as the prior day's *last*, not the new day's
     *   first.
     */
    fun calculateFirstSmokeOfDay(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
        lookbackDays: Int = 14,
        dayStartMs: Long? = null,
    ): FirstSmokeOfDay {
        val midnightCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val midnight = midnightCal.timeInMillis
        // Wake anchor controls *today's* filter only. Prior-day bucketing stays
        // on calendar midnight — we don't have historical wake-times, and the
        // typical-hour estimate is a 14-day average that absorbs occasional
        // post-midnight noise without trouble.
        val todayAnchor = dayStartMs ?: midnight
        val day = TimeUnit.DAYS.toMillis(1)
        val firstTodayTs = allSessions
            .filter { it.timestamp >= todayAnchor && it.timestamp <= nowMs }
            .minOfOrNull { it.timestamp }
        val todayClockHour: Double? = firstTodayTs?.let {
            val c = Calendar.getInstance().apply { timeInMillis = it }
            c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
        }

        // Bucket prior sessions by calendar day and take the earliest each day.
        val priorStart = midnight - lookbackDays * day
        val priorByDay = allSessions
            .filter { it.timestamp in priorStart until midnight }
            .groupBy { (it.timestamp - priorStart) / day }
        val firstHours = priorByDay.values.mapNotNull { dayList ->
            val earliest = dayList.minOfOrNull { it.timestamp } ?: return@mapNotNull null
            val c = Calendar.getInstance().apply { timeInMillis = earliest }
            c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0
        }
        val typicalHour = if (firstHours.size >= 3) firstHours.average() else null

        val delta: Long? = if (typicalHour != null && todayClockHour != null) {
            ((todayClockHour - typicalHour) * 60.0).toLong()
        } else null

        return FirstSmokeOfDay(
            todayFirstClockHour = todayClockHour,
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

    /**
     * Sunday-recap-shaped rollup of the last 7 days. Pulls together every
     * existing signal — reduction, milestones crossed, clean days, longest
     * stretch — into one summary the user can read in 10 seconds and
     * recognize themselves in. Designed to be both honest (no cherry-picking)
     * and motivating (deltas framed positively when warranted).
     */
    data class WeeklyDigest(
        val nowMs: Long,
        /** Dose-weighted sum of smokes in the last 7 days. A drag is 0.25, full is 1.0, etc. */
        val smokesThisWeek: Double,
        val smokesPriorWeek: Double,
        /** Signed change as a percentage. Positive = reduced. Null when prior is zero and current is non-zero (no meaningful denominator). */
        val smokeChangePercent: Double?,
        /** Milestones whose crossing timestamp falls within the last 7 days and where the user has not slipped back below since. */
        val milestonesReachedThisWeek: List<HealthMilestone>,
        /** Days in the last 7 with zero logged sessions. */
        val cleanDaysThisWeek: Int,
        /** Longest gap between consecutive smokes (or from week-start to first smoke) in milliseconds. */
        val longestStretchMs: Long,
    )

    fun calculateWeeklyDigest(
        sessions: List<SmokingSession>,
        primarySubstance: Substance,
        nowMs: Long = System.currentTimeMillis(),
    ): WeeklyDigest {
        val day = TimeUnit.DAYS.toMillis(1)
        val weekMs = 7 * day
        val weekStart = nowMs - weekMs
        val priorStart = nowMs - 2 * weekMs

        // Dose-weighted week totals: reduction reads honestly when a user
        // logs five drags vs. five full smokes — the "smokes this week"
        // number reflects exposure, not just event count.
        val smokesThis = sessions
            .filter { it.timestamp in weekStart until nowMs }
            .sumOf { it.quantity }
        val smokesPrior = sessions
            .filter { it.timestamp in priorStart until weekStart }
            .sumOf { it.quantity }
        val smokeChange = when {
            smokesPrior < 0.01 && smokesThis < 0.01 -> 0.0
            smokesPrior < 0.01 -> null
            else -> ((smokesPrior - smokesThis) / smokesPrior) * 100.0
        }

        // Milestones crossed this week: hours-mark whose anniversary falls in
        // [weekStart, nowMs] AND user hasn't slipped past it since (i.e., the
        // milestone is still currently achieved for the primary substance).
        val lastSmokeForPrimary = sessions
            .filter { it.substance == primarySubstance }
            .maxOfOrNull { it.timestamp } ?: 0L
        val currentHours = if (lastSmokeForPrimary == 0L) {
            // No primary-substance smokes ever — every milestone counts as
            // crossed at "the install moment", which is misleading. Skip.
            -1L
        } else {
            (nowMs - lastSmokeForPrimary) / TimeUnit.HOURS.toMillis(1)
        }
        val crossings = if (currentHours < 0) emptyList()
            else HealthBenefits.getMilestones(currentHours, primarySubstance)
                .filter { it.isAchieved }
                .filter {
                    val crossTime = lastSmokeForPrimary + it.hours * TimeUnit.HOURS.toMillis(1)
                    crossTime in weekStart..nowMs
                }

        // Clean-days count: days in window with no logged sessions.
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endOfToday = cal.timeInMillis + day
        var cleanDays = 0
        for (d in 0 until 7) {
            val dayStart = endOfToday - (d + 1) * day
            val dayEnd = dayStart + day
            val anySmoke = sessions.any { it.timestamp in dayStart until dayEnd }
            if (!anySmoke) cleanDays++
        }

        // Longest stretch within the window: gap between consecutive smokes
        // (clipped to the window edges).
        val weekSmokes = sessions
            .filter { it.timestamp in weekStart until nowMs }
            .map { it.timestamp }
            .sorted()
        val longestStretch: Long = if (weekSmokes.isEmpty()) {
            weekMs
        } else {
            val gaps = mutableListOf<Long>()
            gaps += weekSmokes.first() - weekStart
            for (i in 1 until weekSmokes.size) {
                gaps += weekSmokes[i] - weekSmokes[i - 1]
            }
            gaps += nowMs - weekSmokes.last()
            gaps.max()
        }

        return WeeklyDigest(
            nowMs = nowMs,
            smokesThisWeek = smokesThis,
            smokesPriorWeek = smokesPrior,
            smokeChangePercent = smokeChange,
            milestonesReachedThisWeek = crossings,
            cleanDaysThisWeek = cleanDays,
            longestStretchMs = longestStretch,
        )
    }

    fun estimateSubstanceLevels(
        allSessions: List<SmokingSession>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<SubstanceLevel> {
        if (allSessions.isEmpty()) return emptyList()
        val seen = allSessions.map { it.substance }.toSet().sortedBy { it.ordinal }
        return seen.map { sub ->
            val mostRecent = allSessions
                .filter { it.substance == sub }
                .maxByOrNull { it.timestamp }
            val halfLife = halfLifeHours(sub)
            if (mostRecent == null) {
                SubstanceLevel(sub, 0.0, Double.POSITIVE_INFINITY, halfLife, null)
            } else {
                val hours = max(0.0, (nowMs - mostRecent.timestamp).toDouble() /
                    TimeUnit.HOURS.toMillis(1))
                // Initial level scales with dose: a drag delivers ~25% of the
                // full-cigarette nicotine load, so the post-drag plasma curve
                // starts at 25%, not 100%. Decays normally from there.
                val pct = 100.0 * mostRecent.quantity * 0.5.pow(hours / halfLife)
                SubstanceLevel(sub, pct, hours, halfLife, mostRecent.timestamp)
            }
        }
    }
}





