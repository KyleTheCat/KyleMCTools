package dev.kyle.mctools.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kyle.mctools.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipException

enum class Phase { IDLE, READY, RUNNING, DONE, ERROR }

data class TaskUiState(
    val phase: Phase = Phase.IDLE,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val projectName: String = "",
    val progress: Float = 0f,
    val progressLabel: String = "",
    val logs: List<LogLine> = emptyList(),
    val message: String? = null,
    val summary: String? = null,
    val savedPath: String? = null,
    val folderUri: Uri? = null,
)

/** What a tool's task gets: a private copy of the input, an empty staging folder, and a reporter. */
class TaskContext(val inputFile: File, val projectName: String, val stagingDir: File, val reporter: Reporter)

class TaskViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TaskUiState())
    val state: StateFlow<TaskUiState> = _state.asStateFlow()
    private var inputFile: File? = null

    /** Copies the picked file into app cache so the original is never touched. */
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val (file, name) = withContext(Dispatchers.IO) { copyIn(uri) }
                inputFile?.delete()
                inputFile = file
                _state.value = TaskUiState(
                    phase = Phase.READY, fileName = name, fileSize = file.length(),
                    projectName = name.substringBeforeLast('.'),
                )
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.ERROR, message = "Couldn't open that file. Pick it again, or try a different file.") }
            }
        }
    }

    private fun copyIn(uri: Uri): Pair<File, String> {
        val ctx = getApplication<Application>()
        var name = "pack.zip"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val dir = File(ctx.cacheDir, "inputs").also { it.mkdirs() }
        val file = File(dir, "${System.nanoTime()}.zip")
        ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("no stream")
        return file to name
    }

    fun run(toolName: String, outputTree: Uri?, task: (TaskContext) -> ToolResult) {
        val input = inputFile ?: return
        if (_state.value.phase == Phase.RUNNING) return
        val project = _state.value.projectName.ifBlank { "pack" }
        _state.update { it.copy(phase = Phase.RUNNING, progress = 0f, progressLabel = "Starting...", logs = emptyList(), message = null, summary = null, savedPath = null, folderUri = null) }
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val reporter = object : Reporter {
                override fun log(level: LogLevel, message: String) { _state.update { it.copy(logs = it.logs + LogLine(level, message)) } }
                override fun progress(fraction: Float, label: String) { _state.update { it.copy(progress = fraction.coerceIn(0f, 1f), progressLabel = label) } }
            }
            val staging = File(app.cacheDir, "staging/${System.nanoTime()}").also { it.mkdirs() }
            try {
                val result = task(TaskContext(input, project, staging, reporter))
                reporter.progress(0.97f, "Saving results...")
                val export = OutputExporter.export(app, outputTree, toolName, Ids.folderName(project), staging)
                reporter.log(LogLevel.OK, "Saved to ${export.displayPath}")
                _state.update { it.copy(phase = Phase.DONE, progress = 1f, progressLabel = "Done", summary = result.summary, savedPath = export.displayPath, folderUri = export.folderUri) }
            } catch (e: ToolException) {
                fail(reporter, e.userMessage)
            } catch (e: ZipException) {
                fail(reporter, "That file isn't a valid zip, or it is corrupted. Try re-downloading or re-exporting it.")
            } catch (e: OutOfMemoryError) {
                fail(reporter, "This pack is too big for the memory available on this phone. Close other apps and try again.")
            } catch (e: Exception) {
                fail(reporter, "Something unexpected went wrong. Details: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private fun fail(rep: Reporter, message: String) {
        rep.log(LogLevel.ERROR, message)
        _state.update { it.copy(phase = Phase.ERROR, message = message) }
    }

    fun reset() {
        inputFile?.delete(); inputFile = null
        _state.value = TaskUiState()
    }

    override fun onCleared() { inputFile?.delete() }
}
