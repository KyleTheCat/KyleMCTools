package dev.kyle.mctools.optimizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.TaskViewModel
import dev.kyle.mctools.ui.ToolWorkspace

@Composable
private fun Opt(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PackOptimizerScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "optimizer")
    var png by remember { mutableStateOf(true) }
    var json by remember { mutableStateOf(true) }
    var junk by remember { mutableStateOf(true) }
    var unused by remember { mutableStateOf(false) }
    ToolWorkspace(
        title = "Pack Optimizer",
        hint = "Drop a Java pack or Bedrock pack (.zip, .mcpack)",
        runLabel = "Optimize",
        toolName = "PackOptimizer",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            Opt("Compress PNG images", "Lossless: same pixels, smaller files", png) { png = it }
            Opt("Minify JSON", "Removes spaces and comments", json) { json = it }
            Opt("Remove junk files", "Thumbs.db, .DS_Store, .psd, .git and similar", junk) { junk = it }
            Opt("Remove unreferenced textures", "Custom namespaces only. Don't use on plugin source packs (Nexo, Oraxen, ItemsAdder)", unused) { unused = it }
        },
    ) { c ->
        PackOptimizer.run(c.inputFile, c.stagingDir, OptimizeOptions(c.projectName, png, json, junk, unused), c.reporter)
    }
}
