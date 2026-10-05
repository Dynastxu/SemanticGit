package com.github.semanticgit.ui.view.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

data class BarSeriesItem(
    val name: String,
    val data: List<Double>
)

@Composable
fun BarChart(
    categories: List<String>,
    series: List<BarSeriesItem>,
    title: String? = null,
    xAxisName: String? = null,
    yAxisName: String? = null,
    horizontal: Boolean = false,
    stack: Boolean = false,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    val optionJson = remember(categories, series, title, xAxisName, yAxisName, horizontal, stack) {
        buildBarOptionJson(categories, series, title, xAxisName, yAxisName, horizontal, stack)
    }
    EChartsView(
        modifier = modifier,
        optionJson = optionJson,
        darkMode = darkMode,
        onChartClick = onChartClick
    )
}

internal fun buildBarOptionJson(
    categories: List<String>,
    series: List<BarSeriesItem>,
    title: String?,
    xAxisName: String?,
    yAxisName: String?,
    horizontal: Boolean,
    stack: Boolean
): String {
    val categoriesJson = categories.joinToString(",") { "\"${it.escapeJson()}\"" }
    val seriesJson = series.joinToString(",") { item ->
        val escapedName = item.name.escapeJson()
        val dataJson = item.data.joinToString(",")
        val stackJson = if (stack) ""","stack":"total"""" else ""
        """{"name":"$escapedName","type":"bar","data":[$dataJson]$stackJson}"""
    }
    val titleJson = if (title != null) {
        ""","title":{"text":"${title.escapeJson()}","left":"center"}"""
    } else ""
    val xNameJson = if (xAxisName != null) {
        ""","name":"${xAxisName.escapeJson()}""""
    } else ""
    val yNameJson = if (yAxisName != null) {
        ""","name":"${yAxisName.escapeJson()}""""
    } else ""
    val axisType = if (horizontal) "category" else "value"
    val valueType = if (horizontal) "value" else "category"
    val xAxis = if (horizontal) {
        """{"type":"$axisType"$xNameJson}"""
    } else {
        """{"type":"$axisType","data":[$categoriesJson]$xNameJson}"""
    }
    val yAxis = if (horizontal) {
        """{"type":"$valueType","data":[$categoriesJson]$yNameJson}"""
    } else {
        """{"type":"$valueType"$yNameJson}"""
    }
    return buildString {
        append("""{"tooltip":{"trigger":"axis","axisPointer":{"type":"shadow"}},"legend":{"top":"bottom"}""")
        append(titleJson)
        append(""","xAxis":$xAxis""")
        append(""","yAxis":$yAxis""")
        append(""","series":[$seriesJson]}""")
    }
}

private fun String.escapeJson(): String {
    return this.replace("\\", "\\\\").replace("\"", "\\\"")
}
