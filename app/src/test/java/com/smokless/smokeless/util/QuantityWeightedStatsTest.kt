package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pins the dose-weighting behavior in the read path. A user who logs
 * partial smokes (drags / halves) registers less exposure than the same
 * count of full smokes.
 */
class QuantityWeightedStatsTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `periodStats totalCigarettes sums quantity, not events`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SmokingSession(now - 1 * hour, Substance.TOBACCO, 0.25),
            SmokingSession(now - 2 * hour, Substance.TOBACCO, 0.5),
            SmokingSession(now - 3 * hour, Substance.TOBACCO, 1.0),
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "day")
        // 0.25 + 0.5 + 1.0 = 1.75 cig-equivalents (three events).
        assertEquals(1.75, stats.totalCigarettes, 0.001)
    }

    @Test
    fun `periodStats bestDay and worstDay are dose sums, not counts`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            // Day A: three drags = 0.75
            SmokingSession(now - 2 * day - 1 * hour, Substance.TOBACCO, 0.25),
            SmokingSession(now - 2 * day - 2 * hour, Substance.TOBACCO, 0.25),
            SmokingSession(now - 2 * day - 3 * hour, Substance.TOBACCO, 0.25),
            // Day B: one full smoke = 1.0
            SmokingSession(now - 1 * day, Substance.TOBACCO, 1.0),
        )
        val stats = ScoreCalculator.calculatePeriodStats(sessions, "week")
        // Three-drag day is the better (lower) day at 0.75, not the worst
        // even though it has the most events.
        assertEquals(0.75, stats.bestDay, 0.001)
        assertEquals(1.0, stats.worstDay, 0.001)
    }

    @Test
    fun `reductionStats avg7d sums quantity not events`() {
        val now = System.currentTimeMillis()
        // 14 days of one event/day but at 0.5 each — should average to 0.5,
        // not 1.0.
        val sessions = (1..14).map {
            SmokingSession(now - it * day, Substance.TOBACCO, 0.5)
        }
        val r = ScoreCalculator.calculateReductionStats(sessions)
        assertEquals(0.5, r.rollingAverage7d, 0.001)
    }

    @Test
    fun `substanceLevel scales with last-smoke quantity`() {
        val now = System.currentTimeMillis()
        // Last smoke was a drag (0.25), 1 hour ago. Tobacco half-life is 2h,
        // so percent at 1h = 100 * 0.25 * 0.5^(1/2) ≈ 17.7.
        val sessions = listOf(SmokingSession(now - 1 * hour, Substance.TOBACCO, 0.25))
        val levels = ScoreCalculator.estimateSubstanceLevels(sessions, now)
        val tobacco = levels.first { it.substance == Substance.TOBACCO }
        assertEquals(17.7, tobacco.percentRemaining, 0.5)
    }

    @Test
    fun `weeklyDigest smokesThisWeek sums quantity`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SmokingSession(now - 1 * day, Substance.TOBACCO, 0.5),
            SmokingSession(now - 2 * day, Substance.TOBACCO, 0.5),
            SmokingSession(now - 3 * day, Substance.TOBACCO, 1.0),
        )
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(2.0, d.smokesThisWeek, 0.001)
    }
}
