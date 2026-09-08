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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                NavigationRailItem(
                    selected = selected,
                    onClick = item.onClick,
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    },
                    label = { Text(item.title) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            bottomItems.forEachIndexed { index, item ->
                val selected = selectedIndex == items.size + index
                NavigationRailItem(
                    selected = selected,
                    onClick = item.onClick,
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    },
                    label = { Text(item.title) }
                )
            }
        }
    }
}
