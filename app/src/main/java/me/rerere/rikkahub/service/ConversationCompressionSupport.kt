package me.rerere.rikkahub.service

import android.net.Uri
import androidx.core.net.toUri
import me.rerere.ai.core.MessageRole
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UISyntheticKind
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.readDocumentContent
import me.rerere.rikkahub.data.model.Conversation
import kotlin.math.max

private const val COMPRESSION_MIN_CHUNK_INPUT_TOKENS = 4_000
private const val COMPRESSION_CHUNK_TOKEN_MULTIPLIER = 8
private const val COMPRESSION_MAX_ENTRIES_PER_CHUNK = 256
private const val COMPRESSION_TEXT_PART_MAX_CHARS = 8_000
private const val COMPRESSION_REASONING_PART_MAX_CHARS = 3_000
private const val COMPRESSION_DOCUMENT_PART_MAX_CHARS = 12_000
private const val COMPRESSION_TOOL_INPUT_MAX_CHARS = 4_000
private const val COMPRESSION_TOOL_OUTPUT_MAX_CHARS = 6_000
private const val COMPRESSION_CHECKPOINT_METADATA_KEY = "rikkahub.compression_checkpoint"
private const val COMPRESSION_CHECKPOINT_LEVEL_METADATA_KEY = "rikkahub.compression_checkpoint_level"
private const val COMPRESSION_CHECKPOINT_SOURCE_MESSAGE_COUNT_METADATA_KEY =
    "rikkahub.compression_checkpoint_source_message_count"
private const val COMPRESSION_CHECKPOINT_BUDGET_BUFFER_MIN_TOKENS = 96
private val LEGACY_COMPRESSION_SUMMARY_PREFIXES = listOf(
    "[summary",
    "[summary of previous conversation",
    "[conversation summary",
    "[compressed context",
    "summary:",
    "summary of previous conversation",
    "conversation summary:",
    "compressed context:",
    "[摘要",
    "[总结",
    "[總結",
    "[对话摘要",
    "[對話摘要",
    "[会话摘要",
    "[會話摘要",
    "[要約",
    "[요약",
    "[резюме",
    "摘要:",
    "总结:",
    "總結:",
    "对话摘要:",
    "對話摘要:",
    "会话摘要:",
    "會話摘要:",
    "要約:",
    "요약:",
    "резюме:",
    "сводка:"
)
private val LEGACY_COMPRESSION_SUMMARY_KEYWORDS = listOf(
    "summary",
    "compressed context",
    "conversation summary",
    "previous conversation",
    "摘要",
    "总结",
    "總結",
    "对话摘要",
    "對話摘要",
    "会话摘要",
    "會話摘要",
    "要約",
    "요약",
    "резюме",
    "сводка"
)

internal data class CompressionMessageSplit(
    val messagesToCompress: List<UIMessage>,
    val messagesToKeep: List<UIMessage>,
)

internal data class CompressionTranscriptEntry(
    val transcript: String,
    val sourceMessageCount: Int,
)

internal data class ConversationCompressionPlan(
    val messagesToCompress: List<UIMessage>,
    val visibleMessagesToKeep: List<UIMessage>,
) {
    val visibleKeepCount: Int
        get() = visibleMessagesToKeep.size
}

internal data class CompressionCheckpointMetadata(
    val level: Int,
    val sourceMessageCount: Int,
)

internal data class AutoCompressionPlan(
    val inputTokenBudget: Int,
    val estimatedInputTokens: Int,
    val targetTokens: Int,
    val keepRecentMessages: Int,
    val compressionPlan: ConversationCompressionPlan,
)

internal fun createCompressionCheckpointMessage(
    summary: String,
    level: Int,
    sourceMessageCount: Int,
): UIMessage {
    return UIMessage.user(summary)
        .withCompressionCheckpointMetadata(level = level, sourceMessageCount = sourceMessageCount)
}

internal fun normalizeCompressionCheckpointMessage(message: UIMessage): UIMessage {
    val metadata = message.compressionCheckpointMetadata() ?: return message
    return message.withCompressionCheckpointMetadata(
        level = metadata.level,
        sourceMessageCount = metadata.sourceMessageCount,
    )
}

internal fun normalizeLegacyCompressionSummaryMessage(message: UIMessage): UIMessage {
    return message.withCompressionCheckpointMetadata(level = 1, sourceMessageCount = 0)
}

internal fun UIMessage.toReplacementHistoryMessageOrNull(): UIMessage? {
    val metadata = compressionCheckpointMetadata()
    if (metadata != null) {
        return withCompressionCheckpointMetadata(
            level = metadata.level,
            sourceMessageCount = metadata.sourceMessageCount,
        )
    }

    return if (isLegacyCompressionSummary()) {
        normalizeLegacyCompressionSummaryMessage(this)
    } else {
        null
    }
}

