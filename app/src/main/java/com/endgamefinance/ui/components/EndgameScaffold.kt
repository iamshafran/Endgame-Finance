package com.endgamefinance.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard M3 screen chrome: a pinned TopAppBar (title stays put, recolors as
 * content scrolls under it), an optional back arrow, action slot, and FAB.
 *
 * The outer app Scaffold (EndgameApp) owns the bottom navigation and zeroes
 * its content window insets, so this inner Scaffold's TopAppBar handles the
 * status-bar inset itself with no double padding. [content] receives the
 * inner padding — apply it to the scroll container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndgameScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
    // Tactical voice: screen titles read as uppercase panel headings
    val titleText: @Composable () -> Unit = {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (centered) {
                    CenterAlignedTopAppBar(
                        title = titleText,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    TopAppBar(
                        title = titleText,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                }
                // Registration line — the HUD's thin accent rule under the chrome
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
