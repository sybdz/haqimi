package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.SillyTavernPreset
import me.rerere.rikkahub.data.model.SillyTavernPromptItem
import me.rerere.rikkahub.data.model.SillyTavernPromptOrderItem
import me.rerere.rikkahub.data.model.StPromptInjectionPosition
import me.rerere.rikkahub.data.model.resolvePromptOrder
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantImportKind
import me.rerere.rikkahub.ui.pages.assistant.detail.parseAssistantImportFromJson
import me.rerere.rikkahub.ui.pages.assistant.detail.toSillyTavernPreset
import me.rerere.rikkahub.utils.JsonInstantPretty

object SillyTavernPresetExportSerializer : ExportSerializer<SillyTavernPreset> {
    override val type: String = "st_preset"

    override fun export(data: SillyTavernPreset): ExportData {
        return ExportData(type = type, data = buildPresetJson(data))
    }

    override fun exportToJson(data: SillyTavernPreset, json: Json): String {
        return JsonInstantPretty.encodeToString(JsonObject.serializer(), buildPresetJson(data))
    }

    override fun getExportFileName(data: SillyTavernPreset): String {
        return "${sanitizeExportName(data.displayName, "st-preset")}.json"
    }

    override fun import(context: Context, uri: Uri): Result<SillyTavernPreset> {
        return runCatching {
            val payload = parseAssistantImportFromJson(
                jsonString = readUri(context, uri),
                sourceName = getUriFileName(context, uri)?.substringBeforeLast('.').orEmpty(),
            )
            require(payload.kind == AssistantImportKind.PRESET) { "Unsupported format" }
            payload.toSillyTavernPreset()
        }
    }
}

private fun buildPresetJson(data: SillyTavernPreset): JsonObject {
    val template = data.template
    val resolvedPromptOrder = template.resolvePromptOrder()
    val inlinePromptRegexesByIdentifier = data.regexes
        .filter { it.shouldExportAsInlinePrompt() }
        .groupBy { it.sourceRef }
    val scriptRegexes = data.regexes
        .filterNot { it.shouldExportAsInlinePrompt() }
    val root = linkedMapOf<String, JsonElement>()

    root["name"] = JsonPrimitive(template.sourceName.ifBlank { data.displayName })
    root["scenario_format"] = JsonPrimitive(template.scenarioFormat)
    root["personality_format"] = JsonPrimitive(template.personalityFormat)
    root["wi_format"] = JsonPrimitive(template.wiFormat)
    root["new_chat_prompt"] = JsonPrimitive(template.newChatPrompt)
    root["new_group_chat_prompt"] = JsonPrimitive(template.newGroupChatPrompt)
    root["new_example_chat_prompt"] = JsonPrimitive(template.newExampleChatPrompt)
    root["continue_nudge_prompt"] = JsonPrimitive(template.continueNudgePrompt)
    root["group_nudge_prompt"] = JsonPrimitive(template.groupNudgePrompt)
    root["impersonation_prompt"] = JsonPrimitive(template.impersonationPrompt)
    root["assistant_prefill"] = JsonPrimitive(template.assistantPrefill)
    root["assistant_impersonation"] = JsonPrimitive(template.assistantImpersonation)
    root["continue_prefill"] = JsonPrimitive(template.continuePrefill)
    root["continue_postfix"] = JsonPrimitive(template.continuePostfix)
    root["send_if_empty"] = JsonPrimitive(template.sendIfEmpty)
    root.putOrRemoveNumber("temperature", data.sampling.temperature)
    root.putOrRemoveNumber("top_p", data.sampling.topP)
    root.putOrRemoveNumber("openai_max_tokens", data.sampling.maxTokens)
    root.putOrRemoveNumber("frequency_penalty", data.sampling.frequencyPenalty)
    root.putOrRemoveNumber("presence_penalty", data.sampling.presencePenalty)
    root.putOrRemoveNumber("min_p", data.sampling.minP)
    root.putOrRemoveNumber("top_k", data.sampling.topK)
    root.putOrRemoveNumber("top_a", data.sampling.topA)
    root.putOrRemoveNumber("repetition_penalty", data.sampling.repetitionPenalty)
    root.putOrRemoveNumber("seed", data.sampling.seed)
    if (data.sampling.stopSequences.isNotEmpty()) {
        root["enable_stop_string"] = JsonPrimitive(true)
        root["stop_string"] = JsonPrimitive(data.sampling.stopSequences.first())
        root["stop_strings"] = buildJsonArray {
            data.sampling.stopSequences.forEach { add(JsonPrimitive(it)) }
        }
    }
    root.putOrRemoveString("reasoning_effort", data.sampling.openAIReasoningEffort)
    root.putOrRemoveString("verbosity", data.sampling.openAIVerbosity)
    template.namesBehavior?.let { root["names_behavior"] = JsonPrimitive(it) }
    root["use_sysprompt"] = JsonPrimitive(template.useSystemPrompt)
    root["squash_system_messages"] = JsonPrimitive(template.squashSystemMessages)
    root["prompts"] = buildPresetPrompts(
        preset = data,
        inlinePromptRegexesByIdentifier = inlinePromptRegexesByIdentifier,
    )
    root["prompt_order"] = buildPresetPromptOrder(orderItems = resolvedPromptOrder)

    val extensions = buildPresetExtensions(
        scriptRegexes = scriptRegexes,
    )
    if (extensions != null) {
        root["extensions"] = extensions
    } else {
        root.remove("extensions")
    }

    return JsonObject(root)
}

