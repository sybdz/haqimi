package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ScheduleType
import me.rerere.rikkahub.data.model.ScheduledTask
import me.rerere.rikkahub.data.model.TaskRunStatus
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.search.SearchServiceOptions
import kotlin.uuid.Uuid

class PreferenceStoreV3Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 3
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val assistantsJson = prefs[SettingsStore.ASSISTANTS] ?: "[]"
        val globalModelId = prefs[SettingsStore.SELECT_MODEL]?.let { raw ->
            runCatching { Uuid.parse(raw) }.getOrNull()
        }
        val globalSearchEnabled = prefs[SettingsStore.ENABLE_WEB_SEARCH] == true
        val globalSearchServiceId = getSelectedSearchServiceId(
            searchServicesJson = prefs[SettingsStore.SEARCH_SERVICES],
            selected = prefs[SettingsStore.SEARCH_SELECTED] ?: 0
        )

        val existingTasks = prefs[SettingsStore.SCHEDULED_TASKS]
            ?.let { runCatching { JsonInstant.decodeFromString<List<ScheduledTask>>(it) }.getOrNull() }
            .orEmpty()

        if (existingTasks.isEmpty()) {
            val migrated = migrateAssistantsScheduledTasks(
                assistantsJson = assistantsJson,
                globalModelId = globalModelId,
                globalSearchEnabled = globalSearchEnabled,
                globalSearchServiceId = globalSearchServiceId
            )
            prefs[SettingsStore.ASSISTANTS] = migrated.cleanedAssistantsJson
            prefs[SettingsStore.SCHEDULED_TASKS] = JsonInstant.encodeToString(migrated.tasks)
        }
        if (prefs[SettingsStore.SCHEDULED_TASK_RUNS].isNullOrBlank()) {
            prefs[SettingsStore.SCHEDULED_TASK_RUNS] = "[]"
        }

        prefs[SettingsStore.VERSION] = 3
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

private data class ScheduledTaskMigrationResult(
    val cleanedAssistantsJson: String,
    val tasks: List<ScheduledTask>,
)

private fun migrateAssistantsScheduledTasks(
    assistantsJson: String,
    globalModelId: Uuid?,
    globalSearchEnabled: Boolean,
    globalSearchServiceId: Uuid?,
): ScheduledTaskMigrationResult {
    val root = runCatching { JsonInstant.parseToJsonElement(assistantsJson).jsonArray }.getOrElse {
        return ScheduledTaskMigrationResult(cleanedAssistantsJson = assistantsJson, tasks = emptyList())
    }

    val tasks = mutableListOf<ScheduledTask>()
    val cleanedAssistants = JsonArray(root.map { assistantElement ->
        val assistantObj = assistantElement as? JsonObject ?: return@map assistantElement
        val assistantModelId = assistantObj["chatModelId"]?.jsonPrimitive?.contentOrNull?.let { raw ->
            runCatching { Uuid.parse(raw) }.getOrNull()
        }
        val assistantMcpServerIds = assistantObj["mcpServers"]?.jsonArray
            ?.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.let { runCatching { Uuid.parse(it) }.getOrNull() } }
            ?.toSet()
            ?: emptySet()
        val assistantLocalTools = assistantObj["localTools"]?.let { value ->
            runCatching { JsonInstant.decodeFromString<List<LocalToolOption>>(JsonInstant.encodeToString(value)) }
                .getOrDefault(emptyList())
        } ?: emptyList()

        val legacyTasks = assistantObj["scheduledPromptTasks"]?.jsonArray.orEmpty()
        legacyTasks.forEach { taskElement ->
            val taskObj = taskElement as? JsonObject ?: return@forEach
            tasks += ScheduledTask(
                id = taskObj["id"]?.jsonPrimitive?.contentOrNull?.let { raw ->
                    runCatching { Uuid.parse(raw) }.getOrNull()
                } ?: Uuid.random(),
                enabled = taskObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                title = taskObj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                prompt = taskObj["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                scheduleType = taskObj["scheduleType"]?.jsonPrimitive?.contentOrNull
                    ?.let { raw -> runCatching { enumValueOf<ScheduleType>(raw) }.getOrNull() }
                    ?: ScheduleType.DAILY,
                timeMinutesOfDay = taskObj["timeMinutesOfDay"]?.jsonPrimitive?.intOrNull ?: 9 * 60,
                dayOfWeek = taskObj["dayOfWeek"]?.jsonPrimitive?.intOrNull,
                modelId = assistantModelId ?: globalModelId,
                mcpServerIds = assistantMcpServerIds,
                localTools = assistantLocalTools,
                enableWebSearch = globalSearchEnabled,
                searchServiceId = globalSearchServiceId,
                createdAt = taskObj["createdAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: System.currentTimeMillis(),
                lastRunAt = taskObj["lastRunAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                lastStatus = taskObj["lastStatus"]?.jsonPrimitive?.contentOrNull
                    ?.let { raw -> runCatching { enumValueOf<TaskRunStatus>(raw) }.getOrNull() }
                    ?: TaskRunStatus.IDLE,
                lastError = taskObj["lastError"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

        JsonObject(assistantObj.toMutableMap().apply {
            remove("scheduledPromptTasks")
        })
    })

    return ScheduledTaskMigrationResult(
        cleanedAssistantsJson = JsonInstant.encodeToString(cleanedAssistants),
        tasks = tasks
    )
}

private fun getSelectedSearchServiceId(searchServicesJson: String?, selected: Int): Uuid? {
    val services = searchServicesJson?.let { raw ->
        runCatching { JsonInstant.decodeFromString<List<SearchServiceOptions>>(raw) }.getOrNull()
    }.orEmpty()
    if (services.isEmpty()) return null
    val index = selected.coerceIn(0, services.lastIndex)
    return services[index].id
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

internal fun migrateSettingsScheduledTasksJson(settingsJson: String): String {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(settingsJson).jsonObject.toMutableMap()
        val existingTasks = root["scheduledTasks"]?.let { element ->
            runCatching { JsonInstant.decodeFromString<List<ScheduledTask>>(JsonInstant.encodeToString(element)) }.getOrNull()
        }.orEmpty()
        if (existingTasks.isNotEmpty()) return@runCatching settingsJson

        val globalModelId = root["chatModelId"]?.jsonPrimitive?.contentOrNull?.let { raw ->
            runCatching { Uuid.parse(raw) }.getOrNull()
        }
        val globalSearchEnabled = root["enableWebSearch"]?.jsonPrimitive?.booleanOrNull ?: false
        val selectedSearchServiceId = getSelectedSearchServiceId(
            searchServicesJson = root["searchServices"]?.let { JsonInstant.encodeToString(it) },
            selected = root["searchServiceSelected"]?.jsonPrimitive?.intOrNull ?: 0
        )
        val assistantsJson = root["assistants"]?.let { JsonInstant.encodeToString(it) } ?: "[]"
        val migrated = migrateAssistantsScheduledTasks(
            assistantsJson = assistantsJson,
            globalModelId = globalModelId,
            globalSearchEnabled = globalSearchEnabled,
            globalSearchServiceId = selectedSearchServiceId
        )
        root["assistants"] = JsonInstant.parseToJsonElement(migrated.cleanedAssistantsJson)
        root["scheduledTasks"] = JsonInstant.parseToJsonElement(JsonInstant.encodeToString(migrated.tasks))
        if (root["scheduledTaskRuns"] == null) {
            root["scheduledTaskRuns"] = JsonArray(emptyList())
        }

        JsonInstant.encodeToString(JsonObject(root))
    }.getOrDefault(settingsJson)
}
