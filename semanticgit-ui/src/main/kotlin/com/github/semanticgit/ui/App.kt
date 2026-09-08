package com.github.semanticgit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SemanticGit",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
        }
    }
}
