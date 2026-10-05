package com.github.semanticgit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Folder
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
import com.github.semanticgit.ui.utils.JsonFileUtil
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

    val repoHistoryFile = remember { File(System.getProperty("user.home"), ".semanticgit/repos.json") }
    val legacyRepoTxtFile = remember { File(System.getProperty("user.home"), ".semanticgit/repos.txt") }

    val configFile = remember { File(System.getProperty("user.home"), ".semanticgit/config.json") }
    val legacyLastRepoIndexFile = remember { File(System.getProperty("user.home"), ".semanticgit/last_repo.txt") }

    val repoPaths = remember {
        mutableStateListOf<String>().also { list ->
            loadRepoPaths(repoHistoryFile, legacyRepoTxtFile).forEach { path ->
                list.add(path)
            }
        }
    }

    var selectedRepoIndex by remember {
        val savedIndex = loadLastRepoIndex(configFile, legacyLastRepoIndexFile)
        mutableStateOf(
            when {
                repoPaths.isNotEmpty() && savedIndex in repoPaths.indices -> savedIndex
                repoPaths.isNotEmpty() -> 0
                else -> -1
            }
        )
    }

    LaunchedEffect(repoPaths.toList()) {
        saveRepoPaths(repoHistoryFile, repoPaths.toList())
    }

    LaunchedEffect(selectedRepoIndex) {
        saveLastRepoIndex(configFile, selectedRepoIndex)
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
                    4 -> Setting(
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

private fun loadRepoPaths(jsonFile: File, legacyTxtFile: File): List<String> {
    if (jsonFile.exists()) {
        val paths = JsonFileUtil.readStringArray(jsonFile)
        return paths.filter { it.isNotEmpty() && File(it).exists() && File(it).isDirectory }
    }
    if (legacyTxtFile.exists()) {
        return try {
            val paths = legacyTxtFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && File(it).exists() && File(it).isDirectory }
            JsonFileUtil.writeStringArray(jsonFile, paths)
            legacyTxtFile.delete()
            paths
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    return emptyList()
}

private fun saveRepoPaths(file: File, paths: List<String>) {
    JsonFileUtil.writeStringArray(file, paths)
}

private fun loadLastRepoIndex(configFile: File, legacyTxtFile: File): Int {
    if (configFile.exists()) {
        val config = JsonFileUtil.readConfig(configFile)
        return JsonFileUtil.getConfigInt(config, "lastRepoIndex", -1)
    }
    if (legacyTxtFile.exists()) {
        return try {
            val index = legacyTxtFile.readText().trim().toIntOrNull() ?: -1
            val config = mapOf<String, Any>("lastRepoIndex" to index)
            JsonFileUtil.writeConfig(configFile, config)
            legacyTxtFile.delete()
            index
        } catch (e: Exception) {
            -1
        }
    }
    return -1
}

private fun saveLastRepoIndex(file: File, index: Int) {
    val existing = if (file.exists()) JsonFileUtil.readConfig(file) else emptyMap()
    val updated = existing.toMutableMap()
    updated["lastRepoIndex"] = index
    JsonFileUtil.writeConfig(file, updated)
}
