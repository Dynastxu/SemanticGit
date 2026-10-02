package com.github.semanticgit.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.semanticgit.common.entity.ChangeLog
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.EntityChangeHistory
import com.github.semanticgit.ui.LocalStrings
import com.github.semanticgit.ui.LocalThemeMode
import com.github.semanticgit.ui.ThemeMode
import com.github.semanticgit.ui.chart.EChartsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 实体变更分析页面
 * 
 * 功能：
 * 1. 搜索框 - 输入实体全限定名进行搜索
 * 2. 分支选择下拉列表 - 选择 Git 分支/Ref
 * 3. 变更历史显示 - 展示实体的完整变更记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityAnalysis(
    modifier: Modifier = Modifier,
    repoPaths: List<String>,
    selectedRepoIndex: Int
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val selectedPath = repoPaths.getOrNull(selectedRepoIndex)

    // ============ UI 状态 ============
    var searchQuery by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf("") }
    var branchDropdownExpanded by remember { mutableStateOf(false) }
    
    // 分析状态
    var isLoading by remember { mutableStateOf(false) }
    var entityHistory by remember { mutableStateOf<EntityChangeHistory?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var customDbFile by remember { mutableStateOf<File?>(null) }

    // 分支列表（从 Git 仓库动态获取）
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(customDbFile) {
        if (customDbFile != null && branches.isEmpty()) {
            selectedBranch = "refs/heads/main"
        }
    }

    // 实体搜索自动补全
    var allEntities by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchDropdownExpanded by remember { mutableStateOf(false) }

    // 仓库切换时加载分支和实体列表
    LaunchedEffect(selectedPath) {
        if (selectedPath != null) {
            val gitService = com.github.semanticgit.git.service.GitService(selectedPath)
            branches = try {
                gitService.getBranches().map { "refs/heads/$it" }
            } catch (e: Exception) {
                listOf("refs/heads/main")
            }
            val currentBranch = try {
                gitService.getCurrentBranch()
            } catch (e: Exception) {
                null
            }
            val currentBranchRef = if (currentBranch != null) "refs/heads/$currentBranch" else null
            selectedBranch = if (currentBranchRef != null && currentBranchRef in branches)
                currentBranchRef
            else
                branches.firstOrNull() ?: ""
            allEntities = searchAllEntities(selectedPath)
        } else {
            branches = emptyList()
            selectedBranch = ""
            allEntities = emptyList()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ============ 标题 ============
        Text(
            text = strings.entityAnalysis,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

        // ============ 当前仓库/数据库信息 ============
        if (selectedPath != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${strings.currentRepository}: ${File(selectedPath).name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val chooser = javax.swing.JFileChooser()
                            chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                            chooser.dialogTitle = strings.selectDbFile
                            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Database files (*.db)", "db")
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                customDbFile = file
                                entityHistory = null
                                errorMessage = null
                                scope.launch {
                                    allEntities = searchAllEntitiesFromDb(file)
                                }
                            }
                        }
                    ) {
                        Text(strings.loadDbFile)
                    }
                }
            }
        } else if (customDbFile != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.loadFromDb}: ${customDbFile!!.name}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = customDbFile!!.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val chooser = javax.swing.JFileChooser()
                            chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                            chooser.dialogTitle = strings.selectDbFile
                            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Database files (*.db)", "db")
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                customDbFile = file
                                entityHistory = null
                                errorMessage = null
                                scope.launch {
                                    allEntities = searchAllEntitiesFromDb(file)
                                }
                            }
                        }
                    ) {
                        Text(strings.loadDbFile)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.noRepositorySelected,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val chooser = javax.swing.JFileChooser()
                            chooser.fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
                            chooser.dialogTitle = strings.selectDbFile
                            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Database files (*.db)", "db")
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                customDbFile = file
                                entityHistory = null
                                errorMessage = null
                                scope.launch {
                                    allEntities = searchAllEntitiesFromDb(file)
                                }
                            }
                        }
                    ) {
                        Text(strings.loadDbFile)
                    }
                }
            }
            // 允许仅加载 DB 文件，不强制要求仓库
        }

        // ============ 搜索框（带自动补全） ============
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    searchQuery = newValue
                    searchDropdownExpanded = newValue.isNotBlank()
                            && allEntities.isNotEmpty()
                },
                label = { Text(strings.searchEntity) },
                placeholder = { Text("com.example.MyClass#myMethod()") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 自动补全下拉列表（锚定在搜索框正下方）
            if (searchDropdownExpanded) {
                val filtered = allEntities
                    .filter { it.contains(searchQuery, ignoreCase = true) }
                    .sortedBy { it.indexOf(searchQuery, ignoreCase = true) }
                    .take(20)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = strings.noMatchingEntities,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn {
                            items(filtered) { entity ->
                                Surface(
                                    onClick = {
                                        searchQuery = entity
                                        searchDropdownExpanded = false
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = entity,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============ 分支选择下拉列表 ============
        ExposedDropdownMenuBox(
            expanded = branchDropdownExpanded,
            onExpandedChange = { branchDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedBranch.ifEmpty { strings.selectBranch },
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.selectBranch) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = branchDropdownExpanded,
                onDismissRequest = { branchDropdownExpanded = false }
            ) {
                branches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch) },
                        onClick = {
                            selectedBranch = branch
                            branchDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // ============ 操作按钮行 ============
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    if (searchQuery.isBlank()) {
                        errorMessage = strings.pleaseInputEntityName
                        return@Button
                    }
                    if (selectedBranch.isBlank()) {
                        errorMessage = strings.pleaseSelectBranch
                        return@Button
                    }

                    val dbFileToUse = customDbFile
                    scope.launch {
                        queryEntityChangeHistory(
                            repoPath = selectedPath,
                            dbFile = dbFileToUse,
                            entityName = searchQuery,
                            refName = selectedBranch,
                            onLoading = { isLoading = it },
                            onSuccess = { 
                                entityHistory = it
                                errorMessage = null 
                            },
                            onError = { 
                                errorMessage = it
                                entityHistory = null
                            }
                        )
                    }
                },
                enabled = !isLoading && searchQuery.isNotBlank() && selectedBranch.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) strings.querying else strings.startQuery)
            }

            OutlinedButton(
                onClick = {
                    searchQuery = ""
                    selectedBranch = ""
                    entityHistory = null
                    errorMessage = null
                    searchDropdownExpanded = false
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.reset)
            }
        }

        // ============ 错误提示 ============
        errorMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // ============ 结果展示区域 ============
        if (entityHistory != null && !entityHistory!!.changes.isEmpty()) {
            val changes = entityHistory!!.changes
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntityChangeStatisticsCharts(
                    entityName = searchQuery,
                    changes = changes
                )

                HorizontalDivider()

                EntityChangeHistoryCard(
                    entityName = searchQuery,
                    refName = selectedBranch,
                    changes = changes
                )
            }
        } else if (entityHistory != null && entityHistory!!.changes.isEmpty() && !isLoading) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings.noEntityHistoryFound,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 实体变更统计图表
 *
 * 显示该实体的变更操作分布和变更性质分布图表
 */
