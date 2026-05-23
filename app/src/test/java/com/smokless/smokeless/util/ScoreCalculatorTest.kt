package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.Craving
import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
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
        assertEquals(0.0, stats.totalCigarettes, 0.001)
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

    @Test
    fun `detectCravingVictories counts only confirmed windows past the cursor`() {
        val now = 10_000_000_000L
        val hour = 3_600_000L
        val cursor = now - 5 * hour
        val cravings = listOf(
            Craving(now - 6 * hour).apply { id = 1 }, // before cursor → ignored
            Craving(now - 4 * hour).apply { id = 2 }, // past cursor, window elapsed → victory
            Craving(now - 3 * hour).apply { id = 3 }, // past cursor, smoked within window → not a victory
            Craving(now - 10 * 60 * 1000).apply { id = 4 }, // window not yet elapsed → not confirmable
        )
        val sessions = listOf(
            // Real smoke at (now - 3h + 10min), exposure 10min → timestamp = now - 3h + 20min.
            SmokingSession(now - 3 * hour + 20 * 60 * 1000, Substance.TOBACCO).apply { id = 1 },
        )
        val result = ScoreCalculator.detectCravingVictories(cravings, sessions, cursor, now)
        assertEquals(1, result.newCount)
        // Cursor advances past every craving whose window has fully elapsed (id 2 and 3),
        // but NOT past id 4 (still within its 30-min window).
        assertEquals(now - 3 * hour, result.newCursor)
    }

    @Test
    fun `detectCravingVictories returns zero when nothing is confirmable yet`() {
        val now = 10_000_000_000L
        val cravings = listOf(Craving(now - 5 * 60 * 1000).apply { id = 1 })
        val result = ScoreCalculator.detectCravingVictories(cravings, emptyList(), 0L, now)
        assertEquals(0, result.newCount)
        assertEquals(0L, result.newCursor)
    }

    @Test
    fun `calculateTodayPace returns CALIBRATING with no history`() {
        val pace = ScoreCalculator.calculateTodayPace(emptyList())
        assertEquals(ScoreCalculator.PaceState.CALIBRATING, pace.state)
    }

    @Test
    fun `calculateTodayPace returns CALIBRATING with fewer than 3 prior days`() {
        // Pick a "now" deep into the day so dayFraction is meaningful.
        val now = paceNow(hourOfDay = 18)
        val day = 24L * 3_600_000
        // Two prior days of activity, plus today: priorDays = 2 < 3.
        val sessions = listOf(
            SmokingSession(now - 2 * day).apply { id = 1 },
            SmokingSession(now - 1 * day).apply { id = 2 },
            SmokingSession(now - 3_600_000).apply { id = 3 }, // today
        )
        val pace = ScoreCalculator.calculateTodayPace(sessions, now)
        assertEquals(ScoreCalculator.PaceState.CALIBRATING, pace.state)
        assertEquals(1, pace.actualToday)
    }

    @Test
    fun `calculateTodayPace AHEAD when today smoked below 75 percent of typical-by-now`() {
        val now = paceNow(hourOfDay = 18) // 18/24 = 0.75 of day elapsed
        val day = 24L * 3_600_000
        // 7 prior days, ~10 sessions/day → baseline 10. At 18:00 typical ≈ 7.5.
        // Today: 4 sessions → 4 / 7.5 = 0.53, below 75% → AHEAD.
        val priors = (1..7).flatMap { d ->
            List(10) { SmokingSession(now - d * day - it * 60_000L).apply { id = (d * 100 + it).toLong() } }
        }
        val today = List(4) { SmokingSession(now - it * 60_000L - 7_200_000).apply { id = (1000 + it).toLong() } }
        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.AHEAD, pace.state)
        assertEquals(4, pace.actualToday)
    }

    @Test
    fun `calculateTodayPace BEHIND when today smoked above 125 percent of typical-by-now`() {
        val now = paceNow(hourOfDay = 12) // 12/24 = 0.5
        val day = 24L * 3_600_000
        // 7 prior days, 10/day → baseline 10. At noon typical ≈ 5.
        // Today: 8 → 8/5 = 1.6, above 125% → BEHIND.
        val priors = (1..7).flatMap { d ->
            List(10) { SmokingSession(now - d * day - it * 60_000L).apply { id = (d * 100 + it).toLong() } }
        }
        val today = List(8) { SmokingSession(now - it * 60_000L - 3_600_000).apply { id = (2000 + it).toLong() } }
        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.BEHIND, pace.state)
    }

    @Test
    fun `calculateTodayPace CLEAN_BREAK when baseline near zero but smoked today`() {
        val now = paceNow(hourOfDay = 15)
        val day = 24L * 3_600_000
        // 7 prior clean days (just one early session for tracking-length, then nothing in window)
        val priors = listOf(SmokingSession(now - 8 * day).apply { id = 1 })
        val today = listOf(SmokingSession(now - 60_000L).apply { id = 2 })
        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.CLEAN_BREAK, pace.state)
        assertEquals(1, pace.actualToday)
    }

    @Test
    fun `calculateTodayPace CLEAN_TODAY when baseline near zero and today is clean`() {
        val now = paceNow(hourOfDay = 15)
        val day = 24L * 3_600_000
        val priors = listOf(SmokingSession(now - 8 * day).apply { id = 1 })
        val pace = ScoreCalculator.calculateTodayPace(priors, now)
        assertEquals(ScoreCalculator.PaceState.CLEAN_TODAY, pace.state)
        assertEquals(0, pace.actualToday)
    }

    @Test
    fun `calculateTodayPace with wake anchor counts post-midnight smokes into the prior waking stretch`() {
        // Setup: now is 01:00 today. User woke at 07:00 yesterday and is
        // still up (18h awake). Without a wake anchor, only the 00:30 smoke
        // is "today" and the projection (1h of 24h elapsed) is tiny, so a
        // single event reads BEHIND. With wake anchor, the whole awake
        // window counts and the verdict reflects reality.
        val hour = 3_600_000L
        val day = 24 * hour
        val midnight = paceNow(hourOfDay = 0)
        val now = midnight + hour // 01:00 today
        val wake = midnight - day + 7 * hour // yesterday 07:00 (18h before now)

        // 14 prior days, 10 events each at 06:00 — placed before the 07:00
        // wake anchor so they're clearly pre-wake "prior days" and don't
        // bleed into the wake-anchored "today" window. trackedDays resolves
        // to priorDays=13, so d=14 events fall just outside the window:
        // d=1..13 = 130 events / 13 days = baseline 10/day.
        val priors = (1..14).flatMap { d ->
            List(10) { i ->
                SmokingSession(midnight - d * day + 6 * hour + i * 60_000L)
                    .apply { id = (d * 1000 + i).toLong() }
            }
        }

        // 7 smokes during yesterday's awake hours + 1 post-midnight smoke.
        val sinceWake = (0..6).map { i ->
            SmokingSession(wake + i * 2 * hour + 30 * 60_000L)
                .apply { id = (90_000 + i).toLong() }
        } + SmokingSession(now - 30 * 60_000L).apply { id = 99_000L } // 00:30 today

        val withoutAnchor = ScoreCalculator.calculateTodayPace(priors + sinceWake, now)
        // actualToday = the single 00:30 event. typicalByNow ≈ 10 * (1h/24h) = 0.42.
        // 1 > 0.42 * 1.25 → BEHIND. Misleading — the user is actually on track.
        assertEquals(ScoreCalculator.PaceState.BEHIND, withoutAnchor.state)
        assertEquals(1, withoutAnchor.actualToday)

        val withAnchor = ScoreCalculator.calculateTodayPace(priors + sinceWake, now, dayStartMs = wake)
        // actualToday = 8 (whole awake window). typicalByNow = 10 * (18h/24h) = 7.5.
        // 8/7.5 ≈ 1.07, within ±25% → ON_PACE.
        assertEquals(ScoreCalculator.PaceState.ON_PACE, withAnchor.state)
        assertEquals(8, withAnchor.actualToday)
        assertEquals(wake, withAnchor.dayStartMs)
    }

    /** Returns a deterministic "now" at a fixed hour of day, avoiding clock flakiness. */
    private fun paceNow(hourOfDay: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, 2026); set(java.util.Calendar.MONTH, 4) // May
            set(java.util.Calendar.DAY_OF_MONTH, 15)
            set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun `detectCravingVictories uses real smoke time accounting for exposure offset`() {
        val now = 10_000_000_000L
        val win = ScoreCalculator.CRAVING_VICTORY_WINDOW_MS
        val craving = Craving(now - 2 * win).apply { id = 1 }
        // Cannabis: 30 min exposure. Real smoke at craving - 5 min, timestamp = real + 30min,
        // so the *stored* timestamp falls inside the window even though the smoke is outside.
        val realSmoke = craving.timestamp - 5 * 60 * 1000
        val session = SmokingSession(realSmoke + Substance.CANNABIS.exposureMs, Substance.CANNABIS).apply { id = 1 }
        val result = ScoreCalculator.detectCravingVictories(listOf(craving), listOf(session), 0L, now)
        // Real smoke landed BEFORE the craving → should count as a victory.
        assertEquals(1, result.newCount)
    }
}
