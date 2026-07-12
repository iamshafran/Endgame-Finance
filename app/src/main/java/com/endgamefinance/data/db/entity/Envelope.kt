package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Virtual savings bucket, distinct from accounts. */
@Entity(
    tableName = "envelopes",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["linked_account_id"],
        ),
    ],
    indices = [Index("linked_account_id")],
)
data class Envelope(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    /** Cents. */
    @ColumnInfo(name = "target_amount") val targetAmount: Long? = null,
    /** Cents. */
    @ColumnInfo(name = "current_amount", defaultValue = "0") val currentAmount: Long = 0,
    /** Which asset account backs this envelope. */
    @ColumnInfo(name = "linked_account_id") val linkedAccountId: String? = null,
)

@Entity(
    tableName = "envelope_transfers",
    foreignKeys = [
        ForeignKey(
            entity = Envelope::class,
            parentColumns = ["id"],
            childColumns = ["from_envelope_id"],
        ),
        ForeignKey(
            entity = Envelope::class,
            parentColumns = ["id"],
            childColumns = ["to_envelope_id"],
        ),
    ],
    indices = [Index("from_envelope_id"), Index("to_envelope_id")],
)
data class EnvelopeTransfer(
    @PrimaryKey val id: String,
    /** NULL if from unallocated funds. */
    @ColumnInfo(name = "from_envelope_id") val fromEnvelopeId: String? = null,
    /** NULL if to unallocated funds. */
    @ColumnInfo(name = "to_envelope_id") val toEnvelopeId: String? = null,
    /** Cents. */
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
)
