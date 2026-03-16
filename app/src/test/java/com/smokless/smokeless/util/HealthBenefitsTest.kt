package com.smokless.smokeless.util

import org.junit.Assert.*
import org.junit.Test

class HealthBenefitsTest {

    @Test
    fun `getMilestones returns all milestones`() {
        val milestones = HealthBenefits.getMilestones(0)
        assertEquals(17, milestones.size)
    }

    @Test
    fun `getMilestones marks achieved milestones correctly`() {
        val milestones = HealthBenefits.getMilestones(25) // 25 hours
        val achieved = milestones.filter { it.isAchieved }
        // Should achieve: 0h (Immediate), 1h (20min), 8h, 12h, 24h (1 Day)
        assertEquals(5, achieved.size)
    }

    @Test
    fun `getMilestones marks none achieved at 0 hours except immediate`() {
        val milestones = HealthBenefits.getMilestones(0)
        val achieved = milestones.filter { it.isAchieved }
        assertEquals(1, achieved.size) // Only "Immediate" (0 hours)
    }

    @Test
    fun `getMilestones marks all achieved at max hours`() {
        val milestones = HealthBenefits.getMilestones(200_000)
        assertTrue(milestones.all { it.isAchieved })
    }

    @Test
    fun `getNextMilestone returns first unachieved milestone`() {
        val next = HealthBenefits.getNextMilestone(0)
        assertNotNull(next)
        assertEquals(1, next!!.hours) // "20 Minutes" milestone at 1 hour
    }

    @Test
    fun `getNextMilestone returns null when all achieved`() {
        val next = HealthBenefits.getNextMilestone(200_000)
        assertNull(next)
    }

    @Test
    fun `getCurrentMilestone returns most recent achieved`() {
        val current = HealthBenefits.getCurrentMilestone(50) // 50 hours
        assertNotNull(current)
        assertEquals(48, current!!.hours) // "2 Days" milestone
    }

    @Test
    fun `getCurrentMilestone returns immediate at 0 hours`() {
        val current = HealthBenefits.getCurrentMilestone(0)
        assertNotNull(current)
        assertEquals(0, current!!.hours)
    }

    @Test
    fun `getProgressToNextMilestone returns percentage`() {
        // Between 1h and 8h milestone, at 4.5 hours
        val progress = HealthBenefits.getProgressToNextMilestone(4)
        // (4 - 1) / (8 - 1) * 100 = 42.86%
        assertEquals(42.86f, progress, 1f)
    }

    @Test
    fun `getProgressToNextMilestone returns 100 when all achieved`() {
        val progress = HealthBenefits.getProgressToNextMilestone(200_000)
        assertEquals(100f, progress, 0.01f)
    }

    @Test
    fun `getMotivationalMessage returns different messages for different durations`() {
        val msg0 = HealthBenefits.getMotivationalMessage(0)
        val msg24 = HealthBenefits.getMotivationalMessage(24)
        val msg8760 = HealthBenefits.getMotivationalMessage(8760)

        assertNotEquals(msg0, msg24)
        assertNotEquals(msg24, msg8760)
        assertTrue(msg0.isNotEmpty())
    }
}
