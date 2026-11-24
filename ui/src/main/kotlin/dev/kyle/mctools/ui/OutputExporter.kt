package dev.kyle.mctools.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import dev.kyle.mctools.core.ToolException
import java.io.File

data class ExportResult(val displayPath: String, val folderUri: Uri?)

/**
 * Copies a finished staging folder to KyleMCTools_Output/<Tool>/<project>_<timestamp>/.
 * With a chosen folder it uses the Storage Access Framework (no broad storage permission).
 * Without one it falls back to the app's own storage. Never overwrites: every run gets a new timestamped folder.
 */
object OutputExporter {
    const val ROOT_DIR = "KyleMCTools_Output"

    fun export(ctx: Context, tree: Uri?, tool: String, folder: String, staging: File): ExportResult {
        if (tree != null) {
            val root = DocumentFile.fromTreeUri(ctx, tree)
            if (root == null || !root.canWrite()) {
                throw ToolException("KyleMCTools can't write to the output folder anymore. Open Settings and choose the folder again.")
            }
            val toolDir = ensureDir(ensureDir(root, ROOT_DIR), tool)
            val dest = toolDir.createDirectory(folder)
                ?: throw ToolException("Couldn't create the output folder. Choose a different folder in Settings.")
            copyTree(ctx, staging, dest)
            return ExportResult("$ROOT_DIR/$tool/$folder", dest.uri)
        }
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val dest = File(base, "$ROOT_DIR/$tool/$folder")
        staging.copyRecursively(dest, overwrite = false)
        return ExportResult(dest.absolutePath, null)
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: throw ToolException("Couldn't create the folder $name in your chosen output location.")

    private fun copyTree(ctx: Context, src: File, dest: DocumentFile) {
        for (f in src.listFiles().orEmpty().sortedBy { it.name }) {
            if (f.isDirectory) {
                copyTree(ctx, f, dest.createDirectory(f.name) ?: throw ToolException("Couldn't create folder ${f.name}."))
            } else {
                // octet-stream keeps the file name exactly as written (no extra extension appended)
                val target = dest.createFile("application/octet-stream", f.name)
                    ?: throw ToolException("Couldn't create the file ${f.name}.")
                ctx.contentResolver.openOutputStream(target.uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    ?: throw ToolException("Couldn't write the file ${f.name}.")
            }
        }
    }
}

fun persistTree(ctx: Context, uri: Uri) {
    try {
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (_: Exception) { }
}

fun openFolder(ctx: Context, uri: Uri?, path: String?) {
    if (uri != null) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return
        } catch (_: Exception) { }
    }
    Toast.makeText(ctx, "Open your Files app and go to: ${path ?: "the output folder"}", Toast.LENGTH_LONG).show()
}
