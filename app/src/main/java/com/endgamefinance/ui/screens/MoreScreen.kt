package com.endgamefinance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.endgamefinance.ui.theme.Spacing

/** More tab: houses the Accounts destination plus future settings entries (Milestone 5+). */
@Composable
fun MoreScreen(
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "More",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(Spacing.md),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Accounts") },
            supportingContent = { Text("Manage asset, liability and investment accounts") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable(onClick = onOpenAccounts),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Organize spending with nested categories") },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable(onClick = onOpenCategories),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Reports") },
            supportingContent = { Text("Date-range and year-over-year spending reports") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable(onClick = onOpenReports),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Tags") },
            supportingContent = { Text("Cross-cutting labels like #vacation or #reimbursable") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier.clickable(onClick = onOpenTags),
        )
        HorizontalDivider()
    }
}
