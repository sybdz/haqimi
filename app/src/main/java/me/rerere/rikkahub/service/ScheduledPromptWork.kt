package me.rerere.rikkahub.service

import androidx.work.Data
import kotlin.uuid.Uuid

internal const val SCHEDULED_PROMPT_WORK_TAG = "scheduled_prompt"
private const val TASK_ID_TAG_PREFIX = "scheduled_prompt_task_id:"
internal const val INPUT_TASK_ID = "task_id"

internal fun periodicWorkName(taskId: Uuid): String = "scheduled_prompt_periodic_$taskId"

internal fun catchUpWorkName(taskId: Uuid): String = "scheduled_prompt_catchup_$taskId"

internal fun taskIdTag(taskId: Uuid): String = "$TASK_ID_TAG_PREFIX$taskId"

internal fun parseTaskIdFromTag(tag: String): Uuid? {
    if (!tag.startsWith(TASK_ID_TAG_PREFIX)) return null
    return runCatching { Uuid.parse(tag.removePrefix(TASK_ID_TAG_PREFIX)) }.getOrNull()
}

internal fun scheduledPromptInputData(taskId: Uuid): Data {
    return Data.Builder()
        .putString(INPUT_TASK_ID, taskId.toString())
        .build()
}
