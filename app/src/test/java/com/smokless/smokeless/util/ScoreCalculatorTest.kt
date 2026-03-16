package com.smokless.smokeless.util

import org.junit.Assert.*
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun `calculateGoalProgress returns 100 when both zero`() {
        assertEquals(100.0, ScoreCalculator.calculateGoalProgress(0.0, 0.0), 0.01)
    }

    @Test
    fun `calculateGoalProgress returns 0 when goal is zero but current is not`() {
        assertEquals(0.0, ScoreCalculator.calculateGoalProgress(5.0, 0.0), 0.01)
    }

    @Test
    fun `calculateGoalProgress returns 100 when meeting goal`() {
        assertEquals(100.0, ScoreCalculator.calculateGoalProgress(3.0, 5.0), 0.01)
    }

    @Test
    fun `calculateGoalProgress returns 100 when exactly at goal`() {
        assertEquals(100.0, ScoreCalculator.calculateGoalProgress(5.0, 5.0), 0.01)
    }

    @Test
    fun `calculateGoalProgress returns partial when exceeding goal`() {
        // Current 7.5, goal 5.0 => difference = 2.5, percentOff = 50%, result = 50%
        val progress = ScoreCalculator.calculateGoalProgress(7.5, 5.0)
        assertEquals(50.0, progress, 0.01)
    }

    @Test
    fun `calculateGoalProgress never returns negative`() {
        val progress = ScoreCalculator.calculateGoalProgress(100.0, 1.0)
        assertTrue(progress >= 0.0)
    }

    @Test
    fun `calculatePeriodStats returns zeros for empty sessions`() {
        val stats = ScoreCalculator.calculatePeriodStats(emptyList(), "month")
        assertEquals(0, stats.totalCigarettes)
        assertEquals(0.0, stats.averagePerDay, 0.01)
        assertEquals(0, stats.cleanDays)
        assertEquals(0, stats.totalDays)
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.bestStreak)
    }

    @Test
    fun `calculateTimeSinceLastSmoke returns 0 for timestamp 0`() {
        assertEquals(0L, ScoreCalculator.calculateTimeSinceLastSmoke(0L))
    }

    @Test
    fun `calculateTimeSinceLastSmoke returns positive value for past timestamp`() {
        val oneHourAgo = System.currentTimeMillis() - 3600_000L
        val result = ScoreCalculator.calculateTimeSinceLastSmoke(oneHourAgo)
        assertTrue(result > 0)
        assertTrue(result in 3590_000L..3610_000L) // ~1 hour with tolerance
    }
}
