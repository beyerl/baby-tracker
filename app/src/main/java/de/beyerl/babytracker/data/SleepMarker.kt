package de.beyerl.babytracker.data

/**
 * Role of a SLEEP event within the night, chosen via radio buttons in the editor.
 *
 * The markers anchor the night-based statistics: a night runs from the start of
 * a bedtime event to the end of a wake-up event on the following morning.
 * Naps and sleep phases in the middle of the night stay [NONE] (the default).
 */
enum class SleepMarker {
    NONE,     // weder noch
    BEDTIME,  // Schlafenszeit – the event's start is the evening bedtime
    WAKE_UP,  // Aufwachzeit – the event's end is the morning wake-up
    BOTH;     // Schlafens- und Aufwachzeit – a night slept through in one event

    val isBedtime: Boolean get() = this == BEDTIME || this == BOTH
    val isWakeUp: Boolean get() = this == WAKE_UP || this == BOTH
}
