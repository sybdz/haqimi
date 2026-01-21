package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TtsCustomBody
import me.rerere.tts.provider.TtsCustomHeader

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val updateCustomHeaders: (List<TtsCustomHeader>) -> Unit = { headers ->
        val updated = when (setting) {
            is TTSProviderSetting.OpenAI -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.Gemini -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.SystemTTS -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.MiniMax -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.Qwen -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.Groq -> setting.copy(customHeaders = headers)
            is TTSProviderSetting.Doubao -> setting.copy(customHeaders = headers)
        }
        onValueChange(updated)
    }

    val updateCustomBody: (List<TtsCustomBody>) -> Unit = { bodies ->
        val updated = when (setting) {
            is TTSProviderSetting.OpenAI -> setting.copy(customBody = bodies)
            is TTSProviderSetting.Gemini -> setting.copy(customBody = bodies)
            is TTSProviderSetting.SystemTTS -> setting.copy(customBody = bodies)
            is TTSProviderSetting.MiniMax -> setting.copy(customBody = bodies)
            is TTSProviderSetting.Qwen -> setting.copy(customBody = bodies)
            is TTSProviderSetting.Groq -> setting.copy(customBody = bodies)
            is TTSProviderSetting.Doubao -> setting.copy(customBody = bodies)
        }
        onValueChange(updated)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        // Provider type selector
        var expanded by remember { mutableStateOf(false) }
        val providers = remember { TTSProviderSetting.Types }

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_provider_type)) },
            description = { Text(stringResource(R.string.setting_tts_page_provider_type_description)) },
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = when (setting) {
                        is TTSProviderSetting.OpenAI -> "OpenAI"
                        is TTSProviderSetting.Gemini -> "Gemini"
                        is TTSProviderSetting.SystemTTS -> "System TTS"
                        is TTSProviderSetting.MiniMax -> "MiniMax"
                        is TTSProviderSetting.Qwen -> "Qwen"
                        is TTSProviderSetting.Groq -> "Groq"
                        is TTSProviderSetting.Doubao -> "Doubao"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    providers.forEach { providerClass ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (providerClass) {
                                        TTSProviderSetting.OpenAI::class -> "OpenAI"
                                        TTSProviderSetting.Gemini::class -> "Gemini"
                                        TTSProviderSetting.SystemTTS::class -> "System TTS"
                                        TTSProviderSetting.MiniMax::class -> "MiniMax"
                                        TTSProviderSetting.Qwen::class -> "Qwen"
                                        TTSProviderSetting.Groq::class -> "Groq"
                                        TTSProviderSetting.Doubao::class -> "Doubao"
                                        else -> providerClass.simpleName ?: "Unknown"
                                    }
                                )
                            },
                            onClick = {
                                expanded = false
                                val newSetting = when (providerClass) {
                                    TTSProviderSetting.OpenAI::class -> TTSProviderSetting.OpenAI(
                                        id = setting.id,
                                        name = "OpenAI TTS"
                                    )

                                    TTSProviderSetting.Gemini::class -> TTSProviderSetting.Gemini(
                                        id = setting.id,
                                        name = "Gemini TTS"
                                    )

                                    TTSProviderSetting.SystemTTS::class -> TTSProviderSetting.SystemTTS(
                                        id = setting.id,
                                        name = "System TTS"
                                    )

                                    TTSProviderSetting.MiniMax::class -> TTSProviderSetting.MiniMax(
                                        id = setting.id,
                                        name = "MiniMax TTS"
                                    )

                                    TTSProviderSetting.Qwen::class -> TTSProviderSetting.Qwen(
                                        id = setting.id,
                                        name = "Qwen TTS"
                                    )

                                    TTSProviderSetting.Groq::class -> TTSProviderSetting.Groq(
                                        id = setting.id,
                                        name = "Groq TTS"
                                    )

                                    TTSProviderSetting.Doubao::class -> TTSProviderSetting.Doubao(
                                        id = setting.id,
                                        name = "Doubao TTS"
                                    )

                                    else -> setting
                                }
                                onValueChange(newSetting)
                            }
                        )
                    }
                }
            }
        }

        // Name
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_name)) },
            description = { Text(stringResource(R.string.setting_tts_page_name_description)) }
        ) {
            OutlinedTextField(
                value = setting.name,
                onValueChange = { newName ->
                    onValueChange(setting.copyProvider(name = newName))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) }
            )
        }

        // Provider-specific fields
        when (setting) {
            is TTSProviderSetting.OpenAI -> OpenAITTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Gemini -> GeminiTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.MiniMax -> MiniMaxTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Qwen -> QwenTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Groq -> GroqTTSConfiguration(setting, onValueChange)
            is TTSProviderSetting.Doubao -> DoubaoTTSConfiguration(setting, onValueChange)
        }

        if (setting !is TTSProviderSetting.SystemTTS) {
            HorizontalDivider()
            TtsCustomHeaders(headers = setting.customHeaders, onUpdate = updateCustomHeaders)
            HorizontalDivider()
            TtsCustomBodies(customBodies = setting.customBody, onUpdate = updateCustomBody)
        }
    }
}

