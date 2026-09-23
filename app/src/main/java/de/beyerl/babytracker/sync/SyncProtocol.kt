package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event
import java.net.InetSocketAddress
import java.net.Socket

/** The local side of a sync: its events (tombstones included) and how to merge the peer's. */
interface SyncStore {
    suspend fun allForSync(): List<Event>

    /** Merges [events] from the peer; returns how many rows changed. */
    suspend fun applyRemote(events: List<Event>): Int
}

/** Outcome of one exchange, for the status line. */
data class SyncResult(val peerDeviceId: String, val received: Int, val changedHere: Int)

/**
 * One sync is a single round trip: the client sends its full event set, the
 * server merges it and answers with its full merged set, the client merges
 * that. Afterwards both phones hold the same data.
 */
object SyncProtocol {

    const val TIMEOUT_MS = 15_000

    suspend fun runClient(host: String, port: Int, key: ByteArray, deviceId: String, store: SyncStore): SyncResult =
        Socket().use { socket ->
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            SyncCodec.writeFrame(socket.getOutputStream(), key, Direction.REQUEST, SyncMessage(deviceId, store.allForSync()))
            val reply = SyncCodec.readFrame(socket.getInputStream(), key, Direction.RESPONSE)
            SyncResult(reply.deviceId, reply.events.size, store.applyRemote(reply.events))
        }

    /** Handles one connection accepted by the server socket. */
    suspend fun serve(socket: Socket, key: ByteArray, deviceId: String, store: SyncStore): SyncResult =
        socket.use {
            it.soTimeout = TIMEOUT_MS
            val request = SyncCodec.readFrame(it.getInputStream(), key, Direction.REQUEST)
            val changed = store.applyRemote(request.events)
            SyncCodec.writeFrame(it.getOutputStream(), key, Direction.RESPONSE, SyncMessage(deviceId, store.allForSync()))
            SyncResult(request.deviceId, request.events.size, changed)
        }
}
