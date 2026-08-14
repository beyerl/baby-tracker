package de.beyerl.babytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)

    /** Events whose start falls within the half-open range [start, end). */
    @Query("SELECT * FROM events WHERE startTime >= :start AND startTime < :end ORDER BY startTime ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): Event?
}