@Composable
private fun OpenAITTSConfiguration(
    setting: TTSProviderSetting.OpenAI,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_openai)) },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_openai)) }
        )
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMaxTTSConfiguration(
    setting: TTSProviderSetting.MiniMax,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("speech-2.5-hd-preview") }
        )
    }

    // Voice ID
    var voiceIdExpanded by remember { mutableStateOf(false) }
    val voiceIds = listOf(
        "male-qn-qingse",
        "male-qn-jingying",
        "male-qn-badao",
        "male-qn-daxuesheng",
        "female-shaonv",
        "female-yujie",
        "female-chengshu",
        "female-tianmei",
        "audiobook_male_1",
        "audiobook_female_1",
        "cartoon_pig"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceIdExpanded,
            onExpandedChange = { voiceIdExpanded = !voiceIdExpanded }
        ) {
            OutlinedTextField(
                value = setting.voiceId,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceIdExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceIdExpanded,
                onDismissRequest = { voiceIdExpanded = false }
            ) {
                voiceIds.forEach { voiceId ->
                    DropdownMenuItem(
                        text = { Text(voiceId) },
                        onClick = {
                            voiceIdExpanded = false
                            onValueChange(setting.copy(voiceId = voiceId))
                        }
                    )
                }
            }
        }
    }

    // Emotion
    var emotionExpanded by remember { mutableStateOf(false) }
    val emotions = listOf("calm", "happy", "sad", "angry", "fearful", "disgusted", "surprised")

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_emotion)) },
        description = { Text(stringResource(R.string.setting_tts_page_emotion_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = emotionExpanded,
            onExpandedChange = { emotionExpanded = !emotionExpanded }
        ) {
            OutlinedTextField(
                value = setting.emotion,
                onValueChange = { newEmotion ->
                    onValueChange(setting.copy(emotion = newEmotion))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = emotionExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = emotionExpanded,
                onDismissRequest = { emotionExpanded = false }
            ) {
                emotions.forEach { emotion ->
                    DropdownMenuItem(
                        text = { Text(emotion) },
                        onClick = {
                            emotionExpanded = false
                            onValueChange(setting.copy(emotion = emotion))
                        }
                    )
                }
            }
        }
    }

    // Speed
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speed)) },
        description = { Text(stringResource(R.string.setting_tts_page_speed_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speed,
            onValueChange = { newSpeed ->
                if (newSpeed in 0.25f..4.0f) {
                    onValueChange(setting.copy(speed = newSpeed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speed)
        )
    }
}

@Composable
private fun GeminiTTSConfiguration(
    setting: TTSProviderSetting.Gemini,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_gemini)) },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_gemini)) }
        )
    }

    // Voice Name
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice_name)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_name_description)) }
    ) {
        OutlinedTextField(
            value = setting.voiceName,
            onValueChange = { newVoiceName ->
                onValueChange(setting.copy(voiceName = newVoiceName))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_voice_name_placeholder)) }
        )
    }
}

@Composable
private fun SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // Speech Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speech_rate)
        )
    }

    // Pitch
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_pitch)) },
        description = { Text(stringResource(R.string.setting_tts_page_pitch_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_pitch)
        )
    }
}

