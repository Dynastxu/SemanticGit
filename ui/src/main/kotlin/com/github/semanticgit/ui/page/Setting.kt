package com.github.semanticgit.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.semanticgit.ui.config.DbConfig
import com.github.semanticgit.ui.LocalStrings
import com.github.semanticgit.ui.ThemeMode
import javax.swing.JFileChooser

@Composable
fun Setting(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    languageTag: String,
    onChangeLanguage: (String) -> Unit
) {
    val strings = LocalStrings.current

    var storagePath by remember { mutableStateOf(DbConfig.storagePath) }
    var filenameFormat by remember { mutableStateOf(DbConfig.filenameFormat) }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = strings.settingsTitle,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.settingsLanguage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.settingsLanguageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = languageTag == "zh",
                        onClick = { onChangeLanguage("zh") },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.settingsLanguageZh,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    SegmentedButton(
                        selected = languageTag == "en",
                        onClick = { onChangeLanguage("en") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.settingsLanguageEn,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.settingsAppearance,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.settingsAppearanceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isLight = themeMode == ThemeMode.Light
                    SegmentedButton(
                        selected = isLight,
                        onClick = { if (!isLight) onToggleTheme() },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.settingsAppearanceLight,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    SegmentedButton(
                        selected = !isLight,
                        onClick = { if (isLight) onToggleTheme() },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.settingsAppearanceDark,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.settingsDbStorage,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        DbConfig.resetToDefault()
                        storagePath = DbConfig.storagePath
                        filenameFormat = DbConfig.filenameFormat
                    }) {
                        Text(strings.settingsDbResetDefault)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.settingsDbStoragePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = storagePath,
                        onValueChange = { newValue ->
                            storagePath = newValue
                            DbConfig.storagePath = newValue
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = {
                        val chooser = JFileChooser()
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.dialogTitle = strings.repoSelectFolder
                        val result = chooser.showOpenDialog(null)
                        if (result == JFileChooser.APPROVE_OPTION) {
                            val newPath = chooser.selectedFile.absolutePath
                            storagePath = newPath
                            DbConfig.storagePath = newPath
                        }
                    }) {
                        Text(strings.settingsDbStorageBrowse)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.settingsDbFilenameFormat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = filenameFormat,
                    onValueChange = { newValue ->
                        filenameFormat = newValue
                        DbConfig.filenameFormat = newValue
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.settingsDbFilenameFormatHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.settingsAbout,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.settingsAboutVersion,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.settingsAboutDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
