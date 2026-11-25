package dev.kyle.mctools.protector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.kyle.mctools.ui.cardStyle

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
fun PackProtectorScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "protector")
    var minify by remember { mutableStateOf(true) }
    var decoys by remember { mutableStateOf(true) }
    var rename by remember { mutableStateOf(false) }
    ToolWorkspace(
        title = "Pack Protector",
        hint = "Drop a Java pack or Bedrock pack (.zip, .mcpack)",
        runLabel = "Protect",
        toolName = "PackProtector",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Be realistic", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The game must read your pack, so nothing can fully stop a determined person. This makes casual copying harder. A notes file explains the limits.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Opt("Minify JSON", "Removes whitespace and comments", minify) { minify = it }
            Opt("Add decoy files", "Names that many Windows unzip tools can't extract", decoys) { decoys = it }
            Opt("Rename assets (Java)", "Random names for textures, models, sounds. Finished packs only: breaks plugin configs that use file names", rename) { rename = it }
        },
    ) { c ->
        PackProtector.run(c.inputFile, c.stagingDir, ProtectOptions(c.projectName, minify, decoys, rename), c.reporter)
    }
}
