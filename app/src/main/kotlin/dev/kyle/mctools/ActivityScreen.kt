package dev.kyle.mctools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyle.mctools.core.ActivityEntry
import dev.kyle.mctools.ui.ActivityRepo
import dev.kyle.mctools.ui.Pill
import dev.kyle.mctools.ui.SmallButton
import dev.kyle.mctools.ui.cardStyle
import dev.kyle.mctools.ui.openFolder
import dev.kyle.mctools.ui.statusColor

fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - ms) / 60_000
    return when {
        m < 1 -> "Just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> "${m / (60 * 24)} d ago"
    }
}

@Composable
fun ActivityScreen() {
    val ctx = LocalContext.current
    val runs by ActivityRepo.items.collectAsState()
    var logFor by remember { mutableStateOf<ActivityEntry?>(null) }
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader("ACTIVITY", "Recent runs", if (runs.isEmpty()) "Nothing here yet." else "${runs.size} runs \u00B7 ${runs.count { it.ok }} succeeded")
        }
        if (runs.isEmpty()) {
            item {
                Text(
                    "Open a tool from the Tools tab and run it. Each run shows up here with its result and log.",
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(runs, key = { it.id }) { e ->
            val icon = ToolId.entries.firstOrNull { it.folder == e.tool }?.icon ?: Icons.Default.Build
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().cardStyle().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(e.file, color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                    }
                    Pill(if (e.ok) "Done" else "Failed", textColor = statusColor(e.ok), dot = statusColor(e.ok))
                }
                Text(e.summary, color = cs.onSurfaceVariant, fontSize = 15.sp, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ago(e.time), color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (e.path != null) SmallButton("Folder", { openFolder(ctx, e.folderUri?.let { Uri.parse(it) }, e.path) })
                    SmallButton("Log", { logFor = e })
                    SmallButton("Remove", { ActivityRepo.remove(e.id) }, color = cs.error)
                }
            }
        }
    }

    logFor?.let { e ->
        AlertDialog(
            onDismissRequest = { logFor = null },
            confirmButton = { TextButton(onClick = { logFor = null }) { Text("Close") } },
            title = { Text(e.title) },
            text = {
                Text(
                    e.log.ifBlank { "No log was recorded." },
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}
