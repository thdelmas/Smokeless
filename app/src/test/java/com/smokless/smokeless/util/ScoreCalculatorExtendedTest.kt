package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.SmokingSession
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScoreCalculatorExtendedTest {

    // --- calculateGoal ---

    @Test
    fun `calculateGoal returns 0 for empty sessions`() {
        assertEquals(0.0, ScoreCalculator.calculateGoal(emptyList(), "month", 0), 0.01)
    }

    @Test
    fun `calculateGoal with difficulty 0 returns current average`() {
        val sessions = createSessionsOverDays(10, 5) // 10 cigs over 5 days = avg 2/day
        val goal = ScoreCalculator.calculateGoal(sessions, "all", 0)
        // difficulty 0 = maintain average, so goal ≈ average
        assertTrue(goal > 0)
    }

    @Test
    fun `calculateGoal with higher difficulty returns lower goal`() {
        val sessions = createSessionsOverDays(50, 10)
        val goal0 = ScoreCalculator.calculateGoal(sessions, "all", 0)
        val goal2 = ScoreCalculator.calculateGoal(sessions, "all", 2)
        val goal4 = ScoreCalculator.calculateGoal(sessions, "all", 4)
        assertTrue("Higher difficulty should produce lower goal", goal0 > goal2)
        assertTrue("Difficulty 4 should be lower than 2", goal2 > goal4)
    }

    @Test
    fun `calculateGoal never returns negative`() {
        val sessions = createSessionsOverDays(5, 10)
        val goal = ScoreCalculator.calculateGoal(sessions, "all", 10) // extreme difficulty
        assertTrue(goal >= 0.0)
    }

    // --- calculateDailyProgress ---

    @Test
    fun `calculateDailyProgress returns 0-100 range for zero cigarettes`() {
        val progress = ScoreCalculator.calculateDailyProgress(0, 5.0, System.currentTimeMillis() - 3600_000)
        assertTrue(progress >= 0.0)
        assertTrue(progress <= 100.0)
    }

    @Test
    fun `calculateDailyProgress with cigarettes below expected returns above 50`() {
        // If we smoked 1 cig and expected ~3 by now, we're doing well
        val progress = ScoreCalculator.calculateDailyProgress(1, 10.0, System.currentTimeMillis() - 3600_000)
        assertTrue("Should be above 50% when below expected", progress >= 50.0)
    }

    // --- calculatePeriodStats ---

    @Test
    fun `calculatePeriodStats counts total cigarettes correctly`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            createSession(now - TimeUnit.HOURS.toMillis(1)),
            createSession(now - TimeUnit.HOURS.toMillis(2)),
            createSession(now - TimeUnit.HOURS.toMillis(3))
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "day")
        assertEquals(3, stats.totalCigarettes)
    }

    @Test
    fun `calculatePeriodStats with sessions across multiple days`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            createSession(now - TimeUnit.DAYS.toMillis(2)),
            createSession(now - TimeUnit.DAYS.toMillis(2) + 1000),
            createSession(now - TimeUnit.DAYS.toMillis(1)),
            createSession(now)
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "week")
        assertEquals(4, stats.totalCigarettes)
        assertTrue(stats.totalDays >= 3)
    }

    @Test
    fun `calculatePeriodStats calculates clean days`() {
        val now = System.currentTimeMillis()
        // Smoke only 3 days ago, so days in between should be clean
        val sessions = listOf(createSession(now - TimeUnit.DAYS.toMillis(3)))
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "week")
        assertTrue("Should have clean days", stats.cleanDays > 0)
    }

    @Test
    fun `calculatePeriodStats calculates streaks`() {
        val now = System.currentTimeMillis()
        // Smoke 3 days ago, nothing since
        val sessions = listOf(
            createSession(now - TimeUnit.DAYS.toMillis(3))
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "week")
        // Current streak should be at least 2 (yesterday and today are clean)
        assertTrue("Current streak should be >= 2", stats.currentStreak >= 2)
    }

    @Test
    fun `calculatePeriodStats best streak is at least as large as current streak`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            createSession(now - TimeUnit.DAYS.toMillis(5)),
            createSession(now - TimeUnit.DAYS.toMillis(2))
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "week")
        assertTrue(stats.bestStreak >= stats.currentStreak)
    }

    // --- getDailyCountsForScope ---

    @Test
    fun `getDailyCountsForScope returns empty for no sessions`() {
        val result = ScoreCalculator.getDailyCountsForScope(emptyList(), "month")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDailyCountsForScope includes zero-count days`() {
        val now = System.currentTimeMillis()
        // One session 3 days ago
        val sessions = listOf(createSession(now - TimeUnit.DAYS.toMillis(3)))
        val result = ScoreCalculator.getDailyCountsForScope(sessions, "week")
        // Should have entries for each day from 3 days ago to today
        assertTrue("Should have multiple days including zero-count ones", result.size >= 3)
        val zeroCountDays = result.values.count { it == 0 }
        assertTrue("Should have some zero-count days", zeroCountDays > 0)
    }

    @Test
    fun `getDailyCountsForScope groups multiple sessions on same day`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            createSession(now - 1000),
            createSession(now - 2000),
            createSession(now - 3000)
        )
        val result = ScoreCalculator.getDailyCountsForScope(sessions, "day")
        // Today should have count of 3
        val todayCount = result.values.last()
        assertEquals(3, todayCount)
    }

    // --- getHourlyCountsForToday ---

    @Test
    fun `getHourlyCountsForToday returns at least one entry`() {
        val result = ScoreCalculator.getHourlyCountsForToday(emptyList())
        assertTrue("Should have hourly entries even with no sessions", result.isNotEmpty())
    }

    @Test
    fun `getHourlyCountsForToday counts sessions in correct hour`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            createSession(now),
            createSession(now - 60_000) // 1 minute ago
        )
        val result = ScoreCalculator.getHourlyCountsForToday(sessions)
        val maxCount = result.values.maxOrNull() ?: 0
        assertEquals("Both sessions should be in same hour", 2, maxCount)
    }

    // --- calculateGoalProgress edge cases ---

    @Test
    fun `calculateGoalProgress caps at 0 for extreme overshoot`() {
        val progress = ScoreCalculator.calculateGoalProgress(50.0, 1.0)
        assertEquals(0.0, progress, 0.01)
    }

    @Test
    fun `calculateGoalProgress returns 100 when well under goal`() {
        val progress = ScoreCalculator.calculateGoalProgress(1.0, 10.0)
        assertEquals(100.0, progress, 0.01)
    }

    // --- calculateReductionStats ---

    @Test
    fun `calculateReductionStats marks empty input not comparable`() {
        val stats = ScoreCalculator.calculateReductionStats(emptyList())
        assertFalse(stats.hasEnoughData)
        assertFalse(stats.velocityComparable)
        assertEquals(0.0, stats.velocityPercent, 0.01)
    }

    @Test
    fun `calculateReductionStats suppresses velocity when prior window is empty`() {
        // Only the last 7 days have sessions; prior 30-day window is empty.
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val sessions = (0 until 7).flatMap { d ->
            List(3) { createSession(now - d * day - it * 60_000L) }
        }
        val stats = ScoreCalculator.calculateReductionStats(sessions)
        assertFalse("Brand-new tracking should not yield velocity copy", stats.velocityComparable)
    }

    @Test
    fun `calculateReductionStats suppresses velocity on returning-user gap`() {
        // Real-device pattern: 17 logged days ~30-50 days ago, then 27-day silence,
        // then 4 days of recent activity. Comparison must not fire.
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val priorWindow = (30L..46L).flatMap { d ->
            List(4) { createSession(now - d * day - it * 60_000L) }
        }
        val recentWindow = (0L..3L).flatMap { d ->
            List(5) { createSession(now - d * day - it * 60_000L) }
        }
        val stats = ScoreCalculator.calculateReductionStats(priorWindow + recentWindow)
        assertTrue(stats.hasEnoughData)
        assertFalse("Multi-week gap between windows must suppress velocity", stats.velocityComparable)
        assertEquals(0.0, stats.velocityPercent, 0.01)
    }

    @Test
    fun `calculateReductionStats produces velocity for continuously tracked user`() {
        // 60 days of daily logging, recent week lighter than older history.
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val sessions = (0L..59L).flatMap { d ->
            val perDay = if (d <= 6L) 2 else 4
            List(perDay) { createSession(now - d * day - it * 60_000L) }
        }
        val stats = ScoreCalculator.calculateReductionStats(sessions)
        assertTrue(stats.hasEnoughData)
        assertTrue("Continuous tracking should yield comparable velocity", stats.velocityComparable)
        assertTrue("Recent < prior should produce positive (improving) velocity", stats.velocityPercent > 0)
    }

    @Test
    fun `calculateReductionStats hasEnoughData false for single session 14 days old`() {
        // Calendar age passes (14 days), but only 1 logged day in last 30.
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val sessions = listOf(createSession(now - 14 * day))
        val stats = ScoreCalculator.calculateReductionStats(sessions)
        assertFalse("Sparse history must not unlock trend", stats.hasEnoughData)
    }

    @Test
    fun `calculateReductionStats hasEnoughData true with 5 logged days in last 30`() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        // 5 distinct logged days within the last 30, first session > 14 days ago.
        val sessions = (0L..4L).map { createSession(now - (15L + it) * day) }
        val stats = ScoreCalculator.calculateReductionStats(sessions)
        assertTrue(stats.hasEnoughData)
    }

    @Test
    fun `calculateReductionStats reports coverage for partial week`() {
        // Real-device pattern: 4 logged days in the last 7 after a long gap.
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val priorWindow = (30L..46L).flatMap { d ->
            List(4) { createSession(now - d * day - it * 60_000L) }
        }
        val recentWindow = (0L..3L).flatMap { d ->
            List(5) { createSession(now - d * day - it * 60_000L) }
        }
        val stats = ScoreCalculator.calculateReductionStats(priorWindow + recentWindow)
        assertEquals("Should report 4 of 7 days logged", 4, stats.loggedDaysLast7)
    }

    @Test
    fun `calculateReductionStats reports full coverage for daily logging`() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val sessions = (0L..6L).map { createSession(now - it * day - 60_000L) }
        val stats = ScoreCalculator.calculateReductionStats(sessions)
        assertEquals(7, stats.loggedDaysLast7)
    }

    // --- Helper functions ---

    private fun createSession(timestamp: Long): SmokingSession {
        return SmokingSession(timestamp)
    }

    /**
     * Create evenly distributed sessions over a number of days
     */
    private fun createSessionsOverDays(count: Int, days: Int): List<SmokingSession> {
        val now = System.currentTimeMillis()
        val interval = TimeUnit.DAYS.toMillis(days.toLong()) / count
        return (0 until count).map { i ->
            createSession(now - (i * interval))
        }
    }
}
