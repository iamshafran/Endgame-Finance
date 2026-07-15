package com.endgamefinance.data.migrate

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Tag
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.entity.TransactionTag
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

/**
 * Deterministic Bluecoins-style CSV import. The AI's only job was the column
 * mapping (ColumnMapping.infer) — everything below is plain, testable logic:
 *
 * - "Starting Balance" rows become accounts with an opening-balance
 *   transaction at the CSV timestamp (LedgerRepository convention).
 * - Transfer rows come in +/- pairs (same payee+timestamp+magnitude, two
 *   accounts) and collapse into ONE transfer transaction, source → destination.
 * - Category Group/Category become a two-level category hierarchy typed by the
 *   rows that use them (income rows → income category).
 * - Accounts with a negative opening balance are liabilities.
 * - Re-running the import skips rows that already exist (same account,
 *   timestamp, payee, amount).
 */
object BluecoinsImport {

    /** One source row, normalized. */
    data class Row(
        val kind: Kind,
        val timestamp: Long,
        val payee: String,
        val amountCents: Long, // signed as in the file
        val categoryGroup: String?,
        val category: String?,
        val account: String,
        val notes: String?,
        val labels: List<String>,
        val cleared: Boolean,
    )

    enum class Kind { EXPENSE, INCOME, TRANSFER, STARTING_BALANCE }

    data class Summary(
        val accountsCreated: Int,
        val categoriesCreated: Int,
        val transactionsImported: Int,
        val transfersPaired: Int,
        val duplicatesSkipped: Int,
        val transfersRepaired: Int,
        val warnings: List<String>,
    )

    // ---------- parsing helpers (also used by ColumnMapping.validate) ----------

    private val TS_REGEX = Regex(
        "(\\d{4})-(\\d{2})-(\\d{2})(?:[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.(\\d{1,3}))?)?",
    )

