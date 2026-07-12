package com.endgamefinance.ui.navigation

/** All navigation routes. Six destinations per the spec; five appear in the bottom bar. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val LEDGER = "ledger"
    const val BUDGET = "budget"
    const val REMINDERS = "reminders"
    const val MORE = "more"
    const val ACCOUNTS = "accounts" // reached from inside More
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
