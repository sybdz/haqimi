package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.util.mergeCustomBody
import me.rerere.tts.util.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "DoubaoTTSProvider"

class DoubaoTTSProvider : TTSProvider<TTSProviderSetting.Doubao> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Doubao,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        require(providerSetting.appId.isNotBlank()) { "Doubao App ID is required" }
        require(providerSetting.accessKey.isNotBlank()) { "Doubao Access Key is required" }
        require(providerSetting.resourceId.isNotBlank()) { "Doubao Resource ID is required" }
        require(providerSetting.voiceType.isNotBlank()) { "Doubao voice_type is required" }

        val requestBody = JSONObject().apply {
            put("user", JSONObject().apply {
                put("uid", providerSetting.uid)
            })
            if (providerSetting.namespace.isNotBlank()) {
                put("namespace", providerSetting.namespace)
            }
            put("req_params", JSONObject().apply {
                if (providerSetting.useSsml) {
                    put("ssml", request.text)
                } else {
                    put("text", request.text)
                }
                if (providerSetting.model.isNotBlank()) {
                    put("model", providerSetting.model)
                }
                if (providerSetting.voiceType.isNotBlank()) {
                    put("voice_type", providerSetting.voiceType)
                }
                put("audio_params", JSONObject().apply {
                    put("format", providerSetting.format)
                    put("sample_rate", providerSetting.sampleRate)
                    if (providerSetting.bitRate > 0) {
                        put("bit_rate", providerSetting.bitRate)
                    }
                    put("speech_rate", providerSetting.speechRate)
                    put("loudness_rate", providerSetting.loudnessRate)
                    if (providerSetting.emotion.isNotBlank()) {
                        put("emotion", providerSetting.emotion)
                    }
                    put("emotion_scale", providerSetting.emotionScale)
                    put("enable_timestamp", providerSetting.enableTimestamp)
                })
                if (providerSetting.additions.isNotBlank()) {
                    put("additions", providerSetting.additions)
                }
            })
        }.mergeCustomBody(providerSetting.customBody)

        Log.i(TAG, "generateSpeech: $requestBody")

        val url = "${providerSetting.baseUrl.trimEnd('/')}/api/v3/tts/unidirectional"
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(providerSetting.customHeaders.toHeaders())
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Api-App-Id", providerSetting.appId)
            .addHeader("X-Api-Access-Key", providerSetting.accessKey)
            .addHeader("X-Api-Resource-Id", providerSetting.resourceId)

        if (providerSetting.requestId.isNotBlank()) {
            requestBuilder.addHeader("X-Api-Request-Id", providerSetting.requestId)
        }
        if (providerSetting.requireUsageTokensReturn) {
            requestBuilder.addHeader("X-Control-Require-Usage-Tokens-Return", "true")
        }

        val httpRequest = requestBuilder
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(httpRequest).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e(TAG, "Doubao TTS request failed: ${response.code} ${response.message}, body: $errorBody")
            throw Exception("Doubao TTS request failed: ${response.code} ${response.message}")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("Doubao TTS response body is empty")

        val format = resolveAudioFormat(providerSetting.format)
        val sampleRate = providerSetting.sampleRate.takeIf { it > 0 }
        var hasEmittedAudio = false
        var finished = false

        reader.useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val payload = if (line.startsWith("data:")) {
                    line.removePrefix("data:").trim()
                } else {
                    line
                }
                if (!payload.startsWith("{")) return@forEach

                try {
                    val json = JSONObject(payload)
                    val code = json.optInt("code", 0)
                    val message = json.optString("message", "")
                    val data = json.optString("data", "")
                    val sentence = json.optJSONObject("sentence")
                    val usage = json.optJSONObject("usage")

                    when {
                        data.isNotEmpty() -> {
                            val audioData = Base64.decode(data, Base64.DEFAULT)
                            emit(
                                AudioChunk(
                                    data = audioData,
                                    format = format,
                                    sampleRate = sampleRate,
                                    isLast = false,
                                    metadata = mapOf(
                                        "provider" to "doubao",
                                        "voiceType" to providerSetting.voiceType,
                                        "format" to providerSetting.format,
                                        "sampleRate" to (sampleRate?.toString() ?: "")
                                    )
                                )
                            )
                            hasEmittedAudio = true
                        }

                        code == 20000000 -> {
                            if (!finished) {
                                val metadata = buildMap<String, String> {
                                    put("provider", "doubao")
                                    put("voiceType", providerSetting.voiceType)
                                    put("format", providerSetting.format)
                                    sampleRate?.let { put("sampleRate", it.toString()) }
                                    if (usage != null) put("usage", usage.toString())
                                    if (sentence != null) put("sentence", sentence.toString())
                                }
                                emit(
                                    AudioChunk(
                                        data = byteArrayOf(),
                                        format = format,
                                        sampleRate = sampleRate,
                                        isLast = true,
                                        metadata = metadata
                                    )
                                )
                            }
                            finished = true
                        }

                        code != 0 -> {
                            throw Exception("Doubao TTS error: $code $message")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Doubao TTS chunk: $payload", e)
                }
            }
        }

        if (!finished && hasEmittedAudio) {
            emit(
                AudioChunk(
                    data = byteArrayOf(),
                    format = format,
                    sampleRate = sampleRate,
                    isLast = true,
                    metadata = mapOf(
                        "provider" to "doubao",
                        "voiceType" to providerSetting.voiceType
                    )
                )
            )
        }
    }

    private fun resolveAudioFormat(format: String): AudioFormat {
        return when (format.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "ogg", "ogg_opus" -> AudioFormat.OPUS
            "opus" -> AudioFormat.OPUS
            "pcm" -> AudioFormat.PCM
            else -> AudioFormat.MP3
        }
    }
}
