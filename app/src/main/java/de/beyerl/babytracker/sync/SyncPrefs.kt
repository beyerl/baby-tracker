package de.beyerl.babytracker.sync

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

/** Pairing key, this phone's device id and sync status, stored in SharedPreferences. */
class SyncPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /** Random id of this phone, created on first use. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    /** 256-bit AES key shared by both paired phones, or null while not paired. */
    var key: ByteArray?
        get() = prefs.getString(KEY_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(value) = prefs.edit().putString(KEY_KEY, value?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply()

    val isPaired: Boolean get() = key != null

    /** Keep a background service listening so the other phone can sync while this app is closed. */
    var backgroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** Creates a new random key for this phone to show as QR code. */
    fun newKey(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_KEY = "key"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_LAST_SYNC = "last_sync"

        private const val QR_PREFIX = "babytracker-sync:1:"

        /** QR code content for [key]. */
        fun pairingText(key: ByteArray): String =
            QR_PREFIX + Base64.encodeToString(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        /** The key in a scanned QR code, or null if it isn't a Baby Tracker pairing code. */
        fun keyFromPairingText(text: String): ByteArray? {
            if (!text.startsWith(QR_PREFIX)) return null
            return runCatching { Base64.decode(text.removePrefix(QR_PREFIX), Base64.URL_SAFE) }
                .getOrNull()
                ?.takeIf { it.size == 32 }
        }
    }
}
