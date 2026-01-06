package me.rerere.tts.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.tts.provider.TtsCustomBody
import me.rerere.tts.provider.TtsCustomHeader
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject

fun List<TtsCustomHeader>.toHeaders(): Headers {
    return Headers.Builder().apply {
        this@toHeaders
            .filter { it.name.isNotBlank() }
            .forEach {
                add(it.name, it.value)
            }
    }.build()
}

fun JsonObject.mergeCustomBody(bodies: List<TtsCustomBody>): JsonObject {
    if (bodies.isEmpty()) return this

    val content = toMutableMap()
    bodies.forEach { body ->
        if (body.key.isNotBlank()) {
            val existingValue = content[body.key]
            val newValue = body.value

            content[body.key] = if (existingValue is JsonObject && newValue is JsonObject) {
                mergeJsonObjects(existingValue, newValue)
            } else {
                newValue
            }
        }
    }
    return JsonObject(content)
}

fun JSONObject.mergeCustomBody(bodies: List<TtsCustomBody>): JSONObject {
    if (bodies.isEmpty()) return this

    bodies.forEach { body ->
        val key = body.key.trim()
        if (key.isNotBlank()) {
            val newValue = body.value.toOrgJsonValue()
            val existing = opt(key)
            if (existing is JSONObject && newValue is JSONObject) {
                put(key, mergeJsonObjects(existing, newValue))
            } else {
                put(key, newValue)
            }
        }
    }
    return this
}

private fun mergeJsonObjects(base: JsonObject, overlay: JsonObject): JsonObject {
    val result = base.toMutableMap()

    for ((key, value) in overlay) {
        val baseValue = result[key]
        result[key] = if (baseValue is JsonObject && value is JsonObject) {
            mergeJsonObjects(baseValue, value)
        } else {
            value
        }
    }

    return JsonObject(result)
}

private fun mergeJsonObjects(base: JSONObject, overlay: JSONObject): JSONObject {
    val result = JSONObject(base.toString())
    val keys = overlay.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = overlay.get(key)
        val baseValue = result.opt(key)
        if (baseValue is JSONObject && value is JSONObject) {
            result.put(key, mergeJsonObjects(baseValue, value))
        } else {
            result.put(key, value)
        }
    }
    return result
}

private fun JsonElement.toOrgJsonValue(): Any? {
    return when (this) {
        JsonNull -> JSONObject.NULL
        is JsonPrimitive -> when {
            isString -> content
            booleanValueOrNull() != null -> booleanValueOrNull()
            longValueOrNull() != null -> longValueOrNull()
            doubleValueOrNull() != null -> doubleValueOrNull()
            else -> content
        }
        is JsonObject -> {
            val obj = JSONObject()
            for ((key, value) in this) {
                obj.put(key, value.toOrgJsonValue())
            }
            obj
        }
        is JsonArray -> {
            val array = JSONArray()
            for (item in this) {
                array.put(item.toOrgJsonValue())
            }
            array
        }
        else -> JSONObject.NULL
    }
}

private fun JsonPrimitive.booleanValueOrNull(): Boolean? {
    if (isString) return null
    return content.toBooleanStrictOrNull()
}

private fun JsonPrimitive.longValueOrNull(): Long? {
    if (isString) return null
    return content.toLongOrNull()
}

private fun JsonPrimitive.doubleValueOrNull(): Double? {
    if (isString) return null
    return content.toDoubleOrNull()
}
