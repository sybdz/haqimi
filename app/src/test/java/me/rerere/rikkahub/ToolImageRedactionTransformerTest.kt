package me.rerere.rikkahub

import android.test.mock.MockContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.ToolImageRedactionTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolImageRedactionTransformerTest {
    private val ctx = TransformerContext(
        context = MockContext(),
        model = Model(),
        assistant = Assistant(),
        settings = Settings()
    )

    @Test
    fun `base and image payloads are stripped from tool results`() = runBlocking {
        val base64Image = "data:image/png;base64," + "A".repeat(256)
        val messages = listOf(
            UIMessage(
                role = MessageRole.TOOL,
                parts = listOf(
                    UIMessagePart.ToolResult(
                        toolCallId = "call-1",
                        toolName = "eval_python",
                        content = buildJsonObject {
                            put("image", JsonPrimitive(base64Image))
                            put("base", JsonPrimitive("B".repeat(80)))
                            put("ok", JsonPrimitive(true))
                        },
                        arguments = JsonObject(emptyMap())
                    )
                )
            )
        )

        val redacted = ToolImageRedactionTransformer.transform(ctx, messages).first()
        val toolResult = redacted.parts.first() as UIMessagePart.ToolResult
        val content = toolResult.content.jsonObject

        assertFalse(content.containsKey("image"))
        assertFalse(content.containsKey("base"))
        assertTrue(content["images_redacted"]?.jsonPrimitive?.booleanOrNull == true)
        assertTrue(content["base64_redacted"]?.jsonPrimitive?.booleanOrNull == true)
        assertEquals(1, content["images_count"]?.jsonPrimitive?.int)
        assertEquals(1, content["base64_count"]?.jsonPrimitive?.int)
        assertTrue(content["ok"]?.jsonPrimitive?.booleanOrNull == true)
    }
}
