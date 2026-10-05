package com.github.semanticgit.ui.view.chart

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ChartType(val label: String) {
    Pie("饼图"),
    RoseArea("玫瑰图(area)"),
    RoseRadius("玫瑰图(radius)"),
    Bar("柱状图"),
    HorizontalBar("水平条形图")
}

enum class DropdownCorner(val label: String) {
    TopLeft("左上角"),
    TopRight("右上角"),
    BottomLeft("左下角"),
    BottomRight("右下角")
}

@Composable
fun MultiChartDisplay(
    data: List<PieDataItem>,
    title: String? = null,
    dropdownCorner: DropdownCorner = DropdownCorner.TopLeft,
    modifier: Modifier = Modifier,
    darkMode: Boolean = false,
    onChartClick: ((series: String, name: String, value: String) -> Unit)? = null
) {
    var selectedType by remember { mutableStateOf(ChartType.Pie) }

    val isTop = dropdownCorner == DropdownCorner.TopLeft || dropdownCorner == DropdownCorner.TopRight
    val isLeft = dropdownCorner == DropdownCorner.TopLeft || dropdownCorner == DropdownCorner.BottomLeft

    Column(modifier = modifier) {
        if (isTop) {
            ChartTypeBar(selectedType, isLeft) { selectedType = it }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ChartContent(data, title, selectedType, darkMode, onChartClick)
        }

        if (!isTop) {
            ChartTypeBar(selectedType, isLeft) { selectedType = it }
        }
    }
}

@Composable
private fun ChartContent(
    data: List<PieDataItem>,
    title: String?,
    type: ChartType,
    darkMode: Boolean,
    onChartClick: ((series: String, name: String, value: String) -> Unit)?
) {
    when (type) {
        ChartType.Pie -> PieChart(
            data = data,
            title = title,
            modifier = Modifier.fillMaxSize(),
            darkMode = darkMode,
            onChartClick = onChartClick
        )
        ChartType.RoseArea -> RoseChart(
            data = data,
            title = title,
            roseType = "area",
            modifier = Modifier.fillMaxSize(),
            darkMode = darkMode,
            onChartClick = onChartClick
        )
        ChartType.RoseRadius -> RoseChart(
            data = data,
            title = title,
            roseType = "radius",
            modifier = Modifier.fillMaxSize(),
            darkMode = darkMode,
            onChartClick = onChartClick
        )
        ChartType.Bar -> BarChart(
            categories = data.map { it.name },
            series = listOf(BarSeriesItem("数值", data.map { it.value })),
            title = title,
            modifier = Modifier.fillMaxSize(),
            darkMode = darkMode,
            onChartClick = onChartClick
        )
        ChartType.HorizontalBar -> BarChart(
            categories = data.map { it.name },
            series = listOf(BarSeriesItem("数值", data.map { it.value })),
            title = title,
            horizontal = true,
            modifier = Modifier.fillMaxSize(),
            darkMode = darkMode,
            onChartClick = onChartClick
        )
    }
}

@Composable
private fun ChartTypeBar(
    selectedType: ChartType,
    isLeft: Boolean,
    onTypeSelected: (ChartType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
    ) {
        ChartType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.label) },
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
