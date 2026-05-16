package com.smokless.smokeless.util

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val requirement: Long,  // Days or count
    val isUnlocked: Boolean = false
)

object AchievementManager {

    fun getStreakAchievements(
        currentStreak: Int,
        bestStreak: Int,
        copy: SubstanceCopy = SubstanceCopy.TOBACCO,
    ): List<Achievement> {
        val suffix = copy.cleanSuffix
        val streakAchievements = listOf(
            Achievement("first_hour", "First Hour", "1 hour $suffix", "⏰", 1),
            Achievement("first_day", "Clean Day", "24 hours $suffix", "🌟", 1),
            Achievement("three_days", "Breaking Free", "3 days $suffix", "💪", 3),
            Achievement("one_week", "Week Warrior", "7 days $suffix", "🔥", 7),
            Achievement("two_weeks", "Fortnight Fighter", "14 days $suffix", "💎", 14),
            Achievement("one_month", "Monthly Master", "30 days $suffix", "🏆", 30),
            Achievement("90_days", "Quarter Champion", "90 days $suffix", "👑", 90),
            Achievement("six_months", "Half Year Hero", "180 days $suffix", "🌈", 180),
            Achievement("one_year", "Annual Achievement", "365 days $suffix", "🎊", 365)
        )

        return streakAchievements.map { achievement ->
            achievement.copy(isUnlocked = bestStreak >= achievement.requirement)
        }
    }
    
    fun getCravingsAchievements(resistedCount: Int): List<Achievement> {
        val cravingAchievements = listOf(
            Achievement("first_resist", "Willpower", "Resisted first craving", "💪", 1),
            Achievement("ten_resists", "Strong Mind", "Resisted 10 cravings", "🧠", 10),
            Achievement("fifty_resists", "Iron Will", "Resisted 50 cravings", "🛡️", 50),
            Achievement("hundred_resists", "Unbreakable", "Resisted 100 cravings", "💎", 100),
            Achievement("five_hundred_resists", "Legend", "Resisted 500 cravings", "⚡", 500)
        )
        
        return cravingAchievements.map { achievement ->
            achievement.copy(isUnlocked = resistedCount >= achievement.requirement)
        }
    }
    
    fun getCleanDaysAchievements(cleanDays: Int): List<Achievement> {
        val cleanAchievements = listOf(
            Achievement("five_clean", "Getting Started", "5 clean days", "🌱", 5),
            Achievement("ten_clean", "Building Up", "10 clean days", "🌿", 10),
            Achievement("thirty_clean", "Strong Foundation", "30 clean days", "🌳", 30),
            Achievement("hundred_clean", "Century of Health", "100 clean days", "🏔️", 100)
        )
        
        return cleanAchievements.map { achievement ->
            achievement.copy(isUnlocked = cleanDays >= achievement.requirement)
        }
    }
    
    fun getAllAchievements(
        currentStreak: Int,
        bestStreak: Int,
        resistedCount: Int,
        cleanDays: Int,
        copy: SubstanceCopy = SubstanceCopy.TOBACCO,
    ): List<Achievement> {
        return getStreakAchievements(currentStreak, bestStreak, copy) +
               getCravingsAchievements(resistedCount) +
               getCleanDaysAchievements(cleanDays)
    }

    fun getNextAchievement(
        currentStreak: Int,
        bestStreak: Int,
        resistedCount: Int,
        cleanDays: Int,
        copy: SubstanceCopy = SubstanceCopy.TOBACCO,
    ): Achievement? {
        val all = getAllAchievements(currentStreak, bestStreak, resistedCount, cleanDays, copy)
        return all.firstOrNull { !it.isUnlocked }
    }
}

