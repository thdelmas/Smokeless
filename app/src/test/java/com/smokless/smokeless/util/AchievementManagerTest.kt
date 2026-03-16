package com.smokless.smokeless.util

import org.junit.Assert.*
import org.junit.Test

class AchievementManagerTest {

    @Test
    fun `getStreakAchievements returns 9 achievements`() {
        val achievements = AchievementManager.getStreakAchievements(0, 0)
        assertEquals(9, achievements.size)
    }

    @Test
    fun `getStreakAchievements unlocks correctly based on best streak`() {
        val achievements = AchievementManager.getStreakAchievements(0, 7)
        val unlocked = achievements.filter { it.isUnlocked }
        // Should unlock: first_hour (1), first_day (1), three_days (3), one_week (7) = 4 with requirement <= 7
        assertEquals(4, unlocked.size)
    }

    @Test
    fun `getStreakAchievements none unlocked at 0`() {
        val achievements = AchievementManager.getStreakAchievements(0, 0)
        assertTrue(achievements.none { it.isUnlocked })
    }

    @Test
    fun `getStreakAchievements all unlocked at 365`() {
        val achievements = AchievementManager.getStreakAchievements(365, 365)
        assertTrue(achievements.all { it.isUnlocked })
    }

    @Test
    fun `getCravingsAchievements returns 5 achievements`() {
        val achievements = AchievementManager.getCravingsAchievements(0)
        assertEquals(5, achievements.size)
    }

    @Test
    fun `getCravingsAchievements unlocks at 10 resists`() {
        val achievements = AchievementManager.getCravingsAchievements(10)
        val unlocked = achievements.filter { it.isUnlocked }
        assertEquals(2, unlocked.size) // 1 and 10
    }

    @Test
    fun `getCleanDaysAchievements returns 4 achievements`() {
        val achievements = AchievementManager.getCleanDaysAchievements(0)
        assertEquals(4, achievements.size)
    }

    @Test
    fun `getAllAchievements returns all categories combined`() {
        val all = AchievementManager.getAllAchievements(0, 0, 0, 0)
        assertEquals(18, all.size) // 9 + 5 + 4
    }

    @Test
    fun `getNextAchievement returns first locked achievement`() {
        val next = AchievementManager.getNextAchievement(0, 0, 0, 0)
        assertNotNull(next)
        assertFalse(next!!.isUnlocked)
    }

    @Test
    fun `getNextAchievement returns null when all unlocked`() {
        val next = AchievementManager.getNextAchievement(365, 365, 500, 100)
        assertNull(next)
    }

    @Test
    fun `achievement data class has correct fields`() {
        val achievement = Achievement("test", "Test Title", "Test Description", "🎯", 5, true)
        assertEquals("test", achievement.id)
        assertEquals("Test Title", achievement.title)
        assertEquals("🎯", achievement.icon)
        assertEquals(5L, achievement.requirement)
        assertTrue(achievement.isUnlocked)
    }
}
