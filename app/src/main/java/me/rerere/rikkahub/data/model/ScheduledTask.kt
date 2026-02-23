package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid

@Serializable
data class ScheduledTask(
    val id: Uuid = Uuid.random(),
    val enabled: Boolean = true,
    val title: String = "",
    val prompt: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val timeMinutesOfDay: Int = 9 * 60,
    val dayOfWeek: Int? = null, // 1..7, Monday..Sunday, only used when scheduleType == WEEKLY
    val modelId: Uuid? = null,
    val mcpServerIds: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = emptyList(),
    val enableWebSearch: Boolean = false,
    val searchServiceId: Uuid? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0L,
    val lastStatus: TaskRunStatus = TaskRunStatus.IDLE,
    val lastError: String = "",
)

@Serializable
data class ScheduledTaskRun(
    val id: Uuid = Uuid.random(),
    val taskId: Uuid,
    val taskTitle: String = "",
    val status: TaskRunStatus = TaskRunStatus.IDLE,
    val runAt: Long = System.currentTimeMillis(),
    val conversationId: Uuid? = null,
    val error: String = "",
)

@Serializable
enum class ScheduleType {
    DAILY,
    WEEKLY,
}

@Serializable
enum class TaskRunStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
}

@Serializable
enum class ConversationSource {
    @SerialName("normal")
    NORMAL,

    @SerialName("scheduled_task")
    SCHEDULED_TASK,
}
