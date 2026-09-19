package com.github.semanticgit.ui.page

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.github.semanticgit.ui.chart.EChartsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/**
 * 解析器配置
 */
data class ParserSettings(
    val timeoutMs: Long = 5000L,
    val maxParseSizeMb: Long = 5L,
    val maxRegexSizeMb: Long = 2L
) {
    val maxParseSizeBytes: Long get() = maxParseSizeMb * 1024 * 1024
    val maxRegexSizeBytes: Long get() = maxRegexSizeMb * 1024 * 1024
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPage(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val repoPaths = remember { mutableStateListOf<String>() }
    var selectedIndex by remember { mutableStateOf(-1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val displayNames = remember(repoPaths.toList()) {
        computeDisplayNames(repoPaths.toList())
    }

    // ============ 配置状态 ============
    var timeoutMsText by remember { mutableStateOf("5000") }
    var maxParseSizeMbText by remember { mutableStateOf("5") }
    var maxRegexSizeMbText by remember { mutableStateOf("2") }

    // ============ 分析状态 ============
    var isAnalyzing by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statistics by remember { mutableStateOf<SimpleEntityChangeStatistics?>(null) }

    val selectedPath = repoPaths.getOrNull(selectedIndex)

    fun buildSettings(): ParserSettings = ParserSettings(
        timeoutMs = timeoutMsText.toLongOrNull() ?: 5000L,
        maxParseSizeMb = maxParseSizeMbText.toLongOrNull() ?: 5L,
        maxRegexSizeMb = maxRegexSizeMbText.toLongOrNull() ?: 2L
    )

    fun startAnalysis(forceReanalyze: Boolean = false) {
        val path = selectedPath ?: return
        
        scope.launch {
            isAnalyzing = true
            showResult = true
            errorMessage = null
            statistics = null

            try {
                // ✅ 构建配置并传递给核心分析函数
                val settings = buildSettings()
                val result = executeCoreAnalysis(path, forceReanalyze, settings)
                
                when (result) {
                    is AnalysisResult.Success -> {
                        statistics = result.statistics
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
    
    fun forceReanalysis() {
        startAnalysis(forceReanalyze = true)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        // ============ 顶部：仓库选择 ============
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (selectedIndex in repoPaths.indices) displayNames[selectedIndex] else "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text(strings.repoSelectPlaceholder) },
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
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    repoPaths.forEachIndexed { index, _ ->
                        DropdownMenuItem(
                            text = { Text(displayNames[index]) },
                            onClick = {
                                selectedIndex = index
                                dropdownExpanded = false
                                showResult = false
                                statistics = null       // 切仓库时清空
                                errorMessage = null
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            IconButton(onClick = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = strings.repoSelectFolder
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val newPath = chooser.selectedFile.absolutePath
                    val existingIndex = repoPaths.indexOf(newPath)
                    if (existingIndex >= 0) {
                        selectedIndex = existingIndex
                    } else {
                        repoPaths.add(newPath)
                        selectedIndex = repoPaths.size - 1
                    }
                    showResult = false
                    statistics = null       // 切仓库时清空
                    errorMessage = null
                }
            }) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = strings.repoSelectFolder,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

// ============ 主内容区 ============
        Box(modifier = Modifier.fillMaxSize()) {
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
                showResult -> {
                    // 结果视图：showResult = true 时才创建
                    // 从配置切回结果时重新创建，图表重新加载
                    ResultView(
                        strings = strings,
                        isAnalyzing = isAnalyzing,
                        errorMessage = errorMessage,
                        statistics = statistics,
                        onBack = {
                            showResult = false
                            // 清空 statistics，保证下次分析时图表重新创建
                            statistics = null
                            errorMessage = null
                        },
                        onReanalyze = { forceReanalysis() }
                    )
                }
                else -> {
                    // 配置视图
                    ConfigView(
                        timeoutMsText = timeoutMsText,
                        onTimeoutMsTextChange = { timeoutMsText = it },
                        maxParseSizeMbText = maxParseSizeMbText,
                        onMaxParseSizeMbTextChange = { maxParseSizeMbText = it },
                        maxRegexSizeMbText = maxRegexSizeMbText,
                        onMaxRegexSizeMbTextChange = { maxRegexSizeMbText = it },
                        onStartAnalysis = { startAnalysis() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigView(
    timeoutMsText: String,
    onTimeoutMsTextChange: (String) -> Unit,
    maxParseSizeMbText: String,
    onMaxParseSizeMbTextChange: (String) -> Unit,
    maxRegexSizeMbText: String,
    onMaxRegexSizeMbTextChange: (String) -> Unit,
    onStartAnalysis: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "解析配置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "这些配置会影响每个文件的解析策略。配置修改后点击「开始分析」生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            NumberField(
                value = timeoutMsText,
                onValueChange = onTimeoutMsTextChange,
                label = "单文件解析超时（毫秒）"
            )

            Spacer(modifier = Modifier.height(12.dp))

            NumberField(
                value = maxParseSizeMbText,
                onValueChange = onMaxParseSizeMbTextChange,
                label = "超过此大小走文件级兜底（MB）"
            )

            Spacer(modifier = Modifier.height(12.dp))

            NumberField(
                value = maxRegexSizeMbText,
                onValueChange = onMaxRegexSizeMbTextChange,
                label = "正则降级最大文件大小（MB）"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onStartAnalysis) {
                Text("开始分析")
            }
        }
    }
}

@Composable
private fun ResultView(
    strings: com.github.semanticgit.ui.Strings,
    isAnalyzing: Boolean,
    errorMessage: String?,
    statistics: SimpleEntityChangeStatistics?,
    onBack: () -> Unit,
    onReanalyze: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回配置")
                Spacer(modifier = Modifier.width(4.dp))
                Text("返回配置")
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onReanalyze) {
                Text("重新分析")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isAnalyzing -> {
                AnalyzingView(strings = strings)
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            statistics != null -> {
                StatisticsContent(strings = strings, statistics = statistics)
            }
        }
    }
}

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
        elapsedSeconds < 5 -> "正在读取仓库..."
        elapsedSeconds < 20 -> "正在解析文件..."
        elapsedSeconds < 60 -> "正在统计变更..."
        else -> "正在生成图表..."
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
                text = "已用时：${elapsedSeconds} 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "分析时间取决于仓库大小，请耐心等待",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

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

@Composable
private fun StatisticsContent(
    strings: com.github.semanticgit.ui.Strings,
    statistics: SimpleEntityChangeStatistics?
) {
    if (statistics == null) return

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

        Spacer(modifier = Modifier.height(8.dp))

        val operationOptionJson = remember(statistics, strings) {
            buildRoseChartOption(
                title = strings.repoOperationDistribution,
                dataMap = statistics.operationFloatMap ?: emptyMap(),
                entries = ChangeOperation.entries.toList(),
                nameExtractor = { it.desc }
            )
        }

        val natureOptionJson = remember(statistics, strings) {
            buildRoseChartOption(
                title = strings.repoNatureDistribution,
                dataMap = statistics.natureFlagFloatMap ?: emptyMap(),
                entries = ChangeNatureFlag.entries.toList(),
                nameExtractor = { it.name.lowercase() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EChartsView(
                optionJson = operationOptionJson,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            EChartsView(
                optionJson = natureOptionJson,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

private fun <T> buildRoseChartOption(
    title: String,
    dataMap: Map<T, Float>,
    entries: List<T>,
    nameExtractor: (T) -> String
): String {
    val dataItems = entries.joinToString(",") { entry ->
        val value = ((dataMap[entry] ?: 0f) * 100).toInt()
        """{"value":$value,"name":"${nameExtractor(entry)}"}"""
    }
    val legendItems = entries.joinToString(",") { """"${nameExtractor(it)}"""" }

    return """
        {
            "title": { "text": "$title", "left": "center" },
            "tooltip": { "trigger": "item", "formatter": "{b} : {d}%" },
            "legend": { "left": "center", "top": "bottom", "data": [$legendItems] },
            "series": [{
                "type": "pie",
                "radius": [20, 140],
                "roseType": "radius",
                "itemStyle": { "borderRadius": 5 },
                "label": { "show": false },
                "emphasis": { "label": { "show": true } },
                "data": [$dataItems]
            }]
        }
    """.trimIndent()
}

/**
 * 分析结果密封类 - 强制处理所有情况
 */
sealed class AnalysisResult {
    data class Success(val statistics: SimpleEntityChangeStatistics) : AnalysisResult()
    data class Error(val message: String) : AnalysisResult()
}

/**
 * 核心分析逻辑 - 直接调用 core 模块
 *
 * 职责：
 * 1. 调用 AnalysisEngine 执行分析（如需要）
 * 2. 通过 StatisticsProvider 查询结果
 * 3. 返回统一的结果类型
 *
 * 特点：
 * - 纯业务逻辑，无 UI 依赖
 * - 可独立测试（通过 mock core 模块）
 * - 异常安全，返回 Error 而非抛出异常
 */
private suspend fun executeCoreAnalysis(
    repoPath: String, 
    forceReanalyze: Boolean = false,
    settings: ParserSettings? = null  // ← 新增配置参数
): AnalysisResult {
    return withContext(Dispatchers.IO) {
        try {
            val dbDir = "${System.getProperty("user.home")}/.semanticgit/db"
            val engine = AnalysisEngine()
            val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())

            // ✅ 关键：分析前配置 ParserRegistry
            settings?.let { applyParserConfigs(it) }

            // 判断是否需要执行全量分析
            val needAnalyze = forceReanalyze || !engine.isDatabaseExists(repoPath, dbDir)
            
            if (needAnalyze) {
                if (forceReanalyze) {
                    // 强制重分析：删除旧数据库
                    val dbFile = java.io.File(dbDir, "$dbName.db")
                    if (dbFile.exists()) {
                        dbFile.delete()
                    }
                }
                
                val future = engine.fullAnalysisAsync(
                    repoPath,
                    dbDir,
                    dbName,
                    { /* 进度回调 */ },
                    { e ->
                        e.printStackTrace()
                        null
                    }
                )
                future.join()  // 等待分析完成
            }

            // 查询统计结果
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

/**
 * 将 UI 配置应用到 ParserRegistry 全局配置中心
 *
 * 这是连接 UI 层和 Core 模块的桥梁：
 * - UI 层收集用户输入的配置参数
 * - 通过此函数写入 ParserRegistry 的全局 configMap
 * - AnalysisEngine 内部通过 ParserRegistry.getParserInstance() 读取这些配置
 *
 * @param settings 用户在 UI 上配置的解析器参数
 */
private fun applyParserConfigs(settings: ParserSettings) {
    try {
        // 设置超时时间（毫秒）
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_TIMEOUT,
            ConfigItems.LONG(settings.timeoutMs).build()
        )

        // 设置最大 AST 解析文件大小（字节）
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE,
            ConfigItems.LONG(settings.maxParseSizeMb * 1024L * 1024L).build()
        )

        // 设置最大正则解析文件大小（字节）
        ParserRegistry.setConfig(
            LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE,
            ConfigItems.LONG(settings.maxRegexSizeMb * 1024L * 1024L).build()
        )
        
        println("[Config Applied] timeout=${settings.timeoutMs}ms, maxParse=${settings.maxParseSizeMb}MB, maxRegex=${settings.maxRegexSizeMb}MB")
    } catch (e: Exception) {
        System.err.println("[Warning] Failed to apply parser configs: ${e.message}")
    }
}

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
        val parentStart = p.size - 1 - depth
        val parentPart = p.subList(parentStart, p.size - 1).joinToString(File.separator)
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