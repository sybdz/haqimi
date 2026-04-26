package me.rerere.rikkahub.data.diagnostics

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private const val FRAME_SLOW_MS = 32.0
private const val FRAME_VERY_SLOW_MS = 80.0
private const val FRAME_FROZEN_MS = 700.0
private const val MAIN_THREAD_PULSE_MS = 250L
private const val MAIN_THREAD_STALL_MS = 500L
private const val MEMORY_SAMPLE_INTERVAL_MS = 30_000L
private const val MAX_RECENT_SLOW_FRAMES = 80
private const val MAX_RECENT_STALLS = 24
private const val MAX_RECENT_MEMORY_SAMPLES = 32
private const val MAX_RECENT_TRIM_EVENTS = 24

class DiagnosticsMonitor(
    private val appScope: AppScope,
) {
    private val started = AtomicBoolean(false)
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameMetricsThread = HandlerThread("DiagnosticsFrameMetrics").apply { start() }
    private val frameMetricsHandler = Handler(frameMetricsThread.looper)
    private val frameMetricsListeners = IdentityHashMap<Activity, Window.OnFrameMetricsAvailableListener>()
    private val recentSlowFrames = ArrayDeque<SlowFrameSample>(MAX_RECENT_SLOW_FRAMES)
    private val recentStalls = ArrayDeque<MainThreadStallSample>(MAX_RECENT_STALLS)
    private val recentMemorySamples = ArrayDeque<MemorySample>(MAX_RECENT_MEMORY_SAMPLES)
    private val recentTrimEvents = ArrayDeque<TrimMemoryEvent>(MAX_RECENT_TRIM_EVENTS)
    private val processStartRealtimeMs = SystemClock.elapsedRealtime()

    @Volatile
    private var lastMainPulseRealtimeMs = SystemClock.elapsedRealtime()

    private var foregroundActivityCount = 0
    private var resumedActivity: String? = null
    private var foregroundSinceRealtimeMs: Long? = null
    private var totalForegroundMs = 0L
    private var activityCreatedCount = 0
    private var activityDestroyedCount = 0
    private var totalFrames = 0L
    private var slowFrames = 0L
    private var verySlowFrames = 0L
    private var frozenFrames = 0L
    private var droppedFrameCallbacks = 0L
    private var frameDurationSumMs = 0.0
    private var maxFrameMs = 0.0
    private var lastSlowFrameLogRealtimeMs = 0L
    private var mainThreadStallCount = 0L
    private var maxMainThreadStallMs = 0L

    private val mainPulse = object : Runnable {
        override fun run() {
            lastMainPulseRealtimeMs = SystemClock.elapsedRealtime()
            mainHandler.postDelayed(this, MAIN_THREAD_PULSE_MS)
        }
    }

    fun start(application: Application) {
        if (!started.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        mainHandler.post(mainPulse)
        startMainThreadWatchdog()
        startMemorySampler()
        Diagnostics.info(
            category = "diagnostics",
            message = "performance monitor started",
            metadata = mapOf("pid" to android.os.Process.myPid())
        )
    }

    fun recordTrimMemory(level: Int) {
        val event = TrimMemoryEvent(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            levelName = trimMemoryLevelName(level),
            memory = collectMemorySample(reason = "trimMemory")
        )
        synchronized(lock) {
            recentTrimEvents.addBounded(event, MAX_RECENT_TRIM_EVENTS)
        }
        Diagnostics.warn(
            category = "memory",
            message = "trim memory",
            metadata = mapOf(
                "level" to level,
                "levelName" to event.levelName,
                "heapUsedMb" to event.memory.heapUsedMb,
                "pssTotalKb" to event.memory.totalPssKb
            )
        )
    }

    fun recordLowMemory() {
        val sample = collectMemorySample(reason = "lowMemory")
        synchronized(lock) {
            recentMemorySamples.addBounded(sample, MAX_RECENT_MEMORY_SAMPLES)
        }
        Diagnostics.warn(
            category = "memory",
            message = "low memory",
            metadata = mapOf(
                "heapUsedMb" to sample.heapUsedMb,
                "heapMaxMb" to sample.heapMaxMb,
                "pssTotalKb" to sample.totalPssKb
            )
        )
    }

    fun clear() {
        synchronized(lock) {
            foregroundSinceRealtimeMs = if (foregroundActivityCount > 0) {
                SystemClock.elapsedRealtime()
            } else {
                null
            }
            totalForegroundMs = 0L
            activityCreatedCount = 0
            activityDestroyedCount = 0
            totalFrames = 0L
            slowFrames = 0L
            verySlowFrames = 0L
            frozenFrames = 0L
            droppedFrameCallbacks = 0L
            frameDurationSumMs = 0.0
            maxFrameMs = 0.0
            lastSlowFrameLogRealtimeMs = 0L
            mainThreadStallCount = 0L
            maxMainThreadStallMs = 0L
            recentSlowFrames.clear()
            recentStalls.clear()
            recentMemorySamples.clear()
            recentTrimEvents.clear()
        }
    }

    fun formatSnapshot(): String {
        val snapshot = snapshot()
        return buildString {
            appendLine("uptimeMs=${snapshot.uptimeMs}")
            appendLine("foreground=${snapshot.foreground} foregroundMs=${snapshot.foregroundMs}")
            appendLine("resumedActivity=${snapshot.resumedActivity ?: "none"}")
            appendLine("activity.created=${snapshot.activityCreatedCount} destroyed=${snapshot.activityDestroyedCount}")
            appendLine(
                "frames.total=${snapshot.frameStats.totalFrames} slow=${snapshot.frameStats.slowFrames} " +
                    "verySlow=${snapshot.frameStats.verySlowFrames} frozen=${snapshot.frameStats.frozenFrames} " +
                    "droppedCallbacks=${snapshot.frameStats.droppedCallbacks}"
            )
            appendLine(
                "frames.avgMs=${snapshot.frameStats.averageFrameMs.formatMs()} " +
                    "maxMs=${snapshot.frameStats.maxFrameMs.formatMs()} " +
                    "slowRate=${snapshot.frameStats.slowFrameRate.formatPercent()}"
            )
            appendLine(
                "mainThread.stalls=${snapshot.mainThreadStallCount} " +
                    "maxStallMs=${snapshot.maxMainThreadStallMs}"
            )
            appendLine("recentSlowFrames:")
            if (snapshot.recentSlowFrames.isEmpty()) {
                appendLine("  (empty)")
            } else {
                snapshot.recentSlowFrames.forEach { sample ->
                    appendLine(
                        "  ${DiagnosticFormatter.format(sample.timestampMillis)} " +
                            "activity=${sample.activity} totalMs=${sample.totalMs.formatMs()} " +
                            "top=${sample.topDurationName}:${sample.topDurationMs.formatMs()}ms " +
                            "dropped=${sample.dropCountSinceLastInvocation}"
                    )
                }
            }
            appendLine("recentMainThreadStalls:")
            if (snapshot.recentStalls.isEmpty()) {
                appendLine("  (empty)")
            } else {
                snapshot.recentStalls.forEach { sample ->
                    appendLine(
                        "  ${DiagnosticFormatter.format(sample.timestampMillis)} " +
                            "stallMs=${sample.stallMs} top=${sample.topStackLine}"
                    )
                    sample.stack.take(10).forEach { line ->
                        appendLine("    at $line")
                    }
                }
            }
            appendLine("recentMemorySamples:")
            if (snapshot.recentMemorySamples.isEmpty()) {
                appendLine("  (empty)")
            } else {
                snapshot.recentMemorySamples.forEach { sample ->
                    appendLine(
                        "  ${DiagnosticFormatter.format(sample.timestampMillis)} reason=${sample.reason} " +
                            "heap=${sample.heapUsedMb}/${sample.heapMaxMb}MB " +
                            "pss=${sample.totalPssKb}KB native=${sample.nativePssKb}KB " +
                            "graphics=${sample.graphicsPssKb ?: "n/a"}KB threads=${sample.threadCount}"
                    )
                }
            }
            appendLine("trimMemoryEvents:")
            if (snapshot.recentTrimEvents.isEmpty()) {
                appendLine("  (empty)")
            } else {
                snapshot.recentTrimEvents.forEach { event ->
                    appendLine(
                        "  ${DiagnosticFormatter.format(event.timestampMillis)} " +
                            "level=${event.level}(${event.levelName}) " +
                            "heap=${event.memory.heapUsedMb}/${event.memory.heapMaxMb}MB " +
                            "pss=${event.memory.totalPssKb}KB"
                    )
                }
            }
        }.trimEnd()
    }

    private fun snapshot(): DiagnosticsMonitorSnapshot {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val foregroundMs = totalForegroundMs + (foregroundSinceRealtimeMs?.let { now - it } ?: 0L)
            return DiagnosticsMonitorSnapshot(
                uptimeMs = now - processStartRealtimeMs,
                foreground = foregroundActivityCount > 0,
                foregroundMs = foregroundMs,
                resumedActivity = resumedActivity,
                activityCreatedCount = activityCreatedCount,
                activityDestroyedCount = activityDestroyedCount,
                frameStats = FrameStatsSnapshot(
                    totalFrames = totalFrames,
                    slowFrames = slowFrames,
                    verySlowFrames = verySlowFrames,
                    frozenFrames = frozenFrames,
                    droppedCallbacks = droppedFrameCallbacks,
                    averageFrameMs = if (totalFrames == 0L) 0.0 else frameDurationSumMs / totalFrames,
                    maxFrameMs = maxFrameMs,
                    slowFrameRate = if (totalFrames == 0L) 0.0 else slowFrames.toDouble() / totalFrames,
                ),
                mainThreadStallCount = mainThreadStallCount,
                maxMainThreadStallMs = maxMainThreadStallMs,
                recentSlowFrames = recentSlowFrames.toList(),
                recentStalls = recentStalls.toList(),
                recentMemorySamples = recentMemorySamples.toList(),
                recentTrimEvents = recentTrimEvents.toList(),
            )
        }
    }

    private fun startMainThreadWatchdog() {
        appScope.launch(Dispatchers.Default) {
            var lastReportedPulseMs = 0L
            while (isActive) {
                val pulseMs = lastMainPulseRealtimeMs
                val stallMs = SystemClock.elapsedRealtime() - pulseMs
                if (stallMs >= MAIN_THREAD_STALL_MS && pulseMs != lastReportedPulseMs) {
                    lastReportedPulseMs = pulseMs
                    recordMainThreadStall(stallMs)
                }
                delay(MAIN_THREAD_PULSE_MS)
            }
        }
    }

    private fun startMemorySampler() {
        appScope.launch(Dispatchers.Default) {
            recordMemorySample("startup")
            while (isActive) {
                delay(MEMORY_SAMPLE_INTERVAL_MS)
                recordMemorySample("periodic")
            }
        }
    }

    private fun recordMemorySample(reason: String) {
        val sample = collectMemorySample(reason)
        synchronized(lock) {
            recentMemorySamples.addBounded(sample, MAX_RECENT_MEMORY_SAMPLES)
        }
        val heapUsage = if (sample.heapMaxMb == 0L) 0.0 else sample.heapUsedMb.toDouble() / sample.heapMaxMb
        if (heapUsage >= 0.85) {
            Diagnostics.warn(
                category = "memory",
                message = "high heap usage",
                metadata = mapOf(
                    "reason" to reason,
                    "heapUsedMb" to sample.heapUsedMb,
                    "heapMaxMb" to sample.heapMaxMb,
                    "pssTotalKb" to sample.totalPssKb
                )
            )
        }
    }

    private fun collectMemorySample(reason: String): MemorySample {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        val heapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
        return MemorySample(
            timestampMillis = System.currentTimeMillis(),
            reason = reason,
            heapUsedMb = heapUsedBytes.bytesToMb(),
            heapMaxMb = runtime.maxMemory().bytesToMb(),
            totalPssKb = memoryInfo.totalPss,
            javaPssKb = memoryInfo.dalvikPss,
            nativePssKb = memoryInfo.nativePss,
            graphicsPssKb = memoryInfo.getMemoryStat("summary.graphics")?.toIntOrNull(),
            threadCount = Thread.getAllStackTraces().size,
        )
    }

    private fun recordMainThreadStall(stallMs: Long) {
        val stack = Looper.getMainLooper().thread.stackTrace.map { it.toString() }
        val sample = MainThreadStallSample(
            timestampMillis = System.currentTimeMillis(),
            stallMs = stallMs,
            stack = stack.take(18),
        )
        synchronized(lock) {
            mainThreadStallCount++
            maxMainThreadStallMs = max(maxMainThreadStallMs, stallMs)
            recentStalls.addBounded(sample, MAX_RECENT_STALLS)
        }
        Diagnostics.warn(
            category = "main_thread",
            message = "main thread stall",
            metadata = mapOf(
                "stallMs" to stallMs,
                "top" to sample.topStackLine
            )
        )
    }

    private fun attachFrameMetrics(activity: Activity) {
        if (frameMetricsListeners.containsKey(activity)) return
        val activityName = activity.shortName()
        val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, dropCountSinceLastInvocation ->
            recordFrame(activityName, frameMetrics, dropCountSinceLastInvocation)
        }
        runCatching {
            activity.window.addOnFrameMetricsAvailableListener(listener, frameMetricsHandler)
            frameMetricsListeners[activity] = listener
        }.onFailure {
            Diagnostics.warn(
                category = "frame",
                message = "frame metrics unavailable",
                metadata = mapOf("activity" to activityName, "error" to it.message)
            )
        }
    }

    private fun detachFrameMetrics(activity: Activity) {
        val listener = frameMetricsListeners.remove(activity) ?: return
        runCatching {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
        }
    }

    private fun recordFrame(
        activity: String,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        val totalMs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION).nsToMs()
        if (totalMs <= 0.0) return
        val topDuration = frameMetrics.topDuration()
        val sample = if (totalMs >= FRAME_SLOW_MS) {
            SlowFrameSample(
                timestampMillis = System.currentTimeMillis(),
                activity = activity,
                totalMs = totalMs,
                topDurationName = topDuration.name,
                topDurationMs = topDuration.durationMs,
                dropCountSinceLastInvocation = dropCountSinceLastInvocation,
            )
        } else {
            null
        }
        var shouldLog = false
        var logAsFrozen = false
        synchronized(lock) {
            totalFrames++
            frameDurationSumMs += totalMs
            maxFrameMs = max(maxFrameMs, totalMs)
            droppedFrameCallbacks += dropCountSinceLastInvocation.toLong()
            if (totalMs >= FRAME_SLOW_MS) slowFrames++
            if (totalMs >= FRAME_VERY_SLOW_MS) verySlowFrames++
            if (totalMs >= FRAME_FROZEN_MS) frozenFrames++
            sample?.let { recentSlowFrames.addBounded(it, MAX_RECENT_SLOW_FRAMES) }

            val now = SystemClock.elapsedRealtime()
            if (totalMs >= FRAME_VERY_SLOW_MS && now - lastSlowFrameLogRealtimeMs >= 1_000L) {
                shouldLog = true
                logAsFrozen = totalMs >= FRAME_FROZEN_MS
                lastSlowFrameLogRealtimeMs = now
            }
        }
        if (shouldLog) {
            val metadata = mapOf(
                "activity" to activity,
                "totalMs" to totalMs.formatMs(),
                "top" to topDuration.name,
                "topMs" to topDuration.durationMs.formatMs(),
                "dropped" to dropCountSinceLastInvocation
            )
            if (logAsFrozen) {
                Diagnostics.warn("frame", "frozen frame", metadata)
            } else {
                Diagnostics.info("frame", "slow frame", metadata)
            }
        }
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            synchronized(lock) {
                activityCreatedCount++
            }
            attachFrameMetrics(activity)
            Diagnostics.info(
                category = "lifecycle",
                message = "activity created",
                metadata = mapOf("activity" to activity.shortName())
            )
        }

        override fun onActivityStarted(activity: Activity) {
            var foreground = false
            synchronized(lock) {
                foregroundActivityCount++
                if (foregroundActivityCount == 1) {
                    foreground = true
                    foregroundSinceRealtimeMs = SystemClock.elapsedRealtime()
                }
            }
            if (foreground) {
                Diagnostics.info("lifecycle", "app foreground", mapOf("activity" to activity.shortName()))
            }
        }

        override fun onActivityResumed(activity: Activity) {
            synchronized(lock) {
                resumedActivity = activity.shortName()
            }
            Diagnostics.info("lifecycle", "activity resumed", mapOf("activity" to activity.shortName()))
        }

        override fun onActivityPaused(activity: Activity) {
            Diagnostics.debug("lifecycle", "activity paused", mapOf("activity" to activity.shortName()))
        }

        override fun onActivityStopped(activity: Activity) {
            var background = false
            synchronized(lock) {
                foregroundActivityCount = max(0, foregroundActivityCount - 1)
                if (foregroundActivityCount == 0) {
                    background = true
                    foregroundSinceRealtimeMs?.let {
                        totalForegroundMs += SystemClock.elapsedRealtime() - it
                    }
                    foregroundSinceRealtimeMs = null
                    resumedActivity = null
                }
            }
            if (background) {
                Diagnostics.info("lifecycle", "app background", mapOf("activity" to activity.shortName()))
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            synchronized(lock) {
                activityDestroyedCount++
            }
            detachFrameMetrics(activity)
            Diagnostics.info(
                category = "lifecycle",
                message = "activity destroyed",
                metadata = mapOf("activity" to activity.shortName())
            )
        }
    }
}

