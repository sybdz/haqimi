package me.rerere.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningLevel(
    val budgetTokens: Int,
    val effort: String
) {
    @SerialName("off")
    OFF(0, "none"),
    @SerialName("auto")
    AUTO(-1, "auto"),
    @SerialName("low")
    LOW(1_000, "low"),
    @SerialName("medium")
    MEDIUM(2_000, "medium"),
    @SerialName("high")
    HIGH(8_000, "high"),
    @SerialName("xhigh")
    XHIGH(16_000, "xhigh");

    val isEnabled: Boolean
        get() = this != OFF

    companion object {
        fun fromBudgetTokens(budgetTokens: Int?): ReasoningLevel {
            return entries.minByOrNull { kotlin.math.abs(it.budgetTokens - (budgetTokens ?: AUTO.budgetTokens)) } ?: AUTO
        }
    }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

fun resolveOpenAIChatCompletionsReasoningEffort(
    thinkingBudget: Int?,
    overrideEffort: String,
): String? {
    overrideEffort.trimToNull()?.let { return it }
    val level = ReasoningLevel.fromBudgetTokens(thinkingBudget)
    if (level == ReasoningLevel.AUTO) return null
    return if (level == ReasoningLevel.OFF) "low" else level.effort
}

fun resolveOpenAIResponsesReasoningEffort(
    thinkingBudget: Int?,
    overrideEffort: String,
): String? {
    overrideEffort.trimToNull()?.let { return it }
    val level = ReasoningLevel.fromBudgetTokens(thinkingBudget ?: 0)
    return if (level == ReasoningLevel.AUTO) null else level.effort
}
