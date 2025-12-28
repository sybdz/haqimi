package me.rerere.rikkahub.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Camera
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

@Composable
fun FloatingChatPanel(
    modifier: Modifier = Modifier,
    initialImages: List<String> = emptyList(),
    onClose: () -> Unit,
    onRequestScreenshot: (suspend () -> Result<String>)? = null,
    onResize: ((fromLeft: Boolean, dx: Float, dy: Float) -> Unit)? = null,
) {
    val settingsStore = org.koin.compose.koinInject<SettingsStore>()
    val generationHandler = org.koin.compose.koinInject<GenerationHandler>()
    val templateTransformer = org.koin.compose.koinInject<TemplateTransformer>()

    val settings by settingsStore.settingsFlow.collectAsState(initial = Settings.dummy())
    val coroutineScope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<UIMessage>>(emptyList()) }
    var generating by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val attachments = remember {
        mutableStateListOf<String>().apply { addAll(initialImages) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun send() {
        val prompt = input.trim()
        if (generating) return
        if (prompt.isBlank()) return
        if (settings.init) return

        val modelId = settings.floatingBallModelId
        val model = modelId?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }

        if (model == null) {
            errorText = "请先在设置里选择一个模型"
            return
        }

        if (attachments.isNotEmpty() && !model.inputModalities.contains(Modality.IMAGE)) {
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

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = buildList {
                if (prompt.isNotBlank()) add(UIMessagePart.Text(prompt))
                attachments.forEach { add(UIMessagePart.Image(it)) }
            }
        )

        errorText = null
        input = ""
        attachments.clear()
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

    fun requestScreenshot() {
        if (onRequestScreenshot == null || capturing) return
        capturing = true
        coroutineScope.launch {
            val result = onRequestScreenshot()
            capturing = false
            result.onSuccess { uri ->
                attachments.add(uri)
                errorText = null
            }.onFailure {
                errorText = it.message ?: "截图失败"
            }
        }
    }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "截图提问",
                        style = MaterialTheme.typography.titleSmall,
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                }

                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        attachments.forEachIndexed { index, uri ->
                            AttachmentChip(
                                label = "[图片${index + 1}]",
                                onRemove = { attachments.remove(uri) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onRequestScreenshot != null) {
                        IconButton(
                            onClick = { requestScreenshot() },
                            enabled = !capturing,
                        ) {
                            if (capturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Lucide.Camera, contentDescription = "Screenshot")
                            }
                        }
                    }

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

        if (onResize != null) {
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomStart),
                onDrag = { dx, dy -> onResize(true, dx, dy) }
            )
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomEnd),
                onDrag = { dx, dy -> onResize(false, dx, dy) }
            )
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(18.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    )
}

@Composable
private fun AttachmentChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .widthIn(min = 48.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 12.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = "Remove",
                    modifier = Modifier.size(10.dp),
                )
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
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
