package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.LocalToolPrompt
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.permission.rememberPythonStoragePermissionRequest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                }
            )
        }
    ) { innerPadding ->
        AssistantLocalToolContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val requestPythonStoragePermission = rememberPythonStoragePermissionRequest {
        onUpdate(assistant.copy(localTools = assistant.localTools + LocalToolOption.PythonEngine))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // JavaScript 引擎工具卡片
        LocalToolCard(
            title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
            description = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
            isEnabled = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
            onToggle = { enabled ->
                val newLocalTools = if (enabled) {
                    assistant.localTools + LocalToolOption.JavascriptEngine
                } else {
                    assistant.localTools - LocalToolOption.JavascriptEngine
                }
                onUpdate(assistant.copy(localTools = newLocalTools))
            },
            content = {
                val prompt = assistant.getLocalToolPrompt(LocalToolOption.JavascriptEngine)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { value ->
                        onUpdate(assistant.withLocalToolPrompt(LocalToolOption.JavascriptEngine, value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义描述提示词") },
                    placeholder = { Text("留空使用默认描述") },
                    minLines = 2,
                    maxLines = 6
                )
            }
        )
        LocalToolCard(
            title = stringResource(R.string.assistant_page_local_tools_python_engine_title),
            description = stringResource(R.string.assistant_page_local_tools_python_engine_desc),
            isEnabled = assistant.localTools.contains(LocalToolOption.PythonEngine),
            onToggle = { enabled ->
                if (enabled) {
                    requestPythonStoragePermission()
                } else {
                    val newLocalTools = assistant.localTools - LocalToolOption.PythonEngine
                    onUpdate(assistant.copy(localTools = newLocalTools))
                }
            },
            content = {
                val prompt = assistant.getLocalToolPrompt(LocalToolOption.PythonEngine)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { value ->
                        onUpdate(assistant.withLocalToolPrompt(LocalToolOption.PythonEngine, value))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义描述提示词") },
                    placeholder = { Text("留空使用默认描述") },
                    minLines = 2,
                    maxLines = 6
                )
            }
        )
    }
}

@Composable
private fun LocalToolCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    ) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = {
                Text(title)
            },
            description = {
                Text(description)
            },
            tail = {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            },
            content = {
                if (isEnabled && content != null) {
                    content()
                } else {
                    null
                }
            }
    )
}

private fun Assistant.getLocalToolPrompt(option: LocalToolOption): String {
    return localToolPrompts.firstOrNull { it.option == option }?.description.orEmpty()
}

private fun Assistant.withLocalToolPrompt(option: LocalToolOption, value: String): Assistant {
    val updatedPrompts = localToolPrompts.toMutableList()
    val index = updatedPrompts.indexOfFirst { it.option == option }
    if (value.isBlank()) {
        if (index != -1) {
            updatedPrompts.removeAt(index)
        }
    } else if (index == -1) {
        updatedPrompts.add(LocalToolPrompt(option = option, description = value))
    } else {
        updatedPrompts[index] = updatedPrompts[index].copy(description = value)
    }
    return copy(localToolPrompts = updatedPrompts)
}
}
