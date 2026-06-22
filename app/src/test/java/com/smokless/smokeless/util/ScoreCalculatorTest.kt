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
    fun `calculateTodayPace AHEAD when today is a full dose below typical-by-now`() {
        val now = paceNow(hourOfDay = 18) // 18/24 = 0.75 of day elapsed
        val day = 24L * 3_600_000
        // 7 prior days, ~10 sessions/day → baseline 10. At 18:00 typical ≈ 7.5.
        // Today: 4 dose ≤ 7.5 − 1 = 6.5 → a full-dose margin → AHEAD (taking
        // one more now would still leave you under typical).
        val priors = (1..7).flatMap { d ->
            List(10) { SmokingSession(now - d * day - it * 60_000L).apply { id = (d * 100 + it).toLong() } }
        }
        val today = List(4) { SmokingSession(now - it * 60_000L - 7_200_000).apply { id = (1000 + it).toLong() } }
        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.AHEAD, pace.state)
        assertEquals(4.0, pace.actualToday, 1e-9)
    }

    @Test
    fun `calculateTodayPace ON_PACE when better than typical but without a full-dose margin`() {
        val now = paceNow(hourOfDay = 18) // 0.75 of day elapsed (linear fraction)
        val day = 24L * 3_600_000
        // 4 prior days × 3/day = 12 prior events (< 14 → linear projection,
        // no rhythm CDF). baseline = 12/4 = 3, typical-by-now = 3 × 0.75 = 2.25.
        // Today: 2 dose → 2 < 2.25 (better than usual) but 2 > 2.25 − 1 = 1.25,
        // so no full-dose margin: one more now (→ 3 ≥ 2.25) would tip it BEHIND.
        val priors = (1..4).flatMap { d ->
            List(3) { SmokingSession(now - d * day - it * 60_000L).apply { id = (d * 100 + it).toLong() } }
        }
        val today = List(2) { SmokingSession(now - it * 60_000L - 7_200_000).apply { id = (1000 + it).toLong() } }
        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.ON_PACE, pace.state)
        assertTrue("actualToday should be below typicalByNow", pace.actualToday < pace.typicalByNow)
        assertTrue("but within one full dose", pace.actualToday > pace.typicalByNow - 1.0)
    }

    @Test
    fun `calculateTodayPace BEHIND when today is above typical-by-now`() {
        // 7 prior days × 10 events evenly spread across 24h → baseline 10
        // and an approximately linear rhythm CDF. At noon, ~60% of typical
        // dose has happened → typical-by-now ≈ 6. Today: 8 dose ≥ typical →
        // not better than usual → BEHIND.
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
        // Plus the 6 pre-midnight sinceWake events that the calendar-midnight
        // prior bucket also picks up → baseline ≈ 7.85/day.
        val priors = (2..14).flatMap { d ->
            List(8) { i ->
                SmokingSession(midnight - d * day + 12 * hour + i * 60_000L)
                    .apply { id = (d * 1000 + i).toLong() }
            }
        }

        // 6 smokes during yesterday's awake hours + 1 post-midnight smoke.
        // Kept below baseline so the strict-inferiority gate doesn't fire —
        // this test is about the wake-anchor contrast, not the BEHIND gate.
        val sinceWake = (0..5).map { i ->
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
        // actualToday = 7 events × dose 1.0 = 7.0 (whole awake window). The
        // rhythm CDF saturates to 1.0 by elapsed=18h (no events sit past
        // offset 18 in this dataset), so typicalByNow ≈ baseline ≈ 7.85.
        // 7 < 7.85 (better than usual) but 7 > 7.85 − 1 = 6.85 (no full-dose
        // margin) → ON_PACE.
        assertEquals(ScoreCalculator.PaceState.ON_PACE, withAnchor.state)
        assertEquals(7.0, withAnchor.actualToday, 1e-9)
        assertEquals(wake, withAnchor.dayStartMs)
    }

    @Test
    fun `calculateTodayPace ON_PACE not AHEAD when zero today but typicalByNow under one full dose`() {
        // Reported bug: 0 today vs typicalByNow 0.93 was reading AHEAD.
        // AHEAD requires a full-dose gap below typical (actual ≤ typical − 1).
        // With typicalByNow < 1, that threshold falls below zero, so even a
        // clean day reads ON_PACE rather than over-claiming a margin.
        val now = paceNow(hourOfDay = 14)
        val day = 24L * 3_600_000
        val hour = 3_600_000L
        val midnight = paceNow(hourOfDay = 0)

        // 7 prior days × 1 session/day at 12:00 → baseline 1.0. Rhythm CDF
        // at hour 14 saturates to ~1.0 (events all by noon), so
        // typicalByNow ≈ 0.93 once smoothing is applied. Today: 0 sessions.
        val priors = (1..7).flatMap { d ->
            listOf(12.0).map { h ->
                SmokingSession(midnight - d * day + (h * hour).toLong(), quantity = 1.0)
                    .apply { id = (d * 100).toLong() }
            }
        }

        val pace = ScoreCalculator.calculateTodayPace(priors, now)
        assertEquals(ScoreCalculator.PaceState.ON_PACE, pace.state)
        assertEquals(0.0, pace.actualToday, 1e-9)
        assertTrue("typicalByNow should be < 1", pace.typicalByNow < 1.0)
    }

    @Test
    fun `calculateTodayPace BEHIND when actualToday exceeds typicalByNow even slightly`() {
        // Reported bug: cannabis user at 1 session vs typical-by-now 0.62
        // (delta +0.38) was reading ON_PACE. The reduction thesis is "smoke
        // strictly less than your typical day" — any actualToday at or above
        // typicalByNow means you're not better than usual → BEHIND, no tolerance.
        val now = paceNow(hourOfDay = 14) // mid-afternoon, effectiveFraction ≈ 0.58
        val day = 24L * 3_600_000
        val hour = 3_600_000L
        val midnight = paceNow(hourOfDay = 0)

        // 7 prior days × 1.07 sessions/day → baseline ≈ 1.07. Sessions placed
        // at 14:00 (most days) plus a few at other hours so the rhythm CDF
        // at hour 14 lands around 0.58 → typicalByNow ≈ 0.62.
        val priors = (1..7).flatMap { d ->
            val hours = if (d % 3 == 0) listOf(14.0, 20.0) else listOf(14.0)
            hours.mapIndexed { i, h ->
                SmokingSession(midnight - d * day + (h * hour).toLong(), quantity = 1.0)
                    .apply { id = (d * 100 + i).toLong() }
            }
        }
        // Today: a single session — slightly above typicalByNow.
        val today = listOf(
            SmokingSession(now - 30 * 60_000L, quantity = 1.0).apply { id = 9001 },
        )

        val pace = ScoreCalculator.calculateTodayPace(priors + today, now)
        assertEquals(ScoreCalculator.PaceState.BEHIND, pace.state)
        assertEquals(1.0, pace.actualToday, 1e-9)
        assertTrue("actualToday should exceed typicalByNow", pace.actualToday > pace.typicalByNow)
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
        // Today: 5 drags (quantity 0.25 each) → dose 1.25, a full dose below
        // the 7.5 typical-by-now (≤ 6.5) → AHEAD.
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

    @Test
    fun `calculateScopedBaselines averages each scope over its trailing window`() {
        val now = paceNow(hourOfDay = 12)
        val day = 24L * 3_600_000
        // 40 days of steady 2 sessions/day. Baselines are trailing per-period
        // averages over completed periods: day → avg of the prior 7 days = 2;
        // week → avg of the prior 4 weeks = 14. (Month depends on the calendar
        // span and is asserted only to be populated, below.)
        val sessions = (0..39).flatMap { d ->
            List(2) { i ->
                // (i + 1) offset keeps every session strictly inside its day,
                // so none lands exactly on a window-start boundary.
                SmokingSession(now - d * day - (i + 1) * 3_600_000L)
                    .apply { id = (d * 10 + i).toLong() }
            }
        }
        val baselines = ScoreCalculator.calculateScopedBaselines(sessions, now)
        assertEquals(1, baselines.size)
        val b = baselines.first()
        assertEquals(Substance.TOBACCO, b.substance)
        assertEquals(2.0, b.day.baselinePerPeriod, 1e-6)
        assertEquals(14.0, b.week.baselinePerPeriod, 1e-6)
        // A month of prior data exists, so the month scope is computed, not calibrating.
        assertTrue(b.month.baselinePerPeriod > 0.0)
        assertTrue(b.month.state != ScoreCalculator.PaceState.CALIBRATING)
    }

    @Test
    fun `calculateScopedBaselines reports CALIBRATING for a scope with no completed prior period`() {
        val now = paceNow(hourOfDay = 12)
        val day = 24L * 3_600_000
        // Only two days of history: there's a completed prior day, but no
        // completed prior week or month yet → those scopes calibrate.
        val sessions = (0..1).flatMap { d ->
            List(2) { i -> SmokingSession(now - d * day - (i + 1) * 3_600_000L).apply { id = (d * 10 + i).toLong() } }
        }
        val b = ScoreCalculator.calculateScopedBaselines(sessions, now).first()
        assertEquals(ScoreCalculator.PaceState.CALIBRATING, b.week.state)
        assertEquals(ScoreCalculator.PaceState.CALIBRATING, b.month.state)
        assertTrue("day scope has a completed prior day", b.day.state != ScoreCalculator.PaceState.CALIBRATING)
    }

    @Test
    fun `calculateScopedBaselines separates substances`() {
        val now = paceNow(hourOfDay = 12)
        val day = 24L * 3_600_000
        // 30 days: 2 tobacco/day, 1 cannabis/day.
        val sessions = (0..29).flatMap { d ->
            listOf(
                SmokingSession(now - d * day - 3_600_000L, Substance.TOBACCO).apply { id = (d * 10).toLong() },
                SmokingSession(now - d * day - 7_200_000L, Substance.TOBACCO).apply { id = (d * 10 + 1).toLong() },
                SmokingSession(now - d * day - 10_800_000L, Substance.CANNABIS).apply { id = (d * 10 + 2).toLong() },
            )
        }
        val baselines = ScoreCalculator.calculateScopedBaselines(sessions, now)
        assertEquals(2, baselines.size)
        val tobacco = baselines.first { it.substance == Substance.TOBACCO }
        val cannabis = baselines.first { it.substance == Substance.CANNABIS }
        assertEquals(2.0, tobacco.day.baselinePerPeriod, 1e-6)
        assertEquals(1.0, cannabis.day.baselinePerPeriod, 1e-6)
    }

    @Test
    fun `paceVerdict grades the cushion into four tiers`() {
        // baseline 10/period, half the period elapsed → typical-by-now 5.0.
        val typical = 5.0
        val baseline = 10.0
        // At/above typical-by-now → BEHIND (not better than usual).
        assertEquals(ScoreCalculator.PaceState.BEHIND, ScoreCalculator.paceVerdict(5.0, typical, baseline))
        assertEquals(ScoreCalculator.PaceState.BEHIND, ScoreCalculator.paceVerdict(6.0, typical, baseline))
        // Cushion ≤ 1 dose → ON_PACE: one more flips it to BEHIND.
        assertEquals(ScoreCalculator.PaceState.ON_PACE, ScoreCalculator.paceVerdict(4.5, typical, baseline))
        assertEquals(ScoreCalculator.PaceState.ON_PACE, ScoreCalculator.paceVerdict(4.0, typical, baseline)) // cushion exactly 1
        // Cushion in (1, 2] → SLIGHTLY_AHEAD: one more stays better, two flips.
        assertEquals(ScoreCalculator.PaceState.SLIGHTLY_AHEAD, ScoreCalculator.paceVerdict(3.5, typical, baseline))
        assertEquals(ScoreCalculator.PaceState.SLIGHTLY_AHEAD, ScoreCalculator.paceVerdict(3.0, typical, baseline)) // cushion exactly 2
        // Cushion > 2 → AHEAD: even two more doses stay better than typical.
        assertEquals(ScoreCalculator.PaceState.AHEAD, ScoreCalculator.paceVerdict(2.9, typical, baseline))
        assertEquals(ScoreCalculator.PaceState.AHEAD, ScoreCalculator.paceVerdict(1.0, typical, baseline))
        // Near-zero baseline distinguishes a clean period from a fresh slip.
        assertEquals(ScoreCalculator.PaceState.CLEAN_TODAY, ScoreCalculator.paceVerdict(0.0, 0.0, 0.0))
        assertEquals(ScoreCalculator.PaceState.CLEAN_BREAK, ScoreCalculator.paceVerdict(1.0, 0.0, 0.0))
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
