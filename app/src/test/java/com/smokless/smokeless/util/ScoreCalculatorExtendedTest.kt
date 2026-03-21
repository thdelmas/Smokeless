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
