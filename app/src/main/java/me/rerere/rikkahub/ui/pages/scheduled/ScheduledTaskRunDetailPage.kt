package me.rerere.rikkahub.ui.pages.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.TaskRunStatus
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject
import java.time.Instant
import kotlin.uuid.Uuid

@Composable
fun ScheduledTaskRunDetailPage(runId: String) {
    val navController = LocalNavController.current
    val settingsStore: SettingsStore = koinInject()
    val settings = settingsStore.settingsFlow.collectAsStateWithLifecycle().value
    val runUuid = runCatching { Uuid.parse(runId) }.getOrNull()
    val run = settings.scheduledTaskRuns.firstOrNull { it.id == runUuid }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_task_run_detail_title)) },
                navigationIcon = { BackButton() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (run == null) {
                item {
                    Text(
                        text = stringResource(R.string.scheduled_task_run_detail_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailLine(
                                label = stringResource(R.string.scheduled_task_run_detail_task),
                                value = run.taskTitle.ifBlank { stringResource(R.string.assistant_schedule_untitled) }
                            )
                            DetailLine(
                                label = stringResource(R.string.scheduled_task_run_detail_status),
                                value = run.status.label()
                            )
                            DetailLine(
                                label = stringResource(R.string.scheduled_task_run_detail_time),
                                value = Instant.ofEpochMilli(run.runAt).toLocalDateTime()
                            )
                            if (run.error.isNotBlank()) {
                                DetailLine(
                                    label = stringResource(R.string.scheduled_task_run_detail_error),
                                    value = run.error,
                                    highlightError = true
                                )
                            }
                        }
                    }
                }

                if (run.conversationId != null) {
                    item {
                        Button(
                            onClick = {
                                navController.navigate(Screen.Chat(id = run.conversationId.toString()))
                            }
                        ) {
                            Text(stringResource(R.string.scheduled_task_run_detail_open_chat))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    highlightError: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (highlightError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TaskRunStatus.label(): String = when (this) {
    TaskRunStatus.IDLE -> stringResource(R.string.assistant_schedule_status_idle)
    TaskRunStatus.RUNNING -> stringResource(R.string.assistant_schedule_status_running)
    TaskRunStatus.SUCCESS -> stringResource(R.string.assistant_schedule_status_success)
    TaskRunStatus.FAILED -> stringResource(R.string.assistant_schedule_status_failed)
}
