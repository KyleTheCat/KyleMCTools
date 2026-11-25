package dev.kyle.mctools.version

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kyle.mctools.core.McVersions
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.TaskViewModel
import dev.kyle.mctools.ui.ToolWorkspace

@Composable
fun VersionConverterScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "version")
    var target by remember { mutableStateOf(McVersions.all.last().format) }
    ToolWorkspace(
        title = "Version Converter",
        hint = "Drop a Java resource pack (.zip)",
        runLabel = "Convert",
        toolName = "VersionConverter",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            Text("Convert to", style = MaterialTheme.typography.titleSmall)
            Text("The current version is read from pack.mcmeta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                McVersions.all.reversed().forEach { v ->
                    FilterChip(selected = target == v.format, onClick = { target = v.format }, label = { Text(v.label) })
                }
            }
        },
    ) { c ->
        VersionConverter.run(c.inputFile, c.stagingDir, VersionOptions(c.projectName, target), c.reporter)
    }
}
