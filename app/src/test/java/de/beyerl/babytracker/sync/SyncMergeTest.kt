package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun feed(uuid: String, updatedAt: Long, id: Long = 0, note: String? = null, deleted: Boolean = false) =
        Event(id = id, type = EventType.FEED, startTime = 1_000, createdAt = 500, uuid = uuid, updatedAt = updatedAt, note = note, deleted = deleted)

    @Test
    fun unknownUuid_isInsertedWithFreshLocalId() {
        val changes = SyncMerge.changes(local = emptyList(), remote = listOf(feed("a", 1, id = 42)))

        assertEquals(listOf(feed("a", 1, id = 0)), changes.inserts)
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun newerRemote_overwritesAndKeepsLocalId() {
        val changes = SyncMerge.changes(
            local = listOf(feed("a", 1, id = 7)),
            remote = listOf(feed("a", 2, id = 99, note = "neu")),
        )

        assertEquals(listOf(feed("a", 2, id = 7, note = "neu")), changes.updates)
    }

    @Test
    fun olderOrIdenticalRemote_isIgnored() {
        val local = listOf(feed("a", 5, id = 1), feed("b", 5, id = 2))

        val changes = SyncMerge.changes(local, remote = listOf(feed("a", 4, note = "alt"), feed("b", 5)))

        assertTrue(changes.inserts.isEmpty())
        assertTrue(changes.updates.isEmpty())
    }

    @Test
    fun tombstone_propagates() {
        val changes = SyncMerge.changes(local = listOf(feed("a", 1, id = 3)), remote = listOf(feed("a", 2, deleted = true)))

        assertTrue(changes.updates.single().deleted)
    }

    @Test
    fun equalTimestamps_bothPhonesPickTheSameWinner() {
        val x = feed("a", 5, note = "x")
        val y = feed("a", 5, note = "y")

        assertNotEquals(SyncMerge.wins(x, y), SyncMerge.wins(y, x))
    }
}
