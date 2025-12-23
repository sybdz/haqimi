package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

object ToolImageRedactionTransformer : InputMessageTransformer {
    private val keysToRedact = setOf("images", "image", "image_base64")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.TOOL) return@map message

            val updatedParts = message.parts.map { part ->
                if (part !is UIMessagePart.ToolResult) return@map part
                part.copy(content = redactImages(part.content))
            }
            message.copy(parts = updatedParts)
        }
    }

    private fun redactImages(content: JsonElement): JsonElement {
        if (content !is JsonObject) return content

        val imageCount = countImages(content)
        if (imageCount == 0 && keysToRedact.none { it in content }) return content

        return buildJsonObject {
            content.forEach { (key, value) ->
                if (key !in keysToRedact) {
                    put(key, value)
                }
            }
            put("images_redacted", JsonPrimitive(true))
            put("images_count", JsonPrimitive(imageCount))
        }
    }

    private fun countImages(content: JsonObject): Int {
        val imagesValue = content["images"]
        val imagesCount = when (imagesValue) {
            is JsonArray -> imagesValue.size
            null -> 0
            else -> 1
        }
        val singleImageCount = listOf("image", "image_base64").count { key ->
            content[key] != null
        }
        return imagesCount + singleImageCount
    }
}

