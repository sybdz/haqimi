package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

object ToolImageRedactionTransformer : InputMessageTransformer {
    private val keysToRedact = setOf("images", "image", "image_base64")
    private val baseKeyHints = listOf("base", "base64")
    private val base64Regex = Regex("^[A-Za-z0-9+/=\\r\\n]+\$")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.TOOL) return@map message

            val updatedParts = message.parts.map { part ->
                if (part !is UIMessagePart.ToolResult) return@map part
                part.copy(content = redactContent(part.content))
            }
            message.copy(parts = updatedParts)
        }
    }

    private data class RedactionStats(
        val images: Int = 0,
        val base64: Int = 0,
    )

    private fun redactContent(content: JsonElement): JsonElement {
        val (cleaned, stats) = sanitize(content)

        if (cleaned !is JsonObject) return cleaned
        if (stats.images == 0 && stats.base64 == 0) return cleaned

        return buildJsonObject {
            cleaned.forEach { (key, value) -> put(key, value) }
            if (stats.images > 0) {
                put("images_redacted", JsonPrimitive(true))
                put("images_count", JsonPrimitive(stats.images))
            }
            if (stats.base64 > 0) {
                put("base64_redacted", JsonPrimitive(true))
                put("base64_count", JsonPrimitive(stats.base64))
            }
        }
    }

    private fun sanitize(element: JsonElement): Pair<JsonElement, RedactionStats> {
        return when (element) {
            is JsonObject -> sanitizeObject(element)
            is JsonArray -> sanitizeArray(element)
            is JsonPrimitive -> sanitizePrimitive(element)
            is JsonNull -> element to RedactionStats()
        }
    }

    private fun sanitizeObject(obj: JsonObject): Pair<JsonObject, RedactionStats> {
        var imageCount = 0
        var base64Count = 0

        val cleaned = buildJsonObject {
            obj.forEach { (key, value) ->
                if (key in keysToRedact) {
                    imageCount += countImages(value)
                    return@forEach
                }
                if (shouldRedactBaseKey(key, value)) {
                    base64Count += countBase64Value(value).coerceAtLeast(1)
                    return@forEach
                }

                val (childValue, childStats) = sanitize(value)
                imageCount += childStats.images
                base64Count += childStats.base64
                put(key, childValue)
            }
        }

        return cleaned to RedactionStats(images = imageCount, base64 = base64Count)
    }

    private fun sanitizeArray(array: JsonArray): Pair<JsonArray, RedactionStats> {
        var imageCount = 0
        var base64Count = 0
        val cleaned = buildJsonArray {
            array.forEach { element ->
                val (childValue, childStats) = sanitize(element)
                imageCount += childStats.images
                base64Count += childStats.base64
                add(childValue)
            }
        }
        return cleaned to RedactionStats(images = imageCount, base64 = base64Count)
    }

    private fun sanitizePrimitive(primitive: JsonPrimitive): Pair<JsonPrimitive, RedactionStats> {
        val content = primitive.contentOrNull
        val isRedacted = content != null && isBase64Like(content)
        return if (isRedacted) {
            JsonPrimitive("[base64 omitted]") to RedactionStats(base64 = 1)
        } else {
            primitive to RedactionStats()
        }
    }

    private fun countImages(element: JsonElement): Int = when (element) {
        is JsonArray -> element.size
        is JsonObject -> countImages(element)
        else -> 1
    }

    private fun countImages(obj: JsonObject): Int {
        val imagesValue = obj["images"]
        val imagesCount = when (imagesValue) {
            is JsonArray -> imagesValue.size
            null -> 0
            else -> 1
        }
        val singleImageCount = listOf("image", "image_base64").count { key ->
            obj[key] != null
        }
        return imagesCount + singleImageCount
    }

    private fun countBase64Value(element: JsonElement): Int = when (element) {
        is JsonArray -> element.count { it is JsonPrimitive && it.contentOrNull?.let(::isBase64Like) == true }
        is JsonPrimitive -> if (element.contentOrNull?.let(::isBase64Like) == true) 1 else 0
        else -> 0
    }

    private fun shouldRedactBaseKey(key: String, value: JsonElement): Boolean {
        val lower = key.lowercase()
        val hasBaseHint = baseKeyHints.any { hint -> lower.contains(hint) }
        if (!hasBaseHint) return false
        return when (value) {
            is JsonPrimitive -> {
                val content = value.contentOrNull ?: return false
                content.length > 64 || isBase64Like(content)
            }

            is JsonArray -> value.any { element ->
                element is JsonPrimitive && element.contentOrNull?.let {
                    it.length > 64 || isBase64Like(it)
                } == true
            }

            else -> false
        }
    }

    private fun isBase64Like(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.startsWith("data:") && ";base64," in trimmed) return true
        val compact = trimmed.replace("\\s".toRegex(), "")
        if (compact.length < 128) return false
        return base64Regex.matches(compact)
    }
}
