package com.smokless.smokeless.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `format returns 00 for zero`() {
        assertEquals("00", TimeFormatter.format(0))
    }

    @Test
    fun `format returns 00 for negative`() {
        assertEquals("00", TimeFormatter.format(-1000))
    }

    @Test
    fun `format shows seconds only when under 1 minute`() {
        assertEquals("45", TimeFormatter.format(45_000))
    }

    @Test
    fun `format shows minutes and seconds when under 1 hour`() {
        assertEquals("05:30", TimeFormatter.format(5 * 60_000 + 30_000))
    }

    @Test
    fun `format shows hours minutes seconds when under 1 day`() {
        assertEquals("02:15:30", TimeFormatter.format(2 * 3600_000L + 15 * 60_000 + 30_000))
    }

    @Test
    fun `format shows days when 1 day or more`() {
        assertEquals("1D 00:00:00", TimeFormatter.format(24 * 3600_000L))
    }

    @Test
    fun `format shows months when 30 days or more`() {
        assertEquals("1M 0D 00:00:00", TimeFormatter.format(30L * 24 * 3600_000))
    }

    @Test
    fun `format shows years when 365 days or more`() {
        assertEquals("1Y 0M 0D 00:00:00", TimeFormatter.format(365L * 24 * 3600_000))
    }

    @Test
    fun `formatShort returns 0s for zero`() {
        assertEquals("0s", TimeFormatter.formatShort(0))
    }

    @Test
    fun `formatShort returns 0s for negative`() {
        assertEquals("0s", TimeFormatter.formatShort(-500))
    }

    @Test
    fun `formatShort shows minutes only when under 1 hour`() {
        assertEquals("30m", TimeFormatter.formatShort(30 * 60_000L))
    }

    @Test
    fun `formatShort shows hours and minutes when under 1 day`() {
        assertEquals("2h 30m", TimeFormatter.formatShort(2 * 3600_000L + 30 * 60_000))
    }

    @Test
    fun `formatShort shows days and hours when 1 day or more`() {
        assertEquals("3d 5h", TimeFormatter.formatShort(3 * 24 * 3600_000L + 5 * 3600_000L))
    }
}
