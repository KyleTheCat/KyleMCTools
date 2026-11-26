package dev.kyle.mctools.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.kyle.mctools.core.ActivityEntry
import dev.kyle.mctools.core.Ids
import dev.kyle.mctools.core.ToolException
import java.io.File

/** Reading text files and saving edited files for the visual editors (same output rules as the converters). */
object EditorIO {
    /** Returns (file name, text). Files over 5 MB are refused. */
    fun readText(ctx: Context, uri: Uri): Pair<String, String> {
        var name = "file.yml"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ToolException("Couldn't open that file.")
        if (bytes.size > 5 * 1024 * 1024) throw ToolException("That file is larger than 5 MB, which is too big for the editor.")
        return name to bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    /** Saves [files] (relative path -> text) to KyleMCTools_Output/<tool>/<project>_<time>/ and records the run in Activity. */
    fun save(ctx: Context, tree: Uri?, tool: String, title: String, project: String, files: Map<String, String>, summary: String, sourceName: String): ExportResult {
        val staging = File(ctx.cacheDir, "staging/editor_${System.nanoTime()}").also { it.mkdirs() }
        try {
            for ((path, text) in files) {
                val f = File(staging, path)
                f.parentFile?.mkdirs()
                f.writeText(text)
            }
            val result = OutputExporter.export(ctx, tree, tool, Ids.folderName(project), staging)
            val now = System.currentTimeMillis()
            ActivityRepo.add(ActivityEntry(now, tool, title, sourceName, true, summary, result.displayPath, result.folderUri?.toString(), now, files.keys.joinToString("\n") { "ok: wrote $it" }))
            return result
        } finally {
            staging.deleteRecursively()
        }
    }
}

/** Returns a function that runs an action once an output folder is chosen (asking the first time). */
@Composable
fun rememberEnsureOutput(prefs: Prefs): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistTree(ctx, uri); prefs.outputTree = uri }
        val p = pending
        pending = null
        p?.invoke()
    }
    return { action -> if (prefs.outputTree == null) { pending = action; pick.launch(null) } else action() }
}
