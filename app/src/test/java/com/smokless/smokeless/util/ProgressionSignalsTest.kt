package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Tests for the pedagogical "Today vs. Before" signals: per-substance pace,
 * first-smoke-of-day comparison, and plasma decay estimate.
 */
class ProgressionSignalsTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    // --- calculatePerSubstancePace ---

    @Test
    fun `perSubstancePace empty input returns empty list`() {
        val result = ScoreCalculator.calculatePerSubstancePace(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `perSubstancePace returns one entry per substance with history`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SmokingSession(now - 2 * day, Substance.TOBACCO),
            SmokingSession(now - 3 * day, Substance.TOBACCO),
            SmokingSession(now - 1 * day, Substance.CANNABIS),
        )
        val result = ScoreCalculator.calculatePerSubstancePace(sessions, now)
        assertEquals(2, result.size)
        assertTrue(result.any { it.substance == Substance.TOBACCO })
        assertTrue(result.any { it.substance == Substance.CANNABIS })
    }

    @Test
    fun `perSubstancePace surfaces substance smoked today even if rare prior`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            // History only has tobacco
            SmokingSession(now - 2 * day, Substance.TOBACCO),
            SmokingSession(now - 3 * day, Substance.TOBACCO),
            SmokingSession(now - 4 * day, Substance.TOBACCO),
            // Today: a cannabis session shows up
            SmokingSession(now - 1 * hour, Substance.CANNABIS),
        )
        val result = ScoreCalculator.calculatePerSubstancePace(sessions, now)
        assertTrue(result.any { it.substance == Substance.CANNABIS })
    }

    // --- calculateFirstSmokeOfDay ---

    @Test
    fun `firstSmokeOfDay returns null fields when no data`() {
        val r = ScoreCalculator.calculateFirstSmokeOfDay(emptyList())
        assertNull(r.todayFirstMsFromStartOfDay)
        assertNull(r.typicalFirstHour)
        assertNull(r.deltaMinutes)
        assertEquals(0, r.daysContributing)
    }

    @Test
    fun `firstSmokeOfDay reports today's first smoke and signed delta vs typical`() {
        val now = atHour(today(), 11) // 11:00 today
        val sessions = mutableListOf<SmokingSession>()
        // 5 prior days, all first-smokes at 08:00
        for (d in 1..5) {
            sessions += SmokingSession(atHour(today() - d * day, 8))
        }
        // Today: first smoke at 09:30
        sessions += SmokingSession(atHour(today(), 9) + 30 * 60 * 1000L)
        val r = ScoreCalculator.calculateFirstSmokeOfDay(sessions, now)
        assertNotNull(r.typicalFirstHour)
        assertEquals(8.0, r.typicalFirstHour!!, 0.01)
        assertNotNull(r.todayFirstMsFromStartOfDay)
        // delta: 9:30 minus 8:00 = +90 min
        assertNotNull(r.deltaMinutes)
        assertEquals(90L, r.deltaMinutes)
        assertEquals(5, r.daysContributing)
    }

    @Test
    fun `firstSmokeOfDay needs at least three days of data for typical hour`() {
        val now = atHour(today(), 11)
        // Only 2 prior days
        val sessions = listOf(
            SmokingSession(atHour(today() - 1 * day, 8)),
            SmokingSession(atHour(today() - 2 * day, 8)),
        )
        val r = ScoreCalculator.calculateFirstSmokeOfDay(sessions, now)
        assertNull(r.typicalFirstHour)
    }

    // --- estimateSubstanceLevels ---

    @Test
    fun `substanceLevels empty input returns empty list`() {
        assertTrue(ScoreCalculator.estimateSubstanceLevels(emptyList()).isEmpty())
    }

    @Test
    fun `substanceLevels percent halves after one half-life`() {
        val now = System.currentTimeMillis()
        // Tobacco half-life is 2h.
        val sessions = listOf(SmokingSession(now - 2 * hour, Substance.TOBACCO))
        val levels = ScoreCalculator.estimateSubstanceLevels(sessions, now)
        val tobacco = levels.first { it.substance == Substance.TOBACCO }
        assertEquals(50.0, tobacco.percentRemaining, 0.5)
    }

    @Test
    fun `substanceLevels percent near full immediately after smoke`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(SmokingSession(now - 60_000L, Substance.TOBACCO))
        val levels = ScoreCalculator.estimateSubstanceLevels(sessions, now)
        val tobacco = levels.first { it.substance == Substance.TOBACCO }
        assertTrue(tobacco.percentRemaining > 95.0)
    }

    @Test
    fun `substanceLevels cannabis has much longer decay than tobacco`() {
        val now = System.currentTimeMillis()
        val sessions = listOf(
            SmokingSession(now - 6 * hour, Substance.TOBACCO),
            SmokingSession(now - 6 * hour, Substance.CANNABIS),
        )
        val levels = ScoreCalculator.estimateSubstanceLevels(sessions, now)
        val tobacco = levels.first { it.substance == Substance.TOBACCO }
        val cannabis = levels.first { it.substance == Substance.CANNABIS }
        // After 6h: tobacco (HL 2h) at ~12.5%, cannabis (HL 25h) at ~85%.
        assertTrue("tobacco should decay much more than cannabis at 6h",
            cannabis.percentRemaining > tobacco.percentRemaining + 50)
    }

    // --- Helpers ---

    private fun today(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun atHour(startOfDay: Long, hour: Int): Long =
        startOfDay + hour * TimeUnit.HOURS.toMillis(1)
}
