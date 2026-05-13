package com.smokless.smokeless.data.entity

/**
 * The substance attached to a [SmokingSession]. Persisted by name so the column
 * is stable across enum-ordering changes.
 *
 * Per ROADMAP §2: the set is expected to grow (alcohol, opioids, etc.) but
 * stays YAGNI-scoped to what the UI actually exposes today — tobacco and
 * cannabis.
 */
enum class Substance {
    TOBACCO,
    CANNABIS;

    companion object {
        /** Default used by the Room migration backfill and any legacy code paths. */
        val DEFAULT: Substance = TOBACCO

        fun fromName(name: String?): Substance =
            entries.find { it.name == name } ?: DEFAULT
    }
}
