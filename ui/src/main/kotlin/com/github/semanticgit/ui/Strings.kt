package com.github.semanticgit.ui

import androidx.compose.runtime.staticCompositionLocalOf

data class Strings(
    val appTitle: String,
    val navRepository: String,
    val navCommit: String,
    val navHistory: String,
    val navTools: String,
    val navSettings: String,
    val titlebarToggleLight: String,
    val titlebarToggleDark: String,
    val titlebarMinimize: String,
    val titlebarMaximize: String,
    val titlebarClose: String,
    val repoSelectPlaceholder: String,
    val repoSelectFolder: String,
    val repoNoRepoSelected: String
)

val ZhStrings = Strings(
    appTitle = "SemanticGit",
    navRepository = "仓库",
    navCommit = "提交",
    navHistory = "历史",
    navTools = "工具",
    navSettings = "设置",
    titlebarToggleLight = "切换浅色主题",
    titlebarToggleDark = "切换深色主题",
    titlebarMinimize = "最小化",
    titlebarMaximize = "最大化",
    titlebarClose = "关闭",
    repoSelectPlaceholder = "选择仓库文件夹...",
    repoSelectFolder = "选择文件夹",
    repoNoRepoSelected = "请选择一个仓库文件夹"
)

val EnStrings = Strings(
    appTitle = "SemanticGit",
    navRepository = "Repository",
    navCommit = "Commit",
    navHistory = "History",
    navTools = "Tools",
    navSettings = "Settings",
    titlebarToggleLight = "Switch to Light Theme",
    titlebarToggleDark = "Switch to Dark Theme",
    titlebarMinimize = "Minimize",
    titlebarMaximize = "Maximize",
    titlebarClose = "Close",
    repoSelectPlaceholder = "Select repository folder...",
    repoSelectFolder = "Select Folder",
    repoNoRepoSelected = "Please select a repository folder"
)

val LocalStrings = staticCompositionLocalOf { ZhStrings }
