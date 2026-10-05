package com.github.semanticgit.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.semanticgit.common.entity.Author
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.AuthorChangeStatistics
import com.github.semanticgit.ui.config.DbConfig
import com.github.semanticgit.ui.LocalStrings
import com.github.semanticgit.ui.view.chart.DropdownCorner
import com.github.semanticgit.ui.view.chart.MultiChartDisplay
import com.github.semanticgit.ui.view.chart.PieDataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AutoPortrait(
    modifier: Modifier = Modifier,
    repoPaths: List<String>,
    selectedRepoIndex: Int
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val selectedPath = repoPaths.getOrNull(selectedRepoIndex)

    var searchQuery by remember { mutableStateOf("") }
    var searchDropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var allAuthors by remember { mutableStateOf<List<Author>>(emptyList()) }
    var authorStatsMap by remember { mutableStateOf<Map<Long, AuthorChangeStatistics>>(emptyMap()) }
    var authorCommitCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPath) {
        if (selectedPath != null) {
            scope.launch {
                loadAuthorData(
                    repoPath = selectedPath,
                    onLoading = { isLoading = it },
                    onSuccess = { authors, statsMap, commitCounts ->
                        allAuthors = authors
                        authorStatsMap = statsMap
                        authorCommitCounts = commitCounts
                        errorMessage = null
                    },
                    onError = {
                        errorMessage = it
                        allAuthors = emptyList()
                        authorStatsMap = emptyMap()
                    }
                )
            }
        } else {
            allAuthors = emptyList()
            authorStatsMap = emptyMap()
            authorCommitCounts = emptyMap()
            searchQuery = ""
        }
    }

    val filteredAuthors = remember(allAuthors, searchQuery) {
        if (searchQuery.isBlank()) {
            allAuthors
        } else {
            allAuthors.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.email.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = strings.authorPortrait,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        HorizontalDivider()

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(90f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        searchDropdownExpanded = newValue.isNotBlank()
                                && allAuthors.isNotEmpty()
                    },
                    placeholder = { Text(strings.searchAuthorPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                if (searchDropdownExpanded) {
                    val suggestions = allAuthors
                        .filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.email.contains(searchQuery, ignoreCase = true)
                        }
                        .take(20)

                    if (suggestions.isNotEmpty()) {
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
                                items(suggestions) { author ->
                                    Surface(
                                        onClick = {
                                            searchQuery = author.name
                                            searchDropdownExpanded = false
                                        },
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Column {
                                                Text(
                                                    text = author.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = author.email,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall,
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
            }

            IconButton(
                onClick = {
                    searchQuery = ""
                    searchDropdownExpanded = false
                    scope.launch {
                        loadAuthorData(
                            repoPath = selectedPath,
                            onLoading = { isLoading = it },
                            onSuccess = { authors, statsMap, commitCounts ->
                                allAuthors = authors
                                authorStatsMap = statsMap
                                authorCommitCounts = commitCounts
                                errorMessage = null
                            },
                            onError = {
                                errorMessage = it
                            }
                        )
                    }
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

        val authorCommitData = remember(allAuthors, authorCommitCounts) {
            if (authorCommitCounts.isNotEmpty()) {
                allAuthors.map { author ->
                    val key = "${author.name}|${author.email}"
                    val count = authorCommitCounts[key] ?: 0
                    PieDataItem(name = author.name, value = count.toDouble())
                }.filter { it.value > 0.0 }
                    .sortedByDescending { it.value }
            } else {
                emptyList()
            }
        }

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

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = strings.loadingAuthors,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (allAuthors.isNotEmpty()) {
            if (filteredAuthors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                            text = strings.noAuthorsFound,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (authorCommitData.isNotEmpty()) {
                        item(key = "chart") {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(350.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                MultiChartDisplay(
                                    data = authorCommitData,
                                    title = strings.authorCommitProportion,
                                    dropdownCorner = DropdownCorner.TopRight,
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            }
                        }
                    }

                    item(key = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${strings.totalAuthors}: ${allAuthors.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = strings.authorMatchedCount.replace("{0}", filteredAuthors.size.toString()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(filteredAuthors, key = { it.id }) { author ->
                        val stats = authorStatsMap[author.id]
                        AuthorCard(
                            author = author,
                            stats = stats
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
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
                        text = strings.noAuthorsFound,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorCard(
    author: Author,
    stats: AuthorChangeStatistics?
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = author.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = author.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = author.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (stats != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                val opMap = stats.operationFloatMap
                if (opMap.isNotEmpty()) {
                    Text(
                        text = strings.authorOperationDistribution,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OperationDistribution(opMap)
                }

                Spacer(Modifier.height(8.dp))

                val nfMap = stats.natureFlagFloatMap
                if (nfMap.isNotEmpty()) {
                    Text(
                        text = strings.authorNatureDistribution,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    NatureDistribution(nfMap)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.authorNoStatsData,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OperationDistribution(opMap: Map<ChangeOperation, Float>) {
    val opColors = mapOf(
        ChangeOperation.ADD to Color(0xFF2E7D32),
        ChangeOperation.MODIFY to Color(0xFFE65100),
        ChangeOperation.REMOVE to Color(0xFFC62828)
    )

    val opLabels = mapOf(
        ChangeOperation.ADD to "ADD",
        ChangeOperation.MODIFY to "MOD",
        ChangeOperation.REMOVE to "DEL"
    )

    val entries = opMap.entries
        .filter { it.value > 0f }
        .sortedByDescending { it.value }

    if (entries.isEmpty()) return

    val total = entries.sumOf { it.value.toDouble() }.toFloat()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            entries.forEach { (op, value) ->
                val fraction = if (total > 0f) value / total else 0f
                val color = opColors[op] ?: Color.Gray
                if (fraction > 0.02f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            entries.forEach { (op, value) ->
                val fraction = if (total > 0f) value / total else 0f
                val color = opColors[op] ?: Color.Gray
                val label = opLabels[op] ?: op.name

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${"%.0f".format(fraction * 100)}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NatureDistribution(nfMap: Map<ChangeNatureFlag, Float>) {
    val nfColors = mapOf(
        ChangeNatureFlag.FEAT to Color(0xFF1565C0),
        ChangeNatureFlag.FIX to Color(0xFFC62828),
        ChangeNatureFlag.REFACTOR to Color(0xFF6A1B9A),
        ChangeNatureFlag.PERF to Color(0xFF00695C),
        ChangeNatureFlag.STYLE to Color(0xFF6D4C41),
        ChangeNatureFlag.TEST to Color(0xFF2E7D32),
        ChangeNatureFlag.DOCS to Color(0xFF37474F)
    )

    val nfLabels = mapOf(
        ChangeNatureFlag.FEAT to "FEAT",
        ChangeNatureFlag.FIX to "FIX",
        ChangeNatureFlag.REFACTOR to "REF",
        ChangeNatureFlag.PERF to "PERF",
        ChangeNatureFlag.STYLE to "STYLE",
        ChangeNatureFlag.TEST to "TEST",
        ChangeNatureFlag.DOCS to "DOCS"
    )

    val entries = nfMap.entries
        .filter { it.value > 0f }
        .sortedByDescending { it.value }

    if (entries.isEmpty()) return

    val total = entries.sumOf { it.value.toDouble() }.toFloat()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            entries.forEach { (flag, value) ->
                val fraction = if (total > 0f) value / total else 0f
                val color = nfColors[flag] ?: Color.Gray
                if (fraction > 0.02f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            entries.forEach { (flag, value) ->
                val fraction = if (total > 0f) value / total else 0f
                val color = nfColors[flag] ?: Color.Gray
                val label = nfLabels[flag] ?: flag.name

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${"%.0f".format(fraction * 100)}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun loadAuthorData(
    repoPath: String,
    onLoading: (Boolean) -> Unit,
    onSuccess: (List<Author>, Map<Long, AuthorChangeStatistics>, Map<String, Int>) -> Unit,
    onError: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        onLoading(true)
        val dbFile = DbConfig.findLatestDbFile(repoPath) ?: return@withContext onError("No database found")
        val dbDir = dbFile.parentFile?.absolutePath ?: return@withContext onError("Invalid database directory")
        val dbName = dbFile.nameWithoutExtension

        DatabaseManager(dbDir, dbName, false).use { dbManager ->
            val provider = StatisticsProvider(dbManager)
            val authors = provider.getAuthors()
            val statsMap = mutableMapOf<Long, AuthorChangeStatistics>()
            for (author in authors) {
                try {
                    val stats = provider.getAuthorChangeStatistics(author, null)
                    if (stats != null) {
                        statsMap[author.id] = stats
                    }
                } catch (_: Exception) {
                }
            }
            val commitCounts = provider.getCommits()
                .groupBy { "${it.author.name}|${it.author.email}" }
                .mapValues { it.value.size }
            onSuccess(authors, statsMap, commitCounts)
        }
    } catch (e: Exception) {
        onError("Failed to load author data: ${e.message}")
        e.printStackTrace()
    } finally {
        onLoading(false)
    }
}