internal fun UIMessage.compressionCheckpointMetadata(): CompressionCheckpointMetadata? {
    when (val kind = syntheticKind) {
        is UISyntheticKind.CompressionCheckpoint -> {
            return CompressionCheckpointMetadata(
                level = kind.level.coerceAtLeast(1),
                sourceMessageCount = kind.sourceMessageCount.coerceAtLeast(0),
            )
        }
        null -> {}
    }

    val textPart = parts.filterIsInstance<UIMessagePart.Text>().firstOrNull() ?: return null
    val metadata = textPart.metadata ?: return null
    val isCheckpoint = metadata[COMPRESSION_CHECKPOINT_METADATA_KEY]
        ?.jsonPrimitive
        ?.booleanOrNull == true
    if (!isCheckpoint) return null

    return CompressionCheckpointMetadata(
        level = metadata[COMPRESSION_CHECKPOINT_LEVEL_METADATA_KEY]
            ?.jsonPrimitive
            ?.intOrNull
            ?.coerceAtLeast(1)
            ?: 1,
        sourceMessageCount = metadata[COMPRESSION_CHECKPOINT_SOURCE_MESSAGE_COUNT_METADATA_KEY]
            ?.jsonPrimitive
            ?.intOrNull
            ?.coerceAtLeast(0)
            ?: 0,
    )
}

internal fun UIMessage.isLegacyCompressionSummary(): Boolean {
    if (role != MessageRole.USER || syntheticKind != null) return false

    val textPart = parts.singleOrNull() as? UIMessagePart.Text ?: return false
    val normalizedFirstLine = textPart.text
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.replace('【', '[')
        ?.replace('】', ']')
        ?.replace('：', ':')
        ?.lowercase()
        ?: return false

    if (normalizedFirstLine.isBlank()) return false
    if (LEGACY_COMPRESSION_SUMMARY_PREFIXES.any(normalizedFirstLine::startsWith)) {
        return true
    }

    return normalizedFirstLine.startsWith("[") &&
        LEGACY_COMPRESSION_SUMMARY_KEYWORDS.any { keyword -> keyword in normalizedFirstLine }
}

internal fun splitMessagesForCompression(
    messages: List<UIMessage>,
    keepRecentMessages: Int,
    targetTokens: Int,
    maxInputTokensAfterCompression: Int? = null,
): CompressionMessageSplit {
    if (keepRecentMessages <= 0) {
        return CompressionMessageSplit(
            messagesToCompress = messages,
            messagesToKeep = emptyList(),
        )
    }

    val maxKeepCount = keepRecentMessages.coerceAtMost((messages.size - 1).coerceAtLeast(0))
    if (maxKeepCount <= 0) {
        return CompressionMessageSplit(
            messagesToCompress = emptyList(),
            messagesToKeep = messages,
        )
    }

    if (maxInputTokensAfterCompression == null || maxInputTokensAfterCompression <= 0) {
        val messagesToKeep = messages.takeLast(maxKeepCount)
        return CompressionMessageSplit(
            messagesToCompress = messages.dropLast(messagesToKeep.size),
            messagesToKeep = messagesToKeep,
        )
    }

    val checkpointTokenReserve = estimateCompressionCheckpointTokenReserve(targetTokens)
    for (candidateKeepCount in maxKeepCount downTo 1) {
        val messagesToKeep = messages.takeLast(candidateKeepCount)
        val estimatedTotalTokens = estimateConversationInputTokensWithoutReuse(messagesToKeep) +
            checkpointTokenReserve
        if (estimatedTotalTokens <= maxInputTokensAfterCompression) {
            return CompressionMessageSplit(
                messagesToCompress = messages.dropLast(messagesToKeep.size),
                messagesToKeep = messagesToKeep,
            )
        }
    }

    val fallbackMessagesToKeep = messages.takeLast(1)
    return CompressionMessageSplit(
        messagesToCompress = messages.dropLast(fallbackMessagesToKeep.size),
        messagesToKeep = fallbackMessagesToKeep,
    )
}

