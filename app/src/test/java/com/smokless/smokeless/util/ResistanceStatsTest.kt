package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [ScoreCalculator.calculateResistanceStats] — the verified
 * craving-held / smoked ratio surfaced as the hero "moments of decision" card.
 */
class ResistanceStatsTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)
    private val window = ScoreCalculator.CRAVING_VICTORY_WINDOW_MS

    @Test
    fun `empty input returns zeros and no delta`() {
        val now = System.currentTimeMillis()
        val r = ScoreCalculator.calculateResistanceStats(emptyList(), emptyList(), now)
        assertEquals(0, r.resistedCount)
        assertEquals(0, r.smokedCount)
        assertEquals(0.0, r.resistancePercent, 0.001)
        assertNull(r.vsPriorPercent)
    }

    @Test
    fun `craving without smoke in window counts as resisted`() {
        val now = System.currentTimeMillis()
        // Craving 2 hours ago — window long elapsed, no smokes at all.
        val cravings = listOf(Craving(now - 2 * hour))
        val r = ScoreCalculator.calculateResistanceStats(cravings, emptyList(), now)
        assertEquals(1, r.resistedCount)
        assertEquals(0, r.smokedCount)
        assertEquals(100.0, r.resistancePercent, 0.001)
    }

    @Test
    fun `craving with smoke inside window does not count as resisted`() {
        val now = System.currentTimeMillis()
        val cravingAt = now - 2 * hour
        val cravings = listOf(Craving(cravingAt))
        // Smoke 10 min after the craving — squarely within the 30-min window.
        val smokeRealMs = cravingAt + 10 * 60 * 1000L
        // SmokingSession.timestamp already includes exposureMs offset; the
        // calculator subtracts it back out, so simulate that here.
        val smokeStoredMs = smokeRealMs + Substance.TOBACCO.exposureMs
        val sessions = listOf(SmokingSession(smokeStoredMs, Substance.TOBACCO))
        val r = ScoreCalculator.calculateResistanceStats(cravings, sessions, now)
        assertEquals(0, r.resistedCount)
        assertEquals(1, r.smokedCount)
        assertEquals(0.0, r.resistancePercent, 0.001)
    }

    @Test
    fun `craving whose 30 min window has not yet elapsed is excluded`() {
        val now = System.currentTimeMillis()
        // Craving 10 minutes ago — window still pending.
        val cravings = listOf(Craving(now - 10 * 60 * 1000L))
        val r = ScoreCalculator.calculateResistanceStats(cravings, emptyList(), now)
        assertEquals(0, r.resistedCount)
        assertEquals(0, r.smokedCount)
    }

    @Test
    fun `cravings and smokes outside the lookback window are ignored`() {
        val now = System.currentTimeMillis()
        // Craving 10 days ago — outside default 7-day window.
        val cravings = listOf(Craving(now - 10 * day))
        val sessions = listOf(SmokingSession(now - 10 * day))
        val r = ScoreCalculator.calculateResistanceStats(cravings, sessions, now)
        assertEquals(0, r.resistedCount)
        assertEquals(0, r.smokedCount)
    }

    @Test
    fun `vsPriorPercent reflects improvement from prior window`() {
        val now = System.currentTimeMillis()
        // Current week: 3 resisted, 1 smoked → 75%.
        val cravingsCurrent = listOf(
            Craving(now - 1 * day),
            Craving(now - 2 * day),
            Craving(now - 3 * day),
        )
        val sessionsCurrent = listOf(SmokingSession(now - 4 * day))
        // Prior week: 1 resisted, 3 smoked → 25%.
        val cravingsPrior = listOf(Craving(now - 8 * day))
        val sessionsPrior = listOf(
            SmokingSession(now - 9 * day),
            SmokingSession(now - 10 * day),
            SmokingSession(now - 11 * day),
        )
        val r = ScoreCalculator.calculateResistanceStats(
            cravings = cravingsCurrent + cravingsPrior,
            sessions = sessionsCurrent + sessionsPrior,
            nowMs = now,
        )
        assertEquals(75.0, r.resistancePercent, 0.1)
        assertNotNull(r.vsPriorPercent)
        assertEquals(50.0, r.vsPriorPercent!!, 0.5)
    }

    @Test
    fun `vsPriorPercent is null when prior window is empty`() {
        val now = System.currentTimeMillis()
        val r = ScoreCalculator.calculateResistanceStats(
            cravings = listOf(Craving(now - 1 * day)),
            sessions = emptyList(),
            nowMs = now,
        )
        assertNull(r.vsPriorPercent)
    }

    @Test
    fun `mixed activity in window produces expected ratio`() {
        val now = System.currentTimeMillis()
        val cravings = listOf(
            Craving(now - 1 * day),
            Craving(now - 2 * day),
            Craving(now - 3 * day),
        )
        val sessions = listOf(
            SmokingSession(now - 1 * day - hour),
            SmokingSession(now - 2 * day - hour),
        )
        val r = ScoreCalculator.calculateResistanceStats(cravings, sessions, now)
        assertEquals(3, r.resistedCount)
        assertEquals(2, r.smokedCount)
        assertEquals(60.0, r.resistancePercent, 0.1)
    }
}
