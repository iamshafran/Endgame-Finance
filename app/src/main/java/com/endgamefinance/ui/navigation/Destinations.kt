package com.endgamefinance.ui.navigation

/** All navigation routes. Six destinations per the spec; five appear in the bottom bar. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val LEDGER = "ledger"
    const val BUDGET = "budget"
    const val REMINDERS = "reminders"
    const val MORE = "more"
    const val ACCOUNTS = "accounts" // reached from inside More
    const val ACCOUNT_EDIT = "account_edit" // ?accountId={accountId} for editing
    const val CATEGORIES = "categories" // reached from inside More
    const val TAGS = "tags" // reached from inside More
    const val TRANSACTION_ADD = "transaction_add"
    const val REMINDER_EDIT = "reminder_edit" // ?reminderId={reminderId} for editing
    const val REPORTS = "reports" // reached from inside More
    const val SEARCH = "search" // ledger with filters open, launched from Dashboard
    const val SECURITY = "security" // reached from inside More
}

data class BottomTab(
    val route: String,
    val label: String,
)

val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Dashboard"),
    BottomTab(Routes.LEDGER, "Ledger"),
    BottomTab(Routes.BUDGET, "Budget"),
    BottomTab(Routes.REMINDERS, "Reminders"),
    BottomTab(Routes.MORE, "More"),
)
