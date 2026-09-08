package com.github.semanticgit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.Frame

@Composable
fun WindowScope.TitleBar(
    title: String,
    themeMode: ThemeMode = ThemeMode.Dark,
    onToggleTheme: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val frame = remember { window as Frame }

    WindowDraggableArea(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp).weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (themeMode == ThemeMode.Dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = if (themeMode == ThemeMode.Dark) I18n.t("titlebar.toggle_light") else I18n.t("titlebar.toggle_dark")
                    )
                }

                IconButton(
                    onClick = { frame.extendedState = Frame.ICONIFIED },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Minimize,
                        contentDescription = I18n.t("titlebar.minimize")
                    )
                }

                IconButton(
                    onClick = {
                        frame.extendedState = if (frame.extendedState and Frame.MAXIMIZED_BOTH != 0) {
                            Frame.NORMAL
                        } else {
                            Frame.MAXIMIZED_BOTH
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropSquare,
                        contentDescription = I18n.t("titlebar.maximize")
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = I18n.t("titlebar.close")
                    )
                }
            }
        }
    }
}