private fun buildPresetPrompts(
    preset: SillyTavernPreset,
    inlinePromptRegexesByIdentifier: Map<String, List<AssistantRegex>>,
): JsonArray {
    return buildJsonArray {
        preset.template.prompts.forEach { prompt ->
            add(
                buildPresetPrompt(
                    prompt = prompt,
                    inlineRegexes = inlinePromptRegexesByIdentifier[prompt.identifier].orEmpty(),
                )
            )
        }
    }
}

private fun buildPresetPrompt(
    prompt: SillyTavernPromptItem,
    inlineRegexes: List<AssistantRegex>,
): JsonObject {
    val updated = linkedMapOf<String, JsonElement>()
    updated["identifier"] = JsonPrimitive(prompt.identifier)
    updated["name"] = JsonPrimitive(prompt.name)
    updated["role"] = JsonPrimitive(prompt.role.name.lowercase())
    updated["content"] = JsonPrimitive(
        appendInlinePromptRegexes(
            content = prompt.content,
            regexes = inlineRegexes,
        )
    )
    updated["system_prompt"] = JsonPrimitive(prompt.systemPrompt)
    updated["marker"] = JsonPrimitive(prompt.marker)
    updated["enabled"] = JsonPrimitive(prompt.enabled)
    updated["injection_position"] = JsonPrimitive(
        if (prompt.injectionPosition == StPromptInjectionPosition.ABSOLUTE) 1 else 0
    )
    updated["injection_depth"] = JsonPrimitive(prompt.injectionDepth)
    updated["injection_order"] = JsonPrimitive(prompt.injectionOrder)
    updated["injection_trigger"] = buildJsonArray {
        prompt.injectionTriggers.forEach { add(JsonPrimitive(it)) }
    }
    updated["forbid_overrides"] = JsonPrimitive(prompt.forbidOverrides)
    return JsonObject(updated)
}

private fun buildPresetPromptOrder(
    orderItems: List<SillyTavernPromptOrderItem>,
): JsonArray {
    return buildCanonicalPromptOrder(orderItems)
}

private fun buildCanonicalPromptOrder(
    orderItems: List<SillyTavernPromptOrderItem>,
): JsonArray {
    return buildJsonArray {
        add(buildJsonObject {
            put("character_id", 100001)
            put("order", buildPromptOrderEntries(orderItems = orderItems))
        })
    }
}

private fun buildPromptOrderEntries(
    orderItems: List<SillyTavernPromptOrderItem>,
): JsonArray {
    return buildJsonArray {
        orderItems.forEach { item ->
            add(buildJsonObject {
                put("identifier", item.identifier)
                put("enabled", item.enabled)
            })
        }
    }
}

private fun buildPresetExtensions(scriptRegexes: List<AssistantRegex>): JsonObject? {
    val updated = linkedMapOf<String, JsonElement>()

    if (scriptRegexes.isNotEmpty()) {
        updated["regex_scripts"] = buildJsonArray {
            scriptRegexes.forEach { regex ->
                add(buildRegexScript(regex))
            }
        }
    }

    return updated.takeIf { it.isNotEmpty() }?.let(::JsonObject)
}

private fun MutableMap<String, JsonElement>.putOrRemoveNumber(key: String, value: Number?) {
    if (value != null) {
        this[key] = JsonPrimitive(value)
    } else {
        remove(key)
    }
}

private fun MutableMap<String, JsonElement>.putOrRemoveString(key: String, value: String) {
    if (value.isNotBlank()) {
        this[key] = JsonPrimitive(value)
    } else {
        remove(key)
    }
}
