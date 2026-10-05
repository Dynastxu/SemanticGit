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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.LinearProgressIndicator
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
import com.github.semanticgit.core.config.EngineConfigs
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics
import com.github.semanticgit.core.parser.ParserRegistry
import com.github.semanticgit.parser.java.JavaParser
import com.github.semanticgit.parser.java.api.LanguageParser
import com.github.semanticgit.ui.LocalStrings
import com.github.semanticgit.ui.LocalThemeMode
import com.github.semanticgit.ui.ThemeMode
import com.github.semanticgit.ui.view.chart.EChartsView
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

data class ParserSettings(
    val timeoutMs: Long = 5000L,
    val maxParseSizeMb: Long = 5L,
    val maxRegexSizeMb: Long = 2L,
    val maxDepth: Int = 1000,
    val maxQueue: Int = 64,
    val signatureMatchThreshold: Float = 0.7f,
    val javaLevel: String = "JAVA_25"
)

enum class AnalysisMode {
    FULL,
    INCREMENTAL
}

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
    // core
    var maxDepth by remember { mutableStateOf(EngineConfigs.getMaxDepth()) }
    var maxQueue by remember { mutableStateOf(EngineConfigs.getMaxQueue()) }
    var signatureMatchThreshold by remember { mutableStateOf(EngineConfigs.getSignatureMatchThreshold()) }
    // 分析器
    var timeoutMsText by remember { mutableStateOf((ParserRegistry.getConfig(LanguageParser.CONFIG_KEY_TIMEOUT).value as Long).toString()) }
    var maxParseSizeMbText by remember { mutableStateOf((ParserRegistry.getConfig(LanguageParser.CONFIG_KEY_MAX_PARSE_SIZE).value as Long).toString()) }
    var maxRegexSizeMbText by remember { mutableStateOf((ParserRegistry.getConfig(LanguageParser.CONFIG_KEY_MAX_REGEX_SIZE).value as Long).toString()) }
    // Java
    var javaLevel by remember { mutableStateOf((ParserRegistry.getConfig(JavaParser.CONFIG_KEY_JAVA_LANGUAGE_LEVEL).value as Enum<*>).name) }

    var analysisMode by remember { mutableStateOf(AnalysisMode.FULL) }

    // ============ 分析状态 ============
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf(0f) }
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
        maxRegexSizeMb = maxRegexSizeMbText.toLongOrNull() ?: 2L,
        maxDepth = maxDepth,
        maxQueue = maxQueue,
        signatureMatchThreshold = signatureMatchThreshold,
        javaLevel = javaLevel
    )

    fun startAnalysis(mode: AnalysisMode = analysisMode) {
        val path = selectedPath ?: return
        scope.launch {
            isAnalyzing = true
            analysisProgress = 0f
            errorMessage = null
            statistics = null
            try {
                val settings = buildSettings()
                val result = executeCoreAnalysis(path, mode, settings) { progress ->
                    analysisProgress = progress
                }
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
                logger.error(e) { "executeCoreAnalysis failed" }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
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
            timeoutMsText = timeoutMsText,
            onTimeoutMsTextChange = { timeoutMsText = it },
            maxParseSizeMbText = maxParseSizeMbText,
            onMaxParseSizeMbTextChange = { maxParseSizeMbText = it },
            maxRegexSizeMbText = maxRegexSizeMbText,
            onMaxRegexSizeMbTextChange = { maxRegexSizeMbText = it },
            maxDepth = maxDepth,
            onMaxDepthChange = { maxDepth = it },
            maxQueue = maxQueue,
            onMaxQueueChange = { maxQueue = it },
            signatureMatchThreshold = signatureMatchThreshold,
            onSignatureMatchThresholdChange = { signatureMatchThreshold = it },
            javaLevel = javaLevel,
            onJavaLevelChange = { javaLevel = it },
            analysisMode = analysisMode,
            onAnalysisModeChange = { analysisMode = it },
            selectedPath = selectedPath,
            isAnalyzing = isAnalyzing,
            configChanged = configChanged,
            statistics = statistics,
            onStartAnalysis = { startAnalysis() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ============ 中间：可滚动内容区 ============
        val currentErrorMessage = errorMessage
        val currentStatistics = statistics

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
        ) {
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
                    AnalyzingView(strings = strings, progress = analysisProgress)
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
    timeoutMsText: String,
    onTimeoutMsTextChange: (String) -> Unit,
    maxParseSizeMbText: String,
    onMaxParseSizeMbTextChange: (String) -> Unit,
    maxRegexSizeMbText: String,
    onMaxRegexSizeMbTextChange: (String) -> Unit,
    maxDepth: Int,
    onMaxDepthChange: (Int) -> Unit,
    maxQueue: Int,
    onMaxQueueChange: (Int) -> Unit,
    signatureMatchThreshold: Float,
    onSignatureMatchThresholdChange: (Float) -> Unit,
    javaLevel: String,
    onJavaLevelChange: (String) -> Unit,
    analysisMode: AnalysisMode,
    onAnalysisModeChange: (AnalysisMode) -> Unit,
    selectedPath: String?,
    isAnalyzing: Boolean,
    configChanged: Boolean,
    statistics: SimpleEntityChangeStatistics?,
    onStartAnalysis: () -> Unit
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
                    maxDepth = maxDepth,
                    onMaxDepthChange = onMaxDepthChange,
                    maxQueue = maxQueue,
                    onMaxQueueChange = onMaxQueueChange,
                    signatureMatchThreshold = signatureMatchThreshold,
                    onSignatureMatchThresholdChange = onSignatureMatchThresholdChange,
                    javaLevel = javaLevel,
                    onJavaLevelChange = onJavaLevelChange,
                    analysisMode = analysisMode,
                    onAnalysisModeChange = onAnalysisModeChange
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onStartAnalysis,
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
    maxDepth: Int,
    onMaxDepthChange: (Int) -> Unit,
    maxQueue: Int,
    onMaxQueueChange: (Int) -> Unit,
    signatureMatchThreshold: Float,
    onSignatureMatchThresholdChange: (Float) -> Unit,
    javaLevel: String,
    onJavaLevelChange: (String) -> Unit,
    analysisMode: AnalysisMode,
    onAnalysisModeChange: (AnalysisMode) -> Unit
) {
    val strings = LocalStrings.current
    var maxDepthText by remember(maxDepth) { mutableStateOf(maxDepth.toString()) }
    var maxQueueText by remember(maxQueue) { mutableStateOf(maxQueue.toString()) }
    var thresholdText by remember(signatureMatchThreshold) { mutableStateOf(signatureMatchThreshold.toString()) }

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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = timeoutMsText,
                    onValueChange = onTimeoutMsTextChange,
                    label = strings.repoFileTimeoutLabel,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = maxParseSizeMbText,
                    onValueChange = onMaxParseSizeMbTextChange,
                    label = strings.repoMaxParseSizeLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = maxRegexSizeMbText,
                    onValueChange = onMaxRegexSizeMbTextChange,
                    label = strings.repoMaxRegexSizeLabel,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = javaLevel,
                    onValueChange = onJavaLevelChange,
                    label = { Text("Java 语言级别") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "引擎配置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = maxDepthText,
                    onValueChange = { newValue ->
                        maxDepthText = newValue
                        newValue.toIntOrNull()?.let { onMaxDepthChange(it) }
                    },
                    label = "最大遍历深度",
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = maxQueueText,
                    onValueChange = { newValue ->
                        maxQueueText = newValue
                        newValue.toIntOrNull()?.let { onMaxQueueChange(it) }
                    },
                    label = "最大队列大小",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatField(
                    value = thresholdText,
                    onValueChange = { newValue ->
                        thresholdText = newValue
                        newValue.toFloatOrNull()?.let { onSignatureMatchThresholdChange(it) }
                    },
                    label = "签名匹配阈值",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ==================== 分析中视图 ====================

@Composable
private fun AnalyzingView(
    strings: com.github.semanticgit.ui.Strings,
    progress: Float = 0f
) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L.milliseconds)
            elapsedSeconds++
        }
    }

    val phase = when {
        progress < 0.05f -> strings.repoAnalyzingPhase1
        progress < 0.3f -> strings.repoAnalyzingPhase2
        progress < 0.9f -> strings.repoAnalyzingPhase3
        else -> strings.repoAnalyzingPhase4
    }

    val estimatedTotal = if (progress > 0.01f) (elapsedSeconds / progress).toInt() else 0
    val estimatedRemaining = if (estimatedTotal > elapsedSeconds) estimatedTotal - elapsedSeconds else 0

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(360.dp)
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
            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${strings.elapsedTime}：${elapsedSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress > 0.01f) {
                    Text(
                        text = "${"%.0f".format(progress * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (estimatedRemaining > 0) {
                    Text(
                        text = "${strings.estimatedRemaining}：~${estimatedRemaining}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
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
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

// ==================== 浮点数输入框 ====================

@Composable
private fun FloatField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
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

    Column(modifier = Modifier.fillMaxWidth()) {
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
            visible = true,
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
            "backgroundColor": "transparent",
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
    settings: ParserSettings? = null,
    onProgress: (Float) -> Unit = {}
): AnalysisResult {
    return withContext(Dispatchers.IO) {
        try {
            val dbDir = "${System.getProperty("user.home", "")}/.semanticgit/db"
            val engine = AnalysisEngine()
            val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())
            val dbFile = File(dbDir, "$dbName.db")

            settings?.let { applyParserConfigs(it) }

            when (mode) {
                AnalysisMode.FULL -> {
                    if (dbFile.exists()) dbFile.delete()
                    val future = engine.fullAnalysisAsync(
                        repoPath, dbDir, dbName, onProgress
                    ) { e -> logger.error(e) { "fullAnalysis failed" }; null }
                    future.join()
                }
                AnalysisMode.INCREMENTAL -> {
                    if (dbFile.exists()) {
                        val future = engine.incrementalAnalysisAsync(
                            repoPath, dbFile, onProgress
                        ) { e -> logger.error(e) { "incrementalAnalysis failed" }; null }
                        future.join()
                    } else {
                        val future = engine.fullAnalysisAsync(
                            repoPath, dbDir, dbName, onProgress
                        ) { e -> logger.error(e) { "fullAnalysis failed" }; null }
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
            logger.error(e) { "executeCoreAnalysis failed" }
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
        EngineConfigs.setMaxDepth(settings.maxDepth)
        EngineConfigs.setMaxQueue(settings.maxQueue)
        EngineConfigs.setSignatureMatchThreshold(settings.signatureMatchThreshold)
        logger.info {
            "[Config Applied] timeout=${settings.timeoutMs}ms, maxParse=${settings.maxParseSizeMb}MB, maxRegex=${settings.maxRegexSizeMb}MB, " +
                    "maxDepth=${settings.maxDepth}, maxQueue=${settings.maxQueue}, threshold=${settings.signatureMatchThreshold}, javaLevel=${settings.javaLevel}"
        }
    } catch (e: Exception) {
        logger.error(e) { "[Warning] Failed to apply parser configs: ${e.message}" }
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
