package dev.kyle.mctools.geyser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
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
fun GeyserConverterScreen(prefs: Prefs, onBack: () -> Unit) {
    val vm: TaskViewModel = viewModel(key = "geyser")
    var baseItem by remember { mutableStateOf("minecraft:paper") }
    var sounds by remember { mutableStateOf(true) }
    ToolWorkspace(
        title = "Java to Bedrock",
        hint = "Drop a Java resource pack (.zip)",
        runLabel = "Convert",
        toolName = "JavaToBedrock",
        vm = vm, prefs = prefs, onBack = onBack,
        options = {
            OutlinedTextField(
                value = baseItem, onValueChange = { baseItem = it }, singleLine = true,
                label = { Text("Base item for 1.21.4+ item models") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = sounds, onCheckedChange = { sounds = it })
                Text("Convert custom sounds")
            }
        },
    ) { c ->
        JavaToBedrock.convert(c.inputFile, c.stagingDir, GeyserOptions(c.projectName, baseItem, sounds), c.reporter)
    }
}
