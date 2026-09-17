package de.beyerl.babytracker.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromSleepMarker(value: SleepMarker): String = value.name

    /** Unknown values fall back to NONE instead of crashing the query. */
    @TypeConverter
    fun toSleepMarker(value: String): SleepMarker =
        SleepMarker.entries.firstOrNull { it.name == value } ?: SleepMarker.NONE
}