private data class DiagnosticsMonitorSnapshot(
    val uptimeMs: Long,
    val foreground: Boolean,
    val foregroundMs: Long,
    val resumedActivity: String?,
    val activityCreatedCount: Int,
    val activityDestroyedCount: Int,
    val frameStats: FrameStatsSnapshot,
    val mainThreadStallCount: Long,
    val maxMainThreadStallMs: Long,
    val recentSlowFrames: List<SlowFrameSample>,
    val recentStalls: List<MainThreadStallSample>,
    val recentMemorySamples: List<MemorySample>,
    val recentTrimEvents: List<TrimMemoryEvent>,
)

private data class FrameStatsSnapshot(
    val totalFrames: Long,
    val slowFrames: Long,
    val verySlowFrames: Long,
    val frozenFrames: Long,
    val droppedCallbacks: Long,
    val averageFrameMs: Double,
    val maxFrameMs: Double,
    val slowFrameRate: Double,
)

private data class SlowFrameSample(
    val timestampMillis: Long,
    val activity: String,
    val totalMs: Double,
    val topDurationName: String,
    val topDurationMs: Double,
    val dropCountSinceLastInvocation: Int,
)

private data class MainThreadStallSample(
    val timestampMillis: Long,
    val stallMs: Long,
    val stack: List<String>,
) {
    val topStackLine: String = stack.firstOrNull().orEmpty()
}

