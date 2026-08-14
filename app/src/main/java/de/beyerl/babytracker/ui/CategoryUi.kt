package de.beyerl.babytracker.ui

import androidx.compose.ui.graphics.Color
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.ui.theme.FeedColor
import de.beyerl.babytracker.ui.theme.PeeColor
import de.beyerl.babytracker.ui.theme.SleepColor
import de.beyerl.babytracker.ui.theme.StoolColor

/** Display metadata for an [EventType]. */
data class CategoryUi(
    val type: EventType,
    val label: String,
    val emoji: String,
    val color: Color,
)

val EventType.ui: CategoryUi
    get() = when (this) {
        EventType.STOOL -> CategoryUi(this, "Stuhlgang", "💩", StoolColor)
        EventType.PEE -> CategoryUi(this, "Pinkeln", "💧", PeeColor)
        EventType.FEED -> CategoryUi(this, "Füttern", "🍼", FeedColor)
        EventType.SLEEP -> CategoryUi(this, "Schlaf", "😴", SleepColor)
    }

/** The three point-in-time categories, in display order. */
val pointCategories = listOf(EventType.STOOL, EventType.PEE, EventType.FEED)
