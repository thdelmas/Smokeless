package com.smokless.smokeless.util

/**
 * Evidence-informed tactics for riding out an acute craving (typical duration
 * 3–5 minutes). Drawn from behavioral-substitution literature, HALT
 * (Hungry/Angry/Lonely/Tired) framing, and grounding techniques used in
 * anxiety treatment. Each tactic is something the user can start within 10
 * seconds and finish before the craving peak passes.
 */
data class CravingTactic(
    val icon: String,
    val title: String,
    val body: String,
)

object CravingTactics {

    val pool: List<CravingTactic> = listOf(
        CravingTactic(
            icon = "🌬️",
            title = "Box breathing",
            body = "Breathe in 4s · hold 4s · out 4s · hold 4s. Five rounds. " +
                "Activates the parasympathetic system — cravings ride the same nervous-system wave as stress.",
        ),
        CravingTactic(
            icon = "💧",
            title = "Slow glass of water",
            body = "Sip a full glass without stopping. Hydration blunts " +
                "craving intensity, slows the heart, and gives your hands something to hold.",
        ),
        CravingTactic(
            icon = "🚶",
            title = "Two-minute walk",
            body = "Stand up. Walk anywhere — hallway, balcony, around the block. " +
                "Cravings dissipate faster when you change physical context.",
        ),
        CravingTactic(
            icon = "🖐️",
            title = "5-4-3-2-1 grounding",
            body = "Name 5 things you can see, 4 you can touch, 3 you can hear, " +
                "2 you can smell, 1 you can taste. Pulls you out of the craving loop.",
        ),
        CravingTactic(
            icon = "🥕",
            title = "Crunch something",
            body = "A carrot, an apple, ice, gum. Replaces the oral fixation " +
                "and lets the craving chew itself through.",
        ),
        CravingTactic(
            icon = "✍️",
            title = "Write your why",
            body = "One sentence: why are you doing this today? Re-anchors " +
                "intent in the moment that needs it most.",
        ),
        CravingTactic(
            icon = "🧊",
            title = "Cold water on wrists",
            body = "30 seconds of cold tap water on the inside of your wrists or " +
                "back of the neck. Triggers the mammalian dive reflex, drops heart rate.",
        ),
        CravingTactic(
            icon = "📞",
            title = "Reach out",
            body = "Text one person — anyone. Cravings shrink when they're not held alone, " +
                "and naming the urge externally weakens it.",
        ),
    )

    /** Pick a random tactic. Use [nextDistinctFrom] when cycling. */
    fun random(): CravingTactic = pool.random()

    /** Pick a random tactic different from the current one. */
    fun nextDistinctFrom(current: CravingTactic): CravingTactic {
        if (pool.size <= 1) return current
        val others = pool.filter { it != current }
        return others.random()
    }
}