private data class MemorySample(
    val timestampMillis: Long,
    val reason: String,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val totalPssKb: Int,
    val javaPssKb: Int,
    val nativePssKb: Int,
    val graphicsPssKb: Int?,
    val threadCount: Int,
)

private data class TrimMemoryEvent(
    val timestampMillis: Long,
    val level: Int,
    val levelName: String,
    val memory: MemorySample,
)

private data class FrameDuration(
    val name: String,
    val durationMs: Double,
)

private fun <T> ArrayDeque<T>.addBounded(item: T, maxSize: Int) {
    if (size >= maxSize) removeFirst()
    addLast(item)
}

private fun FrameMetrics.topDuration(): FrameDuration {
    return listOf(
        "unknownDelay" to FrameMetrics.UNKNOWN_DELAY_DURATION,
        "input" to FrameMetrics.INPUT_HANDLING_DURATION,
        "animation" to FrameMetrics.ANIMATION_DURATION,
        "layoutMeasure" to FrameMetrics.LAYOUT_MEASURE_DURATION,
        "draw" to FrameMetrics.DRAW_DURATION,
        "sync" to FrameMetrics.SYNC_DURATION,
        "commandIssue" to FrameMetrics.COMMAND_ISSUE_DURATION,
        "swapBuffers" to FrameMetrics.SWAP_BUFFERS_DURATION,
    ).map { (name, metric) ->
        FrameDuration(name, getMetric(metric).nsToMs())
    }.maxByOrNull { it.durationMs } ?: FrameDuration("unknown", 0.0)
}

private fun Long.nsToMs(): Double = this / 1_000_000.0

private fun Long.bytesToMb(): Long = this / 1024 / 1024

private fun Double.formatMs(): String = String.format(Locale.US, "%.1f", this)

private fun Double.formatPercent(): String = String.format(Locale.US, "%.1f%%", this * 100.0)

private fun Activity.shortName(): String = javaClass.simpleName.ifBlank { javaClass.name }

@Suppress("DEPRECATION")
private fun trimMemoryLevelName(level: Int): String = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
    ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
    else -> "UNKNOWN"
}
