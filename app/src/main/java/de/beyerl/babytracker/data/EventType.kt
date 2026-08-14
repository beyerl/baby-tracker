package de.beyerl.babytracker.data

/**
 * Categories tracked by the app.
 *
 * STOOL, PEE, FEED are point-in-time events (only [Event.startTime] is set).
 * SLEEP is an interval (both [Event.startTime] and [Event.endTime] are set).
 */
enum class EventType {
    STOOL,   // "Gaki" – Stuhlgang
    PEE,     // "Lulu" – Pinkeln
    FEED,    // Füttern / Stillen
    SLEEP;   // Schlaffenster

    val isInterval: Boolean get() = this == SLEEP
}
