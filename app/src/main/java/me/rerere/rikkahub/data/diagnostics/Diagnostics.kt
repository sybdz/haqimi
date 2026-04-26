package me.rerere.rikkahub.data.diagnostics

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

private const val TAG = "RikkaDiagnostics"
private const val MAX_ENTRIES = 800

enum class DiagnosticLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class DiagnosticEntry(
    val timestampMillis: Long,
    val elapsedRealtimeMillis: Long,
    val level: DiagnosticLevel,
    val category: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun format(): String {
        val timestamp = DiagnosticFormatter.format(timestampMillis)
        val details = metadata.entries
            .joinToString(separator = " ") { (key, value) -> "$key=${value.replace('\n', ' ')}" }
            .takeIf { it.isNotBlank() }
            ?.let { " $it" }
            .orEmpty()
        return "$timestamp ${level.name.padEnd(5)} [$category] $message$details"
    }
}

object Diagnostics {
    private val entries = ArrayDeque<DiagnosticEntry>(MAX_ENTRIES)

    fun debug(
        category: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) = record(DiagnosticLevel.DEBUG, category, message, metadata)

    fun info(
        category: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) = record(DiagnosticLevel.INFO, category, message, metadata)

    fun warn(
        category: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) = record(DiagnosticLevel.WARN, category, message, metadata)

    fun error(
        category: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val errorMetadata = if (throwable == null) {
            metadata
        } else {
            metadata + mapOf(
                "throwable" to throwable::class.java.name,
                "error" to (throwable.message ?: throwable.toString())
            )
        }
        record(DiagnosticLevel.ERROR, category, message, errorMetadata)
    }

    fun duration(
        category: String,
        name: String,
        elapsedMs: Long,
        thresholdMs: Long = 0,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        if (elapsedMs < thresholdMs) return
        val level = when {
            elapsedMs >= 250 -> DiagnosticLevel.WARN
            elapsedMs >= 50 -> DiagnosticLevel.INFO
            else -> DiagnosticLevel.DEBUG
        }
        record(
            level = level,
            category = category,
            message = "$name took ${elapsedMs}ms",
            metadata = metadata + ("elapsedMs" to elapsedMs)
        )
    }

    inline fun <T> trace(
        category: String,
        name: String,
        thresholdMs: Long = 0,
        metadata: Map<String, Any?> = emptyMap(),
        block: () -> T,
    ): T {
        val section = "$category:$name".take(120)
        runCatching { Trace.beginSection(section) }
        val startNs = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } catch (throwable: Throwable) {
            error(category, "$name failed", throwable, metadata)
            throw throwable
        } finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
            duration(category, name, elapsedMs, thresholdMs, metadata)
            runCatching { Trace.endSection() }
        }
    }

    suspend inline fun <T> traceSuspend(
        category: String,
        name: String,
        thresholdMs: Long = 0,
        metadata: Map<String, Any?> = emptyMap(),
        block: suspend () -> T,
    ): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } catch (throwable: Throwable) {
            error(category, "$name failed", throwable, metadata)
            throw throwable
        } finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
            duration(category, name, elapsedMs, thresholdMs, metadata)
        }
    }

    fun snapshot(): List<DiagnosticEntry> = synchronized(entries) {
        entries.toList()
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    fun formatSnapshot(): String {
        val snapshot = snapshot()
        if (snapshot.isEmpty()) return "(empty)"
        return snapshot.joinToString(separator = "\n") { it.format() }
    }

    fun record(
        level: DiagnosticLevel,
        category: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val entry = DiagnosticEntry(
            timestampMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            level = level,
            category = category.take(48),
            message = DiagnosticRedactor.text(message).take(512),
            metadata = metadata
                .filterValues { it != null }
                .mapValues { (_, value) -> DiagnosticRedactor.text(value.toString()).take(512) }
        )
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
        if (level != DiagnosticLevel.DEBUG) {
            Log.println(level.toLogPriority(), TAG, entry.format())
        }
    }
}

internal object DiagnosticFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun format(timestampMillis: Long): String = formatter.format(Instant.ofEpochMilli(timestampMillis))
}

private fun DiagnosticLevel.toLogPriority(): Int = when (this) {
    DiagnosticLevel.DEBUG -> Log.DEBUG
    DiagnosticLevel.INFO -> Log.INFO
    DiagnosticLevel.WARN -> Log.WARN
    DiagnosticLevel.ERROR -> Log.ERROR
}
