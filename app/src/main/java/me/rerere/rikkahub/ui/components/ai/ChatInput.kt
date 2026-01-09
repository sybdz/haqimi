package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Earth
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.FileAudio
import com.composables.icons.lucide.Files
import com.composables.icons.lucide.Fullscreen
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.LightbulbOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sparkle
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.dokar.sonner.ToastType
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.FloatingMenu
import me.rerere.rikkahub.ui.components.ui.FloatingMenuDivider
import me.rerere.rikkahub.ui.components.ui.FloatingMenuItem
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.search.SearchServiceOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.LinearWavyProgressIndicator
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

enum class ExpandState {
    Collapsed,
    Files,
}

private enum class ActionSubmenu {
    Search,
    Reasoning,
    Mcp,
    Injection,
}

@Composable
fun ChatInput(
    state: ChatInputState,
    conversation: Conversation,
    settings: Settings,
    mcpManager: McpManager,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateChatModel: (Model) -> Unit,
    multiModelMode: Boolean,
    arenaMode: Boolean,
    selectedChatModels: Set<Uuid>,
    onMultiModelModeChange: (Boolean) -> Unit,
    onArenaModeChange: (Boolean) -> Unit,
    onSelectedChatModelsChange: (Set<Uuid>) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onClearContext: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val navController = LocalNavController.current

    val keyboardController = LocalSoftwareKeyboardController.current

    fun sendMessage() {
        keyboardController?.hide()
        if (state.loading) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        keyboardController?.hide()
        if (state.loading) onCancelClick() else onLongSendClick()
    }

    var expand by remember { mutableStateOf(ExpandState.Collapsed) }
    var activeSubmenu by remember { mutableStateOf<ActionSubmenu?>(null) }
    fun dismissExpand() {
        expand = ExpandState.Collapsed
        activeSubmenu = null
    }

    fun openSubmenu(submenu: ActionSubmenu) {
        activeSubmenu = if (activeSubmenu == submenu) null else submenu
    }

    fun expandToggle(type: ExpandState) {
        if (expand == type) {
            dismissExpand()
        } else {
            expand = type
            activeSubmenu = null
        }
    }

    // Collapse only the main menu when IME is visible to avoid closing submenus with inputs.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, expand, activeSubmenu) {
        if (imeVisible && expand == ExpandState.Files && activeSubmenu == null) {
            dismissExpand()
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var menuAnchorX by remember { mutableStateOf(0.dp) }
    val screenWidth = configuration.screenWidthDp.dp
    val menuEdgePadding = 12.dp
    val menuGap = 8.dp
    val mainMenuWidth = 240.dp
    val subMenuWidth = 240.dp
    val menuOffsetY = (-6).dp
    val mainMenuX = (screenWidth - mainMenuWidth - menuEdgePadding).coerceAtLeast(menuEdgePadding)
    val subMenuX = (mainMenuX - subMenuWidth - menuGap).coerceAtLeast(menuEdgePadding)
    val mainMenuOffsetX = mainMenuX - menuAnchorX
    val subMenuOffsetX = subMenuX - menuAnchorX

    Surface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Medias
            MediaFileInputRow(state = state, context = context)

            // Text Input Row
            TextInputRow(state = state, context = context)

            // Actions Row
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Model Picker
                    ModelSelector(
                        modelId = assistant.chatModelId ?: settings.chatModelId,
                        providers = settings.providers,
                        onSelect = {
                            onUpdateChatModel(it)
                            dismissExpand()
                        },
                        type = ModelType.CHAT,
                        onlyIcon = true,
                        modifier = Modifier,
                        enableChatModes = true,
                        multiModelMode = multiModelMode,
                        arenaMode = arenaMode,
                        selectedModels = selectedChatModels,
                        onMultiModelModeChange = onMultiModelModeChange,
                        onArenaModeChange = onArenaModeChange,
                        onSelectedModelsChange = onSelectedChatModelsChange,
                    )
                }

                // Insert files
                val chatModel = settings.getCurrentChatModel()
                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        menuAnchorX = with(density) {
                            coordinates.positionInWindow().x.toDp()
                        }
                    }
                ) {
                    IconButton(
                        onClick = {
                            expandToggle(ExpandState.Files)
                        }
                    ) {
                        Icon(
                            if (expand == ExpandState.Files) Lucide.X else Lucide.Plus,
                            stringResource(R.string.more_options)
                        )
                    }
                    FloatingMenu(
                        expanded = expand == ExpandState.Files,
                        onDismissRequest = { dismissExpand() },
                        offset = DpOffset(mainMenuOffsetX, menuOffsetY),
                        menuWidth = mainMenuWidth,
                        shadowElevation = menuEdgePadding,
                        properties = PopupProperties(
                            dismissOnClickOutside = activeSubmenu == null,
                            focusable = activeSubmenu == null
                        ),
                    ) {
                        FilesPicker(
                            conversation = conversation,
                            state = state,
                            assistant = assistant,
                            enableSearch = enableSearch,
                            activeSubmenu = activeSubmenu,
                            chatModel = chatModel,
                            onOpenSubmenu = { submenu ->
                                openSubmenu(submenu)
                            },
                            onClearContext = onClearContext,
                            onDismiss = { dismissExpand() }
                        )
                    }
                    FloatingMenu(
                        expanded = expand == ExpandState.Files && activeSubmenu != null,
                        onDismissRequest = { activeSubmenu = null },
                        offset = DpOffset(subMenuOffsetX, menuOffsetY),
                        menuWidth = subMenuWidth,
                        shadowElevation = menuEdgePadding,
                        properties = PopupProperties(
                            dismissOnClickOutside = false,
                            focusable = true
                        ),
                    ) {
                        when (activeSubmenu) {
                            ActionSubmenu.Search -> {
                                val enableSearchMsg = stringResource(R.string.web_search_enabled)
                                val disableSearchMsg = stringResource(R.string.web_search_disabled)
                                SearchQuickConfigMenu(
                                    enableSearch = enableSearch,
                                    settings = settings,
                                    model = chatModel,
                                    modifier = Modifier
                                        .heightIn(max = 360.dp)
                                        .verticalScroll(rememberScrollState()),
                                    onToggleSearch = { enabled ->
                                        onToggleSearch(enabled)
                                        toaster.show(
                                            message = if (enabled) enableSearchMsg else disableSearchMsg,
                                            duration = 1.seconds,
                                            type = if (enabled) {
                                                ToastType.Success
                                            } else {
                                                ToastType.Normal
                                            }
                                        )
                                    },
                                    onUpdateSearchService = onUpdateSearchService,
                                    onOpenSettings = {
                                        activeSubmenu = null
                                        dismissExpand()
                                        navController.navigate(Screen.SettingSearch)
                                    },
                                )
                            }

                            ActionSubmenu.Reasoning -> {
                                ReasoningQuickConfigMenu(
                                    reasoningTokens = assistant.thinkingBudget ?: 0,
                                    onUpdateReasoningTokens = {
                                        onUpdateAssistant(assistant.copy(thinkingBudget = it))
                                    },
                                )
                            }

                            ActionSubmenu.Mcp -> {
                                McpQuickConfigMenu(
                                    assistant = assistant,
                                    servers = settings.mcpServers,
                                    mcpManager = mcpManager,
                                    onUpdateAssistant = onUpdateAssistant,
                                )
                            }

                            ActionSubmenu.Injection -> {
                                InjectionQuickConfigMenu(
                                    assistant = assistant,
                                    settings = settings,
                                    onUpdateAssistant = onUpdateAssistant,
                                )
                            }

                            null -> Unit
                        }
                    }
                }

                // Send Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = state.loading || !state.isEmpty(),
                            onClick = {
                                expand = ExpandState.Collapsed
                                sendMessage()
                            },
                            onLongClick = {
                                expand = ExpandState.Collapsed
                                sendMessageWithoutAnswer()
                            }
                        )
                ) {
                    val containerColor = when {
                        state.loading -> MaterialTheme.colorScheme.errorContainer // 加载时，红色
                        state.isEmpty() -> MaterialTheme.colorScheme.surfaceContainerHigh // 禁用时(输入为空)，灰色
                        else -> MaterialTheme.colorScheme.primary // 启用时(输入非空)，绿色/主题色
                    }
                    val contentColor = when {
                        state.loading -> MaterialTheme.colorScheme.onErrorContainer
                        state.isEmpty() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // 禁用时，内容用带透明度的灰色
                        else -> MaterialTheme.colorScheme.onPrimary
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = containerColor,
                        content = {}
                    )
                    if (state.loading) {
                        KeepScreenOn()
                        Icon(Lucide.X, stringResource(R.string.stop), tint = contentColor)
                    } else {
                        Icon(Lucide.ArrowUp, stringResource(R.string.send), tint = contentColor)
                    }
                }
            }

            BackHandler(
                enabled = expand != ExpandState.Collapsed,
            ) {
                if (activeSubmenu != null) {
                    activeSubmenu = null
                } else {
                    dismissExpand()
                }
            }
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    context: Context,
) {
    val assistant = LocalSettings.current.getCurrentAssistant()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        // TextField
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.weight(1f)
        ) {
            Column {
                if (state.isEditing()) {
                    Surface(
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.editing),
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Lucide.X, stringResource(R.string.cancel_edit),
                                modifier = Modifier
                                    .clickable {
                                        state.clearInput()
                                    }
                            )
                        }
                    }
                }
                var isFocused by remember { mutableStateOf(false) }
                var isFullScreen by remember { mutableStateOf(false) }
                val receiveContentListener = remember {
                    ReceiveContentListener { transferableContent ->
                        when {
                            transferableContent.hasMediaType(MediaType.Image) -> {
                                transferableContent.consume { item ->
                                    val uri = item.uri
                                    if (uri != null) {
                                        state.addImages(
                                            context.createChatFilesByContents(
                                                listOf(
                                                    uri
                                                )
                                            )
                                        )
                                    }
                                    uri != null
                                }
                            }

                            else -> transferableContent
                        }
                    }
                }
                TextField(
                    state = state.textContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .contentReceiver(receiveContentListener)
                        .onFocusChanged {
                            isFocused = it.isFocused
                        },
                    shape = RoundedCornerShape(32.dp),
                    placeholder = {
                        Text(stringResource(R.string.chat_input_placeholder))
                    },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                    colors = TextFieldDefaults.colors().copy(
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (isFocused) {
                            IconButton(
                                onClick = {
                                    isFullScreen = !isFullScreen
                                }
                            ) {
                                Icon(Lucide.Fullscreen, null)
                            }
                        }
                    },
                    leadingIcon = if (assistant.quickMessages.isNotEmpty()) {
                        {
                            QuickMessageButton(assistant = assistant, state = state)
                        }
                    } else null,
                )
                if (isFullScreen) {
                    FullScreenEditor(state = state) {
                        isFullScreen = false
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMessageButton(
    assistant: Assistant,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }
    ) {
        Icon(Lucide.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp)
                .width(IntrinsicSize.Min)
        ) {
            assistant.quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFileInputRow(
    state: ChatInputState,
    context: Context
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        state.messageContent.filterIsInstance<UIMessagePart.Image>().fastForEach { image ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(20.dp)
                        .clickable {
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == image }
                            // Delete image
                            context.deleteChatFiles(listOf(image.url.toUri()))
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Video>().fastForEach { video ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.Video, null)
                    }
                }
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(20.dp)
                        .clickable {
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == video }
                            // Delete image
                            context.deleteChatFiles(listOf(video.url.toUri()))
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Audio>().fastForEach { audio ->
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.FileAudio, null)
                    }
                }
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(20.dp)
                        .clickable {
                            // Remove image
                            state.messageContent =
                                state.messageContent.filterNot { it == audio }
                            // Delete image
                            context.deleteChatFiles(listOf(audio.url.toUri()))
                        }
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.secondary),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
        state.messageContent.filterIsInstance<UIMessagePart.Document>()
            .fastForEach { document ->
                Box {
                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(max = 128.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(
                                0.8f
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    text = document.fileName,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = null,
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(20.dp)
                            .clickable {
                                // Remove image
                                state.messageContent =
                                    state.messageContent.filterNot { it == document }
                                // Delete image
                                context.deleteChatFiles(listOf(document.url.toUri()))
                            }
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.secondary),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
    }
}

@Composable
private fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    enableSearch: Boolean,
    activeSubmenu: ActionSubmenu?,
    chatModel: Model?,
    onOpenSubmenu: (ActionSubmenu) -> Unit,
    onClearContext: () -> Unit,
    onDismiss: () -> Unit
) {
    val settings = LocalSettings.current
    val provider = settings.getCurrentChatModel()?.findProvider(providers = settings.providers)
    val reasoningAvailable = chatModel?.abilities?.contains(ModelAbility.REASONING) == true
    val mcpAvailable = settings.mcpServers.isNotEmpty()
    val injectionAvailable = settings.modeInjections.isNotEmpty() || settings.lorebooks.isNotEmpty()
    val currentSearchService = settings.searchServices.getOrNull(settings.searchServiceSelected)

    FloatingMenuItem(
        icon = Lucide.Earth,
        text = stringResource(R.string.use_web_search),
        contentColor = if (activeSubmenu == ActionSubmenu.Search) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        trailingContent = {
            val serviceName = currentSearchService?.let {
                SearchServiceOptions.TYPES[it::class]
            }
            SubmenuIndicator(
                label = if (enableSearch) serviceName else null,
                active = activeSubmenu == ActionSubmenu.Search
            )
        },
        onClick = { onOpenSubmenu(ActionSubmenu.Search) },
    )

    if (reasoningAvailable) {
        val reasoningLevel = ReasoningLevel.fromBudgetTokens(assistant.thinkingBudget ?: 0)
        val reasoningLabel = when (reasoningLevel) {
            ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
            ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
            ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
            ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
            ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
        }
        FloatingMenuItem(
            icon = Lucide.Lightbulb,
            text = stringResource(R.string.setting_provider_page_reasoning),
            contentColor = if (activeSubmenu == ActionSubmenu.Reasoning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            trailingContent = {
                SubmenuIndicator(
                    label = reasoningLabel,
                    active = activeSubmenu == ActionSubmenu.Reasoning
                )
            },
            onClick = { onOpenSubmenu(ActionSubmenu.Reasoning) },
        )
    }

    if (mcpAvailable) {
        val enabledCount = settings.mcpServers.count { assistant.mcpServers.contains(it.id) }
        FloatingMenuItem(
            icon = Lucide.Terminal,
            text = stringResource(R.string.mcp_picker_title),
            contentColor = if (activeSubmenu == ActionSubmenu.Mcp) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            trailingContent = {
                SubmenuIndicator(
                    label = if (enabledCount > 0) enabledCount.toString() else null,
                    active = activeSubmenu == ActionSubmenu.Mcp
                )
            },
            onClick = { onOpenSubmenu(ActionSubmenu.Mcp) },
        )
    }

    if (injectionAvailable) {
        val activeCount = assistant.modeInjectionIds.size + assistant.lorebookIds.size
        FloatingMenuItem(
            icon = Lucide.BookOpen,
            text = stringResource(R.string.chat_page_prompt_injections),
            contentColor = if (activeSubmenu == ActionSubmenu.Injection) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            trailingContent = {
                SubmenuIndicator(
                    label = if (activeCount > 0) activeCount.toString() else null,
                    active = activeSubmenu == ActionSubmenu.Injection
                )
            },
            onClick = { onOpenSubmenu(ActionSubmenu.Injection) },
        )
    }

    FloatingMenuDivider()

    TakePicButton(
        onAddImages = { state.addImages(it) },
    )

    ImagePickButton(
        onAddImages = { state.addImages(it) },
    )

    if (provider != null && provider is ProviderSetting.Google) {
        VideoPickButton(
            onAddVideos = { state.addVideos(it) },
        )

        AudioPickButton(
            onAddAudios = { state.addAudios(it) },
        )
    }

    FilePickButton(
        onAddFiles = { state.addFiles(it) },
    )

    FloatingMenuDivider()

    FloatingMenuItem(
        icon = Lucide.Eraser,
        text = stringResource(R.string.chat_page_clear_context),
        trailingContent = {
            val displaySetting = settings.displaySetting
            if (displaySetting.showTokenUsage && conversation.messageNodes.isNotEmpty()) {
                val configuredContextSize = assistant.contextMessageSize
                val effectiveMessagesAfterTruncation =
                    conversation.messageNodes.size - conversation.truncateIndex.coerceAtLeast(0)
                val actualContextMessageCount =
                    minOf(effectiveMessagesAfterTruncation, configuredContextSize)
                Text(
                    text = "$actualContextMessageCount/$configuredContextSize",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
        onClick = {
            onDismiss()
            onClearContext()
        },
    )
}

@Composable
private fun SubmenuIndicator(
    label: String?,
    active: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Lucide.ChevronLeft,
            contentDescription = null,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

@Composable
private fun ReasoningQuickConfigMenu(
    reasoningTokens: Int,
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val options = listOf(
            Triple(ReasoningLevel.OFF, R.string.reasoning_off, Lucide.LightbulbOff),
            Triple(ReasoningLevel.AUTO, R.string.reasoning_auto, Lucide.Sparkle),
            Triple(ReasoningLevel.LOW, R.string.reasoning_light, Lucide.Lightbulb),
            Triple(ReasoningLevel.MEDIUM, R.string.reasoning_medium, Lucide.Lightbulb),
            Triple(ReasoningLevel.HIGH, R.string.reasoning_heavy, Lucide.Lightbulb),
        )
        options.forEach { (level, labelRes, icon) ->
            val selected = currentLevel == level
            FloatingMenuItem(
                icon = icon,
                text = stringResource(labelRes),
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                trailingContent = {
                    if (selected) {
                        Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    val newTokens = when (level) {
                        ReasoningLevel.OFF -> 0
                        ReasoningLevel.AUTO -> -1
                        ReasoningLevel.LOW -> 1024
                        ReasoningLevel.MEDIUM -> 16_000
                        ReasoningLevel.HIGH -> 32_000
                    }
                    onUpdateReasoningTokens(newTokens)
                },
            )
        }

        FloatingMenuDivider()

        Text(
            text = stringResource(R.string.reasoning_custom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        var input by remember(reasoningTokens) {
            mutableStateOf(reasoningTokens.toString())
        }
        OutlinedTextField(
            value = input,
            onValueChange = { newValue ->
                input = newValue
                newValue.toIntOrNull()?.let { onUpdateReasoningTokens(it) }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun McpQuickConfigMenu(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.filter { it.commonOptions.enable }

    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearWavyProgressIndicator()
                Text(
                    text = stringResource(id = R.string.mcp_picker_syncing),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            enabledServers.forEach { server ->
                val serverStatus by mcpManager.getStatus(server)
                    .collectAsStateWithLifecycle(McpStatus.Idle)
                val isEnabled = server.id in assistant.mcpServers
                val statusLabel = when (serverStatus) {
                    McpStatus.Idle -> "Idle"
                    McpStatus.Connecting -> "Connecting"
                    McpStatus.Connected -> "Connected"
                    is McpStatus.Error -> "Error: ${(serverStatus as McpStatus.Error).message}"
                }
                FloatingMenuItem(
                    icon = Lucide.Terminal,
                    text = server.commonOptions.name,
                    trailingContent = {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                val newServers = assistant.mcpServers.toMutableSet()
                                if (checked) {
                                    newServers.add(server.id)
                                } else {
                                    newServers.remove(server.id)
                                }
                                newServers.removeIf { id ->
                                    enabledServers.none { it.id == id }
                                }
                                onUpdateAssistant(
                                    assistant.copy(mcpServers = newServers.toSet())
                                )
                            }
                        )
                    },
                    onClick = {
                        val newServers = assistant.mcpServers.toMutableSet()
                        if (isEnabled) {
                            newServers.remove(server.id)
                        } else {
                            newServers.add(server.id)
                        }
                        newServers.removeIf { id ->
                            enabledServers.none { it.id == id }
                        }
                        onUpdateAssistant(
                            assistant.copy(mcpServers = newServers.toSet())
                        )
                    },
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun InjectionQuickConfigMenu(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (settings.modeInjections.isNotEmpty()) {
            Text(
                text = stringResource(R.string.injection_selector_mode_injections),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            settings.modeInjections.fastForEach { injection ->
                val selected = assistant.modeInjectionIds.contains(injection.id)
                FloatingMenuItem(
                    icon = Lucide.BookOpen,
                    text = injection.name.ifBlank {
                        stringResource(R.string.injection_selector_unnamed)
                    },
                    trailingContent = {
                        Switch(
                            checked = selected,
                            onCheckedChange = { checked ->
                                val newIds = if (checked) {
                                    assistant.modeInjectionIds + injection.id
                                } else {
                                    assistant.modeInjectionIds - injection.id
                                }
                                onUpdateAssistant(assistant.copy(modeInjectionIds = newIds))
                            }
                        )
                    },
                    onClick = {
                        val newIds = if (selected) {
                            assistant.modeInjectionIds - injection.id
                        } else {
                            assistant.modeInjectionIds + injection.id
                        }
                        onUpdateAssistant(assistant.copy(modeInjectionIds = newIds))
                    },
                )
            }
        }

        if (settings.lorebooks.isNotEmpty()) {
            if (settings.modeInjections.isNotEmpty()) {
                FloatingMenuDivider()
            }
            Text(
                text = stringResource(R.string.injection_selector_lorebooks),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            settings.lorebooks.fastForEach { lorebook ->
                val selected = assistant.lorebookIds.contains(lorebook.id)
                FloatingMenuItem(
                    icon = Lucide.BookOpen,
                    text = lorebook.name.ifBlank {
                        stringResource(R.string.injection_selector_unnamed_lorebook)
                    },
                    trailingContent = {
                        Switch(
                            checked = selected,
                            onCheckedChange = { checked ->
                                val newIds = if (checked) {
                                    assistant.lorebookIds + lorebook.id
                                } else {
                                    assistant.lorebookIds - lorebook.id
                                }
                                onUpdateAssistant(assistant.copy(lorebookIds = newIds))
                            }
                        )
                    },
                    onClick = {
                        val newIds = if (selected) {
                            assistant.lorebookIds - lorebook.id
                        } else {
                            assistant.lorebookIds + lorebook.id
                        }
                        onUpdateAssistant(assistant.copy(lorebookIds = newIds))
                    },
                )
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState,
    onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }
                        ) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = state.textContent,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(stringResource(R.string.chat_input_placeholder))
                        },
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun useCropLauncher(
    onCroppedImageReady: (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null
): Pair<ActivityResultLauncher<Intent>, (Uri) -> Unit> {
    val context = LocalContext.current
    var cropOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cropActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cropOutputUri?.let { croppedUri ->
                onCroppedImageReady(croppedUri)
            }
        }
        // Clean up crop output file
        cropOutputUri?.toFile()?.delete()
        cropOutputUri = null
        onCleanup?.invoke()
    }

    val launchCrop: (Uri) -> Unit = { sourceUri ->
        val outputFile = File(context.appTempFolder, "crop_output_${System.currentTimeMillis()}.jpg")
        cropOutputUri = Uri.fromFile(outputFile)

        val cropIntent = UCrop.of(sourceUri, cropOutputUri!!)
            .withOptions(UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setAllowedGestures(
                    UCropActivity.SCALE,
                    UCropActivity.ROTATE,
                    UCropActivity.NONE
                )
                setCompressionFormat(Bitmap.CompressFormat.PNG)
            })
            .withMaxResultSize(4096, 4096)
            .getIntent(context)

        cropActivityLauncher.launch(cropIntent)
    }

    return Pair(cropActivityLauncher, launchCrop)
}

@Composable
private fun ImagePickButton(
    onAddImages: (List<Uri>) -> Unit = {},
    onBeforePick: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = LocalSettings.current

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            Log.d("ImagePickButton", "Selected URIs: $selectedUris")
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add images
                onAddImages(context.createChatFilesByContents(selectedUris))
            } else {
                // Show crop interface
                if (selectedUris.size == 1) {
                    // Single image - offer crop
                    launchCrop(selectedUris.first())
                } else {
                    // Multiple images - no crop
                    onAddImages(context.createChatFilesByContents(selectedUris))
                }
            }
        } else {
            Log.d("ImagePickButton", "No images selected")
        }
    }

    FloatingMenuItem(
        icon = Lucide.Image,
        text = stringResource(R.string.photo),
        onClick = {
            onBeforePick()
            imagePickerLauncher.launch("image/*")
        },
    )
}

@Composable
fun TakePicButton(
    onAddImages: (List<Uri>) -> Unit = {},
    onBeforePick: () -> Unit = {},
) {
    val cameraPermission = rememberPermissionState(PermissionCamera)

    val context = LocalContext.current
    val settings = LocalSettings.current
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }

    val (_, launchCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            onAddImages(context.createChatFilesByContents(listOf(croppedUri)))
        },
        onCleanup = {
            // Clean up camera temp file after cropping is done
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            // Check if we should skip crop based on settings
            if (settings.displaySetting.skipCropImage) {
                // Skip crop, directly add image
                onAddImages(context.createChatFilesByContents(listOf(cameraOutputUri!!)))
                // Clean up camera temp file
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
            } else {
                // Show crop interface
                launchCrop(cameraOutputUri!!)
            }
        } else {
            // Clean up camera temp file if capture failed
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }

    // 使用权限管理器包装
    PermissionManager(
        permissionState = cameraPermission
    ) {
        FloatingMenuItem(
            icon = Lucide.Camera,
            text = stringResource(R.string.take_picture),
            onClick = {
                onBeforePick()
                if (cameraPermission.allRequiredPermissionsGranted) {
                    // 权限已授权，直接启动相机
                    cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
                    cameraOutputUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cameraOutputFile!!
                    )
                    cameraLauncher.launch(cameraOutputUri!!)
                } else {
                    // 请求权限
                    cameraPermission.requestPermissions()
                }
            },
        )
    }
}

@Composable
fun VideoPickButton(
    onAddVideos: (List<Uri>) -> Unit = {},
    onBeforePick: () -> Unit = {},
) {
    val context = LocalContext.current
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onAddVideos(context.createChatFilesByContents(selectedUris))
        }
    }

    FloatingMenuItem(
        icon = Lucide.Video,
        text = stringResource(R.string.video),
        onClick = {
            onBeforePick()
            videoPickerLauncher.launch("video/*")
        },
    )
}

@Composable
fun AudioPickButton(
    onAddAudios: (List<Uri>) -> Unit = {},
    onBeforePick: () -> Unit = {},
) {
    val context = LocalContext.current
    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            onAddAudios(context.createChatFilesByContents(selectedUris))
        }
    }

    FloatingMenuItem(
        icon = Lucide.Music,
        text = stringResource(R.string.audio),
        onClick = {
            onBeforePick()
            audioPickerLauncher.launch("audio/*")
        },
    )
}

@Composable
fun FilePickButton(
    onAddFiles: (List<UIMessagePart.Document>) -> Unit = {},
    onBeforePick: () -> Unit = {},
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val allowedMimeTypes = setOf(
                    "text/plain",
                    "text/html",
                    "text/css",
                    "text/javascript",
                    "text/csv",
                    "text/xml",
                    "application/json",
                    "application/javascript",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )

                val documents = uris.mapNotNull { uri ->
                    val fileName = context.getFileNameFromUri(uri) ?: "file"
                    val mime = context.getFileMimeType(uri) ?: "text/plain"

                    // Filter by MIME type or file extension
                    val isAllowed = allowedMimeTypes.contains(mime) ||
                        mime.startsWith("text/") ||
                        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        mime == "application/pdf" ||
                        fileName.endsWith(".txt", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".csv", ignoreCase = true) ||
                        fileName.endsWith(".json", ignoreCase = true) ||
                        fileName.endsWith(".js", ignoreCase = true) ||
                        fileName.endsWith(".html", ignoreCase = true) ||
                        fileName.endsWith(".css", ignoreCase = true) ||
                        fileName.endsWith(".xml", ignoreCase = true) ||
                        fileName.endsWith(".py", ignoreCase = true) ||
                        fileName.endsWith(".java", ignoreCase = true) ||
                        fileName.endsWith(".kt", ignoreCase = true) ||
                        fileName.endsWith(".ts", ignoreCase = true) ||
                        fileName.endsWith(".tsx", ignoreCase = true) ||
                        fileName.endsWith(".md", ignoreCase = true) ||
                        fileName.endsWith(".markdown", ignoreCase = true) ||
                        fileName.endsWith(".mdx", ignoreCase = true) ||
                        fileName.endsWith(".yml", ignoreCase = true) ||
                        fileName.endsWith(".yaml", ignoreCase = true)

                    if (isAllowed) {
                        val localUri = context.createChatFilesByContents(listOf(uri)).firstOrNull()
                        if (localUri == null) {
                            toaster.show("无法读取文件: $fileName", type = ToastType.Error)
                            null
                        } else {
                            UIMessagePart.Document(
                                url = localUri.toString(),
                                fileName = fileName,
                                mime = mime
                            )
                        }
                    } else {
                        toaster.show("不支持的文件类型: $fileName", type = ToastType.Error)
                        null
                    }
                }

                if (documents.isNotEmpty()) {
                    onAddFiles(documents)
                }
            }
        }
    FloatingMenuItem(
        icon = Lucide.Files,
        text = stringResource(R.string.upload_file),
        onClick = {
            onBeforePick()
            pickMedia.launch(arrayOf("*/*"))
        },
    )
}
