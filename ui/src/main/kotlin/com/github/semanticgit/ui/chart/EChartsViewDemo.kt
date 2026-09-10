package com.github.semanticgit.ui.chart

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
        title = "Compose Desktop + ECharts Demo",
        state = rememberWindowState(width = 960.dp, height = 680.dp)
    ) {
        EChartsViewDemo()
    }
}

@Composable
fun EChartsViewDemo() {
    MaterialTheme {
        var chartType by remember { mutableStateOf(ChartType.Bar) }
        var lastClick by remember { mutableStateOf("点击图表元素查看交互效果…") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("图表类型：", style = MaterialTheme.typography.titleMedium)
                ChartType.entries.forEach { type ->
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
                EChartsView(
                    modifier = Modifier.fillMaxSize(),
                    optionJson = chartType.option,
                    onChartClick = { series, name, value ->
                        lastClick = "点击 → 系列：$series，名称：$name，值：$value"
                    }
                )
            }
        }
    }
}

enum class ChartType(val label: String, val option: String) {
    Bar(
        "柱状图",
        """
        {
          "title": { "text": "每月销量", "left": "center" },
          "tooltip": { "trigger": "axis" },
          "grid": { "left": "10%", "right": "10%", "bottom": "10%" },
          "xAxis": { "type": "category", "data": ["一月","二月","三月","四月","五月","六月"] },
          "yAxis": { "type": "value" },
          "series": [
            { "name": "销量", "type": "bar", "data": [120, 200, 150, 80, 170, 210] }
          ]
        }
        """.trimIndent()
    ),
    Line(
        "折线图",
        """
        {
          "title": { "text": "一周温度变化", "left": "center" },
          "tooltip": { "trigger": "axis" },
          "grid": { "left": "10%", "right": "10%", "bottom": "10%" },
          "xAxis": { "type": "category", "boundaryGap": false, "data": ["周一","周二","周三","周四","周五","周六","周日"] },
          "yAxis": { "type": "value" },
          "series": [
            { "name": "最高温", "type": "line", "smooth": true, "data": [22, 24, 26, 25, 23, 21, 20] },
            { "name": "最低温", "type": "line", "smooth": true, "data": [12, 14, 16, 15, 13, 11, 10] }
          ]
        }
        """.trimIndent()
    ),
    Pie(
        "饼图",
        """
        {
          "title": { "text": "访问来源", "left": "center" },
          "tooltip": { "trigger": "item" },
          "legend": { "bottom": 0 },
          "series": [
            {
              "name": "访问来源",
              "type": "pie",
              "radius": ["40%", "65%"],
              "data": [
                { "value": 1048, "name": "搜索引擎" },
                { "value": 735, "name": "直接访问" },
                { "value": 580, "name": "邮件营销" },
                { "value": 484, "name": "联盟广告" },
                { "value": 300, "name": "视频广告" }
              ]
            }
          ]
        }
        """.trimIndent()
    )
}
