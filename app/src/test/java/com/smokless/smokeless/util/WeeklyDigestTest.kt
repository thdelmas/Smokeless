package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [ScoreCalculator.calculateWeeklyDigest] — the Sunday-recap-shaped
 * rollup of the last 7 days.
 */
class WeeklyDigestTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `empty input gives empty digest`() {
        val now = System.currentTimeMillis()
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = emptyList(),
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(0.0, d.smokesThisWeek, 0.001)
        assertEquals(0.0, d.smokesPriorWeek, 0.001)
        assertEquals(0.0, d.smokeChangePercent ?: -1.0, 0.001)
        assertEquals(7, d.cleanDaysThisWeek)
        assertEquals(0, d.resistance.resistedCount)
        assertTrue(d.milestonesReachedThisWeek.isEmpty())
    }

    @Test
    fun `smokeChangePercent reflects reduction positively`() {
        val now = System.currentTimeMillis()
        val sessions = mutableListOf<SmokingSession>()
        // Last week: 5 smokes.
        for (i in 0 until 5) sessions += SmokingSession(now - 1 * day - i * hour)
        // Prior week: 10 smokes.
        for (i in 0 until 10) sessions += SmokingSession(now - 8 * day - i * hour)
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(5.0, d.smokesThisWeek, 0.001)
        assertEquals(10.0, d.smokesPriorWeek, 0.001)
        assertNotNull(d.smokeChangePercent)
        assertEquals(50.0, d.smokeChangePercent!!, 0.5)
    }

    @Test
    fun `smokeChangePercent null when prior is zero and current non-zero`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(SmokingSession(now - 1 * day))
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertNull(d.smokeChangePercent)
    }

    @Test
    fun `cleanDays counts days in window with no smokes`() {
        val now = System.currentTimeMillis()
        // Smoke today + 3 days ago — five other days are clean.
        val sessions = listOf(
            SmokingSession(now - hour),
            SmokingSession(now - 3 * day - hour),
        )
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(5, d.cleanDaysThisWeek)
    }

    @Test
    fun `longest stretch is window when no smokes`() {
        val now = System.currentTimeMillis()
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = emptyList(),
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(7 * day, d.longestStretchMs)
    }

    @Test
    fun `longest stretch is gap between smokes when sparse`() {
        val now = System.currentTimeMillis()
        // Two smokes — one 6 days ago, one 1 day ago. Longest gap: ~5 days.
        val sessions = listOf(
            SmokingSession(now - 6 * day),
            SmokingSession(now - 1 * day),
        )
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        // 5 days, give or take rounding.
        val hours = d.longestStretchMs / hour
        assertTrue("expected ~5 days, got ${hours}h", hours in 119..121)
    }

    @Test
    fun `milestones crossed in window are reported`() {
        val now = System.currentTimeMillis()
        // Last tobacco smoke 80 hours ago, no smokes since — so 72h milestone
        // was crossed 8 hours ago, which is inside the 7-day window.
        val lastSmoke = now - 80 * hour
        val sessions = listOf(SmokingSession(lastSmoke, Substance.TOBACCO))
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertTrue("should include 72h milestone",
            d.milestonesReachedThisWeek.any { it.hours == 72 })
        // Should not include milestones the user hasn't reached.
        assertFalse("should not include 168h milestone (not yet reached)",
            d.milestonesReachedThisWeek.any { it.hours == 168 })
    }

    @Test
    fun `milestones list is empty when user has not logged primary substance`() {
        val now = System.currentTimeMillis()
        // Sessions but only cannabis, primary is tobacco.
        val sessions = listOf(SmokingSession(now - 80 * hour, Substance.CANNABIS))
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = emptyList(),
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertTrue(d.milestonesReachedThisWeek.isEmpty())
    }

    @Test
    fun `resistance block reflects in-window cravings and smokes`() {
        val now = System.currentTimeMillis()
        val cravings = listOf(
            Craving(now - 2 * hour),
            Craving(now - 3 * hour),
        )
        val sessions = listOf(SmokingSession(now - 1 * day))
        val d = ScoreCalculator.calculateWeeklyDigest(
            sessions = sessions,
            cravings = cravings,
            primarySubstance = Substance.TOBACCO,
            nowMs = now,
        )
        assertEquals(2, d.resistance.resistedCount)
        assertEquals(1, d.resistance.smokedCount)
    }
}
