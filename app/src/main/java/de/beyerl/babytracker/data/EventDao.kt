package de.beyerl.babytracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.beyerl.babytracker.sync.SyncMerge
import kotlinx.coroutines.flow.Flow

/** Every read hides tombstones ([Event.deleted]); only the sync sees them. */
@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event): Long

    /** Bulk insert (id = 0 auto-generates) – used by the Excel import. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<Event>)

    /** Turns every live event into a tombstone, so "Ersetzen" also reaches the other phone. */
    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun deleteAll(now: Long)

    @Update
    suspend fun update(event: Event)

    @Update
    suspend fun updateAll(events: List<Event>)

    /** Events whose start falls within the half-open range [start, end). */
    @Query("SELECT * FROM events WHERE deleted = 0 AND startTime >= :start AND startTime < :end ORDER BY startTime ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): Event?

    /** All events, oldest first – used for the full data export. */
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY startTime ASC")
    suspend fun getAll(): List<Event>

    /** All events, oldest first, observed reactively – used for analytics. */
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY startTime ASC")
    fun observeAll(): Flow<List<Event>>

    /** Every row including tombstones – what the sync exchanges. */
    @Query("SELECT * FROM events")
    suspend fun getAllForSync(): List<Event>

    /** Any change including deletions, as a trigger for syncing. */
    @Query("SELECT COUNT(*) || '-' || COALESCE(MAX(updatedAt), 0) FROM events")
    fun observeChangeStamp(): Flow<String>

    /**
     * Merges events from the other phone (last writer wins per uuid, see
     * [SyncMerge]) in one transaction. Returns the number of rows changed.
     */
    @Transaction
    suspend fun applyRemote(remote: List<Event>): Int {
        val changes = SyncMerge.changes(getAllForSync(), remote)
        insertAll(changes.inserts)
        updateAll(changes.updates)
        return changes.inserts.size + changes.updates.size
    }
}
