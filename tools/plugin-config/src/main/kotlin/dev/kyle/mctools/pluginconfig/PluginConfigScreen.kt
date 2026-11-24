package dev.kyle.mctools.pluginconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.TaskViewModel
import dev.kyle.mctools.ui.ToolWorkspace

@Composable
fun PluginConfigScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "pluginconfig")
    var target by remember { mutableStateOf(PluginTarget.NEXO) }
    var material by remember { mutableStateOf("PAPER") }
    ToolWorkspace(
        title = "Pack to plugin config",
        hint = "Drop a Java resource pack (.zip)",
        runLabel = "Generate config",
        toolName = "PackToPluginConfig",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            Text("Plugin")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PluginTarget.entries.forEach { t ->
                    FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.label) })
                }
            }
            OutlinedTextField(
                value = material, onValueChange = { material = it }, singleLine = true,
                label = { Text("Base item for 1.21.4+ item models") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) { c ->
        PluginConfigGenerator.generate(c.inputFile, c.stagingDir, PluginOptions(c.projectName, target, material), c.reporter)
    }
}
