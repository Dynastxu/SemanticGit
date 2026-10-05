package com.github.semanticgit.ui.view.chart

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MultiChartDisplay Demo",
        state = rememberWindowState(width = 960.dp, height = 680.dp)
    ) {
        MultiChartDisplayDemo()
    }
}

@Composable
fun MultiChartDisplayDemo() {
    MaterialTheme {
        var corner by remember { mutableStateOf(DropdownCorner.TopLeft) }
        var lastClick by remember { mutableStateOf("点击图表查看交互效果…") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("下拉栏位置：", style = MaterialTheme.typography.titleMedium)
                DropdownCorner.entries.forEach { c ->
                    FilterChip(
                        selected = corner == c,
                        onClick = { corner = c },
                        label = { Text(c.label) }
                    )
                }
            }

            Text(
                text = lastClick,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                MultiChartDisplay(
                    data = multiDemoData,
                    title = "各品类销售额",
                    dropdownCorner = corner,
                    modifier = Modifier.fillMaxSize(),
                    onChartClick = { series, name, value ->
                        lastClick = "$series → $name：$value"
                    }
                )
            }
        }
    }
}

private val multiDemoData = listOf(
    PieDataItem("电子产品", 3200.0),
    PieDataItem("服装鞋帽", 2800.0),
    PieDataItem("食品饮料", 2100.0),
    PieDataItem("家居用品", 1650.0),
    PieDataItem("图书音像", 920.0),
    PieDataItem("运动户外", 680.0),
    PieDataItem("美妆个护", 540.0),
    PieDataItem("母婴用品", 310.0)
)
