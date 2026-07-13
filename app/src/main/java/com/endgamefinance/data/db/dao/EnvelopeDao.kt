package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.EnvelopeTransfer
import kotlinx.coroutines.flow.Flow

@Dao
interface EnvelopeDao {

    @Query("SELECT * FROM envelopes ORDER BY name")
    fun observeAll(): Flow<List<Envelope>>

    @Query("SELECT * FROM envelopes WHERE id = :id")
    suspend fun getById(id: String): Envelope?

    @Insert
    suspend fun insert(envelope: Envelope)

    @Update
    suspend fun update(envelope: Envelope)

    @Delete
    suspend fun delete(envelope: Envelope)

    @Query(
        """
        SELECT * FROM envelope_transfers
        WHERE from_envelope_id = :envelopeId OR to_envelope_id = :envelopeId
        ORDER BY timestamp DESC
        LIMIT 20
        """,
    )
    fun observeTransfersFor(envelopeId: String): Flow<List<EnvelopeTransfer>>

    @Insert
    suspend fun insertTransfer(transfer: EnvelopeTransfer)

    /** Only called when deleting an emptied envelope; envelope moves are virtual, not ledger history. */
    @Query("DELETE FROM envelope_transfers WHERE from_envelope_id = :envelopeId OR to_envelope_id = :envelopeId")
    suspend fun deleteTransfersFor(envelopeId: String)
}
