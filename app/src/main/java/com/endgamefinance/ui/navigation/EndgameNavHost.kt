package com.endgamefinance.ui.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.endgamefinance.R
import com.endgamefinance.ui.screens.MoreScreen
import com.endgamefinance.ui.screens.PlaceholderScreen
import com.endgamefinance.ui.screens.accounts.AccountEditScreen
import com.endgamefinance.ui.screens.accounts.AccountsScreen
import com.endgamefinance.ui.screens.budget.BudgetScreen
import com.endgamefinance.ui.screens.categories.CategoriesScreen
import com.endgamefinance.ui.screens.dashboard.DashboardScreen
import com.endgamefinance.ui.screens.entry.TransactionEntryScreen
import com.endgamefinance.ui.screens.ledger.LedgerScreen
import com.endgamefinance.ui.screens.reconcile.ReconcileScreen
import com.endgamefinance.ui.screens.reminders.ReminderEditScreen
import com.endgamefinance.ui.screens.reports.ReportsScreen
import com.endgamefinance.ui.screens.settings.SettingsScreen
import com.endgamefinance.ui.screens.reminders.RemindersScreen
import com.endgamefinance.ui.screens.tags.TagsScreen

@Composable
private fun TabIcon(route: String) {
    when (route) {
        Routes.DASHBOARD -> Icon(Icons.Filled.Home, contentDescription = null)
        Routes.LEDGER -> Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        Routes.BUDGET -> Icon(painterResource(R.drawable.ic_budget), contentDescription = null)
        Routes.REMINDERS -> Icon(Icons.Filled.Notifications, contentDescription = null)
        Routes.MORE -> Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
}

@Composable
fun EndgameApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Routes with query args report their full pattern; compare on the base
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Top inset is owned by each screen's own TopAppBar (EndgameScaffold);
        // zero it here so there's no double status-bar padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    // Sub-screens of More keep the More tab highlighted
                    val moreSubRoutes = currentRoute == Routes.ACCOUNTS ||
                        currentRoute == Routes.CATEGORIES ||
                        currentRoute == Routes.TAGS ||
                        currentRoute == Routes.REPORTS ||
                        currentRoute == Routes.SETTINGS ||
                        currentRoute?.startsWith(Routes.ACCOUNT_EDIT) == true ||
                        currentRoute?.startsWith(Routes.RECONCILE) == true
                    val selected = currentRoute == tab.route ||
                        (tab.route == Routes.MORE && moreSubRoutes) ||
                        (tab.route == Routes.LEDGER && currentRoute == Routes.SEARCH) ||
                        (tab.route == Routes.LEDGER &&
                            currentRoute?.startsWith(Routes.TRANSACTION_ADD) == true) ||
                        (tab.route == Routes.REMINDERS &&
                            currentRoute?.startsWith(Routes.REMINDER_EDIT) == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateToTab(tab.route) },
                        icon = { TabIcon(tab.route) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        // Swipe left/right anywhere a child doesn't own the horizontal gesture
        // (lists scroll vertically, so this mostly just works) to move between
        // the bottom tabs. The Ledger tab is excluded entirely: its rows are
        // horizontally swipeable (clear/delete), and the two gestures racing
        // made row swipes unreliable.
        val onTab = bottomTabs.any { it.route == currentRoute } &&
            currentRoute != Routes.LEDGER
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .padding(innerPadding)
                .pointerInput(currentRoute, onTab) {
                    if (!onTab) return@pointerInput
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, amount -> dragTotal += amount },
                        onDragEnd = {
                            val threshold = 96.dp.toPx()
                            val current = bottomTabs.indexOfFirst { it.route == currentRoute }
                            if (current < 0) return@detectHorizontalDragGestures
                            val target = when {
                                dragTotal < -threshold -> current + 1
                                dragTotal > threshold -> current - 1
                                else -> current
                            }.coerceIn(0, bottomTabs.lastIndex)
                            if (target != current) navigateToTab(bottomTabs[target].route)
                        },
                    )
                },
            enterTransition = {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(220),
                )
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(160),
                )
            },
            popEnterTransition = {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(220),
                )
            },
            popExitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(160),
                )
            },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onAddTransaction = { navController.navigate(Routes.TRANSACTION_ADD) },
                    onOpenCalendar = {
                        navController.navigate("${Routes.REMINDERS}?section=calendar") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SEARCH) {
                LedgerScreen(
                    onAddTransaction = { navController.navigate(Routes.TRANSACTION_ADD) },
                    onOpenTransaction = { id ->
                        navController.navigate("${Routes.TRANSACTION_ADD}?transactionId=$id")
                    },
                    showFiltersInitially = true,
                    title = "Search",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LEDGER) {
                LedgerScreen(
                    onAddTransaction = { navController.navigate(Routes.TRANSACTION_ADD) },
                    onOpenTransaction = { id ->
                        navController.navigate("${Routes.TRANSACTION_ADD}?transactionId=$id")
                    },
                )
            }
            composable(
                route = "${Routes.TRANSACTION_ADD}?transactionId={transactionId}",
                arguments = listOf(
                    navArgument("transactionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                TransactionEntryScreen(
                    transactionId = entry.arguments?.getString("transactionId"),
                    onDone = { navController.popBackStack() },
                    onScanReceipt = {
                        navController.navigate("${Routes.RECEIPT_SCAN}?forResult=true")
                    },
                    resultHandle = entry.savedStateHandle,
                )
            }
            composable(Routes.BUDGET) {
                BudgetScreen()
            }
            composable(
                route = "${Routes.REMINDERS}?section={section}",
                arguments = listOf(
                    navArgument("section") {
                        type = NavType.StringType
                        defaultValue = "bills"
                    },
                ),
            ) { entry ->
                RemindersScreen(
                    onAddReminder = { navController.navigate(Routes.REMINDER_EDIT) },
                    onEditReminder = { id ->
                        navController.navigate("${Routes.REMINDER_EDIT}?reminderId=$id")
                    },
                    initialSection = entry.arguments?.getString("section") ?: "bills",
                )
            }
            composable(
                route = "${Routes.REMINDER_EDIT}?reminderId={reminderId}",
                arguments = listOf(
                    navArgument("reminderId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ReminderEditScreen(
                    reminderId = entry.arguments?.getString("reminderId"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenTags = { navController.navigate(Routes.TAGS) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAssistant = { navController.navigate(Routes.ASSISTANT) },
                    onOpenReceiptScan = { navController.navigate(Routes.RECEIPT_SCAN) },
                )
            }
            composable(
                route = "${Routes.RECEIPT_SCAN}?forResult={forResult}",
                arguments = listOf(
                    navArgument("forResult") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val forResult = entry.arguments?.getBoolean("forResult") == true
                com.endgamefinance.ui.screens.receipt.ReceiptScanScreen(
                    onBack = { navController.popBackStack() },
                    // Launched from the entry form: hand the parsed receipt back
                    onUseInEntry = if (forResult) {
                        { json ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle?.set("receipt_result", json)
                            navController.popBackStack()
                        }
                    } else null,
                )
            }
            composable(Routes.ASSISTANT) {
                com.endgamefinance.ui.screens.assistant.AssistantScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                    onOpenCapture = { navController.navigate(Routes.CAPTURE) },
                )
            }
            composable(Routes.CAPTURE) {
                com.endgamefinance.ui.screens.notify.NotificationCaptureScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.IMPORT) {
                com.endgamefinance.ui.screens.importer.ImportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TAGS) {
                TagsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onAddAccount = { navController.navigate(Routes.ACCOUNT_EDIT) },
                    onEditAccount = { id ->
                        navController.navigate("${Routes.ACCOUNT_EDIT}?accountId=$id")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.ACCOUNT_EDIT}?accountId={accountId}",
                arguments = listOf(
                    navArgument("accountId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                AccountEditScreen(
                    accountId = entry.arguments?.getString("accountId"),
                    onDone = { navController.popBackStack() },
                    onReconcile = { id ->
                        navController.navigate("${Routes.RECONCILE}/$id")
                    },
                )
            }
            composable(
                route = "${Routes.RECONCILE}/{accountId}",
                arguments = listOf(
                    navArgument("accountId") { type = NavType.StringType },
                ),
            ) { entry ->
                ReconcileScreen(
                    accountId = requireNotNull(entry.arguments?.getString("accountId")),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
