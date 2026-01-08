
package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpenText
import com.composables.icons.lucide.CircleStop
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.GitFork
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.TextSelect
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.FloatingMenu
import me.rerere.rikkahub.ui.components.ui.FloatingMenuDivider
import me.rerere.rikkahub.ui.components.ui.FloatingMenuItem
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.toLocalString
import java.util.Locale

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    model: Model?,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onWebViewPreview: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.Copy, stringResource(R.string.copy), modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp)
        )

        Icon(
            Lucide.RefreshCw, stringResource(R.string.regenerate), modifier = Modifier
                .clip(CircleShape)
                .clickable { onRegenerate() }
                .padding(8.dp)
                .size(16.dp)
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) Lucide.CircleStop else Lucide.Volume2,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                tts.speak(message.toText())
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
            )

            // Translation button
            if (onTranslate != null) {
                Icon(
                    imageVector = Lucide.Languages,
                    contentDescription = stringResource(R.string.translate),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = {
                                showTranslateDialog = true
                            }
                        )
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }

        Box {
            Icon(
                imageVector = Lucide.Ellipsis,
                contentDescription = "More Options",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            showMoreMenu = true
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp)
            )
            ChatMessageMoreMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                message = message,
                model = model,
                onDelete = onDelete,
                onEdit = onEdit,
                onShare = onShare,
                onFork = onFork,
                onSelectAndCopy = onSelectAndCopy,
                onWebViewPreview = onWebViewPreview,
            )
        }

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )
    }

    // Translation dialog
    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }
}

@Composable
fun ChatMessageMoreMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onWebViewPreview: () -> Unit,
) {
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }

    FloatingMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        FloatingMenuItem(
            icon = Lucide.TextSelect,
            text = stringResource(R.string.select_and_copy),
            onClick = {
                onDismissRequest()
                onSelectAndCopy()
            },
        )

        if (hasTextContent) {
            FloatingMenuItem(
                icon = Lucide.BookOpenText,
                text = stringResource(R.string.render_with_webview),
                onClick = {
                    onDismissRequest()
                    onWebViewPreview()
                },
            )
        }

        FloatingMenuDivider()

        FloatingMenuItem(
            icon = Lucide.Pencil,
            text = stringResource(R.string.edit),
            onClick = {
                onDismissRequest()
                onEdit()
            },
        )

        FloatingMenuItem(
            icon = Lucide.Share,
            text = stringResource(R.string.share),
            onClick = {
                onDismissRequest()
                onShare()
            },
        )

        FloatingMenuItem(
            icon = Lucide.GitFork,
            text = stringResource(R.string.create_fork),
            onClick = {
                onDismissRequest()
                onFork()
            },
        )

        FloatingMenuDivider()

        FloatingMenuItem(
            icon = Lucide.Trash2,
            text = stringResource(R.string.delete),
            contentColor = MaterialTheme.colorScheme.error,
            onClick = {
                onDismissRequest()
                onDelete()
            },
        )

        FloatingMenuDivider()

        ProvideTextStyle(MaterialTheme.typography.labelSmall) {
            Text(message.createdAt.toJavaLocalDateTime().toLocalString())
            if (model != null) {
                Text(model.displayName)
            }
        }
    }
}

@Composable
fun ChatMessageLocalActionButtons(
    message: UIMessage,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    var showTranslateDialog by remember { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.Copy, stringResource(R.string.copy), modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp)
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) Lucide.CircleStop else Lucide.Volume2,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                tts.speak(message.toText())
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
            )

            if (onTranslate != null) {
                Icon(
                    imageVector = Lucide.Languages,
                    contentDescription = stringResource(R.string.translate),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { showTranslateDialog = true }
                        )
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }
    }

    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }
}
