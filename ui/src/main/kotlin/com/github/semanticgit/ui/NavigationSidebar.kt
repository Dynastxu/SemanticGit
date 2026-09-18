package com.github.semanticgit.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


data class NavItem(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon,
    val title: String,
    val onClick: () -> Unit = {}
)


@Composable
fun NavigationSidebar(
    items: List<NavItem>,
    bottomItems: List<NavItem> = emptyList(),
    selectedIndex: Int = 0,
    modifier: Modifier = Modifier,
    railWidth: Dp = 80.dp,
    alwaysShowLabel: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxHeight().width(railWidth),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {

            items.forEachIndexed { index, item ->
                key(item.title) {
                    RailItem(
                        item = item,
                        selected = selectedIndex == index,
                        alwaysShowLabel = alwaysShowLabel
                    )
                }
            }


            Spacer(modifier = Modifier.weight(1f))

            // 底部列表：全局索引 = items.size + 局部索引
            bottomItems.forEachIndexed { index, item ->
                key(item.title) {
                    RailItem(
                        item = item,
                        selected = selectedIndex == items.size + index,
                        alwaysShowLabel = alwaysShowLabel
                    )
                }
            }
        }
    }
}

/**
 * 单个导航栏按钮（抽取出来的私有组件，避免重复代码）
 */
@Composable
private fun RailItem(
    item: NavItem,
    selected: Boolean,
    alwaysShowLabel: Boolean
) {
    NavigationRailItem(
        selected = selected,
        onClick = item.onClick,
        icon = {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,

                contentDescription = if (alwaysShowLabel) null else item.title
            )
        },
        label = if (alwaysShowLabel) {
            { Text(item.title) }
        } else {
            null
        }
    )
}