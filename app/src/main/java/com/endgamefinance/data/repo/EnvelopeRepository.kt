package com.endgamefinance.data.repo

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.EnvelopeTransfer
import java.util.UUID

class EnvelopeRepository(private val db: EndgameDatabase) {

    suspend fun create(name: String, targetCents: Long?, linkedAccountId: String?) {
        db.envelopeDao().insert(
            Envelope(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                targetAmount = targetCents,
                linkedAccountId = linkedAccountId,
            ),
        )
    }

    suspend fun update(envelope: Envelope) = db.envelopeDao().update(envelope)

    /**
     * Explicit, deliberate movement of virtual funds — the ONLY way envelope
     * balances change. NULL side = unallocated funds. Envelope balances can
     * never go negative.
     */
    suspend fun transfer(fromEnvelopeId: String?, toEnvelopeId: String?, amountCents: Long) {
        require(amountCents > 0) { "Transfer amount must be positive" }
        require(fromEnvelopeId != null || toEnvelopeId != null) { "Pick at least one envelope" }
        require(fromEnvelopeId != toEnvelopeId) { "Source and destination must differ" }
        db.withTransaction {
            val from = fromEnvelopeId?.let {
                requireNotNull(db.envelopeDao().getById(it)) { "Source envelope missing" }
            }
            val to = toEnvelopeId?.let {
                requireNotNull(db.envelopeDao().getById(it)) { "Destination envelope missing" }
            }
            if (from != null) {
                require(from.currentAmount >= amountCents) {
                    "${from.name} only holds ${com.endgamefinance.util.Money.format(from.currentAmount)}"
                }
                db.envelopeDao().update(from.copy(currentAmount = from.currentAmount - amountCents))
            }
            if (to != null) {
                db.envelopeDao().update(to.copy(currentAmount = to.currentAmount + amountCents))
            }
            db.envelopeDao().insertTransfer(
                EnvelopeTransfer(
                    id = UUID.randomUUID().toString(),
                    fromEnvelopeId = fromEnvelopeId,
                    toEnvelopeId = toEnvelopeId,
                    amount = amountCents,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Deletion requires an empty envelope; its (virtual) transfer history goes with it. */
    suspend fun delete(envelope: Envelope) {
        require(envelope.currentAmount == 0L) {
            "Empty the envelope before deleting it"
        }
        db.withTransaction {
            db.envelopeDao().deleteTransfersFor(envelope.id)
            db.envelopeDao().delete(envelope)
        }
    }
}
