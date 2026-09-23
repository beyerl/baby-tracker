package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event

/** Rows to write locally after merging the other phone's events. */
data class MergeChanges(val inserts: List<Event>, val updates: List<Event>)

/**
 * Last-writer-wins merge per [Event.uuid], pure so it is unit-tested on the JVM.
 * An unknown uuid is inserted; a newer [Event.updatedAt] overwrites the local
 * row (keeping its local id); an older one is ignored. On equal timestamps with
 * different content a fixed order of the content decides, so both phones pick
 * the same winner and converge.
 */
object SyncMerge {

    fun changes(local: List<Event>, remote: List<Event>): MergeChanges {
        val byUuid = local.associateBy { it.uuid }
        val inserts = ArrayList<Event>()
        val updates = ArrayList<Event>()
        for (incoming in remote.distinctBy { it.uuid }) {
            val mine = byUuid[incoming.uuid]
            when {
                mine == null -> inserts += incoming.copy(id = 0)
                wins(incoming, mine) -> updates += incoming.copy(id = mine.id)
            }
        }
        return MergeChanges(inserts, updates)
    }

    /** Whether [incoming] replaces [mine] (same uuid). */
    internal fun wins(incoming: Event, mine: Event): Boolean = when {
        incoming.updatedAt != mine.updatedAt -> incoming.updatedAt > mine.updatedAt
        else -> contentKey(incoming) > contentKey(mine)
    }

    private fun contentKey(e: Event): String =
        listOf(e.deleted, e.type, e.startTime, e.endTime, e.note, e.sleepMarker, e.createdAt).joinToString("|")
}
