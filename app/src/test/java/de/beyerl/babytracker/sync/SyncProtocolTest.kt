package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class SyncProtocolTest {

    /** In-memory store merging with [SyncMerge], like the Room DAO does. */
    private class MemoryStore(initial: List<Event>) : SyncStore {
        var rows = initial.toMutableList()
        override suspend fun allForSync() = rows.toList()
        override suspend fun applyRemote(events: List<Event>): Int {
            val changes = SyncMerge.changes(rows, events)
            changes.updates.forEach { u -> rows[rows.indexOfFirst { it.uuid == u.uuid }] = u }
            rows.addAll(changes.inserts)
            return changes.inserts.size + changes.updates.size
        }
        fun live() = rows.filter { !it.deleted }.map { it.uuid to it.note }.toSet()
    }

    private val key = ByteArray(32) { 7 }

    private fun ev(uuid: String, updatedAt: Long, note: String? = null, deleted: Boolean = false) =
        Event(type = EventType.FEED, startTime = 1, createdAt = 1, uuid = uuid, updatedAt = updatedAt, note = note, deleted = deleted)

    @Test
    fun oneRoundTrip_bothPhonesConverge() = runBlocking {
        val a = MemoryStore(listOf(ev("only-a", 1), ev("shared", 5, note = "neuer auf A"), ev("gone", 1)))
        val b = MemoryStore(listOf(ev("only-b", 1), ev("shared", 3, note = "älter auf B"), ev("gone", 2, deleted = true)))

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val serving = async(Dispatchers.IO) { SyncProtocol.serve(server.accept(), key, "B", b) }
            val client = SyncProtocol.runClient("127.0.0.1", server.localPort, key, "A", a)
            val served = serving.await()

            assertEquals("B", client.peerDeviceId)
            assertEquals("A", served.peerDeviceId)
        }

        val expected = setOf("only-a" to null, "only-b" to null, "shared" to "neuer auf A")
        assertEquals(expected, a.live())
        assertEquals(expected, b.live())
    }
}
