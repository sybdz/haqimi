package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun SettingTermuxPage() {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var workdirText by remember(settings.termuxWorkdir) {
        mutableStateOf(settings.termuxWorkdir)
    }
    var timeoutText by remember(settings.termuxTimeoutMs) {
        mutableStateOf(settings.termuxTimeoutMs.toString())
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.setting_termux_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("workdir") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_termux_page_workdir_title)) },
                        description = { Text(stringResource(R.string.setting_termux_page_workdir_desc)) },
                        content = {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = workdirText,
                                onValueChange = { value ->
                                    workdirText = value
                                    scope.launch {
                                        settingsStore.update { it.copy(termuxWorkdir = value) }
                                    }
                                },
                                singleLine = true,
                            )
                        },
                    )
                }
            }

            item("background") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_termux_page_background_title)) },
                        description = { Text(stringResource(R.string.setting_termux_page_background_desc)) },
                        tail = {
                            Switch(
                                checked = settings.termuxRunInBackground,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsStore.update { it.copy(termuxRunInBackground = enabled) }
                                    }
                                },
                            )
                        },
                    )
                }
            }

            item("timeout") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_termux_page_timeout_title)) },
                        description = { Text(stringResource(R.string.setting_termux_page_timeout_desc)) },
                        content = {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = timeoutText,
                                onValueChange = { value ->
                                    timeoutText = value.filter { it.isDigit() }
                                    val timeoutMs = timeoutText.toLongOrNull()
                                    if (timeoutMs != null && timeoutMs >= 1_000L) {
                                        scope.launch {
                                            settingsStore.update { it.copy(termuxTimeoutMs = timeoutMs) }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = timeoutText.toLongOrNull()?.let { it < 1_000L } ?: true,
                            )
                        },
                    )
                }
            }

            item("setup") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.setting_termux_page_setup_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(stringResource(R.string.setting_termux_page_setup_step_1))
                        Text(stringResource(R.string.setting_termux_page_setup_step_2))
                        Text(stringResource(R.string.setting_termux_page_setup_step_3))
                        Text(stringResource(R.string.setting_termux_page_setup_step_4))
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.setting_termux_page_open_app_settings))
                        }
                    }
                }
            }
        }
    }
}
