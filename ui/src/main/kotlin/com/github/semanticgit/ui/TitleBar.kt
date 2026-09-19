package com.github.semanticgit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

/**
 * 自定义标题栏（配合无边框窗口使用）
 *
 * @param windowState  Compose 的窗口状态对象，用于最小化/最大化/还原
 * @param themeMode    当前主题模式
 * @param onToggleTheme 切换主题回调
 * @param onClose      关闭窗口回调
 */
@Composable
fun WindowScope.TitleBar(
    title: String,
    windowState: WindowState,
    themeMode: ThemeMode = ThemeMode.Dark,
    onToggleTheme: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

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
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)

            )

            Row(horizontalArrangement = Arrangement.End) {
                TitleBarButton(
                    icon = if (themeMode == ThemeMode.Dark) Icons.Default.DarkMode
                    else Icons.Default.LightMode,
                    contentDescription = if (themeMode == ThemeMode.Dark)
                        strings.titlebarToggleLight else strings.titlebarToggleDark,
                    onClick = onToggleTheme
                )

                TitleBarButton(
                    icon = Icons.Default.Minimize,
                    contentDescription = strings.titlebarMinimize,
                    onClick = { windowState.isMinimized = true }
                )

                TitleBarButton(
                    icon = if (isMaximized) Icons.Default.FilterNone
                    else Icons.Default.CropSquare,
                    contentDescription = if (isMaximized) strings.titlebarMaximize
                    else strings.titlebarMaximize,
                    onClick = {
                        windowState.placement = if (isMaximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    }
                )

                TitleBarButton(
                    icon = Icons.Default.Close,
                    contentDescription = strings.titlebarClose,
                    onClick = onClose,
                    isCloseButton = true
                )
            }
        }
    }
}

/**
 * 标题栏上的单个图标按钮，支持 hover 高亮，关闭按钮 hover 变红
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TitleBarButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isCloseButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    var hovered by remember { mutableStateOf(false) }

    val background = when {
        isCloseButton && hovered -> Color(0xFFE81123)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val tint = when {
        isCloseButton && hovered -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(background)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}