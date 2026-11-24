package dev.kyle.mctools.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kyle.mctools.core.ToolResult

/**
 * The shared workspace every tool uses: drop zone, tool options, run button,
 * animated progress, live log, friendly errors, success animation, "Open output folder".
 */
@Composable
fun ToolWorkspace(
    title: String,
    hint: String,
    runLabel: String,
    toolName: String,
    vm: TaskViewModel,
    prefs: Prefs,
    onBack: () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
    task: (TaskContext) -> ToolResult,
) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()
    var tree by remember { mutableStateOf(prefs.outputTree) }
    var pendingRun by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistTree(ctx, uri); prefs.outputTree = uri; tree = uri }
        if (pendingRun) { pendingRun = false; vm.run(toolName, title, tree, task) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DropZone(
                title = hint, fileName = st.fileName, sizeLabel = if (st.fileName != null) formatBytes(st.fileSize) else null,
                onBrowse = { if (st.phase != Phase.RUNNING) pickFile.launch(arrayOf("*/*")) },
                onDropUri = { if (st.phase != Phase.RUNNING) vm.onFilePicked(it) },
            )
            options()

            val canRun = st.fileName != null && st.phase != Phase.RUNNING
            KButton(
                text = if (st.phase == Phase.RUNNING) "Working..." else runLabel,
                enabled = canRun, modifier = Modifier.fillMaxWidth(),
                onClick = { if (tree == null) { pendingRun = true; pickTree.launch(null) } else vm.run(toolName, title, tree, task) },
            )
            if (st.fileName != null && tree == null && st.phase != Phase.RUNNING) {
                Text(
                    "First run: you'll pick where results are saved. Cancel to use app storage instead.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }

            if (st.phase == Phase.RUNNING || st.phase == Phase.DONE || st.phase == Phase.ERROR) {
                Text(st.progressLabel, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                AnimatedProgressBar(st.progress)
            }
            if (st.logs.isNotEmpty()) LogPanel(st.logs)

            if (st.phase == Phase.ERROR && st.message != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("That didn't work", style = MaterialTheme.typography.titleMedium, color = cs.error)
                    Text(st.message!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (st.phase == Phase.DONE) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SuccessCheck()
                    Text("Finished", style = MaterialTheme.typography.titleLarge)
                    Text(st.summary ?: "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Text(st.savedPath ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    KButton("Open output folder", { openFolder(ctx, st.folderUri, st.savedPath) }, Modifier.fillMaxWidth())
                    KButton("Start over", { vm.reset() }, Modifier.fillMaxWidth(), primary = false)
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}
