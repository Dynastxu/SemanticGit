package com.github.semanticgit.ui.page

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.common.entity.CommitMeta
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.dto.CommitEntityChangeStatistics
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.ui.view.CommitNode
import com.github.semanticgit.ui.view.CommitTopologyGraph
import com.github.semanticgit.ui.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale


@Composable
fun Submit(
    modifier: Modifier = Modifier,
    repoPaths: List<String>,
    selectedRepoIndex: Int
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val selectedPath = repoPaths.getOrNull(selectedRepoIndex)

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var allCommits by remember { mutableStateOf<List<CommitMeta>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<CommitMeta>>(emptyList()) }
    var searchDropdownExpanded by remember { mutableStateOf(false) }

    var selectedCommitHash by remember { mutableStateOf<String?>(null) }
    var commitStatistics by remember { mutableStateOf<CommitEntityChangeStatistics?>(null) }
    var isLoadingStats by remember { mutableStateOf(false) }

    var nodes by remember { mutableStateOf<List<CommitNode>>(emptyList()) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun loadCommits(path: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                withContext(Dispatchers.IO) {
                    val dbDir = "${System.getProperty("user.home", "")}/.semanticgit/db"
                    val dbName = Integer.toHexString(java.io.File(path).absolutePath.hashCode())
                    DatabaseManager(dbDir, dbName, false).use { dbManager ->
                        val provider = StatisticsProvider(dbManager)
                        val commits = provider.getCommits()
                        commits
                    }
                }.let { commits ->
                    allCommits = commits

                    val hashSet = commits.map { it.hash }.toSet()
                    val hashToMeta = commits.associateBy { it.hash }
                    val depthCache = mutableMapOf<String, Int>()

                    fun calcDepth(hash: String): Int {
                        depthCache[hash]?.let { return it }
                        val meta = hashToMeta[hash] ?: return 0
                        val parentDepth = meta.parentCommitMeta?.hash?.let { calcDepth(it) } ?: 0
                        val depth = parentDepth + 1
                        depthCache[hash] = depth
                        return depth
                    }

                    val nodeList = commits.map { commit ->
                        val depth = calcDepth(commit.hash)
                        val isMerge = commit.mergeParentMeta != null
                        CommitNode(
                            hash = commit.hash,
                            authorName = commit.author?.name ?: "Unknown",
                            timestamp = commit.timestamp ?: 0,
                            message = (commit.message ?: "").lines().first(),
                            isMerge = isMerge,
                            parentHash = commit.parentCommitMeta?.hash,
                            mergeParentHash = commit.mergeParentMeta?.hash,
                            depth = depth,
                            lane = 0
                        )
                    }

                    nodes = nodeList.sortedBy { it.depth }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: strings.commitFailedToLoad
                allCommits = emptyList()
                nodes = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadCommitStats(hash: String) {
        scope.launch {
            isLoadingStats = true
            try {
                val stats = withContext(Dispatchers.IO) {
                    val dbDir = "${System.getProperty("user.home", "")}/.semanticgit/db"
                    val dbName = Integer.toHexString(java.io.File(selectedPath!!).absolutePath.hashCode())
                    DatabaseManager(dbDir, dbName, false).use { dbManager ->
                        val provider = StatisticsProvider(dbManager)
                        val result = provider.getCommitEntityChangeStatistics(hash)
                        result
                    }
                }
                commitStatistics = stats
            } catch (e: Exception) {
                commitStatistics = null
            } finally {
                isLoadingStats = false
            }
        }
    }

    LaunchedEffect(selectedPath) {
        selectedPath?.let { loadCommits(it) }
    }

    val filteredCommits = if (searchQuery.isBlank()) {
        allCommits
    } else {
        val q = searchQuery.lowercase()
        allCommits.filter {
            it.hash.lowercase().contains(q) ||
            (it.message ?: "").lowercase().contains(q) ||
            (it.author?.name ?: "").lowercase().contains(q)
        }
    }

    val filteredNodes = if (searchQuery.isBlank()) {
        nodes
    } else {
        val q = searchQuery.lowercase()
        nodes.filter {
            it.hash.lowercase().contains(q) ||
            it.message.lowercase().contains(q) ||
            it.authorName.lowercase().contains(q)
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = strings.commitTopologyGraph,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedPath != null) {
            Text(
                text = "${strings.currentRepository}: ${selectedPath.substringAfterLast("\\").substringAfterLast("/")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = strings.noRepositorySelected,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(90f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        if (query.isNotBlank()) {
                            val q = query.lowercase()
                            searchSuggestions = allCommits.filter {
                                it.hash.lowercase().contains(q) ||
                                (it.message ?: "").lowercase().contains(q) ||
                                (it.author?.name ?: "").lowercase().contains(q)
                            }.take(10)
                            searchDropdownExpanded = searchSuggestions.isNotEmpty()
                        } else {
                            searchSuggestions = emptyList()
                            searchDropdownExpanded = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(strings.commitSearchPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                if (searchDropdownExpanded && searchSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn {
                            items(searchSuggestions) { commit ->
                                Surface(
                                    onClick = {
                                        searchQuery = commit.hash
                                        searchDropdownExpanded = false
                                        selectedCommitHash = commit.hash
                                        loadCommitStats(commit.hash)
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = commit.hash.take(9),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = (commit.message ?: "").lines().first(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "${commit.author?.name ?: "Unknown"}  ${
                                                    dateFormatter.format(java.util.Date(commit.timestamp!! * 1000L))
                                                }",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    searchQuery = ""
                    searchDropdownExpanded = false
                    selectedCommitHash = null
                    commitStatistics = null
                    selectedPath?.let { loadCommits(it) }
                },
                enabled = !isLoading,
                modifier = Modifier.weight(10f).padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = strings.reset,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = strings.commitTotalCount
                .replace("{0}", allCommits.size.toString())
                .replace("{1}", filteredCommits.size.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.commitLoading, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.commitAnalysisFailedTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.commitAnalysisFailedHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                CommitTopologyGraph(
                    nodes = filteredNodes,
                    selectedHash = selectedCommitHash,
                    onCommitClick = { hash ->
                        selectedCommitHash = hash
                        loadCommitStats(hash)
                    },
                    modifier = Modifier.weight(1f)
                )

                if (selectedCommitHash != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CommitDetailPanel(
                        statistics = commitStatistics,
                        isLoading = isLoadingStats,
                        modifier = Modifier.width(300.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitDetailPanel(
    statistics: CommitEntityChangeStatistics?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                return@Column
            }

            if (statistics == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.commitClickDetailHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            val commit = statistics.commitMeta
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            Text(
                text = strings.commitDetail,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = commit.hash,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = commit.author?.name ?: "Unknown",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = dateFormatter.format(java.util.Date(commit.timestamp!! * 1000L)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = (commit.message ?: "").lines().first(),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val opMap = statistics.operationFloatMap ?: emptyMap()
            if (opMap.isNotEmpty()) {
                Text(
                    text = strings.authorOperationDistribution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val totalOp = opMap.values.sum()
                for ((op, count) in opMap) {
                    val pct = if (totalOp > 0) count / totalOp * 100f else 0f
                    val color = when (op) {
                        ChangeOperation.ADD -> Color(0xFF4CAF50)
                        ChangeOperation.MODIFY -> Color(0xFFFF9800)
                        ChangeOperation.REMOVE -> Color(0xFFF44336)
                        else -> Color.Gray
                    }
                    val opName = when (op) {
                        ChangeOperation.ADD -> "ADD"
                        ChangeOperation.MODIFY -> "MOD"
                        ChangeOperation.REMOVE -> "DEL"
                        else -> op.name
                    }
                    OperationBar(opName, pct, count.toInt(), color)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val natureMap = statistics.natureFlagFloatMap ?: emptyMap()
            if (natureMap.isNotEmpty()) {
                Text(
                    text = strings.authorNatureDistribution,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val totalNat = natureMap.values.sum()
                for ((flag, count) in natureMap) {
                    val pct = if (totalNat > 0) count / totalNat * 100f else 0f
                    val color = flagColor(flag)
                    OperationBar(flag.name, pct, count.toInt(), color)
                }
            }
        }
    }
}

@Composable
private fun OperationBar(label: String, pct: Float, count: Int, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.width(48.dp)
            )
            Text(
                text = "${"%.1f".format(pct)}% ($count)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

private fun flagColor(flag: ChangeNatureFlag): Color = when (flag) {
    ChangeNatureFlag.FEAT -> Color(0xFF4CAF50)
    ChangeNatureFlag.FIX -> Color(0xFF2196F3)
    ChangeNatureFlag.REFACTOR -> Color(0xFF9C27B0)
    ChangeNatureFlag.PERF -> Color(0xFFFF9800)
    ChangeNatureFlag.STYLE -> Color(0xFF795548)
    ChangeNatureFlag.TEST -> Color(0xFF00BCD4)
    ChangeNatureFlag.DOCS -> Color(0xFF607D8B)
    else -> Color.Gray
}