internal fun planConversationCompression(
    replacementHistoryMessages: List<UIMessage>,
    visibleMessages: List<UIMessage>,
    keepRecentMessages: Int,
    targetTokens: Int,
    maxInputTokensAfterCompression: Int? = null,
): ConversationCompressionPlan {
    val preferredKeepCount = keepRecentMessages
        .coerceAtLeast(0)
        .coerceAtMost(visibleMessages.size)
    val minKeepCount = if (visibleMessages.isNotEmpty()) 1 else 0

    if (maxInputTokensAfterCompression == null || maxInputTokensAfterCompression <= 0) {
        return buildConversationCompressionPlan(
            replacementHistoryMessages = replacementHistoryMessages,
            visibleMessages = visibleMessages,
            visibleKeepCount = preferredKeepCount,
        )
    }

    val checkpointTokenReserve = estimateCompressionCheckpointTokenReserve(targetTokens)
    for (candidateKeepCount in preferredKeepCount downTo minKeepCount) {
        val plan = buildConversationCompressionPlan(
            replacementHistoryMessages = replacementHistoryMessages,
            visibleMessages = visibleMessages,
            visibleKeepCount = candidateKeepCount,
        )
        if (plan.messagesToCompress.isEmpty()) {
            continue
        }

        val estimatedTotalTokens = estimateConversationInputTokensWithoutReuse(plan.visibleMessagesToKeep) +
            checkpointTokenReserve
        if (estimatedTotalTokens <= maxInputTokensAfterCompression) {
            return plan
        }
    }

    return buildConversationCompressionPlan(
        replacementHistoryMessages = replacementHistoryMessages,
        visibleMessages = visibleMessages,
        visibleKeepCount = minKeepCount,
    )
}

internal fun estimateCompressionCheckpointTokenReserve(targetTokens: Int): Int {
    val normalizedTarget = targetTokens.coerceAtLeast(1)
    return normalizedTarget + max(
        COMPRESSION_CHECKPOINT_BUDGET_BUFFER_MIN_TOKENS,
        normalizedTarget / 4
    )
}

internal fun planAutoCompression(
    conversation: Conversation,
    inputTokenBudget: Int?,
    targetTokens: Int,
    keepRecentMessages: Int,
): AutoCompressionPlan? {
    val normalizedBudget = inputTokenBudget?.takeIf { it > 0 } ?: return null
    val generationMessages = conversation.buildGenerationMessages()
    if (generationMessages.isEmpty()) return null

    val estimatedInputTokens = estimateConversationInputTokens(
        messages = generationMessages,
        allowPromptTokenReuse = conversation.replacementHistory.isEmpty()
    )
    if (estimatedInputTokens < normalizedBudget) return null

    val normalizedKeepRecentMessages = keepRecentMessages.coerceAtLeast(1)
    val compressionPlan = planConversationCompression(
        replacementHistoryMessages = conversation.replacementHistoryMessages,
        visibleMessages = conversation.currentMessages,
        keepRecentMessages = normalizedKeepRecentMessages,
        targetTokens = targetTokens,
        maxInputTokensAfterCompression = normalizedBudget,
    )
    if (compressionPlan.messagesToCompress.isEmpty()) return null

    return AutoCompressionPlan(
        inputTokenBudget = normalizedBudget,
        estimatedInputTokens = estimatedInputTokens,
        targetTokens = targetTokens,
        keepRecentMessages = normalizedKeepRecentMessages,
        compressionPlan = compressionPlan,
    )
}

internal fun UIMessage.toCompressionTranscript(): String {
    val checkpointMetadata = compressionCheckpointMetadata()
    val renderedParts = parts.mapNotNull { part ->
        part.toCompressionTranscriptPart()
    }
    return buildString {
        append("[")
        append(role.name)
        append("]")
        checkpointMetadata?.let { metadata ->
            append(" [COMPRESSION CHECKPOINT]")
            append("\n")
            append("Compressed earlier context at level ")
            append(metadata.level)
            if (metadata.sourceMessageCount > 0) {
                append(" from ")
                append(metadata.sourceMessageCount)
                append(" messages")
            }
        }
        if (renderedParts.isNotEmpty()) {
            append("\n")
            append(renderedParts.joinToString(separator = "\n"))
        }
    }
}

internal fun UIMessage.effectiveCompressionSourceMessageCount(): Int {
    return compressionCheckpointMetadata()
        ?.sourceMessageCount
        ?.takeIf { it > 0 }
        ?: 1
}

internal fun UIMessage.effectiveCompressionLevel(): Int {
    return compressionCheckpointMetadata()
        ?.level
        ?.takeIf { it > 0 }
        ?: 0
}

internal fun UIMessage.toCompressionTranscriptEntry(): CompressionTranscriptEntry {
    return CompressionTranscriptEntry(
        transcript = toCompressionTranscript(),
        sourceMessageCount = effectiveCompressionSourceMessageCount(),
    )
}

