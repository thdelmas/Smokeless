package com.smokless.smokeless.ui.main

import org.junit.Assert.*
import org.junit.Test

class ScoreDataTest {

    @Test
    fun `getDisplayValue formats average with one decimal`() {
        val score = ScoreData(
            label = "Daily Average",
            value = 3,
            decimalValue = 3.456,
            percentage = 70.0,
            unit = "cigs/day",
            type = ScoreData.StatType.AVERAGE
        )
        assertEquals("3.5", score.getDisplayValue())
    }

    @Test
    fun `getDisplayValue formats frequency with one decimal`() {
        val score = ScoreData(
            label = "Rate",
            value = 0,
            decimalValue = 0.0,
            percentage = 100.0,
            type = ScoreData.StatType.FREQUENCY
        )
        assertEquals("0.0", score.getDisplayValue())
    }

    @Test
    fun `getDisplayValue returns integer string for count type`() {
        val score = ScoreData(
            label = "Total",
            value = 42,
            type = ScoreData.StatType.COUNT
        )
        assertEquals("42", score.getDisplayValue())
    }

    @Test
    fun `getDisplayValue returns integer string for streak type`() {
        val score = ScoreData(
            label = "Streak",
            value = 7,
            type = ScoreData.StatType.STREAK
        )
        assertEquals("7", score.getDisplayValue())
    }

    @Test
    fun `colorLevel returns 4 for high percentage`() {
        val score = ScoreData(label = "Test", value = 0, percentage = 90.0)
        assertEquals(4, score.colorLevel)
    }

    @Test
    fun `colorLevel returns 3 for 60-79 percentage`() {
        val score = ScoreData(label = "Test", value = 0, percentage = 65.0)
        assertEquals(3, score.colorLevel)
    }

    @Test
    fun `colorLevel returns 2 for 40-59 percentage`() {
        val score = ScoreData(label = "Test", value = 0, percentage = 45.0)
        assertEquals(2, score.colorLevel)
    }

    @Test
    fun `colorLevel returns 1 for 20-39 percentage`() {
        val score = ScoreData(label = "Test", value = 0, percentage = 25.0)
        assertEquals(1, score.colorLevel)
    }

    @Test
    fun `colorLevel returns 0 for low percentage`() {
        val score = ScoreData(label = "Test", value = 0, percentage = 10.0)
        assertEquals(0, score.colorLevel)
    }

    @Test
    fun `statusEmoji returns trophy for zero count`() {
        val score = ScoreData(label = "Total", value = 0, type = ScoreData.StatType.COUNT)
        assertEquals("\uD83C\uDFC6", score.statusEmoji) // 🏆
    }

    @Test
    fun `statusEmoji returns green for low count`() {
        val score = ScoreData(label = "Total", value = 2, type = ScoreData.StatType.COUNT)
        assertEquals("\uD83D\uDFE2", score.statusEmoji) // 🟢
    }

    @Test
    fun `statusEmoji returns red for high count`() {
        val score = ScoreData(label = "Total", value = 20, type = ScoreData.StatType.COUNT)
        assertEquals("\uD83D\uDD34", score.statusEmoji) // 🔴
    }

    @Test
    fun `statusEmoji returns fire for long streak`() {
        val score = ScoreData(label = "Streak", value = 14, type = ScoreData.StatType.STREAK)
        assertEquals("\uD83D\uDD25", score.statusEmoji) // 🔥
    }

    @Test
    fun `statusEmoji returns seedling for 1-day streak`() {
        val score = ScoreData(label = "Streak", value = 1, type = ScoreData.StatType.STREAK)
        assertEquals("\uD83C\uDF31", score.statusEmoji) // 🌱
    }

    @Test
    fun `statusEmoji shows improving trend for positive percentage`() {
        val score = ScoreData(label = "Trend", value = 0, percentage = 35.0, type = ScoreData.StatType.TREND)
        assertEquals("\uD83D\uDCC8", score.statusEmoji) // 📈
    }

    @Test
    fun `statusEmoji shows worsening trend for very negative percentage`() {
        val score = ScoreData(label = "Trend", value = 0, percentage = -50.0, type = ScoreData.StatType.TREND)
        assertEquals("\uD83D\uDCC9", score.statusEmoji) // 📉
    }
}
