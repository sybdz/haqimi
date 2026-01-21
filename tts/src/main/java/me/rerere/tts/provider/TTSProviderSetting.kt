package me.rerere.tts.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class TTSProviderSetting {
    abstract val id: Uuid
    abstract val name: String
    abstract val customHeaders: List<TtsCustomHeader>
    abstract val customBody: List<TtsCustomBody>

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): TTSProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "OpenAI TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "gpt-4o-mini-tts",
        val voice: String = "alloy",
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("gemini")
    data class Gemini(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Gemini TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val model: String = "gemini-2.5-flash-preview-tts",
        val voiceName: String = "Kore",
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("system")
    data class SystemTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "System TTS",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("minimax")
    data class MiniMax(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiniMax TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.minimaxi.com/v1",
        val model: String = "speech-2.6-turbo",
        val voiceId: String = "female-shaonv",
        val emotion: String = "calm",
        val speed: Float = 1.0f,
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("qwen")
    data class Qwen(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Qwen TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://dashscope.aliyuncs.com/api/v1",
        val model: String = "qwen3-tts-flash",
        val voice: String = "Cherry",
        val languageType: String = "Auto",
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("doubao")
    data class Doubao(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Doubao TTS",
        val appId: String = "",
        val accessKey: String = "",
        val baseUrl: String = "https://openspeech.bytedance.com",
        val resourceId: String = "volc.service_type.10029",
        val requestId: String = "",
        val requireUsageTokensReturn: Boolean = false,
        val uid: String = "rikkahub",
        val namespace: String = "BidirectionalTTS",
        val model: String = "",
        val voiceType: String = "",
        val useSsml: Boolean = false,
        val format: String = "mp3",
        val sampleRate: Int = 24000,
        val bitRate: Int = 0,
        val speechRate: Int = 0,
        val loudnessRate: Int = 0,
        val emotion: String = "",
        val emotionScale: Int = 4,
        val enableTimestamp: Boolean = false,
        val additions: String = "",
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("groq")
    data class Groq(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Groq TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.groq.com/openai/v1",
        val model: String = "canopylabs/orpheus-v1-english",
        val voice: String = "austin",
        override val customHeaders: List<TtsCustomHeader> = emptyList(),
        override val customBody: List<TtsCustomBody> = emptyList(),
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Gemini::class,
                SystemTTS::class,
                MiniMax::class,
                Qwen::class,
                Groq::class,
                Doubao::class,
            )
        }
    }
}
