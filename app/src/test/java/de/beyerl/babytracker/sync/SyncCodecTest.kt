package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SyncCodecTest {

    private val key = ByteArray(32) { it.toByte() }
    private val message = SyncMessage(
        deviceId = "phone-a",
        events = listOf(
            Event(type = EventType.SLEEP, startTime = 1, endTime = 2, note = "Mittagsschlaf – ä", createdAt = 3,
                sleepMarker = SleepMarker.BEDTIME, uuid = "u1", updatedAt = 4),
            Event(type = EventType.FEED, startTime = 5, createdAt = 6, uuid = "u2", updatedAt = 7, deleted = true),
        ),
    )

    private fun frame(k: ByteArray = key, direction: Direction = Direction.REQUEST): ByteArray =
        ByteArrayOutputStream().also { SyncCodec.writeFrame(it, k, direction, message) }.toByteArray()

    @Test
    fun roundTrip_keepsAllFields() {
        val read = SyncCodec.readFrame(ByteArrayInputStream(frame()), key, Direction.REQUEST)

        assertEquals(message, read)
    }

    @Test(expected = SyncAuthException::class)
    fun wrongKey_isRejected() {
        SyncCodec.readFrame(ByteArrayInputStream(frame()), ByteArray(32), Direction.REQUEST)
    }

    @Test(expected = SyncAuthException::class)
    fun requestReflectedAsResponse_isRejected() {
        SyncCodec.readFrame(ByteArrayInputStream(frame(direction = Direction.REQUEST)), key, Direction.RESPONSE)
    }

    @Test(expected = SyncAuthException::class)
    fun tamperedFrame_isRejected() {
        val bytes = frame()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        SyncCodec.readFrame(ByteArrayInputStream(bytes), key, Direction.REQUEST)
    }
}
