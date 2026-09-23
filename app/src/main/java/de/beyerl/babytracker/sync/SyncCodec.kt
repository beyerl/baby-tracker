package de.beyerl.babytracker.sync

import de.beyerl.babytracker.data.Event
import de.beyerl.babytracker.data.EventType
import de.beyerl.babytracker.data.SleepMarker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** One side's message: who sent it and its full event set, tombstones included. */
data class SyncMessage(val deviceId: String, val events: List<Event>)

/** Which way a frame travels; part of the authenticated data so a frame can't be sent back. */
enum class Direction { REQUEST, RESPONSE }

/** The frame couldn't be decrypted or authenticated: wrong pairing key, tampered or foreign data. */
class SyncAuthException(message: String) : IOException(message)

/**
 * Wire format of the sync: a [SyncMessage] as compact binary, GZIP-compressed,
 * encrypted and authenticated with AES-256-GCM under the pairing key.
 *
 * Frame: `int length | 12-byte random nonce | ciphertext + 16-byte tag`.
 * Associated data: protocol magic + version + [Direction].
 */
object SyncCodec {

    const val PROTOCOL_VERSION = 1
    private const val MAGIC = 0x42545359 // "BTSY"
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val MAX_FRAME_BYTES = 64 * 1024 * 1024

    private val random = SecureRandom()

    fun writeFrame(out: OutputStream, key: ByteArray, direction: Direction, message: SyncMessage) {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, direction)
        val sealed = cipher.doFinal(serialize(message))
        DataOutputStream(out).apply {
            writeInt(NONCE_BYTES + sealed.size)
            write(nonce)
            write(sealed)
            flush()
        }
    }

    fun readFrame(input: InputStream, key: ByteArray, direction: Direction): SyncMessage {
        val data = DataInputStream(input)
        val length = data.readInt()
        if (length <= NONCE_BYTES || length > MAX_FRAME_BYTES) throw SyncAuthException("Ungültige Nachrichtenlänge")
        val nonce = ByteArray(NONCE_BYTES).also(data::readFully)
        val sealed = ByteArray(length - NONCE_BYTES).also(data::readFully)
        val plain = try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, direction).doFinal(sealed)
        } catch (e: GeneralSecurityException) {
            throw SyncAuthException("Nachricht nicht authentisch – ist das andere Handy mit diesem gekoppelt?")
        }
        return deserialize(plain)
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, direction: Direction): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(byteArrayOf(0x42, 0x54, PROTOCOL_VERSION.toByte(), direction.ordinal.toByte()))
        }

    internal fun serialize(message: SyncMessage): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(PROTOCOL_VERSION)
            out.writeUTF(message.deviceId)
            out.writeInt(message.events.size)
            for (e in message.events) {
                out.writeUTF(e.uuid)
                out.writeUTF(e.type.name)
                out.writeLong(e.startTime)
                out.writeBoolean(e.endTime != null)
                out.writeLong(e.endTime ?: 0L)
                out.writeBoolean(e.note != null)
                out.writeUTF(e.note ?: "")
                out.writeLong(e.createdAt)
                out.writeUTF(e.sleepMarker.name)
                out.writeLong(e.updatedAt)
                out.writeBoolean(e.deleted)
            }
        }
        return bytes.toByteArray()
    }

    internal fun deserialize(bytes: ByteArray): SyncMessage =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Keine Baby-Tracker-Nachricht")
            val version = input.readInt()
            if (version != PROTOCOL_VERSION) {
                throw IOException("Unterschiedliche App-Versionen – bitte beide Handys aktualisieren")
            }
            val deviceId = input.readUTF()
            val events = List(input.readInt()) {
                val uuid = input.readUTF()
                val type = EventType.valueOf(input.readUTF())
                val startTime = input.readLong()
                val endTime = input.readBoolean().let { has -> input.readLong().takeIf { has } }
                val note = input.readBoolean().let { has -> input.readUTF().takeIf { has } }
                val createdAt = input.readLong()
                val marker = input.readUTF().let { name -> SleepMarker.entries.firstOrNull { it.name == name } ?: SleepMarker.NONE }
                Event(
                    type = type,
                    startTime = startTime,
                    endTime = endTime,
                    note = note,
                    createdAt = createdAt,
                    sleepMarker = marker,
                    uuid = uuid,
                    updatedAt = input.readLong(),
                    deleted = input.readBoolean(),
                )
            }
            SyncMessage(deviceId, events)
        }
}
