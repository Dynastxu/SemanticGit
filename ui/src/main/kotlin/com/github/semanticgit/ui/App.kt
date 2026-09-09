package com.github.semanticgit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons.Filled as FilledIcons
import androidx.compose.material.icons.Icons.Outlined as OutlinedIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SemanticGit",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
        undecorated = true
    ) {
        var themeMode by remember { mutableStateOf(ThemeMode.Dark) }

        val lyricist = rememberStrings(
            translations = mapOf(
                "zh" to ZhStrings,
                "en" to EnStrings
            ),
            defaultLanguageTag = "zh"
        )

        ProvideStrings(lyricist, LocalStrings) {
            SemanticGitTheme(themeMode = themeMode) {
                SemanticGitApp(
                    themeMode = themeMode,
                    onToggleTheme = { themeMode = if (themeMode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark },
                    onClose = ::exitApplication
                )
            }
        }
    }
}

@Composable
private fun WindowScope.SemanticGitApp(
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onClose: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val strings = LocalStrings.current

    val navItems = listOf(
        NavItem(
            selectedIcon = FilledIcons.Folder,
            unselectedIcon = OutlinedIcons.Folder,
            title = strings.navRepository,
            onClick = { selectedIndex = 0 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Commit,
            unselectedIcon = OutlinedIcons.Commit,
            title = strings.navCommit,
            onClick = { selectedIndex = 1 }
        ),
        NavItem(
            selectedIcon = FilledIcons.History,
            unselectedIcon = OutlinedIcons.History,
            title = strings.navHistory,
            onClick = { selectedIndex = 2 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Build,
            unselectedIcon = OutlinedIcons.Build,
            title = strings.navTools,
            onClick = { selectedIndex = 3 }
        )
    )

    val bottomNavItems = listOf(
        NavItem(
            selectedIcon = FilledIcons.Settings,
            unselectedIcon = OutlinedIcons.Settings,
            title = strings.navSettings,
            onClick = { selectedIndex = 4 }
        )
    )

    val allNavItems = navItems + bottomNavItems

    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(
            title = strings.appTitle,
            themeMode = themeMode,
            onToggleTheme = onToggleTheme,
            onClose = onClose
        )

        Row(modifier = Modifier.fillMaxSize()) {
            NavigationSidebar(
                items = navItems,
                bottomItems = bottomNavItems,
                selectedIndex = selectedIndex
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = allNavItems[selectedIndex].title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
