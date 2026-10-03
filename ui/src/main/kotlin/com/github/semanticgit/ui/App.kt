package com.github.semanticgit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons.Filled as FilledIcons
import androidx.compose.material.icons.Icons.Outlined as OutlinedIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.semanticgit.ui.page.AutoPortrait
import com.github.semanticgit.ui.page.EntityAnalysis
import com.github.semanticgit.ui.page.RepoPage
import com.github.semanticgit.ui.page.Setting
import com.github.semanticgit.ui.page.Submit
import java.io.File

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "SemanticGit",
        state = windowState,          // ← 绑定到 Window
        undecorated = true
    ) {
        var themeMode by remember { mutableStateOf(ThemeMode.Light) }

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
                    windowState = windowState,
                    themeMode = themeMode,
                    onToggleTheme = { themeMode = if (themeMode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark },
                    languageTag = lyricist.languageTag,
                    onChangeLanguage = { lyricist.languageTag = it },
                    onClose = ::exitApplication
                )
            }
        }

    }
}

@Composable
private fun WindowScope.SemanticGitApp(
    windowState: WindowState,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    languageTag: String,
    onChangeLanguage: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val strings = LocalStrings.current

    val repoHistoryFile = remember { File(System.getProperty("user.home"), ".semanticgit/repos.txt") }

    val repoPaths = remember {
        mutableStateListOf<String>().also { list ->
            loadRepoPaths(repoHistoryFile).forEach { path ->
                list.add(path)
            }
        }
    }
    var selectedRepoIndex by remember {
        mutableStateOf(if (repoPaths.isNotEmpty()) 0 else -1)
    }

    LaunchedEffect(repoPaths.toList()) {
        saveRepoPaths(repoHistoryFile, repoPaths.toList())
    }

    val navItems = listOf(
        NavItem(
            selectedIcon = FilledIcons.Folder,
            unselectedIcon = OutlinedIcons.Folder,
            title = strings.navRepository,
            onClick = { selectedIndex = 0 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Category,
            unselectedIcon = OutlinedIcons.Category,
            title = strings.navEntity,
            onClick = { selectedIndex = 1 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Person,
            unselectedIcon = OutlinedIcons.Person,
            title = strings.navAuthorPortrait,
            onClick = { selectedIndex = 2 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Commit,
            unselectedIcon = OutlinedIcons.Commit,
            title = strings.navCommit,
            onClick = { selectedIndex = 3 }
        ),
        NavItem(
            selectedIcon = FilledIcons.History,
            unselectedIcon = OutlinedIcons.History,
            title = strings.navHistory,
            onClick = { selectedIndex = 4 }
        ),
        NavItem(
            selectedIcon = FilledIcons.Build,
            unselectedIcon = OutlinedIcons.Build,
            title = strings.navTools,
            onClick = { selectedIndex = 5 }
        )
    )

    val bottomNavItems = listOf(
        NavItem(
            selectedIcon = FilledIcons.Settings,
            unselectedIcon = OutlinedIcons.Settings,
            title = strings.navSettings,
            onClick = { selectedIndex = 6 }
        )
    )

    val allNavItems = navItems + bottomNavItems

    Column(modifier = Modifier.fillMaxSize()) {
        TitleBar(
            title = strings.appTitle,
            windowState = windowState,
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
            ) {
                when (selectedIndex) {
                    0 -> RepoPage(
                        modifier = Modifier.fillMaxSize(),
                        repoPaths = repoPaths,
                        selectedIndex = selectedRepoIndex,
                        onSelectedRepoIndexChanged = { selectedRepoIndex = it }
                    )
                    1 -> EntityAnalysis(
                        modifier = Modifier.fillMaxSize(),
                        repoPaths = repoPaths,
                        selectedRepoIndex = selectedRepoIndex
                    )
                    2 -> AutoPortrait(
                        modifier = Modifier.fillMaxSize(),
                        repoPaths = repoPaths,
                        selectedRepoIndex = selectedRepoIndex
                    )
                    3 -> Submit(
                        modifier = Modifier.fillMaxSize(),
                        repoPaths = repoPaths,
                        selectedRepoIndex = selectedRepoIndex
                    )
                    6 -> Setting(
                        modifier = Modifier.fillMaxSize(),
                        themeMode = themeMode,
                        onToggleTheme = onToggleTheme,
                        languageTag = languageTag,
                        onChangeLanguage = onChangeLanguage
                    )
                    else -> Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
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
    }
}

private fun loadRepoPaths(file: File): List<String> {
    if (!file.exists()) return emptyList()
    return try {
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && File(it).exists() && File(it).isDirectory }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private fun saveRepoPaths(file: File, paths: List<String>) {
    try {
        file.parentFile?.mkdirs()
        file.writeText(paths.joinToString("\n"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}