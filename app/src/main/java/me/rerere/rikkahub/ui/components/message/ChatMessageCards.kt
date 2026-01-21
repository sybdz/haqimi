package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.MessageNodeGroupType
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.base64Encode
import java.util.Locale

@Composable
fun ChatMessageCards(
    nodes: List<MessageNode>,
    conversation: Conversation,
    settings: Settings,
    assistant: Assistant? = null,
    loading: Boolean = false,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onFork: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onShare: (MessageNode) -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onArenaVote: ((MessageNode) -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
) {
    if (nodes.isEmpty()) return

    val pagerState = rememberPagerState { nodes.size }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val displaySetting = LocalSettings.current.displaySetting
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio
    )

    val groupType = nodes.first().groupType
    val arenaState = nodes.first().arena
    val isArena = groupType == MessageNodeGroupType.ARENA && arenaState != null
    val revealed = arenaState?.revealed == true

    fun currentNode(): MessageNode? = nodes.getOrNull(pagerState.currentPage)
    fun currentMessage(): UIMessage? = currentNode()?.currentMessage
    fun currentModel(): Model? {
        val message = currentMessage() ?: return null
        if (isArena && !revealed) return null
        return message.modelId?.let(settings::findModelById)
    }

    var showActionsMenu by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val node = nodes[page]
            val message = node.currentMessage
            val model = if (isArena && !revealed) null else message.modelId?.let(settings::findModelById)
            val messages = conversation.currentMessages
            val messageIndex = messages.indexOf(message)
            val showModelIcon = displaySetting.showModelIcon && model != null
            val showModelName = displaySetting.showModelName && model != null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when {
                                isArena && !revealed -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = ('A' + page).toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }

                                showModelIcon -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                    ) {
                                        AutoAIIcon(
                                            name = model!!.modelId,
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }
                            }

                            val title = when {
                                isArena && !revealed -> "答案 ${('A' + page)}"
                                showModelName -> model!!.displayName
                                else -> ""
                            }
                            if (title.isNotBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (isArena && !revealed && !loading && onArenaVote != null) {
                            TextButton(onClick = { onArenaVote(node) }) {
                                Text(text = "投票")
                            }
                        }
                    }

                    ProvideTextStyle(textStyle) {
                        if (message.role == MessageRole.ASSISTANT && message.parts.isEmptyUIMessage() && message.finishedAt == null) {
                            LoadingIndicator()
                        } else {
                            MessagePartsBlock(
                                assistant = assistant,
                                role = message.role,
                                model = model,
                                parts = message.parts,
                                annotations = message.annotations,
                                messages = messages,
                                messageIndex = messageIndex,
                                loading = loading && message.finishedAt == null,
                                onToolApproval = onToolApproval,
                                onDeleteToolPart = null,
                            )

                            message.translation?.let { translation ->
                                CollapsibleTranslationText(
                                    content = translation,
                                    onClickCitation = {},
                                )
                            }
                        }
                    }

                    ChatMessageLocalActionButtons(
                        message = message,
                        onTranslate = onTranslate,
                        onClearTranslation = onClearTranslation,
                    )

                    ChatMessageNerdLine(message = message)
                }
            }
        }

        if (!loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = currentMessage() != null,
                    onClick = { currentMessage()?.let(onRegenerate) }
                ) {
                    Icon(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = "Retry",
                        modifier = Modifier.size(18.dp),
                    )
                }

                Box {
                    IconButton(
                        enabled = currentMessage() != null,
                        onClick = { showActionsMenu = true }
                    ) {
                        Icon(
                            imageVector = Lucide.Ellipsis,
                            contentDescription = "More",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    val message = currentMessage()
                    val node = currentNode()
                    if (message != null && node != null) {
                        ChatMessageMoreMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false },
                            message = message,
                            model = currentModel(),
                            onEdit = { onEdit(message) },
                            onDelete = { onDelete(message) },
                            onShare = { onShare(node) },
                            onFork = { onFork(message) },
                            onSelectAndCopy = {
                                showSelectCopySheet = true
                            },
                            onWebViewPreview = {
                                val textContent = message.parts
                                    .filterIsInstance<UIMessagePart.Text>()
                                    .joinToString("\n\n") { it.text }
                                    .trim()
                                if (textContent.isNotBlank()) {
                                    val htmlContent = buildMarkdownPreviewHtml(
                                        context = context,
                                        markdown = textContent,
                                        colorScheme = colorScheme
                                    )
                                    navController.navigate(Screen.WebView(content = htmlContent.base64Encode()))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSelectCopySheet) {
        val message = currentMessage()
        if (message != null) {
            ChatMessageCopySheet(
                message = message,
                onDismissRequest = {
                    showSelectCopySheet = false
                }
            )
        }
    }
}
