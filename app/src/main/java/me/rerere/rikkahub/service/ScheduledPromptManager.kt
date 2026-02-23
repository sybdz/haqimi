package me.rerere.rikkahub.service

import android.app.Application
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.ScheduleType
import me.rerere.rikkahub.data.model.ScheduledTask
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

private const val TAG = "ScheduledPromptManager"

class ScheduledPromptManager(
    context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    private val workManager = WorkManager.getInstance(context)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            settingsStore.settingsFlow.collectLatest { settings ->
                runCatching {
                    reconcile(settings)
                }.onFailure {
                    Log.e(TAG, "Failed to reconcile scheduled prompt tasks", it)
                }
            }
        }
    }

    suspend fun reconcile(settings: Settings) {
        val enabledTasks = settings.scheduledTasks
            .filter { task ->
                task.enabled &&
                    task.prompt.isNotBlank() &&
                    task.modelId != null &&
                    settings.findModelById(task.modelId) != null
            }
        val expectedTaskIds = enabledTasks.map { it.id }.toSet()

        cancelStaleWorks(expectedTaskIds)

        val now = ZonedDateTime.now()
        enabledTasks.forEach { task ->
            schedulePeriodic(task)
            if (ScheduledPromptTime.shouldRunCatchUp(task, now)) {
                scheduleCatchUp(task)
            }
        }
    }

    private fun schedulePeriodic(task: ScheduledTask) {
        val repeatDays = when (task.scheduleType) {
            ScheduleType.DAILY -> 1L
            ScheduleType.WEEKLY -> 7L
        }
        val request = PeriodicWorkRequestBuilder<ScheduledPromptWorker>(
            repeatDays,
            TimeUnit.DAYS,
            15L,
            TimeUnit.MINUTES
        )
            .setInitialDelay(ScheduledPromptTime.initialDelayMillis(task), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(scheduledPromptInputData(taskId = task.id))
            .addTag(SCHEDULED_PROMPT_WORK_TAG)
            .addTag(taskIdTag(task.id))
            .build()

        workManager.enqueueUniquePeriodicWork(
            periodicWorkName(task.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleCatchUp(task: ScheduledTask) {
        val request = OneTimeWorkRequestBuilder<ScheduledPromptWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(scheduledPromptInputData(taskId = task.id))
            .addTag(SCHEDULED_PROMPT_WORK_TAG)
            .addTag(taskIdTag(task.id))
            .build()

        workManager.enqueueUniqueWork(
            catchUpWorkName(task.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun cancelStaleWorks(expectedTaskIds: Set<Uuid>) {
        withContext(Dispatchers.IO) {
            val workInfos = workManager.getWorkInfosByTag(SCHEDULED_PROMPT_WORK_TAG).get()
            workInfos.forEach { info ->
                val taskId = info.tags
                    .firstNotNullOfOrNull { parseTaskIdFromTag(it) }
                    ?: return@forEach
                if (taskId !in expectedTaskIds) {
                    workManager.cancelWorkById(info.id)
                }
            }
        }
    }
}
