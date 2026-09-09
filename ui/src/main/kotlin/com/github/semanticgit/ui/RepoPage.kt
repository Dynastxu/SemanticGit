package com.github.semanticgit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

        if (selectedIndex in repoPaths.indices) {
            Text(
                text = repoPaths[selectedIndex],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
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
