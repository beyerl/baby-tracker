package de.beyerl.babytracker.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Average of the values within one Monday–Sunday week. */
data class WeekAverage(
    val monday: LocalDate,
    val average: Double,
    /** Number of days (or nights) with a value in this week. */
    val count: Int,
)

/** Averages [values] per Monday–Sunday week, oldest week first; weeks without values are left out. */
fun weeklyAverages(values: Map<LocalDate, Double>): List<WeekAverage> =
    values.entries
        .groupBy { it.key.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .map { (monday, days) -> WeekAverage(monday, days.map { it.value }.average(), days.size) }
