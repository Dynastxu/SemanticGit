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
        title = "PieChart / RoseChart Demo",
        state = rememberWindowState(width = 960.dp, height = 680.dp)
    ) {
        PieChartDemo()
    }
}

@Composable
fun PieChartDemo() {
    MaterialTheme {
        var chartType by remember { mutableStateOf(DemoChartType.Pie) }
        var lastClick by remember { mutableStateOf("点击图表扇区查看交互效果…") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图表类型：", style = MaterialTheme.typography.titleMedium)
                DemoChartType.entries.forEach { type ->
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
                    DemoChartType.Pie -> PieChart(
                        data = pieDemoData,
                        title = "访问来源分布",
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { _, name, value ->
                            lastClick = "饼图 → $name：$value"
                        }
                    )
                    DemoChartType.RoseArea -> RoseChart(
                        data = roseDemoData,
                        title = "各品类销售额（面积玫瑰图）",
                        roseType = "area",
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { _, name, value ->
                            lastClick = "玫瑰图(area) → $name：$value"
                        }
                    )
                    DemoChartType.RoseRadius -> RoseChart(
                        data = roseDemoData,
                        title = "各品类销售额（半径玫瑰图）",
                        roseType = "radius",
                        modifier = Modifier.fillMaxSize(),
                        onChartClick = { _, name, value ->
                            lastClick = "玫瑰图(radius) → $name：$value"
                        }
                    )
                }
            }
        }
    }
}

private enum class DemoChartType(val label: String) {
    Pie("饼图"),
    RoseArea("玫瑰图(area)"),
    RoseRadius("玫瑰图(radius)")
}

private val pieDemoData = listOf(
    PieDataItem("搜索引擎", 1048.0),
    PieDataItem("直接访问", 735.0),
    PieDataItem("邮件营销", 580.0),
    PieDataItem("联盟广告", 484.0),
    PieDataItem("视频广告", 300.0),
    PieDataItem("社交媒体", 212.0)
)

private val roseDemoData = listOf(
    PieDataItem("电子产品", 3200.0),
    PieDataItem("服装鞋帽", 2800.0),
    PieDataItem("食品饮料", 2100.0),
    PieDataItem("家居用品", 1650.0),
    PieDataItem("图书音像", 920.0),
    PieDataItem("运动户外", 680.0),
    PieDataItem("美妆个护", 540.0),
    PieDataItem("母婴用品", 310.0)
)
