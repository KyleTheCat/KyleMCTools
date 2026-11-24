package dev.kyle.mctools.core

enum class LogLevel { INFO, OK, WARN, ERROR }

data class LogLine(val level: LogLevel, val text: String)

/** Every tool reports progress and log lines through this. The UI shows them live. */
interface Reporter {
    fun log(level: LogLevel, message: String)
    fun progress(fraction: Float, label: String)
}

fun Reporter.info(m: String) = log(LogLevel.INFO, m)
fun Reporter.ok(m: String) = log(LogLevel.OK, m)
fun Reporter.warn(m: String) = log(LogLevel.WARN, m)
fun Reporter.err(m: String) = log(LogLevel.ERROR, m)

class CollectingReporter : Reporter {
    val lines = mutableListOf<LogLine>()
    var lastProgress = 0f
    override fun log(level: LogLevel, message: String) { lines += LogLine(level, message) }
    override fun progress(fraction: Float, label: String) { lastProgress = fraction }
}

/** An error whose message is safe and friendly to show to the user as-is. */
class ToolException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

data class ToolResult(val summary: String)
