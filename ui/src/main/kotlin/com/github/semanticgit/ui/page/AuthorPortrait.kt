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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.semanticgit.common.entity.Author
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.AuthorChangeStatistics
import com.github.semanticgit.ui.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPath) {
        if (selectedPath != null) {
            scope.launch {
                loadAuthorData(
                    repoPath = selectedPath,
                    onLoading = { isLoading = it },
                    onSuccess = { authors, statsMap ->
                        allAuthors = authors
                        authorStatsMap = statsMap
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
                        selectedPath?.let { path ->
                            loadAuthorData(
                                repoPath = path,
                                onLoading = { isLoading = it },
                                onSuccess = { authors, statsMap ->
                                    allAuthors = authors
                                    authorStatsMap = statsMap
                                    errorMessage = null
                                },
                                onError = {
                                    errorMessage = it
                                }
                            )
                        }
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
                        text = "匹配 ${filteredAuthors.size} 位",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAuthors, key = { it.id }) { author ->
                        val stats = authorStatsMap[author.id]
                        AuthorCard(
                            author = author,
                            stats = stats
                        )
                    }
                }
            }
        } else if (!isLoading) {
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

                val strings = LocalStrings.current

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
                    text = "暂无统计数据",
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
        ChangeOperation.ADD to Color(0xFF4CAF50),
        ChangeOperation.MODIFY to Color(0xFFFFA726),
        ChangeOperation.REMOVE to Color(0xFFEF5350)
    )

    val opLabels = mapOf(
        ChangeOperation.ADD to "ADD",
        ChangeOperation.MODIFY to "MOD",
        ChangeOperation.REMOVE to "DEL"
    )

    val total = opMap.values.sum()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        opMap.entries.sortedByDescending { it.value }.forEachIndexed { index, (op, value) ->
            val fraction = if (total > 0f) value / total else 0f
            val color = opColors[op] ?: Color.Gray
            val label = opLabels[op] ?: op.name

            Column(
                modifier = if (index < opMap.size - 1) Modifier.weight(fraction) else Modifier
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                    Text(
                        text = "${"%.1f".format(value * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun NatureDistribution(nfMap: Map<ChangeNatureFlag, Float>) {
    val nfColors = mapOf(
        ChangeNatureFlag.FEAT to Color(0xFF1976D2),
        ChangeNatureFlag.FIX to Color(0xFFE53935),
        ChangeNatureFlag.REFACTOR to Color(0xFF7B1FA2),
        ChangeNatureFlag.PERF to Color(0xFF00897B),
        ChangeNatureFlag.STYLE to Color(0xFFFDD835),
        ChangeNatureFlag.TEST to Color(0xFF43A047),
        ChangeNatureFlag.DOCS to Color(0xFF6D4C41)
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

    val total = nfMap.values.sum()

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        nfMap.entries
            .sortedByDescending { it.value }
            .forEach { (flag, value) ->
                val fraction = if (total > 0f) value / total else 0f
                val color = nfColors[flag] ?: Color.Gray
                val label = nfLabels[flag] ?: flag.name

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier.width(44.dp)
                    )
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "${"%.1f".format(value * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp)
                    )
                }
            }
    }
}

private suspend fun loadAuthorData(
    repoPath: String,
    onLoading: (Boolean) -> Unit,
    onSuccess: (List<Author>, Map<Long, AuthorChangeStatistics>) -> Unit,
    onError: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        onLoading(true)
        val dbDir = "${System.getProperty("user.home", "")}/.semanticgit/db"
        val dbName = Integer.toHexString(File(repoPath).absolutePath.hashCode())

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
            onSuccess(authors, statsMap)
        }
    } catch (e: Exception) {
        onError("Failed to load author data: ${e.message}")
        e.printStackTrace()
    } finally {
        onLoading(false)
    }
}