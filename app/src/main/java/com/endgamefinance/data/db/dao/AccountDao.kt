package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Query

/** Milestone 0: just enough to prove the encrypted DB opens. Real queries arrive in Milestone 1. */
@Dao
interface AccountDao {
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Long
}
