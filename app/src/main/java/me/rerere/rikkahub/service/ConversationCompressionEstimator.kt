package me.rerere.rikkahub.service

import kotlin.math.ceil
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

private const val REQUEST_OVERHEAD_TOKENS = 24
private const val MESSAGE_OVERHEAD_TOKENS = 6
private const val MEDIA_PART_ESTIMATE_TOKENS = 32
private const val DOCUMENT_PART_ESTIMATE_TOKENS = 48
private const val TOOL_PART_ESTIMATE_TOKENS = 24
private const val UTF8_BYTES_PER_TOKEN = 3.5

internal fun estimateConversationInputTokens(messages: List<UIMessage>): Int {
    if (messages.isEmpty()) return 0

    val lastAssistantIndex = messages.indexOfLast { message ->
        message.role == MessageRole.ASSISTANT && (message.usage?.promptTokens ?: 0) > 0
    }

    return if (lastAssistantIndex >= 0) {
        val exactPromptTokens = messages[lastAssistantIndex].usage?.promptTokens ?: 0
        exactPromptTokens + messages.drop(lastAssistantIndex).sumOf(::estimateMessageTokens) + REQUEST_OVERHEAD_TOKENS
    } else {
        messages.sumOf(::estimateMessageTokens) + REQUEST_OVERHEAD_TOKENS
    }
}

internal fun estimateMessageTokens(message: UIMessage): Int {
    return MESSAGE_OVERHEAD_TOKENS + message.parts.sumOf(::estimatePartTokens)
}

@Suppress("DEPRECATION")
internal fun estimatePartTokens(part: UIMessagePart): Int {
    return when (part) {
        is UIMessagePart.Text -> estimateTextTokens(part.text)
        is UIMessagePart.Image -> MEDIA_PART_ESTIMATE_TOKENS
        is UIMessagePart.Video -> MEDIA_PART_ESTIMATE_TOKENS
        is UIMessagePart.Audio -> MEDIA_PART_ESTIMATE_TOKENS
        is UIMessagePart.Document -> DOCUMENT_PART_ESTIMATE_TOKENS + estimateTextTokens(part.fileName)
        is UIMessagePart.Reasoning -> 0
        is UIMessagePart.Search -> 0
        is UIMessagePart.Tool -> {
            TOOL_PART_ESTIMATE_TOKENS +
                estimateTextTokens(part.toolName) +
                estimateTextTokens(part.input) +
                part.output.sumOf(::estimatePartTokens)
        }

        is UIMessagePart.ToolCall -> {
            TOOL_PART_ESTIMATE_TOKENS +
                estimateTextTokens(part.toolName) +
                estimateTextTokens(part.arguments)
        }

        is UIMessagePart.ToolResult -> {
            TOOL_PART_ESTIMATE_TOKENS +
                estimateTextTokens(part.toolName) +
                estimateTextTokens(part.arguments.toString()) +
                estimateTextTokens(part.content.toString())
        }
    }
}

internal fun estimateTextTokens(text: String): Int {
    if (text.isBlank()) return 0
    val utf8Bytes = text.toByteArray(Charsets.UTF_8).size.toDouble()
    return ceil(utf8Bytes / UTF8_BYTES_PER_TOKEN).toInt().coerceAtLeast(1)
}
