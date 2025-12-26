package me.rerere.rikkahub.ui.activity

import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.X
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.ToolImageRedactionTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.floating.FloatingBallService
import me.rerere.rikkahub.ui.theme.RikkahubTheme

class FloatingChatActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SCREENSHOT_URI = "screenshot_uri"
    }

    private var screenshotUriString: String? = null
    private var hasSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenshotUriString = intent.getStringExtra(EXTRA_SCREENSHOT_URI)

        setContent {
            RikkahubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FloatingChatDialog(
                        screenshotUri = screenshotUriString,
                        onSent = { hasSent = true },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            FloatingBallService.showBall(this)
        }
        val uri = screenshotUriString?.toUri()
        if (uri != null) {
            runCatching {
                val file = uri.toFile()
                if (file.exists()) file.delete()
            }
        }
        super.onDestroy()
    }
}

@Composable
private fun FloatingChatDialog(
    screenshotUri: String?,
    onSent: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val settingsStore = org.koin.compose.koinInject<SettingsStore>()
    val generationHandler = org.koin.compose.koinInject<GenerationHandler>()
    val templateTransformer = org.koin.compose.koinInject<TemplateTransformer>()

    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = Settings.dummy())
    val coroutineScope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<UIMessage>>(emptyList()) }
    var generating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isBlank() || generating) return
        val screenshot = screenshotUri ?: return
        if (settings.init) return

        val modelId = settings.floatingBallModelId
        val model = modelId?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT && it.inputModalities.contains(Modality.IMAGE) }

        if (model == null) {
            errorText = "请先在设置里选择一个支持图像输入的模型"
            return
        }

        val assistantId = settings.floatingBallAssistantId ?: settings.getCurrentAssistant().id
        val assistant = settings.getAssistantById(assistantId) ?: settings.getCurrentAssistant()
        val assistantForFloating = assistant.copy(
            enableMemory = false,
            localTools = emptyList(),
            mcpServers = emptySet(),
        )

        val attachScreenshot = messages.none { msg ->
            msg.role == MessageRole.USER && msg.parts.any { part -> part is UIMessagePart.Image }
        }

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = buildList {
                add(UIMessagePart.Text(prompt))
                if (attachScreenshot) add(UIMessagePart.Image(screenshot))
            }
        )

        onSent()
        errorText = null
        input = ""
        generating = true
        job?.cancel()

        val baseMessages = messages + userMessage

        job = coroutineScope.launch {
            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = baseMessages,
                    assistant = assistantForFloating,
                    memories = emptyList(),
                    tools = emptyList(),
                    truncateIndex = -1,
                    maxSteps = 1,
                    executeTools = false,
                    inputTransformers = listOf(
                        PlaceholderTransformer,
                        DocumentAsPromptTransformer,
                        OcrTransformer,
                        PromptInjectionTransformer,
                        ToolImageRedactionTransformer,
                        templateTransformer,
                    ),
                    outputTransformers = listOf(
                        ThinkTagTransformer,
                        Base64ImageToLocalFileTransformer,
                        RegexOutputTransformer,
                    ),
                ).collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> {
                            messages = chunk.messages
                        }
                    }
                }
            }.onFailure {
                errorText = it.message ?: it.toString()
            }
            generating = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(12.dp)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "截图提问",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Lucide.X, contentDescription = "Close")
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .onKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.keyCode == AndroidKeyEvent.KEYCODE_ENTER) {
                                    send()
                                    true
                                } else {
                                    false
                                }
                            },
                        placeholder = { Text("输入问题，回车发送") },
                        singleLine = true,
                        enabled = !generating,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )

                    IconButton(
                        onClick = { send() },
                        enabled = !generating && input.isNotBlank()
                    ) {
                        if (generating) {
                            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        } else {
                            Icon(Lucide.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UIMessage) {
    val isUser = message.role == MessageRole.USER
    val text = message.toText().trim()
    if (text.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
