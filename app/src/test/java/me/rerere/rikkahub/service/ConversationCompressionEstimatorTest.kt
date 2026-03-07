package me.rerere.rikkahub.service

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCompressionEstimatorTest {
    @Test
    fun `estimateConversationInputTokens should reuse latest assistant prompt tokens when available`() {
        val assistantMessage = UIMessage.assistant("Previous answer").copy(
            usage = TokenUsage(
                promptTokens = 120,
                completionTokens = 30,
                totalTokens = 150
            )
        )
        val messages = listOf(
            UIMessage.user("Earlier question"),
            assistantMessage,
            UIMessage.user("New question")
        )

        val estimated = estimateConversationInputTokens(messages)
        val expected = 120 + estimateMessageTokens(assistantMessage) + estimateMessageTokens(messages.last()) + 24

        assertEquals(expected, estimated)
    }

    @Test
    fun `estimateConversationInputTokens should fall back to message estimates without usage`() {
        val messages = listOf(
            UIMessage.user("Hello"),
            UIMessage.assistant("World")
        )

        val estimated = estimateConversationInputTokens(messages)
        val expected = messages.sumOf(::estimateMessageTokens) + 24

        assertEquals(expected, estimated)
    }

    @Test
    fun `estimateTextTokens should count multi byte text conservatively`() {
        assertTrue(estimateTextTokens("Hello, world!") >= 4)
        assertTrue(estimateTextTokens("你好，世界") >= 4)
    }
}
