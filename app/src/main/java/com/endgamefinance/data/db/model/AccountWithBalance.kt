package com.endgamefinance.data.db.model

import androidx.room.Embedded
import com.endgamefinance.data.db.entity.Account

/**
 * Account plus its ledger-derived balance in cents.
 *
 * Sign convention (load-bearing, do not change): asset/investment balances are
 * positive when you own money; liability balances are NEGATIVE while debt is
 * owed. Net worth is therefore simply SUM(balance) across active accounts.
 */
data class AccountWithBalance(
    @Embedded val account: Account,
    val balance: Long,
)
