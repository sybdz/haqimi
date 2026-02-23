package me.rerere.rikkahub.ui.pages.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ScheduledTaskRun
import me.rerere.rikkahub.data.model.TaskRunStatus
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.compose.koinInject
import java.time.Instant

@Composable
fun ScheduledTaskRunsPage() {
    val navController = LocalNavController.current
    val settingsStore: SettingsStore = koinInject()
    val settings = settingsStore.settingsFlow.collectAsStateWithLifecycle().value
    val runs = settings.scheduledTaskRuns.sortedByDescending { it.runAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_task_runs_page_title)) },
                navigationIcon = { BackButton() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (runs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.scheduled_task_runs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(items = runs, key = { it.id.toString() }) { run ->
                    RunItem(
                        run = run,
                        onClick = {
                            navController.navigate(Screen.ScheduledTaskRunDetail(run.id.toString()))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunItem(
    run: ScheduledTaskRun,
    onClick: () -> Unit,
) {
    val statusColor = when (run.status) {
        TaskRunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        TaskRunStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskRunStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        TaskRunStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = run.taskTitle.ifBlank { stringResource(R.string.assistant_schedule_untitled) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(Instant.ofEpochMilli(run.runAt).toLocalDateTime())
            },
            trailingContent = {
                Text(
                    text = run.status.label(),
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
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
