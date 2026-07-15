package com.endgamefinance.data.backup

import androidx.room.withTransaction
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.data.db.entity.Account
import com.endgamefinance.data.db.entity.Budget
import com.endgamefinance.data.db.entity.Category
import com.endgamefinance.data.db.entity.Envelope
import com.endgamefinance.data.db.entity.EnvelopeTransfer
import com.endgamefinance.data.db.entity.NetWorthSnapshot
import com.endgamefinance.data.db.entity.Reminder
import com.endgamefinance.data.db.entity.Tag
import com.endgamefinance.data.db.entity.TransactionAudit
import com.endgamefinance.data.db.entity.TransactionEntity
import com.endgamefinance.data.db.entity.TransactionSplit
import com.endgamefinance.data.db.entity.TransactionTag
import com.endgamefinance.util.Money
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val db: EndgameDatabase) {

    // ---------------------------------------------------------------- CSV

    /** Plain CSV — explicitly the UNPROTECTED export; one row per split. */
    suspend fun exportCsv(output: OutputStream) {
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zone = ZoneId.systemDefault()
        output.bufferedWriter().use { writer ->
            writer.appendLine(
                "date,type,payee,account,to_account,category,amount,amount_cents,notes,cleared,shared",
            )
            db.backupDao().csvRows().forEach { row ->
                writer.appendLine(
                    listOf(
                        Instant.ofEpochMilli(row.timestamp).atZone(zone).format(dateFormat),
                        row.type,
                        row.payee,
                        row.accountName,
                        row.toAccountName ?: "",
                        row.categoryName ?: "",
                        Money.formatPlain(row.amount),
                        row.amount.toString(),
                        row.notes ?: "",
                        if (row.isCleared) "yes" else "no",
                        if (row.isShared) "yes" else "no",
                    ).joinToString(",") { csvEscape(it) },
                )
            }
        }
    }

    private fun csvEscape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field

    // ------------------------------------------------------- Encrypted backup

    suspend fun writeEncryptedBackup(output: OutputStream, password: CharArray) {
        val json = buildBackupJson().toString().encodeToByteArray()
        BackupCrypto.encryptTo(output, json, password)
    }

    /**
     * Decrypts + fully parses BEFORE any DB write, then replaces everything in
     * one transaction — a wrong password or corrupt file can't touch live data.
     * @throws WrongPasswordException on bad password.
     */
    suspend fun restoreEncryptedBackup(input: InputStream, password: CharArray) {
        val json = JSONObject(BackupCrypto.decryptFrom(input, password).decodeToString())
        val payload = parseBackupJson(json) // full validation happens here
        db.withTransaction {
            val dao = db.backupDao()
            // children → parents
            dao.clearTransactionTags()
            dao.clearSplits()
            dao.clearAudit()
            dao.clearEnvelopeTransfers()
            dao.clearBudgets()
            dao.clearReminders()
            dao.clearTransactions()
            dao.clearEnvelopes()
            dao.clearTags()
            dao.clearCategories()
            dao.clearCategoryGroups()
            dao.clearAccounts()
            dao.clearSnapshots()
            // parents → children; groups before the categories that point at them
            dao.insertAccounts(payload.accounts)
            dao.insertCategoryGroups(payload.categoryGroups)
            dao.insertCategories(payload.categories.sortedBy { it.parentId != null })
            dao.insertTags(payload.tags)
            dao.insertTransactions(payload.transactions)
            dao.insertTransactionTags(payload.transactionTags)
            dao.insertSplits(payload.splits)
            dao.insertAudit(payload.audit)
            dao.insertEnvelopes(payload.envelopes)
            dao.insertEnvelopeTransfers(payload.envelopeTransfers)
            dao.insertReminders(payload.reminders)
            dao.insertBudgets(payload.budgets)
            dao.insertSnapshots(payload.snapshots)
        }
    }

    // ------------------------------------------------------------ JSON codec

    private data class Payload(
        val accounts: List<Account>,
        val categories: List<Category>,
        val categoryGroups: List<com.endgamefinance.data.db.entity.CategoryGroup>,
        val tags: List<Tag>,
        val transactionTags: List<TransactionTag>,
        val transactions: List<TransactionEntity>,
        val splits: List<TransactionSplit>,
        val audit: List<TransactionAudit>,
        val reminders: List<Reminder>,
        val budgets: List<Budget>,
        val envelopes: List<Envelope>,
        val envelopeTransfers: List<EnvelopeTransfer>,
        val snapshots: List<NetWorthSnapshot>,
    )

    private suspend fun buildBackupJson(): JSONObject {
        val dao = db.backupDao()
        return JSONObject().apply {
            put("format", "endgame-finance-backup")
            // v2 (2026-07-15): categoryGroups table + categories.groupId
            put("payloadVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put("accounts", JSONArray(dao.allAccounts().map { a ->
                JSONObject().apply {
                    put("id", a.id); put("name", a.name); put("type", a.type)
                    put("creditLimit", a.creditLimit); put("originalPrincipal", a.originalPrincipal)
                    put("currency", a.currency); put("isActive", a.isActive)
                }
            }))
            put("categories", JSONArray(dao.allCategories().map { c ->
                JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("parentId", c.parentId)
                    put("type", c.type); put("icon", c.icon); put("groupId", c.groupId)
                }
            }))
            put("categoryGroups", JSONArray(dao.allCategoryGroups().map { g ->
                JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("type", g.type)
                }
            }))
            put("tags", JSONArray(dao.allTags().map { t ->
                JSONObject().apply { put("id", t.id); put("name", t.name) }
            }))
            put("transactionTags", JSONArray(dao.allTransactionTags().map { t ->
                JSONObject().apply {
                    put("transactionId", t.transactionId); put("tagId", t.tagId)
                }
            }))
            put("transactions", JSONArray(dao.allTransactions().map { t ->
                JSONObject().apply {
                    put("id", t.id); put("accountId", t.accountId)
                    put("toAccountId", t.toAccountId); put("timestamp", t.timestamp)
                    put("payee", t.payee); put("notes", t.notes); put("type", t.type)
                    put("isCleared", t.isCleared); put("isShared", t.isShared)
                }
            }))
            put("splits", JSONArray(dao.allSplits().map { s ->
                JSONObject().apply {
                    put("id", s.id); put("transactionId", s.transactionId)
                    put("categoryId", s.categoryId); put("amount", s.amount)
                }
            }))
            put("audit", JSONArray(dao.allAudit().map { a ->
                JSONObject().apply {
                    put("id", a.id); put("transactionId", a.transactionId)
                    put("fieldName", a.fieldName); put("oldValue", a.oldValue)
                    put("newValue", a.newValue); put("changedAt", a.changedAt)
                }
            }))
            put("reminders", JSONArray(dao.allReminders().map { r ->
                JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("categoryId", r.categoryId)
                    put("accountId", r.accountId); put("toAccountId", r.toAccountId)
                    put("amount", r.amount); put("frequency", r.frequency)
                    put("frequencyInterval", r.frequencyInterval); put("anchorDay", r.anchorDay)
                    put("nextDueDate", r.nextDueDate); put("isAutoPost", r.isAutoPost)
                    put("isAutoDetected", r.isAutoDetected)
                }
            }))
            put("budgets", JSONArray(dao.allBudgets().map { b ->
                JSONObject().apply {
                    put("id", b.id); put("categoryId", b.categoryId); put("month", b.month)
                    put("allocatedAmount", b.allocatedAmount); put("rolloverMode", b.rolloverMode)
                }
            }))
            put("envelopes", JSONArray(dao.allEnvelopes().map { e ->
                JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("targetAmount", e.targetAmount)
                    put("currentAmount", e.currentAmount)
                    put("linkedAccountId", e.linkedAccountId)
                }
            }))
            put("envelopeTransfers", JSONArray(dao.allEnvelopeTransfers().map { t ->
                JSONObject().apply {
                    put("id", t.id); put("fromEnvelopeId", t.fromEnvelopeId)
                    put("toEnvelopeId", t.toEnvelopeId); put("amount", t.amount)
                    put("timestamp", t.timestamp)
                }
            }))
            put("snapshots", JSONArray(dao.allSnapshots().map { s ->
                JSONObject().apply {
                    put("id", s.id); put("snapshotDate", s.snapshotDate)
                    put("totalAssets", s.totalAssets)
                    put("totalLiabilities", s.totalLiabilities); put("netWorth", s.netWorth)
                }
            }))
        }
    }

    private fun parseBackupJson(json: JSONObject): Payload {
        require(json.optString("format") == "endgame-finance-backup") {
            "Not an Endgame Finance backup payload"
        }
        fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else getString(key)
        fun JSONObject.optLongOrNull(key: String): Long? =
            if (isNull(key)) null else getLong(key)
        fun JSONObject.optIntOrNull(key: String): Int? =
            if (isNull(key)) null else getInt(key)
        fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

        return upgradeLegacyCategories(Payload(
            accounts = json.getJSONArray("accounts").objects().map {
                Account(
                    id = it.getString("id"), name = it.getString("name"),
                    type = it.getString("type"),
                    creditLimit = it.optLongOrNull("creditLimit"),
                    originalPrincipal = it.optLongOrNull("originalPrincipal"),
                    currency = it.optString("currency", "USD"),
                    isActive = it.optBoolean("isActive", true),
                )
            },
            categories = json.getJSONArray("categories").objects().map {
                Category(
                    id = it.getString("id"), name = it.getString("name"),
                    parentId = it.optStringOrNull("parentId"),
                    type = it.getString("type"), icon = it.optStringOrNull("icon"),
                    groupId = if (it.has("groupId")) it.optStringOrNull("groupId") else null,
                )
            },
            categoryGroups = json.optJSONArray("categoryGroups")?.objects().orEmpty().map {
                com.endgamefinance.data.db.entity.CategoryGroup(
                    id = it.getString("id"), name = it.getString("name"),
                    type = it.getString("type"),
                )
            },
            tags = json.getJSONArray("tags").objects().map {
                Tag(id = it.getString("id"), name = it.getString("name"))
            },
            transactionTags = json.getJSONArray("transactionTags").objects().map {
                TransactionTag(
                    transactionId = it.getString("transactionId"),
                    tagId = it.getString("tagId"),
                )
            },
            transactions = json.getJSONArray("transactions").objects().map {
                TransactionEntity(
                    id = it.getString("id"), accountId = it.getString("accountId"),
                    toAccountId = it.optStringOrNull("toAccountId"),
                    timestamp = it.getLong("timestamp"), payee = it.getString("payee"),
                    notes = it.optStringOrNull("notes"), type = it.getString("type"),
                    isCleared = it.optBoolean("isCleared"),
                    isShared = it.optBoolean("isShared"),
                )
            },
            splits = json.getJSONArray("splits").objects().map {
                TransactionSplit(
                    id = it.getString("id"), transactionId = it.getString("transactionId"),
                    categoryId = it.optStringOrNull("categoryId"), amount = it.getLong("amount"),
                )
            },
            audit = json.getJSONArray("audit").objects().map {
                TransactionAudit(
                    id = it.getString("id"), transactionId = it.getString("transactionId"),
                    fieldName = it.getString("fieldName"),
                    oldValue = it.optStringOrNull("oldValue"),
                    newValue = it.optStringOrNull("newValue"),
                    changedAt = it.getLong("changedAt"),
                )
            },
            reminders = json.getJSONArray("reminders").objects().map {
                Reminder(
                    id = it.getString("id"), name = it.getString("name"),
                    categoryId = it.optStringOrNull("categoryId"),
                    accountId = it.getString("accountId"),
                    toAccountId = it.optStringOrNull("toAccountId"),
                    amount = it.optLongOrNull("amount"),
                    frequency = it.getString("frequency"),
                    frequencyInterval = it.optIntOrNull("frequencyInterval") ?: 1,
                    anchorDay = it.optIntOrNull("anchorDay"),
                    nextDueDate = it.getLong("nextDueDate"),
                    isAutoPost = it.optBoolean("isAutoPost"),
                    isAutoDetected = it.optBoolean("isAutoDetected"),
                )
            },
            budgets = json.getJSONArray("budgets").objects().map {
                Budget(
                    id = it.getString("id"), categoryId = it.getString("categoryId"),
                    month = it.getString("month"),
                    allocatedAmount = it.getLong("allocatedAmount"),
                    rolloverMode = it.optString("rolloverMode", "reset"),
                )
            },
            envelopes = json.getJSONArray("envelopes").objects().map {
                Envelope(
                    id = it.getString("id"), name = it.getString("name"),
                    targetAmount = it.optLongOrNull("targetAmount"),
                    currentAmount = it.getLong("currentAmount"),
                    linkedAccountId = it.optStringOrNull("linkedAccountId"),
                )
            },
            envelopeTransfers = json.getJSONArray("envelopeTransfers").objects().map {
                EnvelopeTransfer(
                    id = it.getString("id"),
                    fromEnvelopeId = it.optStringOrNull("fromEnvelopeId"),
                    toEnvelopeId = it.optStringOrNull("toEnvelopeId"),
                    amount = it.getLong("amount"), timestamp = it.getLong("timestamp"),
                )
            },
            snapshots = json.getJSONArray("snapshots").objects().map {
                NetWorthSnapshot(
                    id = it.getString("id"), snapshotDate = it.getLong("snapshotDate"),
                    totalAssets = it.getLong("totalAssets"),
                    totalLiabilities = it.getLong("totalLiabilities"),
                    netWorth = it.getLong("netWorth"),
                )
            },
        ))
    }

    /**
     * Pre-v2 payloads carry parent/child categories and no groups. Applies the
     * same rules as the DB v6 migration: parents-with-children become groups
     * (reusing their id); a parent referenced by splits/budgets/reminders
     * survives as a same-named category inside its group; purely structural
     * parents are dropped; everything left without a group folds into the
     * sentinel "Other" group of its type.
     */
    private fun upgradeLegacyCategories(p: Payload): Payload {
        if (p.categoryGroups.isNotEmpty() || p.categories.isEmpty()) return p
        val referenced = buildSet {
            p.splits.forEach { it.categoryId?.let(::add) }
            p.budgets.forEach { add(it.categoryId) }
            p.reminders.forEach { it.categoryId?.let(::add) }
        }
        val byId = p.categories.associateBy { it.id }
        val parentIds = p.categories.mapNotNull { it.parentId }
            .filter { it in byId }.toSet()
        val groups = mutableListOf<com.endgamefinance.data.db.entity.CategoryGroup>()
        parentIds.forEach { pid ->
            byId[pid]?.let { parent ->
                groups += com.endgamefinance.data.db.entity.CategoryGroup(
                    id = parent.id, name = parent.name, type = parent.type,
                )
            }
        }
        var needOtherExpense = false
        var needOtherIncome = false
        val categories = p.categories.mapNotNull { c ->
            val isParent = c.id in parentIds
            when {
                isParent && c.id in referenced -> c.copy(parentId = null, groupId = c.id)
                isParent -> null // purely structural — the group replaces it
                c.parentId != null && c.parentId in parentIds ->
                    c.copy(parentId = null, groupId = c.parentId)
                else -> {
                    val sentinel = if (c.type == Category.TYPE_INCOME) {
                        needOtherIncome = true
                        com.endgamefinance.data.db.entity.CategoryGroup.OTHER_INCOME_ID
                    } else {
                        needOtherExpense = true
                        com.endgamefinance.data.db.entity.CategoryGroup.OTHER_EXPENSE_ID
                    }
                    c.copy(parentId = null, groupId = sentinel)
                }
            }
        }
        if (needOtherExpense) {
            groups += com.endgamefinance.data.db.entity.CategoryGroup(
                com.endgamefinance.data.db.entity.CategoryGroup.OTHER_EXPENSE_ID,
                "Other", Category.TYPE_EXPENSE,
            )
        }
        if (needOtherIncome) {
            groups += com.endgamefinance.data.db.entity.CategoryGroup(
                com.endgamefinance.data.db.entity.CategoryGroup.OTHER_INCOME_ID,
                "Other", Category.TYPE_INCOME,
            )
        }
        return p.copy(categories = categories, categoryGroups = groups)
    }
}
