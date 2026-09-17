package de.beyerl.babytracker.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyAveragesTest {

    @Test
    fun groupsValuesByMondayToSundayWeek() {
        val values = mapOf(
            LocalDate.parse("2026-09-13") to 600.0, // Sunday
            LocalDate.parse("2026-09-14") to 660.0, // Monday
            LocalDate.parse("2026-09-20") to 700.0, // Sunday
            LocalDate.parse("2026-09-07") to 620.0, // Monday
        )

        val weeks = weeklyAverages(values)

        assertEquals(listOf(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-14")), weeks.map { it.monday })
        assertEquals(610.0, weeks[0].average, 1e-9)
        assertEquals(2, weeks[0].count)
        assertEquals(680.0, weeks[1].average, 1e-9)
        assertEquals(2, weeks[1].count)
    }

    @Test
    fun noValues_noWeeks() {
        assertTrue(weeklyAverages(emptyMap()).isEmpty())
    }
}
