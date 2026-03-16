package com.smokless.smokeless.ui.main

/**
 * Enhanced data model for statistics display
 * Now focuses on cigarette amount and frequency metrics
 */
data class ScoreData(
    val label: String,
    val value: Long,              // Integer value (count, days, etc.)
    val decimalValue: Double = 0.0,  // Decimal value (for averages, frequencies)
    val percentage: Double = 0.0,     // Progress/comparison percentage
    val unit: String = "",            // Unit of measurement (e.g., "cigs", "days", "cigs/day")
    val type: StatType = StatType.COUNT
) {
    enum class StatType {
        COUNT,      // Total count (e.g., total cigarettes)
        AVERAGE,    // Average value (e.g., avg per day)
        FREQUENCY,  // Frequency metric (e.g., cigs/day)
        STREAK,     // Streak of clean days
        TREND,      // Trend indicator
        DAYS        // Number of days
    }
    
    val statusEmoji: String
        get() = when (type) {
            StatType.COUNT -> when {
                value == 0L -> "🏆"  // Perfect!
                value <= 3 -> "🟢"   // Very good
                value <= 7 -> "🟡"   // Moderate
                value <= 14 -> "🟠"  // Concerning
                else -> "🔴"         // Poor
            }
            StatType.AVERAGE -> when {
                decimalValue == 0.0 -> "🏆"  // Smoke-free
                decimalValue <= 2.0 -> "🟢"  // Excellent
                decimalValue <= 5.0 -> "💚"  // Very good
                decimalValue <= 10.0 -> "🟡"  // Good
                decimalValue <= 15.0 -> "🟠"  // Improving
                else -> "🔴"                   // Needs work
            }
            StatType.STREAK -> when {
                value >= 30 -> "🏆"  // Champion
                value >= 14 -> "🔥"  // Amazing
                value >= 7 -> "💪"   // Strong
                value >= 3 -> "⭐"   // Building
                value >= 1 -> "🌱"   // Started
                else -> "🔴"         // None
            }
            StatType.DAYS -> when {
                percentage == 100.0 -> "🏆"  // Perfect
                percentage >= 80 -> "🔥"     // Amazing
                percentage >= 60 -> "💚"     // Great
                percentage >= 40 -> "🟡"     // Good
                percentage >= 20 -> "🟠"     // Keep going
                else -> "🔴"                 // Needs work
            }
            StatType.TREND -> when {
                percentage >= 30 -> "📈"  // Improving a lot
                percentage >= 10 -> "↗️"  // Improving
                percentage >= -10 -> "➡️"  // Stable
                percentage >= -30 -> "↘️"  // Declining
                else -> "📉"              // Worsening a lot
            }
            else -> when {
                percentage >= 80 -> "🟢"
                percentage >= 60 -> "🟡"
                percentage >= 40 -> "🟠"
                else -> "🔴"
            }
        }
    
    val colorLevel: Int
        get() = when {
            percentage >= 80 -> 4 // green
            percentage >= 60 -> 3 // white/teal
            percentage >= 40 -> 2 // yellow
            percentage >= 20 -> 1 // orange
            else -> 0 // red
        }
    
    /**
     * Get formatted display value
     */
    fun getDisplayValue(): String {
        return when (type) {
            StatType.FREQUENCY, StatType.AVERAGE -> String.format("%.1f", decimalValue)
            else -> value.toString()
        }
    }
}


