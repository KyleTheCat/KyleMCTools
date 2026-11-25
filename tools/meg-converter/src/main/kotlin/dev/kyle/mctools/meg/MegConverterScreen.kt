package dev.kyle.mctools.meg

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
fun MegConverterScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "meg")
    var swap by remember { mutableStateOf(false) }
    ToolWorkspace(
        title = "MEG Converter",
        hint = "Drop a .bbmodel file, or a zip of .bbmodel files",
        runLabel = "Convert",
        toolName = "MegConverter",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Swap east/west faces")
                    Text("Turn on if side faces look mirrored in-game", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = swap, onCheckedChange = { swap = it })
            }
        },
    ) { c ->
        MegConverter.run(c.inputFile, c.stagingDir, MegOptions(c.projectName, swap), c.reporter)
    }
}
