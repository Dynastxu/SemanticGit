package com.github.semanticgit.ui.page

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.semanticgit.common.entity.ChangeLog
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.dao.ChangeLogDao
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.EntityChangeHistory
import com.github.semanticgit.core.dto.HistoryEdge
import com.github.semanticgit.core.dto.HistoryNode
import com.github.semanticgit.ui.LocalStrings
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

    // 遍历模式：DFS / BFS
    var traversalMode by remember { mutableStateOf(ChangeLogDao.TraversalMode.DFS) }
    var traversalDropdownExpanded by remember { mutableStateOf(false) }

    // 视图模式：DAG 图 / 扁平列表
    var showDagView by remember { mutableStateOf(true) }

    // 分支列表（从 Git 仓库动态获取）
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }

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
            selectedBranch = branches.firstOrNull() ?: ""
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

        // ============ 当前仓库信息 ============
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
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = strings.noRepositorySelected,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            return@Column
        }

        // ============ 查询工具栏 ============
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 搜索框（带自动补全）
            Column(modifier = Modifier.weight(50f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        searchDropdownExpanded = newValue.isNotBlank()
                                && allEntities.isNotEmpty()
                    },
                    placeholder = { Text(strings.searchEntityPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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

            // 分支选择
            ExposedDropdownMenuBox(
                expanded = branchDropdownExpanded,
                onExpandedChange = { branchDropdownExpanded = it },
                modifier = Modifier.weight(20f)
            ) {
                OutlinedTextField(
                    value = selectedBranch.ifEmpty { strings.selectBranch },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = branchDropdownExpanded
                        )
                    },
                    modifier = Modifier.menuAnchor(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                ExposedDropdownMenu(
                    expanded = branchDropdownExpanded,
                    onDismissRequest = { branchDropdownExpanded = false }
                ) {
                    branches.forEach { branch ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    branch,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                selectedBranch = branch
                                branchDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // 遍历模式
            ExposedDropdownMenuBox(
                expanded = traversalDropdownExpanded,
                onExpandedChange = { traversalDropdownExpanded = it },
                modifier = Modifier.weight(15f)
            ) {
                OutlinedTextField(
                    value = if (traversalMode == ChangeLogDao.TraversalMode.DFS) "DFS" else "BFS",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = traversalDropdownExpanded
                        )
                    },
                    modifier = Modifier.menuAnchor(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(
                    expanded = traversalDropdownExpanded,
                    onDismissRequest = { traversalDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                strings.traversalDfs,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            traversalMode = ChangeLogDao.TraversalMode.DFS
                            traversalDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                strings.traversalBfs,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            traversalMode = ChangeLogDao.TraversalMode.BFS
                            traversalDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }

            // 查询按钮
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

                    scope.launch {
                        queryEntityChangeHistory(
                            repoPath = selectedPath!!,
                            entityName = searchQuery,
                            refName = selectedBranch,
                            mode = traversalMode,
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
                modifier = Modifier.weight(10f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(if (isLoading) strings.querying else strings.startQuery)
            }

            // 重置按钮
            IconButton(
                onClick = {
                    searchQuery = ""
                    selectedBranch = ""
                    entityHistory = null
                    errorMessage = null
                    searchDropdownExpanded = false
                },
                modifier = Modifier.weight(5f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = strings.reset,
                    modifier = Modifier.size(18.dp)
                )
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
            EntityChangeHistoryCard(
                entityName = searchQuery,
                refName = selectedBranch,
                changes = entityHistory!!.changes,
                nodes = entityHistory!!.nodes,
                edges = entityHistory!!.edges,
                showDagView = showDagView,
                onToggleView = { showDagView = it }
            )
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
 * 实体变更历史卡片 — 支持 DAG 图谱视图和扁平列表视图切换
 */
@Composable
private fun EntityChangeHistoryCard(
    entityName: String,
    refName: String,
    changes: List<ChangeLog>,
    nodes: List<HistoryNode>,
    edges: List<HistoryEdge>,
    showDagView: Boolean,
    onToggleView: (Boolean) -> Unit
) {
    val strings = LocalStrings.current
    val hasNodes = nodes.isNotEmpty() && edges.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
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

            // 视图切换控件 + 节点数统计
            if (hasNodes) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = showDagView,
                            onClick = { onToggleView(true) },
                            label = { Text(strings.dagGraphView) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = !showDagView,
                            onClick = { onToggleView(false) },
                            label = { Text(strings.flatListView) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    Text(
                        text = "${nodes.size} nodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 内容区域
            if (showDagView && hasNodes) {
                DagGraphView(
                    nodes = nodes,
                    edges = edges,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 扁平列表视图
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
}

/**
 * DAG 图谱视图 — 使用 Canvas 绘制类似 git log --graph 的提交历史图谱
 *
 * - 实线 = FIRST_PARENT
 * - 虚线 = MERGE_PARENT
 * - merge commit 节点高亮（黄色实心圆 + [M] 标签）
 */
@Composable
private fun DagGraphView(
    nodes: List<HistoryNode>,
    edges: List<HistoryEdge>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val mergeColor = Color(0xFFF9A825)

    val rowHeight = 54.dp
    val laneWidth = 22.dp
    val nodeRadius = 5.dp
    val labelStart = 70.dp
    val textSize = 10.sp

    val sorted = nodes.sortedBy { it.depth }

    val nodeByHash = nodes.associateBy { it.commitHash }

    val validEdges = edges.filter { it.toHash != null && it.fromHash != null }
    val incomingEdges: Map<String, List<HistoryEdge>> = validEdges.groupBy { it.toHash }

    val laneByHash = mutableMapOf<String, Int>()
    var nextLane = 0

    for (node in sorted) {
        val incoming = incomingEdges[node.commitHash] ?: emptyList()
        if (incoming.isNotEmpty()) {
            val fromHash = incoming.first().fromHash
            val parentLane = laneByHash[fromHash]
            if (parentLane != null) {
                laneByHash[node.commitHash] = parentLane
            } else {
                laneByHash[node.commitHash] = nextLane++
            }
        } else {
            laneByHash[node.commitHash] = nextLane++
        }
    }

    val maxLane = laneByHash.values.maxOrNull() ?: 0
    val totalWidth = labelStart + 300.dp

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .horizontalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.width(totalWidth).height(rowHeight * sorted.size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rowH = rowHeight.toPx()
                val laneW = laneWidth.toPx()
                val nodeR = nodeRadius.toPx()
                val labelX = labelStart.toPx()
                val centerYOffset = rowH * 0.55f

                // 1. 画边
                for (edge in validEdges) {
                    val fromNode = nodeByHash[edge.fromHash] ?: continue
                    val toNode = nodeByHash[edge.toHash] ?: continue
                    val fromIdx = sorted.indexOfFirst { it.commitHash == fromNode.commitHash }
                    val toIdx = sorted.indexOfFirst { it.commitHash == toNode.commitHash }
                    if (fromIdx < 0 || toIdx < 0) continue

                    val fromLane = laneByHash[edge.fromHash] ?: continue
                    val toLane = laneByHash[edge.toHash] ?: continue

                    val x1 = fromLane * laneW + laneW / 2
                    val y1 = fromIdx * rowH + centerYOffset
                    val x2 = toLane * laneW + laneW / 2
                    val y2 = toIdx * rowH + centerYOffset

                    if (edge.type == HistoryEdge.EdgeType.MERGE_PARENT) {
                        drawLine(
                            color = mergeColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    } else {
                        drawLine(
                            color = primaryColor,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.8f
                        )
                    }
                }

                // 2. 画节点圆 + 标签
                for ((idx, node) in sorted.withIndex()) {
                    val lane = laneByHash[node.commitHash] ?: continue
                    val cx = lane * laneW + laneW / 2
                    val cy = idx * rowH + centerYOffset

                    val isMerge = node.isMergeCommit

                    drawCircle(
                        color = if (isMerge) mergeColor else primaryColor,
                        radius = if (isMerge) nodeR + 2f else nodeR,
                        center = Offset(cx, cy)
                    )

                    if (!isMerge) {
                        drawCircle(
                            color = Color.White,
                            radius = nodeR * 0.45f,
                            center = Offset(cx, cy)
                        )
                    }

                    val labelText = "${node.commitHash.take(7)} ${node.shortMessage}"
                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            fontSize = textSize,
                            color = onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(labelX, idx * rowH + (rowH - textLayout.size.height) / 2)
                    )

                    if (isMerge) {
                        val tagText = "[M]"
                        val tagLayout = textMeasurer.measure(
                            text = tagText,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = mergeColor
                            )
                        )
                        drawText(
                            textLayoutResult = tagLayout,
                            topLeft = Offset(
                                labelX + textLayout.size.width + 6f,
                                idx * rowH + (rowH - tagLayout.size.height) / 2
                            )
                        )
                    }
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
 */
private suspend fun queryEntityChangeHistory(
    repoPath: String,
    entityName: String,
    refName: String,
    mode: ChangeLogDao.TraversalMode,
    onLoading: (Boolean) -> Unit,
    onSuccess: (EntityChangeHistory) -> Unit,
    onError: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        onLoading(true)

        val dbDir = "${System.getProperty("user.home")}/.semanticgit/db"
        val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())

        DatabaseManager(dbDir, dbName, false).use { dbManager ->
            val provider = StatisticsProvider(dbManager)
            val result = provider.getEntityChangeHistory(entityName, refName, mode)
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
 * TODO: 待实现的接口 - 分支/Ref 列表服务
 * 
 * 功能：获取 Git 仓库的所有分支和 Ref
 * 用途：填充分支选择下拉列表
 * 
 * 示例实现位置：git 模块 或 core 模块
 * 
 * interface RefListService {
 *     /**
 *      * 获取所有 Ref 列表
 *      * @param repoPath Git 仓库路径
 *      * @return Ref 名称列表（如 ["refs/heads/main", "refs/heads/develop", "refs/tags/v1.0"]）
 *      */
 *     fun getRefs(repoPath: String): List<String>
 *     
 *     /**
 *      * 获取所有本地分支
 *      * @param repoPath Git 仓库路径
 *      * @return 分支名称列表（如 ["main", "develop", "feature/x"]）
 *      */
 *     fun getLocalBranches(repoPath: String): List<String>
 *     
 *     /**
 *      * 获取所有远程分支
 *      * @param repoPath Git 仓库路径
 *      * @return 远程分支名称列表
 *      */
 *     fun getRemoteBranches(repoPath: String): List<String>
 * }
 */