package com.endgamefinance.data.notify

import android.content.Context
import com.endgamefinance.data.ai.CategorySuggester
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import java.util.UUID

/**
 * Resolves a model-parsed notification into real ledger ids and posts it
 * (Milestone 8.1). Account and category are guessed from the text plus the
 * historical payee profiles built in Milestone 7 — the same classical
 * trigram matcher, reused with (payee → accountId) pairs.
 *
 * Posting always produces an *uncleared* transaction: captured money is
 * unreconciled until the user clears it, exactly like a reminder-posted bill.
 */
class NotificationCaptureRepository(
    private val db: EndgameDatabase,
    private val context: Context? = null,
) {

    /** A [NotificationParser.Parsed] with ids resolved and ready to post.
     *  [accountId] is null only when the user has no accounts at all.
     *  [accountMatched] is false when we fell back to a default account (the
     *  UI flags that so the user knows to double-check it). */
    data class Resolved(
        val payee: String,
        val amountCents: Long,
        val type: String,
        val accountId: String?,
        val accountName: String?,
        val accountMatched: Boolean,
        val categoryId: String?,
        val categoryName: String?,
    )

    suspend fun resolve(parsed: NotificationParser.Parsed): Resolved {
        val accounts = db.accountDao().getAllOnce().filter { it.isActive }
        val accById = accounts.associateBy { it.id }

        val matchedId = parsed.accountHint?.let { matchAccount(it, accounts) }
            ?: run {
                val profile = CategorySuggester.build(
                    db.transactionDao().payeeAccountHistory().map { it.payee to it.accountId },
                )
                profile.suggest(parsed.payee)?.categoryId?.takeIf { it in accById.keys }
            }

        // Fall back to a sensible default so the 1-tap Confirm is always usable:
        // the account most recently posted to, else the first asset account.
        val accountId = matchedId
            ?: lastUsedAccountId()?.takeIf { it in accById.keys }
            ?: accounts.firstOrNull { it.type == Account.TYPE_ASSET }?.id
            ?: accounts.firstOrNull()?.id

        val cats = db.categoryDao().getAllOnce()
        val categoryId = if (parsed.type == "transfer") null else resolveCategory(parsed, cats)

        return Resolved(
            payee = parsed.payee,
            amountCents = parsed.amountCents,
            type = parsed.type,
            accountId = accountId,
            accountName = accountId?.let { accById[it]?.name },
            accountMatched = matchedId != null,
            categoryId = categoryId,
            categoryName = categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name },
        )
    }

    /** The account last posted to from the entry form — a good default. */
    private fun lastUsedAccountId(): String? = context
        ?.getSharedPreferences("entry_prefs", Context.MODE_PRIVATE)
        ?.getString("last_account_id", null)

    private fun matchAccount(hint: String, accounts: List<Account>): String? {
        val h = hint.lowercase().trim()
        if (h.isBlank()) return null
        return accounts.firstOrNull { it.name.lowercase() == h }?.id
            ?: accounts.firstOrNull {
                val n = it.name.lowercase()
                n.length >= 3 && (h.contains(n) || n.contains(h))
            }?.id
    }

    private suspend fun resolveCategory(
        parsed: NotificationParser.Parsed,
        cats: List<Category>,
    ): String? {
        parsed.categoryGuess?.let { g ->
            val gl = g.lowercase().trim()
            cats.firstOrNull { it.name.lowercase() == gl }?.let { return it.id }
            cats.firstOrNull {
                val n = it.name.lowercase()
                n.length >= 3 && (n.contains(gl) || gl.contains(n))
            }?.let { return it.id }
        }
        val profile = CategorySuggester.build(
            db.transactionDao().payeeCategoryHistory().map { it.payee to it.categoryId },
        )
        return profile.suggest(parsed.payee)?.categoryId
    }

    /**
     * Posts [resolved] to the ledger. Returns false (posting nothing) when no
     * account could be resolved — the caller surfaces a confirm UI instead of
     * auto-posting into the void. A parsed "transfer" with no known destination
     * is recorded as an expense (money leaving the named account).
     */
    suspend fun post(resolved: Resolved): Boolean {
        val accountId = resolved.accountId ?: return false
        val txId = UUID.randomUUID().toString()
        db.transactionDao().insertWithSplits(
            TransactionEntity(
                id = txId,
                accountId = accountId,
                timestamp = System.currentTimeMillis(),
                payee = resolved.payee,
                notes = "Captured from notification",
                type = if (resolved.type == "income") "income" else "expense",
                isCleared = false,
            ),
            listOf(
                TransactionSplit(
                    id = UUID.randomUUID().toString(),
                    transactionId = txId,
                    categoryId = resolved.categoryId,
                    amount = resolved.amountCents,
                ),
            ),
        )
        return true
    }
}