@Composable
private fun QwenTTSConfiguration(
    setting: TTSProviderSetting.Qwen,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen3-tts-flash") }
        )
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf(
        "Cherry", "Serene", "Ethan", "Chelsie",
        "Momo", "Vivian", "Moon", "Maia", "Kai",
        "Nofish", "Bella", "Jennifer", "Ryan",
        "Katerina", "Aiden", "Eldric Sage", "Mia",
        "Mochi", "Bellona", "Vincent", "Bunny",
        "Neil", "Elias", "Arthur", "Nini"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }

    // Language Type
    var languageExpanded by remember { mutableStateOf(false) }
    val languageTypes = listOf("Auto", "Chinese", "English", "Japanese", "Korean")

    FormItem(
        label = { Text("Language Type") },
        description = { Text("Language type for TTS synthesis") }
    ) {
        ExposedDropdownMenuBox(
            expanded = languageExpanded,
            onExpandedChange = { languageExpanded = !languageExpanded }
        ) {
            OutlinedTextField(
                value = setting.languageType,
                onValueChange = { newLanguageType ->
                    onValueChange(setting.copy(languageType = newLanguageType))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false }
            ) {
                languageTypes.forEach { languageType ->
                    DropdownMenuItem(
                        text = { Text(languageType) },
                        onClick = {
                            languageExpanded = false
                            onValueChange(setting.copy(languageType = languageType))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroqTTSConfiguration(
    setting: TTSProviderSetting.Groq,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gsk_xxx") },
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("canopylabs/orpheus-v1-english") }
        )
    }

    // Voice
    var voiceExpanded by remember { mutableStateOf(false) }
    val voices = listOf("austin", "natalie", "kailin")

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_voice)) },
        description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = voiceExpanded,
            onExpandedChange = { voiceExpanded = !voiceExpanded }
        ) {
            OutlinedTextField(
                value = setting.voice,
                onValueChange = { newVoice ->
                    onValueChange(setting.copy(voice = newVoice))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = voiceExpanded,
                onDismissRequest = { voiceExpanded = false }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voice = voice))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DoubaoTTSConfiguration(
    setting: TTSProviderSetting.Doubao,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // App ID
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_app_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_app_id_description)) }
    ) {
        OutlinedTextField(
            value = setting.appId,
            onValueChange = { onValueChange(setting.copy(appId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_app_id_placeholder)) }
        )
    }

    // Access Key
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_access_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_access_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.accessKey,
            onValueChange = { onValueChange(setting.copy(accessKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_access_key_placeholder)) }
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://openspeech.bytedance.com") }
        )
    }

    // Resource ID
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_resource_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_resource_id_description)) }
    ) {
        OutlinedTextField(
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_resource_id_placeholder)) }
        )
    }

    // Request ID
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_request_id)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_request_id_description)) }
    ) {
        OutlinedTextField(
            value = setting.requestId,
            onValueChange = { onValueChange(setting.copy(requestId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_request_id_placeholder)) }
        )
    }

    // Require Usage Tokens Return
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_return_usage_tokens)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_return_usage_tokens_description)) }
    ) {
        Switch(
            checked = setting.requireUsageTokensReturn,
            onCheckedChange = { onValueChange(setting.copy(requireUsageTokensReturn = it)) }
        )
    }

    // UID
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_user_uid)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_user_uid_description)) }
    ) {
        OutlinedTextField(
            value = setting.uid,
            onValueChange = { onValueChange(setting.copy(uid = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_user_uid_placeholder)) }
        )
    }

    // Namespace
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_namespace)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_namespace_description)) }
    ) {
        OutlinedTextField(
            value = setting.namespace,
            onValueChange = { onValueChange(setting.copy(namespace = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_namespace_placeholder)) }
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("seed-tts-1.0") }
        )
    }

    // Voice Type
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_voice_type)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_voice_type_description)) }
    ) {
        OutlinedTextField(
            value = setting.voiceType,
            onValueChange = { onValueChange(setting.copy(voiceType = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_voice_type_placeholder)) }
        )
    }

    // Use SSML
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_use_ssml)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_use_ssml_description)) }
    ) {
        Switch(
            checked = setting.useSsml,
            onCheckedChange = { onValueChange(setting.copy(useSsml = it)) }
        )
    }

    // Audio Format
    var formatExpanded by remember { mutableStateOf(false) }
    val formats = listOf("mp3", "ogg_opus", "pcm")

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_audio_format)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_audio_format_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = formatExpanded,
            onExpandedChange = { formatExpanded = !formatExpanded }
        ) {
            OutlinedTextField(
                value = setting.format,
                onValueChange = { onValueChange(setting.copy(format = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded)
                }
            )
            ExposedDropdownMenu(
                expanded = formatExpanded,
                onDismissRequest = { formatExpanded = false }
            ) {
                formats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = {
                            formatExpanded = false
                            onValueChange(setting.copy(format = format))
                        }
                    )
                }
            }
        }
    }

    // Sample Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_sample_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_sample_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { onValueChange(setting.copy(sampleRate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_doubao_sample_rate)
        )
    }

    // Bit Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_bit_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_bit_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.bitRate,
            onValueChange = { onValueChange(setting.copy(bitRate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_doubao_bit_rate)
        )
    }

    // Speech Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { onValueChange(setting.copy(speechRate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_doubao_speech_rate)
        )
    }

    // Loudness Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_loudness_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_loudness_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.loudnessRate,
            onValueChange = { onValueChange(setting.copy(loudnessRate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_doubao_loudness_rate)
        )
    }

    // Emotion
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_emotion)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_emotion_description)) }
    ) {
        OutlinedTextField(
            value = setting.emotion,
            onValueChange = { onValueChange(setting.copy(emotion = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_emotion_placeholder)) }
        )
    }

    // Emotion Scale
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_emotion_scale)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_emotion_scale_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.emotionScale,
            onValueChange = { onValueChange(setting.copy(emotionScale = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_doubao_emotion_scale)
        )
    }

    // Enable Timestamp
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_enable_timestamp)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_enable_timestamp_description)) }
    ) {
        Switch(
            checked = setting.enableTimestamp,
            onCheckedChange = { onValueChange(setting.copy(enableTimestamp = it)) }
        )
    }

    // Additions
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_doubao_additions)) },
        description = { Text(stringResource(R.string.setting_tts_page_doubao_additions_description)) }
    ) {
        OutlinedTextField(
            value = setting.additions,
            onValueChange = { onValueChange(setting.copy(additions = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_doubao_additions_placeholder)) },
            minLines = 2
        )
    }
}
