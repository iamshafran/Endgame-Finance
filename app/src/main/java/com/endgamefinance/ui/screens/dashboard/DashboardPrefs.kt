package com.endgamefinance.ui.screens.dashboard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dashboard layout preferences: widget order + visibility. UI preference only
 * (never financial data), so plain SharedPreferences.
 */
class DashboardPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE)

    private val _order = MutableStateFlow(loadOrder())
    val order: StateFlow<List<String>> = _order.asStateFlow()

    private val _hidden = MutableStateFlow(loadHidden())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private fun loadOrder(): List<String> {
        val stored = prefs.getString(KEY_ORDER, null)
            ?.split(',')?.filter { it in ALL }.orEmpty()
        // Widgets added in later versions append to the stored order
        return stored + ALL.filter { it !in stored }
    }

    private fun loadHidden(): Set<String> =
        prefs.getString(KEY_HIDDEN, null)
            ?.split(',')?.filter { it in ALL }?.toSet().orEmpty()

    fun setOrder(order: List<String>) {
        prefs.edit().putString(KEY_ORDER, order.joinToString(",")).apply()
        _order.value = order + ALL.filter { it !in order }
    }

    fun setVisible(key: String, visible: Boolean) {
        val updated = if (visible) _hidden.value - key else _hidden.value + key
        prefs.edit().putString(KEY_HIDDEN, updated.joinToString(",")).apply()
        _hidden.value = updated
    }

    fun move(key: String, up: Boolean) {
        val current = _order.value.toMutableList()
        val index = current.indexOf(key)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target > current.lastIndex) return
        current[index] = current[target].also { current[target] = key }
        setOrder(current)
    }

    companion object {
        const val SAFE_TO_SPEND = "safe_to_spend"
        const val NET_WORTH = "net_worth"
        const val CALENDAR = "calendar"
        const val CASH_FLOW = "cash_flow"
        const val BUDGET = "budget"
        const val TOP_SPENDING = "top_spending"

        val ALL = listOf(SAFE_TO_SPEND, NET_WORTH, CALENDAR, CASH_FLOW, BUDGET, TOP_SPENDING)

        fun label(key: String): String = when (key) {
            SAFE_TO_SPEND -> "Safe to spend"
            NET_WORTH -> "Net worth"
            CALENDAR -> "Calendar"
            CASH_FLOW -> "Cash flow"
            BUDGET -> "Budget summary"
            TOP_SPENDING -> "Top spending"
            else -> key
        }

        private const val KEY_ORDER = "widget_order"
        private const val KEY_HIDDEN = "widget_hidden"
    }
}
