package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY next_due_date")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY next_due_date")
    suspend fun allOnce(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): Reminder?

    @Query("SELECT * FROM reminders WHERE next_due_date <= :nowMs")
    suspend fun dueNow(nowMs: Long): List<Reminder>

    @Query("SELECT COUNT(*) FROM reminders WHERE name = :name AND account_id = :accountId")
    suspend fun countByNameAndAccount(name: String, accountId: String): Int

    @Insert
    suspend fun insert(reminder: Reminder)

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}
