package com.smokless.smokeless.util

import com.smokless.smokeless.data.entity.SmokingSession
import com.smokless.smokeless.data.entity.Substance
import java.util.concurrent.TimeUnit

/**
 * Per-substance UI copy. Replaces hard-coded "cigarette" / "smoke-free" strings
 * scattered across the UI so a user logging only cannabis doesn't see
 * tobacco-framed reasoning.
 *
 * Scope is intentionally narrow: unit nouns, the "clean" verb, and the daily
 * abbreviation used in headlines. Physiology copy (health milestones,
 * "nicotine withdrawal" insights) and money-saved math stay tobacco-only
 * pending substance-specific data — see ROADMAP §2.2.
 */
data class SubstanceCopy(
    val unit: String,        // "cigarette" / "session"
    val units: String,       // "cigarettes" / "sessions"
    val perDay: String,      // headline abbreviation: "cigs/day" / "uses/day"
    val cleanLabel: String,  // hero label: "SMOKE-FREE FOR" / "CLEAN FOR"
) {
    fun unitFor(count: Long): String = if (count == 1L) unit else units
    fun unitFor(count: Int): String = unitFor(count.toLong())

    companion object {
        val TOBACCO = SubstanceCopy(
            unit = "cigarette",
            units = "cigarettes",
            perDay = "cigs/day",
            cleanLabel = "SMOKE-FREE FOR",
        )

        val CANNABIS = SubstanceCopy(
            unit = "session",
            units = "sessions",
            perDay = "uses/day",
            cleanLabel = "CLEAN FOR",
        )

        fun forSubstance(substance: Substance): SubstanceCopy = when (substance) {
            Substance.TOBACCO -> TOBACCO
            Substance.CANNABIS -> CANNABIS
        }

        /**
         * The substance that should drive headline copy: whichever the user
         * has logged most in the last 30 days. Ties and empty histories
         * default to [Substance.DEFAULT] (tobacco).
         *
         * Future: replace with an explicit user preference once onboarding
         * grows a "what are you tracking?" step.
         */
        fun primarySubstance(sessions: List<SmokingSession>): Substance {
            if (sessions.isEmpty()) return Substance.DEFAULT
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            val recent = sessions.filter { it.timestamp >= cutoff }
            val pool = if (recent.isNotEmpty()) recent else sessions
            val counts = pool.groupingBy { it.substance }.eachCount()
            return counts.maxByOrNull { it.value }?.key ?: Substance.DEFAULT
        }
    }
}
