package com.smokless.smokeless.util

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
        assertEquals(1.0, pace.actualToday, 1e-9)
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
        assertEquals(4.0, pace.actualToday, 1e-9)
    }

    @Test
    fun `calculateTodayPace BEHIND when today smoked above 125 percent of typical-by-now`() {
        // 7 prior days × 10 events evenly spread across 24h → baseline 10
        // and an approximately linear rhythm CDF. At noon, ~60% of typical
        // dose has happened → typical-by-now ≈ 6. Today: 8 dose → above the
        // 125% threshold → BEHIND.
        val midnight = paceNow(hourOfDay = 0)
        val now = paceNow(hourOfDay = 12)
        val day = 24L * 3_600_000
        val hour = 3_600_000L
        val priors = (1..7).flatMap { d ->
            (0..9).map { i ->
                val hourOffset = i * 2.4 // 0, 2.4, 4.8, ..., 21.6
                SmokingSession(midnight - d * day + (hourOffset * hour).toLong())
                    .apply { id = (d * 100 + i).toLong() }
            }
        }
        val today = List(8) {
            SmokingSession(now - it * 60_000L - 3_600_000).apply { id = (2000 + it).toLong() }
        }
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
        assertEquals(1.0, pace.actualToday, 1e-9)
    }

    @Test
    fun `calculateTodayPace CLEAN_TODAY when baseline near zero and today is clean`() {
        val now = paceNow(hourOfDay = 15)
        val day = 24L * 3_600_000
        val priors = listOf(SmokingSession(now - 8 * day).apply { id = 1 })
        val pace = ScoreCalculator.calculateTodayPace(priors, now)
        assertEquals(ScoreCalculator.PaceState.CLEAN_TODAY, pace.state)
        assertEquals(0.0, pace.actualToday, 1e-9)
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

        // Prior days 2..14 with 8 events each at 12:00. d=1 (yesterday) is
        // intentionally skipped so the wake-anchored "today" window — which
        // starts at yesterday 07:00 and runs to now — only contains the
        // sinceWake events, not noon-of-yesterday. d=14 falls just outside
        // the priorDays=13 cutoff; d=2..13 contributes 12 × 8 = 96 events.
        // Plus the 7 pre-midnight sinceWake events that the calendar-midnight
        // prior bucket also picks up → baseline ≈ 7.9/day.
        val priors = (2..14).flatMap { d ->
            List(8) { i ->
                SmokingSession(midnight - d * day + 12 * hour + i * 60_000L)
                    .apply { id = (d * 1000 + i).toLong() }
            }
        }

        // 7 smokes during yesterday's awake hours + 1 post-midnight smoke.
        val sinceWake = (0..6).map { i ->
            SmokingSession(wake + i * 2 * hour + 30 * 60_000L)
                .apply { id = (90_000 + i).toLong() }
        } + SmokingSession(now - 30 * 60_000L).apply { id = 99_000L } // 00:30 today

        val withoutAnchor = ScoreCalculator.calculateTodayPace(priors + sinceWake, now)
        // actualToday = the single 00:30 event (dose 1.0). No prior events
        // ever land in the 00:00–01:00 hour-of-day bucket, so the rhythm
        // CDF at elapsed=1h is 0 → typicalByNow = 0 → any smoke flags as
        // BEHIND. Misleading: the user is actually on track for an 18h
        // awake stretch.
        assertEquals(ScoreCalculator.PaceState.BEHIND, withoutAnchor.state)
        assertEquals(1.0, withoutAnchor.actualToday, 1e-9)

        val withAnchor = ScoreCalculator.calculateTodayPace(priors + sinceWake, now, dayStartMs = wake)
        // actualToday = 8 events × dose 1.0 = 8.0 (whole awake window). The
        // rhythm CDF saturates to 1.0 by elapsed=18h (no events sit past
        // offset 18 in this dataset), so typicalByNow ≈ baseline ≈ 7.9.
        // 8 vs 7.9 → within ±25% → ON_PACE.
        assertEquals(ScoreCalculator.PaceState.ON_PACE, withAnchor.state)
        assertEquals(8.0, withAnchor.actualToday, 1e-9)
        assertEquals(wake, withAnchor.dayStartMs)
    }

    @Test
    fun `calculateTodayPace stays ON_PACE at low baselines instead of flipping to BEHIND for one extra dose`() {
        // Light user: baseline 3 cig/day, spread across morning/midday/evening
        // so the rhythm CDF at hour 08 lands at ~1/3 → typical-by-now ≈ 1.0.
        // Under a strict ±25% band, 1.5 today would be BEHIND (1.5 > 1.25).
        // With the 0.5-dose floor it stays ON_PACE so one unusually intense
        // morning drag doesn't read as falling off the wagon.
        val midnight = paceNow(hourOfDay = 0)
        val now = paceNow(hourOfDay = 8)
        val day = 24L * 3_600_000
        val hour = 3_600_000L

        // 5 prior days × 3 events at 04:00, 12:00, 20:00 → 15 events spread
        // evenly across the day so the CDF approximates linear.
        val priors = (1..5).flatMap { d ->
            listOf(4, 12, 20).map { h ->
                SmokingSession(midnight - d * day + h * hour)
                    .apply { id = (d * 100 + h).toLong() }
            }
        }
        // Today: 1 full + 1 half = 1.5 dose.
        val today = listOf(
            SmokingSession(now - 30 * 60_000L, quantity = 1.0).apply { id = 9001 },
            SmokingSession(now - 10 * 60_000L, quantity = 0.5).apply { id = 9002 },
        )

        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.ON_PACE, pace.state)
        assertEquals(1.5, pace.actualToday, 1e-9)
    }

    @Test
    fun `calculateTodayPace AHEAD when same-count today swaps full smokes for drags`() {
        // Reduction thesis: replacing 5 full smokes with 5 drags is 75%
        // reduction in exposure. Event count alone would call this "matching
        // pace" (5 vs 5); dose-weighting correctly flags it AHEAD.
        val now = paceNow(hourOfDay = 18) // 18/24 = 0.75 day elapsed
        val day = 24L * 3_600_000

        // 7 prior days of 10 *full* smokes/day → baseline dose 10/day, so
        // typical-by-now at 18:00 = 7.5.
        val priors = (1..7).flatMap { d ->
            List(10) {
                SmokingSession(now - d * day - it * 60_000L, quantity = 1.0)
                    .apply { id = (d * 1000 + it).toLong() }
            }
        }
        // Today: 5 drags (quantity 0.25 each) → dose 1.25, well below the
        // 0.75 × 7.5 = 5.625 cutoff → AHEAD.
        val today = List(5) {
            SmokingSession(now - it * 60_000L - 7_200_000, quantity = 0.25)
                .apply { id = (90_000 + it).toLong() }
        }

        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.AHEAD, pace.state)
        assertEquals(1.25, pace.actualToday, 1e-9)
    }

    @Test
    fun `calculateTodayPace AHEAD by 11am for a morning-heavy smoker who only did half their typical morning`() {
        // Morning-heavy pattern: 10 smokes/day all between 06:00 and 10:30.
        // Linear projection would say typical-by-11am = 10 * 11/24 ≈ 4.6 →
        // 5 smokes today reads as ON_PACE. Rhythm-aware sees that ~all
        // smoking normally finishes by 10:30 → typical-by-11am ≈ 10 →
        // 5 today reads as AHEAD, which is the honest verdict.
        val midnight = paceNow(hourOfDay = 0)
        val now = paceNow(hourOfDay = 11)
        val day = 24L * 3_600_000
        val hour = 3_600_000L

        // 14 days × 10 events at 06:00, 06:30, ..., 10:30.
        val morningHalfHours = (0..9).map { 6.0 + it * 0.5 } // 6.0..10.5
        val priors = (1..14).flatMap { d ->
            morningHalfHours.mapIndexed { i, h ->
                SmokingSession(midnight - d * day + (h * hour).toLong())
                    .apply { id = (d * 100 + i).toLong() }
            }
        }
        // Today: 5 morning smokes at 06:00, 06:30, 07:00, 07:30, 08:00.
        val today = (0..4).map { i ->
            SmokingSession(midnight + ((6.0 + i * 0.5) * hour).toLong())
                .apply { id = (90_000 + i).toLong() }
        }

        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.AHEAD, pace.state)
        assertEquals(5.0, pace.actualToday, 1e-9)
        // Rhythm CDF should have been built (≥ 14 prior events).
        assertTrue(pace.rhythmCdf.isNotEmpty())
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

}
