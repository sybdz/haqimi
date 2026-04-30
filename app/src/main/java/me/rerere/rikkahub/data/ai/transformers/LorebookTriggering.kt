package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantRegexApplyPhase
import me.rerere.rikkahub.data.model.AssistantRegexPlacement
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookGlobalSettings
import me.rerere.rikkahub.data.model.LorebookTriggerContext
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.WorldInfoCharacterStrategy
import me.rerere.rikkahub.data.model.effectiveUserName
import me.rerere.rikkahub.data.model.extractContextForMatching
import me.rerere.rikkahub.data.model.matchesTriggerKeywords
import me.rerere.rikkahub.data.model.replaceRegexes

internal fun collectTriggeredLorebookEntries(
    historyMessages: List<UIMessage>,
    assistant: Assistant,
    lorebooks: List<Lorebook>,
    triggerContext: LorebookTriggerContext,
    settings: Settings? = null,
    runtimeState: LorebookRuntimeState? = null,
): List<PromptInjection.RegexInjection> {
    val globalSettings = settings?.lorebookGlobalSettings ?: LorebookGlobalSettings()
    val applicableLorebooks = resolveApplicableLorebooks(
        assistant = assistant,
        lorebooks = lorebooks,
        settings = settings,
    )
    if (applicableLorebooks.isEmpty()) return emptyList()

    val nonSystemMessages = historyMessages.filter { it.role != MessageRole.SYSTEM }
    val userName = settings?.effectiveUserName().orEmpty().ifBlank { "User" }
    val assistantName = assistant.stCharacterData?.name?.ifBlank { assistant.name } ?: assistant.name.ifBlank { "Assistant" }
    val sharedBudget = resolveSharedLorebookBudget(assistant, globalSettings)
    return selectTriggeredLorebookEntries(
        entries = applicableLorebooks.flatMap { scopedLorebook ->
            scopedLorebook.lorebook.collectTriggeredEntries(
                historyMessages = nonSystemMessages,
                triggerContext = triggerContext,
                assistant = assistant,
                settings = settings,
                runtimeState = runtimeState,
                globalSettings = globalSettings,
                userName = userName,
                assistantName = assistantName,
                budget = scopedLorebook.lorebook.explicitBudgetOrNull(),
            ).map { entry ->
                ActivatedLorebookEntry(
                    entry = entry,
                    scope = scopedLorebook.scope,
                )
            }
        },
        budget = sharedBudget,
        strategy = globalSettings.characterStrategy,
    )
}

private fun Lorebook.collectTriggeredEntries(
    historyMessages: List<UIMessage>,
    triggerContext: LorebookTriggerContext,
    assistant: Assistant,
    settings: Settings?,
    runtimeState: LorebookRuntimeState?,
    globalSettings: LorebookGlobalSettings,
    userName: String,
    assistantName: String,
    budget: Int?,
): List<PromptInjection.RegexInjection> {
    if (entries.isEmpty()) return emptyList()

    val sortedEntries = entries
        .asSequence()
        .filterNot { it.position == InjectionPosition.OUTLET }
        .sortedByDescending { it.priority }
        .toList()
    val historyMessageCount = historyMessages.size
    if (sortedEntries.isEmpty() || historyMessageCount == 0) return emptyList()

    val candidates = sortedEntries.mapNotNull { entry ->
        val recentMessagesText = extractContextForMatching(
            messages = historyMessages,
            scanDepth = entry.effectiveScanDepth(globalSettings, depthSkew = 0),
            includeNames = globalSettings.includeNames,
            userName = userName,
            assistantName = assistantName,
        )
        val currentTriggerContext = triggerContext.copy(recentMessagesText = recentMessagesText)
        val triggered = entry.matchesTriggerKeywords(
            context = recentMessagesText,
            triggerContext = currentTriggerContext,
            globalSettings = globalSettings,
        )
        if (!triggered) return@mapNotNull null

        LorebookCandidate(
            entry = entry,
            isSticky = false,
            score = 0,
        )
    }
    val activatedEntries = selectBudgetedCandidates(
        candidates = candidates,
        budget = budget,
        activatedEntries = emptyList(),
    )

    return activatedEntries.map { entry ->
        entry.copy(
            content = entry.content.replaceRegexes(
                assistant = assistant,
                settings = settings,
                scope = entry.toAffectScope(),
                phase = AssistantRegexApplyPhase.PROMPT_ONLY,
                messageDepthFromEnd = entry.injectDepth.takeIf { entry.position == InjectionPosition.AT_DEPTH },
                placement = AssistantRegexPlacement.WORLD_INFO,
            )
        )
    }
}
