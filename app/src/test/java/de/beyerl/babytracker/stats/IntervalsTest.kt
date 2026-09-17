package de.beyerl.babytracker.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalsTest {

    @Test
    fun clipsIntervalsToWindow() {
        assertEquals(30L, coveredMillis(listOf(0L to 50L), from = 20, to = 100))
        assertEquals(20L, coveredMillis(listOf(80L to 150L), from = 0, to = 100))
    }

    @Test
    fun countsOverlapsOnce() {
        val intervals = listOf(0L to 50L, 40L to 80L, 60L to 70L)

        assertEquals(80L, coveredMillis(intervals, from = 0, to = 100))
    }

    @Test
    fun handlesUnsortedDisjointIntervals() {
        assertEquals(30L, coveredMillis(listOf(70L to 90L, 10L to 20L), from = 0, to = 100))
    }

    @Test
    fun intervalsOutsideWindow_coverNothing() {
        assertEquals(0L, coveredMillis(listOf(0L to 10L, 100L to 120L), from = 10, to = 100))
    }
}
