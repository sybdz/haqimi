package me.rerere.rikkahub.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.ai.AILogging
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.ChatService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val MAX_BODY_SNIPPET = 4096
private const val MAX_LOGCAT_CHARS = 96 * 1024
private const val MAX_COMMAND_OUTPUT_CHARS = 16 * 1024
private const val COMMAND_TIMEOUT_MS = 1_500L

class DiagnosticsBundleBuilder(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val aiLoggingManager: AILoggingManager,
    private val chatService: ChatService,
    private val diagnosticsMonitor: DiagnosticsMonitor,
) {
    suspend fun writeReportFile(): File = withContext(Dispatchers.IO) {
        val file = File(
            File(context.cacheDir, "diagnostics").apply { mkdirs() },
            "rikkahub-diagnostics-${fileTimestamp()}.txt"
        )
        file.writeText(buildReport(), Charsets.UTF_8)
        file
    }

    suspend fun buildReport(): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.value
        buildString {
            appendSection("Summary") {
                appendLine("generatedAt=${Date()}")
                appendLine("package=${BuildConfig.APPLICATION_ID}")
                appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("buildType=${BuildConfig.BUILD_TYPE}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("abi=${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("pid=${Process.myPid()}")
            }

            appendSection("Memory") {
                appendMemoryInfo()
            }

            appendSection("Performance Monitor") {
                appendLine(diagnosticsMonitor.formatSnapshot())
            }

            appendSection("Runtime Snapshot") {
                appendRuntimeSnapshot()
            }

            appendSection("Thread Snapshot") {
                appendThreadSnapshot()
            }

            appendSection("Best-Effort System Probes") {
                appendBestEffortSystemProbes()
            }

            appendSection("Settings Snapshot") {
                appendSettingsSummary(settings)
            }

            appendSection("Recent Chat Errors") {
                appendChatErrors()
            }

            appendSection("AI Generation Logs") {
                appendAiLogs()
            }

            appendSection("HTTP Logs (redacted)") {
                appendHttpLogs()
            }

            appendSection("In-App Diagnostics") {
                appendLine(Diagnostics.formatSnapshot())
            }

            appendSection("Own Logcat Tail") {
                appendLine(readOwnLogcatTail())
            }
        }
    }

    private fun StringBuilder.appendMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val systemMemory = ActivityManager.MemoryInfo().also {
            activityManager?.getMemoryInfo(it)
        }

        appendLine("runtime.max=${runtime.maxMemory().toMbString()}")
        appendLine("runtime.total=${runtime.totalMemory().toMbString()}")
        appendLine("runtime.free=${runtime.freeMemory().toMbString()}")
        appendLine("pss.total=${memoryInfo.totalPss}KB")
        appendLine("pss.javaHeap=${memoryInfo.dalvikPss}KB")
        appendLine("pss.nativeHeap=${memoryInfo.nativePss}KB")
        appendLine("pss.graphics=${memoryInfo.getMemoryStat("summary.graphics") ?: "n/a"}KB")
        appendLine("system.avail=${systemMemory.availMem.toMbString()}")
        appendLine("system.lowMemory=${systemMemory.lowMemory}")
        appendLine("system.threshold=${systemMemory.threshold.toMbString()}")
    }

    private fun StringBuilder.appendRuntimeSnapshot() {
        val runtime = Runtime.getRuntime()
        appendLine("processors=${runtime.availableProcessors()}")
        appendLine("nativeHeap.size=${Debug.getNativeHeapSize().toMbString()}")
        appendLine("nativeHeap.allocated=${Debug.getNativeHeapAllocatedSize().toMbString()}")
        appendLine("nativeHeap.free=${Debug.getNativeHeapFreeSize().toMbString()}")
        appendLine("fd.count=${countOpenFileDescriptors()}")
        appendLine("cache.usable=${context.cacheDir.usableSpace.toMbString()} total=${context.cacheDir.totalSpace.toMbString()}")
        appendLine("files.usable=${context.filesDir.usableSpace.toMbString()} total=${context.filesDir.totalSpace.toMbString()}")

        appendLine("proc.status:")
        readProcStatusLines().forEach { appendLine("  $it") }

        appendLine("runtimeStats:")
        runCatching { Debug.getRuntimeStats() }
            .getOrNull()
            ?.toSortedMap()
            ?.forEach { (key, value) -> appendLine("  $key=$value") }
            ?: appendLine("  unavailable")
    }

    private fun StringBuilder.appendThreadSnapshot() {
        val traces = Thread.getAllStackTraces()
        val mainThread = Looper.getMainLooper().thread
        val stateCounts = traces.keys.groupingBy { it.state }.eachCount()
        appendLine("threadCount=${traces.size}")
        appendLine("states=$stateCounts")
        traces.entries
            .sortedWith(
                compareBy<Map.Entry<Thread, Array<StackTraceElement>>>(
                    { if (it.key == mainThread) 0 else 1 },
                    { it.key.name.lowercase(Locale.ROOT) }
                )
            )
            .take(32)
            .forEachIndexed { index, (thread, stack) ->
                appendLine(
                    "#$index name=${redact(thread.name)} state=${thread.state} " +
                        "daemon=${thread.isDaemon} priority=${thread.priority}"
                )
                stack.take(8).forEach { element ->
                    appendLine("  at $element")
                }
                if (stack.size > 8) {
                    appendLine("  ... ${stack.size - 8} more")
                }
            }
    }

    private fun StringBuilder.appendBestEffortSystemProbes() {
        appendCommandProbe("dumpsys gfxinfo", listOf("dumpsys", "gfxinfo", BuildConfig.APPLICATION_ID))
        appendCommandProbe(
            "dumpsys gfxinfo framestats",
            listOf("dumpsys", "gfxinfo", BuildConfig.APPLICATION_ID, "framestats")
        )
        appendCommandProbe("dumpsys meminfo", listOf("dumpsys", "meminfo", BuildConfig.APPLICATION_ID))
    }

    private fun StringBuilder.appendCommandProbe(title: String, command: List<String>) {
        appendLine("### $title")
        appendLine(runBestEffortCommand(command))
    }

    private fun StringBuilder.appendSettingsSummary(settings: Settings) {
        val display = settings.displaySetting
        appendLine("developerMode=${settings.developerMode}")
        appendLine("providers=${settings.providers.size} enabled=${settings.providers.count { it.enabled }}")
        settings.providers.forEachIndexed { index, provider ->
            appendLine(
                "provider[$index]=${provider.name} type=${provider.javaClass.simpleName} " +
                    "enabled=${provider.enabled} models=${provider.models.size} apiKey=${provider.apiKeyState()}"
            )
        }
        appendLine("assistants=${settings.assistants.size} currentAssistant=${settings.assistantId}")
        appendLine("webSearch=${settings.enableWebSearch} mcpServers=${settings.mcpServers.size}")
        appendLine("stPresetEnabled=${settings.stPresetEnabled} stCompatScriptEnabled=${settings.stCompatScriptEnabled}")
        appendLine("globalRegexEnabled=${settings.globalRegexEnabled} globalRegexes=${settings.globalRegexes.size}")
        appendLine("termuxCommandMode=${settings.termuxCommandModeEnabled} termuxPty=${settings.termuxPtyInteractiveEnabled}")
        appendLine("display.showAssistantBubble=${display.showAssistantBubble}")
        appendLine("display.fontSizeRatio=${display.fontSizeRatio}")
        appendLine("display.enableBlurEffect=${display.enableBlurEffect}")
        appendLine("display.enableAutoScroll=${display.enableAutoScroll}")
        appendLine("display.enableLatexRendering=${display.enableLatexRendering}")
        appendLine("display.enableCodeBlockRichRender=${display.enableCodeBlockRichRender}")
        appendLine("display.codeBlockRenderMaxDepth=${display.codeBlockRenderMaxDepth}")
        appendLine("display.codeBlockAutoWrap=${display.codeBlockAutoWrap}")
        appendLine("display.codeBlockAutoCollapse=${display.codeBlockAutoCollapse}")
        appendLine("display.showLineNumbers=${display.showLineNumbers}")
    }

    private fun StringBuilder.appendChatErrors() {
        val errors = chatService.errors.value
        if (errors.isEmpty()) {
            appendLine("(empty)")
            return
        }
        errors.takeLast(16).forEachIndexed { index, error ->
            appendLine("#$index time=${formatTime(error.timestamp)} conversation=${error.conversationId}")
            appendLine(formatThrowable(error.error).trim().take(8_000))
            appendLine()
        }
    }

    private fun StringBuilder.appendAiLogs() {
        val logs = aiLoggingManager.getLogs().value
        if (logs.isEmpty()) {
            appendLine("(empty)")
            return
        }
        logs.takeLast(16).forEachIndexed { index, log ->
            when (log) {
                is AILogging.Generation -> {
                    appendLine(
                        "#$index generation stream=${log.stream} provider=${log.providerSetting.name} " +
                            "providerType=${log.providerSetting.javaClass.simpleName} model=${log.params.model.modelId}"
                    )
                    appendLine(
                        "params temperature=${log.params.temperature} topP=${log.params.topP} maxTokens=${log.params.maxTokens} " +
                            "reasoning=${log.params.reasoningLevel} tools=${log.params.tools.size} " +
                            "customHeaders=${log.params.customHeaders.size} customBody=${log.params.customBody.size}"
                    )
                    appendLine("messages=${log.messages.size}")
                    log.messages.takeLast(8).forEach { message ->
                        appendLine(
                            "  role=${message.role} parts=${message.parts.size} textChars=${message.parts.textChars()} " +
                                "tools=${message.parts.filterIsInstance<UIMessagePart.Tool>().size}"
                        )
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendHttpLogs() {
        val logs = Logging.getRecentLogs()
        if (logs.isEmpty()) {
            appendLine("(empty)")
            return
        }
        logs.take(80).forEachIndexed { index, log ->
            when (log) {
                is LogEntry.TextLog -> {
                    appendLine("#$index text ${formatTime(log.timestamp)} tag=${log.tag}")
                    appendLine(redact(log.message).take(MAX_BODY_SNIPPET))
                }

                is LogEntry.RequestLog -> {
                    appendLine(
                        "#$index request ${formatTime(log.timestamp)} ${log.method} ${redactUrl(log.url)} " +
                            "status=${log.responseCode ?: "n/a"} durationMs=${log.durationMs ?: "n/a"}"
                    )
                    log.error?.let { appendLine("error=${redact(it)}") }
                    appendLine("requestHeaders=${log.requestHeaders.redactedHeaderSummary()}")
                    appendLine("responseHeaders=${log.responseHeaders.redactedHeaderSummary()}")
                    appendLine("requestBody=${log.requestBody?.bodySummary() ?: "null"}")
                    appendLine("responseBody=${log.responseBody?.redactedSnippet() ?: "null"}")
                }
            }
            appendLine()
        }
    }

    private fun readOwnLogcatTail(): String {
        return runCatching {
            val process = ProcessBuilder(
                "logcat",
                "-d",
                "-t",
                "600",
                "--pid",
                Process.myPid().toString()
            ).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            redact(text).takeLast(MAX_LOGCAT_CHARS).ifBlank { "(empty)" }
        }.getOrElse {
            "unavailable: ${it::class.java.simpleName}: ${it.message}"
        }
    }

    private fun StringBuilder.appendSection(
        title: String,
        block: StringBuilder.() -> Unit,
    ) {
        appendLine()
        appendLine("## $title")
        block()
    }
}

private fun ProviderSetting.apiKeyState(): String = when (this) {
    is ProviderSetting.OpenAI -> apiKey.maskedState()
    is ProviderSetting.Google -> {
        val keyState = apiKey.maskedState()
        if (useServiceAccount) {
            "$keyState serviceAccount=${serviceAccountEmail.maskedEmailState()} privateKey=${privateKey.maskedState()}"
        } else {
            keyState
        }
    }
    is ProviderSetting.Claude -> apiKey.maskedState()
}

private fun List<UIMessagePart>.textChars(): Int = sumOf { part ->
    when (part) {
        is UIMessagePart.Text -> part.text.length
        is UIMessagePart.Reasoning -> part.reasoning.length
        is UIMessagePart.Tool -> part.input.length + part.output.textChars()
        else -> 0
    }
}

private fun String.maskedState(): String = if (isBlank()) {
    "empty"
} else {
    "set(length=$length)"
}

private fun String.maskedEmailState(): String = if (isBlank()) {
    "empty"
} else {
    val domain = substringAfter('@', missingDelimiterValue = "")
    "set(domain=${domain.ifBlank { "unknown" }})"
}

private fun Map<String, String>.redactedHeaderSummary(): String {
    if (isEmpty()) return "{}"
    return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        val redacted = if (DiagnosticRedactor.isSensitiveHeader(key)) "<redacted>" else value.take(96)
        "$key=$redacted"
    }
}

private fun String.bodySummary(): String = "omitted(length=${length}, snippet=${redactedSnippet()})"

private fun String.redactedSnippet(): String {
    val redacted = redact(this)
    return redacted.take(MAX_BODY_SNIPPET).let {
        if (redacted.length > it.length) "$it...[truncated ${redacted.length - it.length} chars]" else it
    }
}

private fun redactUrl(url: String): String {
    return DiagnosticRedactor.url(url)
}

private fun redact(text: String): String {
    return DiagnosticRedactor.text(text)
}

private fun countOpenFileDescriptors(): String {
    return runCatching {
        File("/proc/self/fd").list()?.size?.toString() ?: "unavailable"
    }.getOrElse {
        "unavailable: ${it::class.java.simpleName}"
    }
}

private fun readProcStatusLines(): List<String> {
    val prefixes = listOf(
        "Name:",
        "State:",
        "Tgid:",
        "Pid:",
        "PPid:",
        "VmPeak:",
        "VmSize:",
        "VmHWM:",
        "VmRSS:",
        "RssAnon:",
        "RssFile:",
        "RssShmem:",
        "VmData:",
        "VmStk:",
        "VmExe:",
        "VmLib:",
        "Threads:",
        "voluntary_ctxt_switches:",
        "nonvoluntary_ctxt_switches:",
    )
    return runCatching {
        File("/proc/self/status")
            .readLines()
            .filter { line -> prefixes.any { prefix -> line.startsWith(prefix) } }
    }.getOrElse {
        listOf("unavailable: ${it::class.java.simpleName}: ${it.message}")
    }
}

private fun runBestEffortCommand(command: List<String>): String {
    return runCatching {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val readerThread = thread(name = "diagnostics-command-reader") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < MAX_COMMAND_OUTPUT_CHARS) {
                        output.appendLine(line)
                    }
                }
            }
        }
        val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
        readerThread.join(500)
        val exit = if (finished) process.exitValue().toString() else "timeout"
        val text = redact(output.toString()).take(MAX_COMMAND_OUTPUT_CHARS)
        buildString {
            appendLine("command=${command.joinToString(" ")} exit=$exit")
            append(text.ifBlank { "(empty)" })
            if (output.length > MAX_COMMAND_OUTPUT_CHARS) {
                appendLine()
                append("[truncated ${output.length - MAX_COMMAND_OUTPUT_CHARS} chars]")
            }
        }.trimEnd()
    }.getOrElse {
        "command=${command.joinToString(" ")} unavailable: ${it::class.java.simpleName}: ${it.message}"
    }
}

private fun formatThrowable(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    return redact(writer.toString())
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

private fun fileTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}

private fun Long.toMbString(): String = "${this / 1024 / 1024}MB"
