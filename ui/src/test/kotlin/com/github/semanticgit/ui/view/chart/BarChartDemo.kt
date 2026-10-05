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
        title = "BarChart Demo",
        state = rememberWindowState(width = 960.dp, height = 680.dp)
    ) {
        BarChartDemo()
    }
}

@Composable
fun BarChartDemo() {
    MaterialTheme {
        var chartType by remember { mutableStateOf(BarDemoType.Vertical) }
        var lastClick by remember { mutableStateOf("点击柱状图查看交互效果…") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图表类型：", style = MaterialTheme.typography.titleMedium)
                BarDemoType.entries.forEach { type ->
                    FilterChip(
                        selected = chartType == type,
                        onClick = { chartType = type },
                        label = { Text(type.label) }
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
                when (chartType) {
                    BarDemoType.Vertical -> BarChart(
                        categories = barCategories,
                        series = barSeriesData,
                        title = "月度收支对比",
                        xAxisName = "月份",
                        yAxisName = "金额（万元）",
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { series, name, value ->
                            lastClick = "柱状图 → $series / $name：$value"
                        }
                    )
                    BarDemoType.Horizontal -> BarChart(
                        categories = barCategories,
                        series = barSeriesData,
                        title = "月度收支对比（水平）",
                        xAxisName = "金额（万元）",
                        yAxisName = "月份",
                        horizontal = true,
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { series, name, value ->
                            lastClick = "条形图 → $series / $name：$value"
                        }
                    )
                    BarDemoType.Stacked -> BarChart(
                        categories = barCategories,
                        series = barSeriesData,
                        title = "月度收支堆叠",
                        xAxisName = "月份",
                        yAxisName = "金额（万元）",
                        stack = true,
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { series, name, value ->
                            lastClick = "堆叠图 → $series / $name：$value"
                        }
                    )
                }
            }
        }
    }
}

private enum class BarDemoType(val label: String) {
    Vertical("垂直柱状图"),
    Horizontal("水平条形图"),
    Stacked("堆叠柱状图")
}

private val barCategories = listOf("一月", "二月", "三月", "四月", "五月", "六月")

private val barSeriesData = listOf(
    BarSeriesItem("收入", listOf(120.0, 200.0, 150.0, 180.0, 220.0, 260.0)),
    BarSeriesItem("支出", listOf(80.0, 90.0, 100.0, 110.0, 130.0, 140.0)),
    BarSeriesItem("利润", listOf(40.0, 110.0, 50.0, 70.0, 90.0, 120.0))
)
