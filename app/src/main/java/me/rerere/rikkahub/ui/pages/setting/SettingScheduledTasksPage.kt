package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.ScheduleType
import me.rerere.rikkahub.data.model.ScheduledTask
import me.rerere.rikkahub.data.model.TaskRunStatus
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import kotlin.uuid.Uuid

@Composable
fun SettingScheduledTasksPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }

    fun upsertTask(task: ScheduledTask) {
        val normalized = task.copy(
            title = task.title.trim(),
            prompt = task.prompt.trim(),
            dayOfWeek = if (task.scheduleType == ScheduleType.WEEKLY) {
                task.dayOfWeek?.coerceIn(DayOfWeek.MONDAY.value, DayOfWeek.SUNDAY.value) ?: DayOfWeek.MONDAY.value
            } else {
                null
            },
            modelId = task.modelId ?: settings.chatModelId,
            mcpServerIds = task.mcpServerIds.filter { id -> settings.mcpServers.any { it.id == id } }.toSet(),
            localTools = task.localTools.distinct(),
            searchServiceId = if (task.enableWebSearch) task.searchServiceId else null,
        )
        if (normalized.prompt.isBlank() || normalized.modelId == null) return
        scope.launch {
            settingsStore.update { old ->
                val hasSameId = old.scheduledTasks.any { it.id == normalized.id }
                val tasks = if (hasSameId) {
                    old.scheduledTasks.map { if (it.id == normalized.id) normalized else it }
                } else {
                    old.scheduledTasks + normalized
                }.sortedByDescending { it.createdAt }
                old.copy(scheduledTasks = tasks)
            }
        }
        editingTask = null
    }

    fun deleteTask(taskId: Uuid) {
        scope.launch {
            settingsStore.update { old ->
                old.copy(
                    scheduledTasks = old.scheduledTasks.filter { it.id != taskId },
                    scheduledTaskRuns = old.scheduledTaskRuns.filter { it.taskId != taskId }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_scheduled_tasks_page_title)) },
                navigationIcon = { BackButton() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = ScheduledTask(
                        dayOfWeek = DayOfWeek.MONDAY.value,
                        modelId = settings.chatModelId,
                        searchServiceId = settings.searchServices.getOrNull(settings.searchServiceSelected)?.id
                    )
                }
            ) {
                Icon(Lucide.Plus, contentDescription = stringResource(R.string.assistant_schedule_add_task))
            }
        }
    ) { innerPadding ->
        val tasks = remember(settings.scheduledTasks) {
            settings.scheduledTasks.sortedByDescending { it.createdAt }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.assistant_schedule_summary_title)) },
                        supportingContent = {
                            val enabledCount = tasks.count { it.enabled }
                            Text(stringResource(R.string.assistant_schedule_summary_desc, enabledCount))
                        }
                    )
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.assistant_schedule_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(items = tasks, key = { it.id.toString() }) { task ->
                    ScheduledTaskCard(
                        task = task,
                        settings = settings,
                        onToggleEnabled = { enabled ->
                            upsertTask(task.copy(enabled = enabled))
                        },
                        onEdit = { editingTask = task },
                        onDelete = { deleteTask(task.id) },
                    )
                }
            }
        }
    }

    editingTask?.let { task ->
        TaskEditorSheet(
            task = task,
            settings = settings,
            onDismiss = { editingTask = null },
            onSave = { upsertTask(it) },
        )
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    settings: Settings,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val modelName = task.modelId?.let { settings.findModelById(it)?.displayName }
        ?: stringResource(R.string.setting_scheduled_tasks_model_not_set)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(
                        text = task.title.ifBlank { stringResource(R.string.assistant_schedule_untitled) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = task.prompt.replace("\n", " "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = task.scheduleSummary(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.setting_scheduled_tasks_model_line, modelName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = task.statusSummary(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = task.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            )
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Lucide.Pencil, contentDescription = stringResource(R.string.assistant_schedule_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Lucide.Trash2, contentDescription = stringResource(R.string.assistant_schedule_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    task: ScheduledTask,
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var prompt by remember(task.id) { mutableStateOf(task.prompt) }
    var scheduleType by remember(task.id) { mutableStateOf(task.scheduleType) }
    var timeMinutesOfDay by remember(task.id) { mutableStateOf(task.timeMinutesOfDay.coerceIn(0, 1439)) }
    var dayOfWeek by remember(task.id) { mutableStateOf(task.dayOfWeek ?: DayOfWeek.MONDAY.value) }
    var modelId by remember(task.id) { mutableStateOf(task.modelId) }
    var mcpServerIds by remember(task.id) { mutableStateOf(task.mcpServerIds.toSet()) }
    var localTools by remember(task.id) { mutableStateOf(task.localTools) }
    var enableWebSearch by remember(task.id) { mutableStateOf(task.enableWebSearch) }
    var searchServiceId by remember(task.id) {
        mutableStateOf(task.searchServiceId ?: settings.searchServices.getOrNull(settings.searchServiceSelected)?.id)
    }
    var showTimePicker by remember(task.id) { mutableStateOf(false) }

    val enabledServers = settings.mcpServers.filter { it.commonOptions.enable }
    val canSave = prompt.isNotBlank() && modelId != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.assistant_schedule_editor_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.assistant_schedule_task_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.assistant_schedule_task_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf(ScheduleType.DAILY, ScheduleType.WEEKLY)
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { scheduleType = option },
                            selected = scheduleType == option
                        ) {
                            Text(
                                text = if (option == ScheduleType.DAILY) {
                                    stringResource(R.string.assistant_schedule_daily)
                                } else {
                                    stringResource(R.string.assistant_schedule_weekly)
                                }
                            )
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { showTimePicker = true }) {
                    Icon(Lucide.Clock, contentDescription = null)
                    Text(
                        text = stringResource(
                            R.string.assistant_schedule_time_at,
                            formatTime(timeMinutesOfDay)
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (scheduleType == ScheduleType.WEEKLY) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = dayOfWeek == day.value,
                                onClick = { dayOfWeek = day.value },
                                label = { Text(day.displayName()) }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_scheduled_tasks_model_title)) },
                        supportingContent = {
                            if (modelId == null) {
                                Text(stringResource(R.string.setting_scheduled_tasks_model_required))
                            }
                        },
                        trailingContent = {
                            ModelSelector(
                                modelId = modelId,
                                providers = settings.providers,
                                type = ModelType.CHAT,
                                onSelect = { modelId = it.id }
                            )
                        }
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setting_scheduled_tasks_mcp_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (enabledServers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.setting_scheduled_tasks_mcp_empty),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                enabledServers.forEach { server ->
                                    FilterChip(
                                        selected = server.id in mcpServerIds,
                                        onClick = {
                                            mcpServerIds = if (server.id in mcpServerIds) {
                                                mcpServerIds - server.id
                                            } else {
                                                mcpServerIds + server.id
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = server.commonOptions.name.ifBlank {
                                                    server::class.simpleName ?: "MCP"
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setting_scheduled_tasks_local_tools_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allLocalToolOptions().forEach { option ->
                                FilterChip(
                                    selected = option in localTools,
                                    onClick = {
                                        localTools = if (option in localTools) {
                                            localTools - option
                                        } else {
                                            localTools + option
                                        }
                                    },
                                    label = { Text(localToolLabel(option)) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.setting_scheduled_tasks_web_search_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = enableWebSearch,
                                onCheckedChange = { enable ->
                                    enableWebSearch = enable
                                    if (!enable) searchServiceId = null
                                }
                            )
                        }
                        if (enableWebSearch) {
                            if (settings.searchServices.isEmpty()) {
                                Text(stringResource(R.string.setting_scheduled_tasks_search_service_empty))
                            } else {
                                val selected = settings.searchServices.firstOrNull { it.id == searchServiceId }
                                    ?: settings.searchServices.first()
                                Select(
                                    options = settings.searchServices,
                                    selectedOption = selected,
                                    onOptionSelected = { searchServiceId = it.id },
                                    optionToString = {
                                        val name = me.rerere.search.SearchServiceOptions.TYPES[it::class]
                                        name ?: it::class.simpleName.orEmpty()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.assistant_schedule_cancel))
                    }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            if (!canSave) return@TextButton
                            onSave(
                                task.copy(
                                    title = title.trim(),
                                    prompt = prompt.trim(),
                                    scheduleType = scheduleType,
                                    timeMinutesOfDay = timeMinutesOfDay.coerceIn(0, 1439),
                                    dayOfWeek = if (scheduleType == ScheduleType.WEEKLY) dayOfWeek else null,
                                    modelId = modelId,
                                    mcpServerIds = mcpServerIds,
                                    localTools = localTools.distinct(),
                                    enableWebSearch = enableWebSearch,
                                    searchServiceId = if (enableWebSearch) searchServiceId else null,
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.assistant_schedule_save))
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = timeMinutesOfDay / 60,
            initialMinute = timeMinutesOfDay % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = {
                TimePicker(state = timeState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        timeMinutesOfDay = timeState.hour * 60 + timeState.minute
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.assistant_schedule_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.assistant_schedule_cancel))
                }
            }
        )
    }
}

@Composable
private fun ScheduledTask.scheduleSummary(): String {
    val time = formatTime(timeMinutesOfDay)
    return when (scheduleType) {
        ScheduleType.DAILY -> stringResource(R.string.assistant_schedule_summary_daily, time)
        ScheduleType.WEEKLY -> {
            val day = runCatching {
                DayOfWeek.of(dayOfWeek ?: DayOfWeek.MONDAY.value).displayName()
            }.getOrElse { DayOfWeek.MONDAY.displayName() }
            stringResource(R.string.assistant_schedule_summary_weekly, day, time)
        }
    }
}

@Composable
private fun ScheduledTask.statusSummary(): String {
    val status = when (lastStatus) {
        TaskRunStatus.IDLE -> stringResource(R.string.assistant_schedule_status_idle)
        TaskRunStatus.RUNNING -> stringResource(R.string.assistant_schedule_status_running)
        TaskRunStatus.SUCCESS -> stringResource(R.string.assistant_schedule_status_success)
        TaskRunStatus.FAILED -> stringResource(R.string.assistant_schedule_status_failed)
    }
    val timeText = if (lastRunAt > 0) {
        Instant.ofEpochMilli(lastRunAt).toLocalDateTime()
    } else {
        stringResource(R.string.assistant_schedule_status_never_run)
    }
    return stringResource(R.string.assistant_schedule_status_line, status, timeText)
}

private fun DayOfWeek.displayName(): String {
    return getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

private fun formatTime(timeMinutesOfDay: Int): String {
    val hour = (timeMinutesOfDay.coerceIn(0, 1439)) / 60
    val minute = (timeMinutesOfDay.coerceIn(0, 1439)) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

private fun localToolLabel(option: LocalToolOption): String = when (option) {
    LocalToolOption.JavascriptEngine -> "JavaScript"
    LocalToolOption.TimeInfo -> "Time"
    LocalToolOption.Clipboard -> "Clipboard"
    LocalToolOption.TermuxExec -> "Termux Cmd"
    LocalToolOption.TermuxPython -> "Termux Python"
    LocalToolOption.Tts -> "TTS"
}

private fun allLocalToolOptions(): List<LocalToolOption> = listOf(
    LocalToolOption.JavascriptEngine,
    LocalToolOption.TimeInfo,
    LocalToolOption.Clipboard,
    LocalToolOption.TermuxExec,
    LocalToolOption.TermuxPython,
    LocalToolOption.Tts,
)
