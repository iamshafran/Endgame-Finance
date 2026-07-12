package com.endgamefinance.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.endgamefinance.R
import com.endgamefinance.ui.screens.DashboardScreen
import com.endgamefinance.ui.screens.MoreScreen
import com.endgamefinance.ui.screens.PlaceholderScreen

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
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    // Accounts lives under More, so keep the More tab highlighted there
                    val selected = currentRoute == tab.route ||
                        (tab.route == Routes.MORE && currentRoute == Routes.ACCOUNTS)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { TabIcon(tab.route) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen() }
            composable(Routes.LEDGER) {
                PlaceholderScreen("Ledger", "Transactions arrive in Milestone 1.")
            }
            composable(Routes.BUDGET) {
                PlaceholderScreen("Budget", "Envelope budgeting arrives in Milestone 2.")
            }
            composable(Routes.REMINDERS) {
                PlaceholderScreen("Reminders", "Bills and forecasting arrive in Milestone 3.")
            }
            composable(Routes.MORE) {
                MoreScreen(onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) })
            }
            composable(Routes.ACCOUNTS) {
                PlaceholderScreen("Accounts", "Account management arrives in Milestone 1.")
            }
        }
    }
}
