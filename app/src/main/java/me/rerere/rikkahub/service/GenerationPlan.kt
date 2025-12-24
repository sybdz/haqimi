package me.rerere.rikkahub.service

import me.rerere.ai.provider.Model
import me.rerere.ai.ui.MessageGroupType
import kotlin.uuid.Uuid

data class GenerationPlan(
    val models: List<Model>,
    val groupId: Uuid? = null,
    val groupType: MessageGroupType? = null,
    val anonymous: Boolean = false,
)
