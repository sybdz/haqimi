package me.rerere.tts.provider

fun TTSProviderSetting.validate(): String? {
    return when (this) {
        is TTSProviderSetting.OpenAI -> when {
            apiKey.isBlank() -> "OpenAI API key is required"
            baseUrl.isBlank() -> "OpenAI base URL is required"
            model.isBlank() -> "OpenAI model is required"
            voice.isBlank() -> "OpenAI voice is required"
            else -> null
        }

        is TTSProviderSetting.Gemini -> when {
            apiKey.isBlank() -> "Gemini API key is required"
            baseUrl.isBlank() -> "Gemini base URL is required"
            model.isBlank() -> "Gemini model is required"
            voiceName.isBlank() -> "Gemini voice name is required"
            else -> null
        }

        is TTSProviderSetting.MiniMax -> when {
            apiKey.isBlank() -> "MiniMax API key is required"
            baseUrl.isBlank() -> "MiniMax base URL is required"
            model.isBlank() -> "MiniMax model is required"
            voiceId.isBlank() -> "MiniMax voice id is required"
            else -> null
        }

        is TTSProviderSetting.Qwen -> when {
            apiKey.isBlank() -> "Qwen API key is required"
            baseUrl.isBlank() -> "Qwen base URL is required"
            model.isBlank() -> "Qwen model is required"
            voice.isBlank() -> "Qwen voice is required"
            else -> null
        }

        is TTSProviderSetting.Doubao -> when {
            appId.isBlank() -> "Doubao App ID is required"
            accessKey.isBlank() -> "Doubao Access Key is required"
            resourceId.isBlank() -> "Doubao Resource ID is required"
            baseUrl.isBlank() -> "Doubao base URL is required"
            voiceType.isBlank() -> "Doubao voice_type is required"
            format.isBlank() -> "Doubao format is required"
            sampleRate <= 0 -> "Doubao sample rate must be positive"
            emotionScale !in 1..5 -> "Doubao emotion scale must be 1-5"
            else -> null
        }

        is TTSProviderSetting.SystemTTS -> null
    }
}
