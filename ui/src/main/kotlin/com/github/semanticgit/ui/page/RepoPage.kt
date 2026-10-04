package com.github.semanticgit.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.semanticgit.common.config.ConfigItems
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.AnalysisEngine
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics
import com.github.semanticgit.core.parser.ParserRegistry
import com.github.semanticgit.parser.java.api.LanguageParser
import com.github.semanticgit.ui.LocalStrings
import com.github.semanticgit.ui.LocalThemeMode
import com.github.semanticgit.ui.ThemeMode
import com.github.semanticgit.ui.chart.EChartsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

data class ParserSettings(
    val timeoutMs: Long = 5000L,
    val maxParseSizeMb: Long = 5L,
    val maxRegexSizeMb: Long = 2L
) {
    val maxParseSizeBytes: Long get() = maxParseSizeMb * 1024 * 1024
    val maxRegexSizeBytes: Long get() = maxRegexSizeMb * 1024 * 1024
}

enum class AnalysisMode {
    FULL,
    INCREMENTAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPage(
    modifier: Modifier = Modifier,
    repoPaths: MutableList<String>,
    selectedIndex: Int,
    onSelectedRepoIndexChanged: (Int) -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var dropdownExpanded by remember { mutableStateOf(false) }
    val displayNames = remember(repoPaths.toList()) {
        computeDisplayNames(repoPaths.toList())
    }

    // ============ 配置状态 ============
    var timeoutMsText by remember { mutableStateOf("5000") }
    var maxParseSizeMbText by remember { mutableStateOf("5") }
    var maxRegexSizeMbText by remember { mutableStateOf("2") }
    var analysisMode by remember { mutableStateOf(AnalysisMode.FULL) }

    // ============ 分析状态 ============
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statistics by remember { mutableStateOf<SimpleEntityChangeStatistics?>(null) }

    // ============ 面板状态 ============
    var configExpanded by remember { mutableStateOf(true) }

    // ============ 配置变更跟踪 ============
    var lastAnalyzedConfig by remember { mutableStateOf("") }
    val currentConfigFingerprint = remember(
        timeoutMsText, maxParseSizeMbText, maxRegexSizeMbText, analysisMode
    ) {
        "$timeoutMsText|$maxParseSizeMbText|$maxRegexSizeMbText|$analysisMode"
    }
    val configChanged = statistics != null && currentConfigFingerprint != lastAnalyzedConfig

    val selectedPath = repoPaths.getOrNull(selectedIndex)

    fun buildSettings(): ParserSettings = ParserSettings(
        timeoutMs = timeoutMsText.toLongOrNull() ?: 5000L,
        maxParseSizeMb = maxParseSizeMbText.toLongOrNull() ?: 5L,
        maxRegexSizeMb = maxRegexSizeMbText.toLongOrNull() ?: 2L
    )

    fun startAnalysis(mode: AnalysisMode = analysisMode) {
        val path = selectedPath ?: return
        scope.launch {
            isAnalyzing = true
            errorMessage = null
            statistics = null
            try {
                val settings = buildSettings()
                val result = executeCoreAnalysis(path, mode, settings)
                when (result) {
                    is AnalysisResult.Success -> {
                        statistics = result.statistics
                        lastAnalyzedConfig = currentConfigFingerprint
                        configExpanded = false
                    }
                    is AnalysisResult.Error -> {
                        errorMessage = result.message
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: strings.repoAnalysisFailed
                e.printStackTrace()
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun resetRepoState() {
        statistics = null
        errorMessage = null
        lastAnalyzedConfig = ""
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ============ 顶部：仓库选择（固定） ============
        RepoSelector(
            repoPaths = repoPaths,
            displayNames = displayNames,
            selectedIndex = selectedIndex,
            dropdownExpanded = dropdownExpanded,
            onDropdownExpandedChange = { dropdownExpanded = it },
            onRepoSelected = { index ->
                onSelectedRepoIndexChanged(index)
                dropdownExpanded = false
                resetRepoState()
            },
            onAddRepo = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = strings.repoSelectFolder
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val newPath = chooser.selectedFile.absolutePath
                    val existingIndex = repoPaths.indexOf(newPath)
                    if (existingIndex >= 0) {
                        onSelectedRepoIndexChanged(existingIndex)
                    } else {
                        repoPaths.add(newPath)
                        onSelectedRepoIndexChanged(repoPaths.size - 1)
                    }
                    resetRepoState()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ============ 可折叠配置面板 ============
        CollapsibleConfigPanel(
            expanded = configExpanded,
            onToggle = { configExpanded = !configExpanded },
            isAnalyzing = isAnalyzing,
            timeoutMsText = timeoutMsText,
            onTimeoutMsTextChange = { timeoutMsText = it },
            maxParseSizeMbText = maxParseSizeMbText,
            onMaxParseSizeMbTextChange = { maxParseSizeMbText = it },
            maxRegexSizeMbText = maxRegexSizeMbText,
            onMaxRegexSizeMbTextChange = { maxRegexSizeMbText = it },
            analysisMode = analysisMode,
            onAnalysisModeChange = { analysisMode = it }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ============ 中间：可滚动内容区 ============
        val currentErrorMessage = errorMessage
        val currentStatistics = statistics

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                selectedPath == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.repoNoRepoSelected,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isAnalyzing -> {
                    AnalyzingView(strings = strings)
                }
                currentErrorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentErrorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                currentStatistics != null -> {
                    StatisticsContent(strings = strings, statistics = currentStatistics)
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.repoClickToStart,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ============ 底部：分析按钮（固定） ============
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { startAnalysis() },
                enabled = selectedPath != null && !isAnalyzing
            ) {
                val modeLabel = if (analysisMode == AnalysisMode.FULL)
                    strings.repoAnalysisModeFull else strings.repoAnalysisModeIncremental
                Text(
                    text = when {
                        isAnalyzing -> strings.repoAnalyzingButton
                        configChanged -> strings.repoReanalyzeConfigChanged
                        statistics != null -> "${strings.repoReanalyzePrefix}（${modeLabel}）"
                        else -> strings.repoStartAnalysis
                    }
                )
            }
        }
    }
}

// ==================== 仓库选择器 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoSelector(
    repoPaths: MutableList<String>,
    displayNames: List<String>,
    selectedIndex: Int,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onRepoSelected: (Int) -> Unit,
    onAddRepo: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = onDropdownExpandedChange,
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = if (selectedIndex in repoPaths.indices) displayNames[selectedIndex] else "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { onDropdownExpandedChange(false) }
            ) {
                repoPaths.forEachIndexed { index, _ ->
                    DropdownMenuItem(
                        text = { Text(displayNames[index]) },
                        onClick = { onRepoSelected(index) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        IconButton(onClick = onAddRepo) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = strings.repoSelectFolderContentDesc,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 可折叠配置面板 ====================

@Composable
private fun CollapsibleConfigPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    isAnalyzing: Boolean,
    timeoutMsText: String,
    onTimeoutMsTextChange: (String) -> Unit,
    maxParseSizeMbText: String,
    onMaxParseSizeMbTextChange: (String) -> Unit,
    maxRegexSizeMbText: String,
    onMaxRegexSizeMbTextChange: (String) -> Unit,
    analysisMode: AnalysisMode,
    onAnalysisModeChange: (AnalysisMode) -> Unit
) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.repoAnalysisConfigTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!expanded && !isAnalyzing) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val modeLabel = if (analysisMode == AnalysisMode.FULL)
                        strings.repoAnalysisModeFull else strings.repoAnalysisModeIncremental
                    val summary = buildString {
                        append(modeLabel)
                        append(" · ${timeoutMsText}ms · ${maxParseSizeMbText}MB")
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) strings.repoAnalysisConfigCollapse
                                     else strings.repoAnalysisConfigExpand,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                ConfigContent(
                    timeoutMsText = timeoutMsText,
                    onTimeoutMsTextChange = onTimeoutMsTextChange,
                    maxParseSizeMbText = maxParseSizeMbText,
                    onMaxParseSizeMbTextChange = onMaxParseSizeMbTextChange,
                    maxRegexSizeMbText = maxRegexSizeMbText,
                    onMaxRegexSizeMbTextChange = onMaxRegexSizeMbTextChange,
                    analysisMode = analysisMode,
                    onAnalysisModeChange = onAnalysisModeChange
                )
            }
        }
    }
}

@Composable
private fun ConfigContent(
    timeoutMsText: String,
    onTimeoutMsTextChange: (String) -> Unit,
    maxParseSizeMbText: String,
    onMaxParseSizeMbTextChange: (String) -> Unit,
    maxRegexSizeMbText: String,
    onMaxRegexSizeMbTextChange: (String) -> Unit,
    analysisMode: AnalysisMode,
    onAnalysisModeChange: (AnalysisMode) -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strings.repoAnalysisModeSection,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnalysisMode.entries.forEach { mode ->
                    val modeLabel = if (mode == AnalysisMode.FULL)
                        strings.repoAnalysisModeFull else strings.repoAnalysisModeIncremental
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = analysisMode == mode,
                            onClick = { onAnalysisModeChange(mode) }
                        )
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = when (analysisMode) {
                    AnalysisMode.FULL -> strings.repoAnalysisModeFullDesc
                    AnalysisMode.INCREMENTAL -> strings.repoAnalysisModeIncrementalDesc
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.repoParseConfigSection,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = strings.repoParseConfigHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            NumberField(
                value = timeoutMsText,
                onValueChange = onTimeoutMsTextChange,
                label = strings.repoFileTimeoutLabel
            )

            Spacer(modifier = Modifier.height(8.dp))

            NumberField(
                value = maxParseSizeMbText,
                onValueChange = onMaxParseSizeMbTextChange,
                label = strings.repoMaxParseSizeLabel
            )

            Spacer(modifier = Modifier.height(8.dp))

            NumberField(
                value = maxRegexSizeMbText,
                onValueChange = onMaxRegexSizeMbTextChange,
                label = strings.repoMaxRegexSizeLabel
            )
        }
    }
}

// ==================== 分析中视图 ====================

@Composable
private fun AnalyzingView(strings: com.github.semanticgit.ui.Strings) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val phase = when {
        elapsedSeconds < 5 -> strings.repoAnalyzingPhase1
        elapsedSeconds < 20 -> strings.repoAnalyzingPhase2
        elapsedSeconds < 60 -> strings.repoAnalyzingPhase3
        else -> strings.repoAnalyzingPhase4
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(320.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = strings.repoAnalyzing,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = phase,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = strings.repoElapsedTime.replace("{0}", elapsedSeconds.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = strings.repoWaitPatiently,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ==================== 数字输入框 ====================

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

// ==================== 统计结果 + 图表 ====================

@Composable
private fun StatisticsContent(
    strings: com.github.semanticgit.ui.Strings,
    statistics: SimpleEntityChangeStatistics?
) {
    if (statistics == null) return

    val themeMode = LocalThemeMode.current
    val isDark = themeMode == ThemeMode.Dark

    val operationOptionJson = remember(statistics, strings, isDark) {
        buildRoseChartOption(
            title = strings.repoOperationDistribution,
            dataMap = statistics.operationFloatMap ?: emptyMap(),
            entries = ChangeOperation.entries.toList(),
            nameExtractor = { it.desc },
            darkMode = isDark
        )
    }

    val natureOptionJson = remember(statistics, strings, isDark) {
        buildRoseChartOption(
            title = strings.repoNatureDistribution,
            dataMap = statistics.natureFlagFloatMap ?: emptyMap(),
            entries = ChangeNatureFlag.entries.toList(),
            nameExtractor = { it.name.lowercase() },
            darkMode = isDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "${strings.repoAnalysisStatus}: ${if (statistics.isSuccess) "OK" else "FAILED"}",
            style = MaterialTheme.typography.titleMedium,
            color = if (statistics.isSuccess) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${strings.repoTotalCommits}: ${statistics.totalCommits}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
            visible = operationOptionJson != null,
            enter = fadeIn(animationSpec = tween(400))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EChartsView(
                    optionJson = operationOptionJson,
                    darkMode = isDark,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                EChartsView(
                    optionJson = natureOptionJson,
                    darkMode = isDark,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ==================== 图表 Option 构建 ====================

private fun <T> buildRoseChartOption(
    title: String,
    dataMap: Map<T, Float>,
    entries: List<T>,
    nameExtractor: (T) -> String,
    darkMode: Boolean = false
): String {
    val dataItems = entries.joinToString(",") { entry ->
        val value = ((dataMap[entry] ?: 0f) * 100).toInt()
        """{"value":$value,"name":"${nameExtractor(entry)}"}"""
    }
    val legendItems = entries.joinToString(",") { """"${nameExtractor(it)}"""" }

    val titleColor = if (darkMode) "#E2E2E6" else "#1A1C1E"
    val legendColor = if (darkMode) "#C3C7CF" else "#43474E"

    return """
        {
            "title": {
                "text": "$title",
                "left": "center",
                "textStyle": { "color": "$titleColor" }
            },
            "tooltip": { "trigger": "item", "formatter": "{b} : {d}%" },
            "legend": {
                "left": "center",
                "top": "bottom",
                "textStyle": { "color": "$legendColor" },
                "data": [$legendItems]
            },
            "animationDuration": 800,
            "animationEasing": "cubicOut",
            "series": [{
                "type": "pie",
                "radius": [20, 140],
                "roseType": "radius",
                "itemStyle": { "borderRadius": 5 },
                "label": { "show": false },
                "emphasis": { "label": { "show": true } },
                "animationType": "scale",
                "animationEasing": "elasticOut",
                "animationDelay": "function (idx) { return idx * 100; }",
                "data": [$dataItems]
            }]
        }
    """.trimIndent()
}

// ==================== 分析结果 ====================

sealed class AnalysisResult {
    data class Success(val statistics: SimpleEntityChangeStatistics) : AnalysisResult()
    data class Error(val message: String) : AnalysisResult()
}

// ==================== 核心分析逻辑 ====================

private suspend fun executeCoreAnalysis(
    repoPath: String,
    mode: AnalysisMode = AnalysisMode.FULL,
    settings: ParserSettings? = null
): AnalysisResult {
    return withContext(Dispatchers.IO) {
        try {
            val dbDir = "${System.getProperty("user.home", "")}/.semanticgit/db"
            val engine = AnalysisEngine()
            val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())
            val dbFile = java.io.File(dbDir, "$dbName.db")

            settings?.let { applyParserConfigs(it) }

            when (mode) {
                AnalysisMode.FULL -> {
                    if (dbFile.exists()) dbFile.delete()
                    val future = engine.fullAnalysisAsync(
                        repoPath, dbDir, dbName, {}, { e -> e.printStackTrace(); null }
                    )
                    future.join()
                }
                AnalysisMode.INCREMENTAL -> {
                    if (dbFile.exists()) {
                        val future = engine.incrementalAnalysisAsync(
                            repoPath, dbFile, {}, { e -> e.printStackTrace(); null }
                        )
                        future.join()
                    } else {
                        val future = engine.fullAnalysisAsync(
                            repoPath, dbDir, dbName, {}, { e -> e.printStackTrace(); null }
                        )
                        future.join()
                    }
                }
            }

            DatabaseManager(dbDir, dbName, false).use { dbManager ->
                val provider = StatisticsProvider(dbManager)
                val stats = provider.simpleEntityChangeStatistics
                if (stats != null && stats.isSuccess) {
                    AnalysisResult.Success(stats)
                } else {
                    AnalysisResult.Error("Failed to retrieve statistics")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AnalysisResult.Error(e.message ?: "Unknown error during analysis")
        }
    }
}

private fun applyParserConfigs(settings: ParserSettings) {
    try {
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_TIMEOUT,
            ConfigItems.LONG(settings.timeoutMs).build()
        )
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE,
            ConfigItems.LONG(settings.maxParseSizeMb * 1024L * 1024L).build()
        )
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE,
            ConfigItems.LONG(settings.maxRegexSizeMb * 1024L * 1024L).build()
        )
        println("[Config Applied] timeout=${settings.timeoutMs}ms, maxParse=${settings.maxParseSizeMb}MB, maxRegex=${settings.maxRegexSizeMb}MB")
    } catch (e: Exception) {
        System.err.println("[Warning] Failed to apply parser configs: ${e.message}")
    }
}

// ==================== 路径显示名 ====================

internal fun computeDisplayNames(paths: List<String>): List<String> {
    if (paths.isEmpty()) return emptyList()
    val parts = paths.map { path ->
        path.trimEnd('\\', '/').split(File.separatorChar).filter { it.isNotEmpty() }
    }
    val depths = IntArray(paths.size) { 0 }
    fun displayFor(i: Int): String {
        val p = parts[i]
        val depth = depths[i]
        if (depth == 0) return p.last()
        val parentPart = p.subList(p.size - 1 - depth, p.size - 1).joinToString(File.separator)
        return "${p.last()} ($parentPart)"
    }
    var changed = true
    while (changed) {
        changed = false
        val displays = depths.indices.map { displayFor(it) }
        val counts = displays.groupingBy { it }.eachCount()
        for (i in depths.indices) {
            if (counts[displays[i]]!! > 1 && depths[i] < parts[i].size - 1) {
                depths[i]++
                changed = true
            }
        }
    }
    return depths.indices.map { displayFor(it) }
}