private fun buildConversationCompressionPlan(
    replacementHistoryMessages: List<UIMessage>,
    visibleMessages: List<UIMessage>,
    visibleKeepCount: Int,
): ConversationCompressionPlan {
    val normalizedKeepCount = visibleKeepCount.coerceIn(0, visibleMessages.size)
    val visibleMessagesToKeep = visibleMessages.takeLast(normalizedKeepCount)
    val visibleMessagesToCompress = visibleMessages.take(
        (visibleMessages.size - normalizedKeepCount).coerceAtLeast(0)
    )
    return ConversationCompressionPlan(
        messagesToCompress = replacementHistoryMessages + visibleMessagesToCompress,
        visibleMessagesToKeep = visibleMessagesToKeep,
    )
}

internal fun chunkCompressionEntries(
    entries: List<String>,
    targetTokens: Int,
): List<List<String>> {
    return chunkCompressionItems(entries, targetTokens, itemToText = { it })
}

internal fun chunkCompressionTranscriptEntries(
    entries: List<CompressionTranscriptEntry>,
    targetTokens: Int,
): List<List<CompressionTranscriptEntry>> {
    return chunkCompressionItems(entries, targetTokens, itemToText = CompressionTranscriptEntry::transcript)
}

internal fun combineCompressedChunkSummaries(
    chunkedEntries: List<List<CompressionTranscriptEntry>>,
    summaries: List<String>,
): List<CompressionTranscriptEntry> {
    require(chunkedEntries.size == summaries.size) {
        "chunkedEntries.size=${chunkedEntries.size} must match summaries.size=${summaries.size}"
    }

    return summaries.mapIndexed { index, summary ->
        CompressionTranscriptEntry(
            transcript = summary,
            sourceMessageCount = chunkedEntries[index].sumOf(CompressionTranscriptEntry::sourceMessageCount),
        )
    }
}

internal fun shouldContinueCompressionAfterSingleEntryPass(
    previousEntries: List<CompressionTranscriptEntry>,
    compressedEntries: List<CompressionTranscriptEntry>,
    targetTokens: Int,
): Boolean {
    if (compressedEntries.size < previousEntries.size) return true
    if (hasCompressionMergeOpportunity(compressedEntries, targetTokens)) return true
    return estimateCompressionTranscriptTokens(compressedEntries) <
        estimateCompressionTranscriptTokens(previousEntries)
}

private fun <T> chunkCompressionItems(
    entries: List<T>,
    targetTokens: Int,
    itemToText: (T) -> String,
): List<List<T>> {
    if (entries.isEmpty()) return emptyList()

    val chunkInputTokenBudget = (targetTokens * COMPRESSION_CHUNK_TOKEN_MULTIPLIER)
        .coerceAtLeast(COMPRESSION_MIN_CHUNK_INPUT_TOKENS)

    val chunks = mutableListOf<MutableList<T>>()
    var currentChunk = mutableListOf<T>()
    var currentChunkTokens = 0

    entries.forEach { entry ->
        val entryTokens = estimateTextTokens(itemToText(entry))
        val exceedsChunkBudget = currentChunkTokens > 0 &&
            currentChunkTokens + entryTokens > chunkInputTokenBudget
        val exceedsChunkCount = currentChunk.size >= COMPRESSION_MAX_ENTRIES_PER_CHUNK

        if (exceedsChunkBudget || exceedsChunkCount) {
            chunks += currentChunk
            currentChunk = mutableListOf()
            currentChunkTokens = 0
        }

        currentChunk += entry
        currentChunkTokens += entryTokens
    }

    if (currentChunk.isNotEmpty()) {
        chunks += currentChunk
    }

    return chunks
}

private fun UIMessage.withCompressionCheckpointMetadata(
    level: Int,
    sourceMessageCount: Int,
): UIMessage {
    val normalizedLevel = level.coerceAtLeast(1)
    val normalizedSourceMessageCount = sourceMessageCount.coerceAtLeast(0)
    val updatedParts = parts.updateFirstTextPartMetadata(
        level = normalizedLevel,
        sourceMessageCount = normalizedSourceMessageCount,
    )
    return copy(
        syntheticKind = UISyntheticKind.CompressionCheckpoint(
            level = normalizedLevel,
            sourceMessageCount = normalizedSourceMessageCount,
        ),
        parts = updatedParts,
    )
}

