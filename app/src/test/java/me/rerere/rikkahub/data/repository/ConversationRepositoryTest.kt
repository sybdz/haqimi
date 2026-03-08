package me.rerere.rikkahub.data.repository

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isCompressionCheckpoint
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.service.createCompressionCheckpointMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {
    @Test
    fun `extractLeadingCompressionCheckpoints should preserve legacy node ids`() {
        val legacyCheckpointId = kotlin.uuid.Uuid.random()
        val secondCheckpointId = kotlin.uuid.Uuid.random()
        val visibleNode = UIMessage.assistant("visible").toMessageNode()

        val extracted = extractLeadingCompressionCheckpoints(
            listOf(
                MessageNode(
                    id = legacyCheckpointId,
                    messages = listOf(
                        createCompressionCheckpointMessage(
                            summary = "summary-1",
                            level = 1,
                            sourceMessageCount = 4
                        )
                    )
                ),
                MessageNode(
                    id = secondCheckpointId,
                    messages = listOf(
                        createCompressionCheckpointMessage(
                            summary = "summary-2",
                            level = 2,
                            sourceMessageCount = 7
                        )
                    )
                ),
                visibleNode,
            )
        )

        assertEquals(listOf(legacyCheckpointId, secondCheckpointId), extracted.checkpoints.map { it.id })
        assertEquals(listOf("summary-1", "summary-2"), extracted.checkpoints.map { it.message.toText() })
        assertEquals(listOf(visibleNode.id), extracted.visibleNodes.map { it.id })
    }

    @Test
    fun `extractLeadingCompressionCheckpoints should stop at first visible node`() {
        val visibleNode = UIMessage.user("visible").toMessageNode()
        val trailingCheckpoint = MessageNode(
            messages = listOf(
                createCompressionCheckpointMessage(
                    summary = "trailing-summary",
                    level = 1,
                    sourceMessageCount = 3
                )
            )
        )

        val extracted = extractLeadingCompressionCheckpoints(
            listOf(
                visibleNode,
                trailingCheckpoint,
            )
        )

        assertTrue(extracted.checkpoints.isEmpty())
        assertEquals(listOf(visibleNode.id, trailingCheckpoint.id), extracted.visibleNodes.map { it.id })
    }

    @Test
    fun `extractLeadingCompressionCheckpoints should migrate legacy summary nodes into replacement history`() {
        val legacyCheckpointId = kotlin.uuid.Uuid.random()
        val legacySummary = MessageNode(
            id = legacyCheckpointId,
            messages = listOf(
                UIMessage.user(
                    """
                    [Summary of previous conversation]
                    User is debugging a compression-history rollout and needs legacy exports preserved.
                    """.trimIndent()
                )
            )
        )
        val visibleNode = UIMessage.assistant("visible").toMessageNode()

        val extracted = extractLeadingCompressionCheckpoints(
            listOf(
                legacySummary,
                visibleNode,
            )
        )

        assertEquals(listOf(legacyCheckpointId), extracted.checkpoints.map { it.id })
        assertTrue(extracted.checkpoints.single().message.isCompressionCheckpoint())
        assertEquals(
            legacySummary.currentMessage.toText(),
            extracted.checkpoints.single().message.toText()
        )
        assertEquals(listOf(visibleNode.id), extracted.visibleNodes.map { it.id })
    }
}