@Composable
private fun EntityChangeStatisticsCharts(
    entityName: String,
    changes: List<ChangeLog>
) {
    val strings = LocalStrings.current
    val themeMode = LocalThemeMode.current
    val isDark = themeMode == ThemeMode.Dark

    val operationMap = remember(changes) {
        val map = mutableMapOf<ChangeOperation, Float>()
        val total = changes.size.toFloat()
        if (total > 0) {
            changes.groupingBy { it.operation }.eachCount().forEach { (op, count) ->
                map[op] = count / total
            }
        }
        map
    }

    val natureMap = remember(changes) {
        val map = mutableMapOf<ChangeNatureFlag, Float>()
        val total = changes.size.toFloat()
        if (total > 0) {
            for (change in changes) {
                val flags = ChangeNatureFlag.fromCode(change.natureFlagCode)
                for (flag in flags) {
                    map[flag] = (map[flag] ?: 0f) + (1f / total)
                }
            }
        }
        map
    }

    val operationOptionJson = remember(operationMap, strings, isDark) {
        buildRoseChartOption(
            title = strings.entityOperationDistribution,
            dataMap = operationMap,
            entries = ChangeOperation.entries.toList(),
            nameExtractor = { it.desc },
            darkMode = isDark
        )
    }

    val natureOptionJson = remember(natureMap, strings, isDark) {
        buildRoseChartOption(
            title = strings.entityNatureDistribution,
            dataMap = natureMap,
            entries = ChangeNatureFlag.entries.toList(),
            nameExtractor = { it.name.lowercase() },
            darkMode = isDark
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "${strings.entityAnalysis}: $entityName",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${strings.totalChanges}: ${changes.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EChartsView(
                    optionJson = operationOptionJson,
                    darkMode = isDark,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                EChartsView(
                    optionJson = natureOptionJson,
                    darkMode = isDark,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}

/**
 * 构建玫瑰图 Option JSON（与 RepoPage 共用逻辑）
 */
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

/**
 * 实体变更历史卡片
 */
@Composable
private fun EntityChangeHistoryCard(
    entityName: String,
    refName: String,
    changes: List<ChangeLog>
) {
    val strings = LocalStrings.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${strings.entityChangeHistory}:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = entityName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "${strings.totalChanges}: ${changes.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 变更列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(changes, key = { it.id }) { changeLog ->
                    ChangeLogItem(changeLog = changeLog)
                }
            }
        }
    }
}

/**
 * 单条变更日志项
 */
@Composable
private fun ChangeLogItem(changeLog: ChangeLog) {
    val shortHash = changeLog.commit.hash.take(7)
    val parentInfo = changeLog.parentEntity?.let { " ← ${it.name}" } ?: ""
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 第一行：提交哈希 + 操作类型 + 实体名
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 操作类型标签
                Surface(
                    color = when (changeLog.operation) {
                        ChangeOperation.ADD -> 
                            MaterialTheme.colorScheme.primaryContainer
                        ChangeOperation.REMOVE -> 
                            MaterialTheme.colorScheme.errorContainer
                        ChangeOperation.MODIFY -> 
                            MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = changeLog.operation.desc,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                Text(
                    text = "$shortHash  ${changeLog.entity.name} (${changeLog.entity.kind})$parentInfo",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 第二行：作者 + 时间
            Text(
                text = "${changeLog.commit.author.name} <${changeLog.commit.author.email}>  ${changeLog.commit.timestamp}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 第三行：文件路径
            Text(
                text = "📄 ${changeLog.filePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 第四行：提交消息
            Text(
                text = "💬 ${changeLog.commit.message.lines().first()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 查询实体变更历史（异步）
 *
 * 直接调用 core 模块的 StatisticsProvider.getEntityChangeHistory() 接口，
 * 无需反射，类型安全且与 RepoPage 保持一致。
 */
private suspend fun queryEntityChangeHistory(
    repoPath: String?,
    dbFile: File? = null,
    entityName: String,
    refName: String,
    onLoading: (Boolean) -> Unit,
    onSuccess: (EntityChangeHistory) -> Unit,
    onError: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        onLoading(true)

        val dbManager: DatabaseManager = if (dbFile != null && dbFile.exists()) {
            DatabaseManager(dbFile.parentFile?.absolutePath ?: ".", dbFile.nameWithoutExtension, false)
        } else {
            val repo = repoPath ?: throw IllegalStateException("No repo path or DB file provided")
            val dbDir = "${System.getProperty("user.home")}/.semanticgit/db"
            val dbName = Integer.toHexString(File(repo).absolutePath.hashCode())
            DatabaseManager(dbDir, dbName, false)
        }

        dbManager.use { dbManager ->
            val provider = StatisticsProvider(dbManager)
            val result = provider.getEntityChangeHistory(entityName, refName)
            onSuccess(result)
        }
    } catch (e: Exception) {
        onError("Failed to query entity history: ${e.message}")
        e.printStackTrace()
    } finally {
        onLoading(false)
    }
}

/**
 * 搜索所有实体（异步）
 *
 * 从数据库加载全部实体名，供客户端过滤匹配。
 */
private suspend fun searchAllEntities(repoPath: String): List<String> = withContext(Dispatchers.IO) {
    try {
        val dbDir = "${System.getProperty("user.home")}/.semanticgit/db"
        val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())

        DatabaseManager(dbDir, dbName, false).use { dbManager ->
            val provider = StatisticsProvider(dbManager)
            provider.getEntities().map { it.name }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * 从指定的 DB 文件加载全部实体名
 */
private suspend fun searchAllEntitiesFromDb(dbFile: File): List<String> = withContext(Dispatchers.IO) {
    try {
        val parentDir = dbFile.parentFile?.absolutePath ?: "."
        val dbName = dbFile.nameWithoutExtension

        DatabaseManager(parentDir, dbName, false).use { dbManager ->
            val provider = StatisticsProvider(dbManager)
            provider.getEntities().map { it.name }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}