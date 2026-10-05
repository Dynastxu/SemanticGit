package com.github.semanticgit.ui.view.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.oshai.kotlinlogging.KotlinLogging

data class PieDataItem(
    val name: String,
    val value: Double
)

@Composable
fun PieChart(
    data: List<PieDataItem>,
    title: String? = null,
    radius: String = "60%",
    center: Pair<String, String> = Pair("50%", "50%"),
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    val optionJson = remember(data, title, radius, center) {
        buildPieOptionJson(data, title, radius, center, null)
    }
    EChartsView(
        modifier = modifier,
        optionJson = optionJson,
        darkMode = darkMode,
        onChartClick = onChartClick
    )
}

@Composable
fun RoseChart(
    data: List<PieDataItem>,
    title: String? = null,
    roseType: String = "area",
    radius: String = "60%",
    center: Pair<String, String> = Pair("50%", "50%"),
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    val optionJson = remember(data, title, radius, center, roseType) {
        buildPieOptionJson(data, title, radius, center, roseType)
    }
    EChartsView(
        modifier = modifier,
        optionJson = optionJson,
        darkMode = darkMode,
        onChartClick = onChartClick
    )
}

internal fun buildPieOptionJson(
    data: List<PieDataItem>,
    title: String?,
    radius: String,
    center: Pair<String, String>,
    roseType: String?
): String {
    val dataJson = data.joinToString(",") { item ->
        val escapedName = item.name.replace("\\", "\\\\").replace("\"", "\\\"")
        """{"name":"$escapedName","value":${item.value}}"""
    }
    val titleJson = if (title != null) {
        val escaped = title.replace("\\", "\\\\").replace("\"", "\\\"")
        ""","title":{"text":"$escaped","left":"center"}"""
    } else ""
    val roseTypeJson = if (roseType != null) {
        ""","roseType":"$roseType""""
    } else ""
    return buildString {
        append("""{"tooltip":{"trigger":"item"},"legend":{"orient":"vertical","left":"left"}""")
        append(titleJson)
        append(""","series":[{"type":"pie","radius":"$radius","center":["${center.first}","${center.second}"]""")
        append(roseTypeJson)
        append(""","data":[$dataJson],"emphasis":{"itemStyle":{"shadowBlur":10,"shadowOffsetX":0,"shadowColor":"rgba(0,0,0,0.5)"}}}]}""")
    }
}

private val logger = KotlinLogging.logger {}

/*
 * Sunburst (旭日图)
 */

data class SunburstDataItem(
    val name: String,
    val value: Double? = null,
    val children: List<SunburstDataItem>? = null
)

@Composable
fun SunburstChart(
    data: SunburstDataItem,
    title: String? = null,
    radius: Pair<String, String> = Pair("15%", "90%"),
    center: Pair<String, String> = Pair("50%", "50%"),
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    val optionJson = remember(data, title, radius, center) {
        buildSunburstOptionJson(data, title, radius, center)
    }
    EChartsView(
        modifier = modifier,
        optionJson = optionJson,
        darkMode = darkMode,
        onChartClick = onChartClick
    )
}

internal fun buildSunburstOptionJson(
    data: SunburstDataItem,
    title: String?,
    radius: Pair<String, String>,
    center: Pair<String, String>
): String {
    val dataJson = buildSunburstDataJson(data)
    val titleJson = if (title != null) {
        val escaped = title.replace("\\", "\\\\").replace("\"", "\\\"")
        ""","title":{"text":"$escaped","left":"center"}"""
    } else ""
    return buildString {
        append("""{"tooltip":{"trigger":"item"}""")
        append(titleJson)
        append(""","series":[{"type":"sunburst","radius":["${radius.first}","${radius.second}"],""")
        append(""""center":["${center.first}","${center.second}"],""")
        append(""""data":[$dataJson],"emphasis":{"itemStyle":{"shadowBlur":10,"shadowOffsetX":0,"shadowColor":"rgba(0,0,0,0.5)"}}}]}""")
    }
}

private fun buildSunburstDataJson(item: SunburstDataItem): String {
    val escapedName = item.name.replace("\\", "\\\\").replace("\"", "\\\"")
    val valueJson = if (item.value != null) {
        ""","value":${item.value}"""
    } else ""
    val childrenJson = if (!item.children.isNullOrEmpty()) {
        val childrenArray = item.children.joinToString(",") { buildSunburstDataJson(it) }
        ""","children":[$childrenArray]"""
    } else ""
    return """{"name":"$escapedName"$valueJson$childrenJson}"""
}