private fun List<UIMessagePart>.updateFirstTextPartMetadata(
    level: Int,
    sourceMessageCount: Int,
): List<UIMessagePart> {
    val firstTextIndex = indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex < 0) return this

    return mapIndexed { index, part ->
        if (index != firstTextIndex) {
            part
        } else {
            val textPart = part as UIMessagePart.Text
            textPart.copy(
                metadata = buildJsonObject {
                    textPart.metadata?.forEach { (key, value) -> put(key, value) }
                    put(COMPRESSION_CHECKPOINT_METADATA_KEY, JsonPrimitive(true))
                    put(COMPRESSION_CHECKPOINT_LEVEL_METADATA_KEY, JsonPrimitive(level))
                    put(
                        COMPRESSION_CHECKPOINT_SOURCE_MESSAGE_COUNT_METADATA_KEY,
                        JsonPrimitive(sourceMessageCount)
                    )
                }
            )
        }
    }
}

private fun hasCompressionMergeOpportunity(
    entries: List<CompressionTranscriptEntry>,
    targetTokens: Int,
): Boolean {
    return chunkCompressionTranscriptEntries(entries, targetTokens).any { it.size > 1 }
}

private fun estimateCompressionTranscriptTokens(entries: List<CompressionTranscriptEntry>): Int {
    return entries.sumOf { entry -> estimateTextTokens(entry.transcript) }
}

@Suppress("DEPRECATION")
private fun UIMessagePart.toCompressionTranscriptPart(): String? {
    return when (this) {
        is UIMessagePart.Text -> truncateForCompression(text, COMPRESSION_TEXT_PART_MAX_CHARS)
            .takeIf { it.isNotBlank() }

        is UIMessagePart.Image -> "[Image attachment] ${describeAttachment(url)}"
        is UIMessagePart.Video -> "[Video attachment] ${describeAttachment(url)}"
        is UIMessagePart.Audio -> "[Audio attachment] ${describeAttachment(url)}"
        is UIMessagePart.Document -> buildString {
            append("[Document] ")
            append(fileName)
            append(" (")
            append(mime)
            append(")")
            val content = truncateForCompression(
                readDocumentContent(this@toCompressionTranscriptPart),
                COMPRESSION_DOCUMENT_PART_MAX_CHARS
            )
            if (content.isNotBlank()) {
                append("\n")
                append(content)
            }
        }

        is UIMessagePart.Reasoning -> truncateForCompression(
            reasoning,
            COMPRESSION_REASONING_PART_MAX_CHARS
        ).takeIf { it.isNotBlank() }?.let { "[Reasoning]\n$it" }

        is UIMessagePart.Search -> "[Search tool used]"

        is UIMessagePart.ToolCall -> buildString {
            append("[Tool call] ")
            append(toolName.ifBlank { "unknown_tool" })
            val arguments = truncateForCompression(arguments, COMPRESSION_TOOL_INPUT_MAX_CHARS)
            if (arguments.isNotBlank()) {
                append("\nInput:\n")
                append(arguments)
            }
        }

        is UIMessagePart.ToolResult -> buildString {
            append("[Tool result] ")
            append(toolName.ifBlank { "unknown_tool" })
            val arguments = truncateForCompression(arguments.toString(), COMPRESSION_TOOL_INPUT_MAX_CHARS)
            val content = truncateForCompression(content.toString(), COMPRESSION_TOOL_OUTPUT_MAX_CHARS)
            if (arguments.isNotBlank()) {
                append("\nInput:\n")
                append(arguments)
            }
            if (content.isNotBlank()) {
                append("\nOutput:\n")
                append(content)
            }
        }

        is UIMessagePart.Tool -> buildString {
            append("[Tool] ")
            append(toolName.ifBlank { "unknown_tool" })
            val inputText = truncateForCompression(input, COMPRESSION_TOOL_INPUT_MAX_CHARS)
            if (inputText.isNotBlank()) {
                append("\nInput:\n")
                append(inputText)
            }
            val outputText = output.mapNotNull { outputPart ->
                outputPart.toCompressionTranscriptPart()
            }.joinToString(separator = "\n")
            val truncatedOutput = truncateForCompression(outputText, COMPRESSION_TOOL_OUTPUT_MAX_CHARS)
            if (truncatedOutput.isNotBlank()) {
                append("\nOutput:\n")
                append(truncatedOutput)
            }
        }
    }
}

private fun truncateForCompression(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars) + "\n...[truncated]"
}

private fun describeAttachment(rawUrl: String): String {
    val uri = runCatching { rawUrl.toUri() }.getOrNull()
    return when {
        uri == null -> rawUrl
        uri.scheme == "file" -> uri.lastPathSegment ?: rawUrl
        uri.scheme.isNullOrBlank() -> rawUrl
        else -> uri.lastPathSegment ?: uri.toString()
    }
}

private val Uri.lastPathSegment: String?
    get() = runCatching { pathSegments.lastOrNull() }.getOrNull()
