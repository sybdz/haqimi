package me.rerere.tts.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TtsCustomHeader(
    val name: String,
    val value: String
)

@Serializable
data class TtsCustomBody(
    val key: String,
    val value: JsonElement
)
