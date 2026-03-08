package me.rerere.rikkahub.service

import androidx.work.Data
import kotlin.uuid.Uuid

internal const val CONVERSATION_COMPRESSION_WORK_TAG = "conversation_compression"
private const val CONVERSATION_ID_TAG_PREFIX = "conversation_compression_id:"
internal const val INPUT_CONVERSATION_ID = "conversation_id"

internal fun autoCompressionWorkName(conversationId: Uuid): String =
    "conversation_auto_compression_$conversationId"

internal fun conversationCompressionTag(conversationId: Uuid): String =
    "$CONVERSATION_ID_TAG_PREFIX$conversationId"

internal fun conversationCompressionInputData(conversationId: Uuid): Data {
    return Data.Builder()
        .putString(INPUT_CONVERSATION_ID, conversationId.toString())
        .build()
}
