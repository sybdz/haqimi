package me.rerere.rikkahub.data.ai.tools.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TermuxCommandManager(
    private val context: Context,
) {
    private val executionIdCounter = AtomicInteger(1000)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxResult>>()

    suspend fun run(request: TermuxRunCommandRequest): TermuxResult {
        ensureTermuxInstalled()
        ensurePermissionGranted()

        val executionId = executionIdCounter.getAndIncrement()
        val deferred = CompletableDeferred<TermuxResult>()
        pending[executionId] = deferred

        val resultIntent = Intent(context, TermuxResultService::class.java).apply {
            putExtra(TermuxResultService.EXTRA_EXECUTION_ID, executionId)
        }
        val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getService(
            context,
            executionId,
            resultIntent,
            pendingIntentFlags,
        )

        val intent = Intent().apply {
            setClassName(TermuxProtocol.TERMUX_PACKAGE_NAME, TermuxProtocol.RUN_COMMAND_SERVICE)
            action = TermuxProtocol.ACTION_RUN_COMMAND

            putExtra(TermuxProtocol.EXTRA_COMMAND_PATH, request.commandPath)
            putExtra(TermuxProtocol.EXTRA_ARGUMENTS, request.arguments.toTypedArray())
            putExtra(TermuxProtocol.EXTRA_WORKDIR, request.workdir)
            putExtra(TermuxProtocol.EXTRA_BACKGROUND, request.background)
            putExtra(TermuxProtocol.EXTRA_PENDING_INTENT, pendingIntent)

            request.stdin?.let { putExtra(TermuxProtocol.EXTRA_STDIN, it) }
            request.label?.let { putExtra(TermuxProtocol.EXTRA_COMMAND_LABEL, it) }
            request.description?.let { putExtra(TermuxProtocol.EXTRA_COMMAND_DESCRIPTION, it) }
        }

        runCatching {
            context.startService(intent)
        }.onFailure { e ->
            pending.remove(executionId)
            throw e
        }

        return runCatching {
            withTimeout(request.timeoutMs) {
                deferred.await()
            }
        }.getOrElse {
            pending.remove(executionId)
            TermuxResult(timedOut = true, errMsg = "Timed out after ${request.timeoutMs}ms")
        }.also {
            pending.remove(executionId)
        }
    }

    fun complete(executionId: Int, result: TermuxResult) {
        pending.remove(executionId)?.complete(result)
    }

    private fun ensureTermuxInstalled() {
        val pm = context.packageManager
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    TermuxProtocol.TERMUX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TermuxProtocol.TERMUX_PACKAGE_NAME, 0)
            }
        }.isSuccess

        check(installed) {
            "Termux is not installed. Please install Termux (com.termux) first."
        }
    }

    private fun ensurePermissionGranted() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            TermuxProtocol.PERMISSION_RUN_COMMAND,
        ) == PackageManager.PERMISSION_GRANTED

        check(granted) {
            "Permission ${TermuxProtocol.PERMISSION_RUN_COMMAND} is not granted. " +
                "Grant it in Android Settings -> App Info -> Permissions -> Additional permissions."
        }
    }
}

