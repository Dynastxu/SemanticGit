package com.github.semanticgit.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.semanticgit.common.entity.ChangeNatureFlag
import com.github.semanticgit.common.entity.ChangeOperation
import com.github.semanticgit.core.AnalysisEngine
import com.github.semanticgit.core.StatisticsProvider
import com.github.semanticgit.core.db.DatabaseManager
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics
import com.github.semanticgit.ui.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPage(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val repoPaths = remember { mutableStateListOf<String>() }
    var selectedIndex by remember { mutableStateOf(-1) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val displayNames = remember(repoPaths.toList()) {
        computeDisplayNames(repoPaths.toList())
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statistics by remember { mutableStateOf<SimpleEntityChangeStatistics?>(null) }

    val selectedPath = repoPaths.getOrNull(selectedIndex)

    LaunchedEffect(selectedPath) {
        if (selectedPath == null) {
            statistics = null
            errorMessage = null
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        statistics = null

        withContext(Dispatchers.IO) {
            val dbDir = "${System.getProperty("user.home")}/.semanticgit/db"
            val engine = AnalysisEngine()

            if (!engine.isDatabaseExists(selectedPath, dbDir)) {
                val success = engine.fullAnalysis(selectedPath, dbDir)
                if (!success) {
                    errorMessage = engine.failMessage.ifBlank { strings.repoAnalysisFailed }
                    return@withContext
                }
            }

            val dbName = Integer.toHexString(File(selectedPath).absolutePath.hashCode())
            DatabaseManager(dbDir, dbName, false).use { dbManager ->
                val provider = StatisticsProvider(dbManager)
                statistics = provider.simpleEntityChangeStatistics
                if (!statistics!!.isSuccess) {
                    errorMessage = strings.repoAnalysisFailed
                }
            }
        }

        isLoading = false
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    repoPaths.forEachIndexed { index, path ->
                        DropdownMenuItem(
                            text = { Text(displayNames[index]) },
                            onClick = {
                                selectedIndex = index
                                dropdownExpanded = false
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
                    val selectedPath = chooser.selectedFile.absolutePath
                    val existingIndex = repoPaths.indexOf(selectedPath)
                    if (existingIndex >= 0) {
                        selectedIndex = existingIndex
                    } else {
                        repoPaths.add(selectedPath)
                        selectedIndex = repoPaths.size - 1
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = strings.repoSelectFolder
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedPath == null) {
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
        } else if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.repoAnalyzing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            StatisticsContent(strings = strings, statistics = statistics)
        }
    }
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.repoOperationDistribution,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        val operationMap = statistics.operationFloatMap ?: emptyMap()
        ChangeOperation.entries.forEach { op ->
            StatRow(label = op.desc, ratio = operationMap[op] ?: 0f)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.repoNatureDistribution,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        val natureMap = statistics.natureFlagFloatMap ?: emptyMap()
        ChangeNatureFlag.entries.forEach { flag ->
            StatRow(label = flag.name.lowercase(), ratio = natureMap[flag] ?: 0f)
        }
    }
}

@Composable
private fun StatRow(label: String, ratio: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.weight(1f).height(12.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(ratio * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
