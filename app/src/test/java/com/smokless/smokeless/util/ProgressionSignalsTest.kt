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
        assertNull(r.todayFirstClockHour)
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
        assertNotNull(r.todayFirstClockHour)
        assertEquals(9.5, r.todayFirstClockHour!!, 0.01)
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

    @Test
    fun `firstSmokeOfDay treats post-midnight smoke as new day's first when no wake anchor`() {
        // User still up from prior evening, smokes at 00:15. Without a wake
        // anchor, calendar midnight rolls the day and this counts as today's
        // first — the exact bug the wake-time path is meant to fix.
        val now = atHour(today(), 11)
        val sessions = listOf(
            SmokingSession(atHour(today(), 0) + 15 * 60 * 1000L), // 00:15
        )
        val r = ScoreCalculator.calculateFirstSmokeOfDay(sessions, now)
        assertNotNull(r.todayFirstClockHour)
        assertEquals(0.25, r.todayFirstClockHour!!, 0.01)
    }

    @Test
    fun `firstSmokeOfDay ignores pre-wake smoke when dayStartMs anchors at wake time`() {
        // Same 00:15 smoke as above, but the user's actual wake-up was 08:00.
        // With the wake anchor, the 00:15 smoke is excluded — it belongs to
        // the prior waking day. No smoke since wake → today's first is null.
        val now = atHour(today(), 11)
        val wake = atHour(today(), 8)
        val sessions = listOf(
            SmokingSession(atHour(today(), 0) + 15 * 60 * 1000L), // 00:15 pre-wake
        )
        val r = ScoreCalculator.calculateFirstSmokeOfDay(
            sessions, now, dayStartMs = wake
        )
        assertNull(r.todayFirstClockHour)
    }

    @Test
    fun `firstSmokeOfDay reports post-wake smoke clock hour with wake anchor set`() {
        val now = atHour(today(), 11)
        val wake = atHour(today(), 8)
        val sessions = listOf(
            SmokingSession(atHour(today(), 0) + 15 * 60 * 1000L), // 00:15 pre-wake, ignored
            SmokingSession(atHour(today(), 9) + 30 * 60 * 1000L), // 09:30 post-wake
        )
        val r = ScoreCalculator.calculateFirstSmokeOfDay(
            sessions, now, dayStartMs = wake
        )
        assertNotNull(r.todayFirstClockHour)
        assertEquals(9.5, r.todayFirstClockHour!!, 0.01)
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

    // --- calculateTriggerWindows ---

    @Test
    fun `triggerWindows empty input returns empty list`() {
        val r = ScoreCalculator.calculateTriggerWindows(emptyList())
        assertTrue(r.isEmpty())
    }

    @Test
    fun `triggerWindows detects clustered hours as peaks`() {
        val now = atHour(today(), 12) // run "now" at noon
        val sessions = mutableListOf<SmokingSession>()
        // Over 20 days, 10 cigs at 09:00 daily and 8 at 22:00 daily, plus 2
        // scattered at random hours. 09:00 and 22:00 should be peaks.
        for (d in 1..20) {
            sessions += SmokingSession(atHour(today() - d * day, 9))
            sessions += SmokingSession(atHour(today() - d * day, 22))
        }
        val result = ScoreCalculator.calculateTriggerWindows(sessions, now)
        assertEquals(1, result.size)
        val tobacco = result.first { it.substance == Substance.TOBACCO }
        assertTrue("09 should be a peak", 9 in tobacco.peakHours)
        assertTrue("22 should be a peak", 22 in tobacco.peakHours)
    }

    @Test
    fun `triggerWindows uniform distribution yields no peaks`() {
        val now = atHour(today(), 12)
        val sessions = mutableListOf<SmokingSession>()
        // One session per hour for 15 days — perfectly uniform.
        for (d in 1..15) {
            for (h in 0..23) {
                sessions += SmokingSession(atHour(today() - d * day, h))
            }
        }
        val result = ScoreCalculator.calculateTriggerWindows(sessions, now)
        val tobacco = result.first { it.substance == Substance.TOBACCO }
        assertTrue("Uniform distribution should not have peaks",
            tobacco.peakHours.isEmpty())
    }

    @Test
    fun `triggerWindows separates substances`() {
        val now = atHour(today(), 12)
        val sessions = mutableListOf<SmokingSession>()
        for (d in 1..15) {
            // Tobacco mornings
            sessions += SmokingSession(atHour(today() - d * day, 8), Substance.TOBACCO)
            sessions += SmokingSession(atHour(today() - d * day, 9), Substance.TOBACCO)
            // Cannabis evenings
            sessions += SmokingSession(atHour(today() - d * day, 21), Substance.CANNABIS)
            sessions += SmokingSession(atHour(today() - d * day, 22), Substance.CANNABIS)
        }
        val result = ScoreCalculator.calculateTriggerWindows(sessions, now)
        val tobacco = result.first { it.substance == Substance.TOBACCO }
        val cannabis = result.first { it.substance == Substance.CANNABIS }
        assertTrue(tobacco.peakHours.any { it in listOf(8, 9) })
        assertTrue(cannabis.peakHours.any { it in listOf(21, 22) })
        // Tobacco peaks should NOT include cannabis hours and vice versa.
        assertFalse(tobacco.peakHours.any { it >= 18 })
        assertFalse(cannabis.peakHours.any { it < 12 })
    }

    @Test
    fun `triggerWindows nearPeakNow flips when within one hour of peak`() {
        val sessions = mutableListOf<SmokingSession>()
        for (d in 1..15) {
            sessions += SmokingSession(atHour(today() - d * day, 9))
            sessions += SmokingSession(atHour(today() - d * day, 9))
        }
        val nearNow = atHour(today(), 10) // one hour after peak
        val farNow = atHour(today(), 15)  // far from peak
        val near = ScoreCalculator.calculateTriggerWindows(sessions, nearNow).first()
        val far = ScoreCalculator.calculateTriggerWindows(sessions, farNow).first()
        assertTrue(near.nearPeakNow)
        assertFalse(far.nearPeakNow)
    }

    @Test
    fun `triggerWindows withholds peaks under minimum-sessions threshold`() {
        val now = atHour(today(), 12)
        // Only 3 sessions total in window — not enough to call peaks.
        val sessions = listOf(
            SmokingSession(atHour(today() - 1 * day, 9)),
            SmokingSession(atHour(today() - 2 * day, 9)),
            SmokingSession(atHour(today() - 3 * day, 9)),
        )
        val result = ScoreCalculator.calculateTriggerWindows(sessions, now)
        assertTrue(result.first().peakHours.isEmpty())
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