    /** "2026-07-09 21:59:00.0" (fraction/time optional) → epoch ms, local time. */
    fun parseTimestamp(text: String): Long? {
        val m = TS_REGEX.find(text.trim()) ?: return null
        val g = m.groupValues
        return try {
            Calendar.getInstance().apply {
                clear()
                set(
                    g[1].toInt(), g[2].toInt() - 1, g[3].toInt(),
                    g[4].ifEmpty { "0" }.toInt(),
                    g[5].ifEmpty { "0" }.toInt(),
                    g[6].ifEmpty { "0" }.toInt(),
                )
                set(Calendar.MILLISECOND, g[7].ifEmpty { "0" }.padEnd(3, '0').toInt())
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    /** "-1,234.56" / "£12.30" → signed cents, or null. */
    fun parseAmountCents(text: String): Long? {
        val cleaned = text.trim().filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-") return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return try {
            value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun rowKind(typeText: String?, amountCents: Long): Kind {
        val t = typeText?.trim()?.lowercase() ?: ""
        return when {
            "starting" in t || "new account" in t -> Kind.STARTING_BALANCE
            "transfer" in t -> Kind.TRANSFER
            "income" in t -> Kind.INCOME
            "expense" in t -> Kind.EXPENSE
            // No/unknown type column: fall back to the sign.
            amountCents >= 0 -> Kind.INCOME
            else -> Kind.EXPENSE
        }
    }

    /** Applies [mapping] to raw CSV [rows] (header excluded). Bad rows → warnings. */
    fun normalize(
        mapping: ColumnMapping,
        rows: List<List<String>>,
        warnings: MutableList<String>,
    ): List<Row> {
        fun cell(row: List<String>, i: Int?): String? =
            if (i != null && i < row.size) row[i].trim().ifBlank { null } else null

        return rows.mapIndexedNotNull { index, raw ->
            val line = index + 2 // 1-based + header
            val ts = cell(raw, mapping.date)?.let { parseTimestamp(it) }
            val cents = cell(raw, mapping.amount)?.let { parseAmountCents(it) }
            if (ts == null || cents == null) {
                warnings += "Line $line skipped: unreadable date or amount."
                return@mapIndexedNotNull null
            }
            val account = cell(raw, mapping.account)
            if (account == null) {
                warnings += "Line $line skipped: no account."
                return@mapIndexedNotNull null
            }
            val group = cell(raw, mapping.categoryGroup)?.takeIf { !it.startsWith("(") }
            val cat = cell(raw, mapping.category)?.takeIf { !it.startsWith("(") }
            Row(
                kind = rowKind(cell(raw, mapping.type), cents),
                timestamp = ts,
                payee = cell(raw, mapping.payee) ?: "Imported",
                amountCents = cents,
                categoryGroup = group,
                category = cat,
                account = account,
                notes = cell(raw, mapping.notes),
                labels = cell(raw, mapping.labels)
                    ?.split(',', ';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                cleared = cell(raw, mapping.status)
                    ?.lowercase()
                    ?.let { "reconcil" in it || "cleared" in it } ?: false,
            )
        }
    }

    /** A transfer leg pair: source (negative) + destination (positive). */
    data class TransferPair(val source: Row, val destination: Row)

    data class PairedRows(
        val transfers: List<TransferPair>,
        val unpaired: List<Row>, // transfer rows we couldn't match
        val plain: List<Row>, // expense/income
        val startingBalances: List<Row>,
    )

    /** Collapses +/- transfer legs into pairs by (payee, timestamp, magnitude). */
    fun pairTransfers(rows: List<Row>): PairedRows {
        val transfers = rows.filter { it.kind == Kind.TRANSFER }
        val plain = rows.filter { it.kind == Kind.EXPENSE || it.kind == Kind.INCOME }
        val starting = rows.filter { it.kind == Kind.STARTING_BALANCE }

        val pairs = mutableListOf<TransferPair>()
        val unmatched = mutableListOf<Row>()
        val byKey = transfers.groupBy { Triple(it.payee, it.timestamp, abs(it.amountCents)) }
        for ((_, legs) in byKey) {
            val negatives = legs.filter { it.amountCents < 0 }.toMutableList()
            val positives = legs.filter { it.amountCents >= 0 }.toMutableList()
            while (negatives.isNotEmpty() && positives.isNotEmpty()) {
                val neg = negatives.removeAt(0)
                // Prefer a destination on a different account.
                val posIdx = positives.indexOfFirst { it.account != neg.account }
                val pos = if (posIdx >= 0) positives.removeAt(posIdx) else positives.removeAt(0)
                pairs += TransferPair(neg, pos)
            }
            unmatched += negatives
            unmatched += positives
        }
        return PairedRows(pairs, unmatched, plain, starting)
    }

    // ---------- repair: transfers that were once imported as expense+income ----------

    /** One fix: turn [expenseId] into a transfer to [toAccountId], drop [incomeId]. */
    data class Repair(val expenseId: String, val incomeId: String, val toAccountId: String)

    /**
     * Pure matcher: uncategorized expense+income rows with identical
     * (payee, timestamp, amount) on different accounts are two halves of one
     * transfer (the signature of an import that missed the type column).
     */
    fun findMistypedTransfers(legs: List<com.endgamefinance.data.db.model.RepairLeg>): List<Repair> {
        val repairs = mutableListOf<Repair>()
        for ((_, group) in legs.groupBy { Triple(it.payee, it.timestamp, it.amountCents) }) {
            val expenses = group.filter { it.type == "expense" }.toMutableList()
            val incomes = group.filter { it.type == "income" }.toMutableList()
            while (expenses.isNotEmpty() && incomes.isNotEmpty()) {
                val e = expenses.removeAt(0)
                val iIdx = incomes.indexOfFirst { it.accountId != e.accountId }
                if (iIdx < 0) break
                val i = incomes.removeAt(iIdx)
                repairs += Repair(e.id, i.id, i.accountId)
            }
        }
        return repairs
    }

    /** Applies [findMistypedTransfers] to the live DB. Returns fixes applied. */
    suspend fun repairMistypedTransfers(db: EndgameDatabase): Int {
        var repaired = 0
        db.withTransaction {
            val dao = db.transactionDao()
            for (r in findMistypedTransfers(dao.uncategorizedPlainLegs())) {
                val expense = dao.getById(r.expenseId) ?: continue
                dao.updateTransaction(expense.copy(type = "transfer", toAccountId = r.toAccountId))
                dao.deleteById(r.incomeId) // cascade removes its split
                repaired++
            }
        }
        return repaired
    }

    // ---------- database import ----------

    suspend fun import(db: EndgameDatabase, mapping: ColumnMapping, csvRows: List<List<String>>): Summary {
        val warnings = mutableListOf<String>()
        // Heal any transfers a previous (buggy or type-less) run split into
        // expense+income pairs BEFORE importing, so dedupe sees clean data.
        val repaired = repairMistypedTransfers(db)
        val rows = normalize(mapping, csvRows, warnings)
        val paired = pairTransfers(rows)

        var accountsCreated = 0
        var categoriesCreated = 0
        var imported = 0
        var duplicates = 0

        db.withTransaction {
            val accountDao = db.accountDao()
            val categoryDao = db.categoryDao()
            val txDao = db.transactionDao()

            // --- accounts: existing by name, else created ---
            val accounts = accountDao.getAllOnce().associateBy { it.name.lowercase() }.toMutableMap()
            val openingByAccount = paired.startingBalances.associateBy { it.account.lowercase() }

            suspend fun accountId(name: String): String {
                accounts[name.lowercase()]?.let { return it.id }
                val opening = openingByAccount[name.lowercase()]
                val isLiability = (opening?.amountCents ?: 0) < 0
                val account = Account(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = if (isLiability) Account.TYPE_LIABILITY else Account.TYPE_ASSET,
                )
                accountDao.insert(account)
                accounts[name.lowercase()] = account
                accountsCreated++
                // Opening balance at the CSV's own timestamp (repo convention:
                // a visible 'Starting Balance' transaction, cleared).
                val cents = opening?.amountCents ?: 0L
                if (cents != 0L) {
                    val txId = UUID.randomUUID().toString()
                    txDao.insertWithSplits(
                        TransactionEntity(
                            id = txId,
                            accountId = account.id,
                            timestamp = opening!!.timestamp,
                            payee = "Starting Balance",
                            type = if (cents > 0) "income" else "expense",
                            isCleared = true,
                        ),
                        listOf(
                            TransactionSplit(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                categoryId = null,
                                amount = abs(cents),
                            ),
                        ),
                    )
                }
                return account.id
            }

            // --- categories: Bluecoins groups map to category groups (DB v6) ---
            val groupDao = db.categoryGroupDao()
            val existing = categoryDao.getAllOnce()
            val existingGroups = groupDao.getAllOnce()
            val groupNamesById = existingGroups.associateBy({ it.id }, { it.name })
            val categoryIds = existing.associateBy(
                { (it.groupId?.let { g -> groupNamesById[g] } ?: "") + "/" + it.name },
                { it.id },
            ).toMutableMap()
            val groupIds = existingGroups
                .associateBy({ it.type + "/" + it.name.lowercase() }, { it.id }).toMutableMap()

            suspend fun groupIdFor(groupName: String?, type: String): String {
                // Rows without a Bluecoins group land in the sentinel "Other"
                if (groupName == null) {
                    val sentinel = if (type == Category.TYPE_INCOME) {
                        com.endgamefinance.data.db.entity.CategoryGroup.OTHER_INCOME_ID
                    } else {
                        com.endgamefinance.data.db.entity.CategoryGroup.OTHER_EXPENSE_ID
                    }
                    return groupIds.getOrPut(type + "/other") {
                        com.endgamefinance.data.db.entity.CategoryGroup(sentinel, "Other", type)
                            .also { groupDao.insert(it) }
                            .id
                    }
                }
                return groupIds.getOrPut(type + "/" + groupName.lowercase()) {
                    val g = com.endgamefinance.data.db.entity.CategoryGroup(
                        id = UUID.randomUUID().toString(),
                        name = groupName,
                        type = type,
                    )
                    groupDao.insert(g)
                    g.id
                }
            }

            suspend fun categoryId(row: Row): String? {
                val name = row.category ?: return null
                val type = if (row.kind == Kind.INCOME) Category.TYPE_INCOME else Category.TYPE_EXPENSE
                val key = (row.categoryGroup ?: "") + "/" + name
                categoryIds[key]?.let { return it }
                val c = Category(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    groupId = groupIdFor(row.categoryGroup, type),
                )
                categoryDao.insert(c)
                categoriesCreated++
                categoryIds[key] = c.id
                return c.id
            }

            // --- tags ---
            val tagIds = mutableMapOf<String, String>()
            suspend fun tagId(name: String): String = tagIds.getOrPut(name.lowercase()) {
                val t = Tag(id = UUID.randomUUID().toString(), name = name)
                db.tagDao().insert(t)
                t.id
            }

            // Ensure every referenced account exists (incl. those without a
            // starting-balance row), oldest rows first for coherent history.
            val everyAccount = (paired.plain + paired.unpaired).map { it.account } +
                paired.transfers.flatMap { listOf(it.source.account, it.destination.account) } +
                paired.startingBalances.map { it.account }
            everyAccount.distinctBy { it.lowercase() }.forEach { accountId(it) }

            suspend fun insertPlain(row: Row, forcedType: String? = null) {
                val accId = accountId(row.account)
                val magnitude = abs(row.amountCents)
                if (txDao.countSimilar(accId, row.timestamp, row.payee, magnitude) > 0) {
                    duplicates++
                    return
                }
                val txId = UUID.randomUUID().toString()
                val type = forcedType ?: if (row.kind == Kind.INCOME) "income" else "expense"
                txDao.insertWithSplits(
                    TransactionEntity(
                        id = txId,
                        accountId = accId,
                        timestamp = row.timestamp,
                        payee = row.payee,
                        notes = row.notes,
                        type = type,
                        isCleared = row.cleared,
                    ),
                    listOf(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = categoryId(row),
                            amount = magnitude,
                        ),
                    ),
                )
                if (row.labels.isNotEmpty()) {
                    txDao.insertTransactionTags(
                        row.labels.map { TransactionTag(transactionId = txId, tagId = tagId(it)) },
                    )
                }
                imported++
            }

            paired.plain.sortedBy { it.timestamp }.forEach { insertPlain(it) }

            for (pair in paired.transfers.sortedBy { it.source.timestamp }) {
                val srcId = accountId(pair.source.account)
                val dstId = accountId(pair.destination.account)
                if (srcId == dstId) {
                    warnings += "Transfer '${pair.source.payee}' has the same source and " +
                        "destination account; skipped."
                    continue
                }
                val magnitude = abs(pair.source.amountCents)
                if (txDao.countSimilar(srcId, pair.source.timestamp, pair.source.payee, magnitude) > 0) {
                    duplicates++
                    continue
                }
                val txId = UUID.randomUUID().toString()
                txDao.insertWithSplits(
                    TransactionEntity(
                        id = txId,
                        accountId = srcId,
                        toAccountId = dstId,
                        timestamp = pair.source.timestamp,
                        payee = pair.source.payee,
                        notes = pair.source.notes,
                        type = "transfer",
                        isCleared = pair.source.cleared && pair.destination.cleared,
                    ),
                    listOf(
                        TransactionSplit(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            categoryId = null, // transfer receive rule: NULL category
                            amount = magnitude,
                        ),
                    ),
                )
                imported++
            }

            // Unpaired transfer legs: keep the money, flag the ambiguity.
            for (row in paired.unpaired.sortedBy { it.timestamp }) {
                warnings += "Transfer '${row.payee}' (${row.account}) had no matching " +
                    "opposite leg; imported as ${if (row.amountCents < 0) "expense" else "income"}."
                insertPlain(row, forcedType = if (row.amountCents < 0) "expense" else "income")
            }
        }

        return Summary(
            accountsCreated = accountsCreated,
            categoriesCreated = categoriesCreated,
            transactionsImported = imported,
            transfersPaired = paired.transfers.size,
            duplicatesSkipped = duplicates,
            transfersRepaired = repaired,
            warnings = warnings,
        )
    }
}
