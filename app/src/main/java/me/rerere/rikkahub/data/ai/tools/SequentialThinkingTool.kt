package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

internal data class SequentialThinkingProcessResult(
    val payload: JsonObject,
    val sequenceEnded: Boolean,
)

/**
 * Per-conversation/session state for the MCP-compatible sequentialthinking tool.
 *
 * Design goals (per PR review):
 * - No global shared state across conversations
 * - Concurrency safe
 * - Bounded memory usage (do not retain full thought content)
 */
internal class SequentialThinkingSessionState(
    private val maxBranchIds: Int = 128,
) {
    private val mutex = Mutex()

    private var thoughtHistoryLength: Int = 0
    private var maxThoughtNumberSeen: Int = 0
    private val branchIds: LinkedHashSet<String> = LinkedHashSet()

    suspend fun process(args: JsonObject): SequentialThinkingProcessResult = mutex.withLock {
        val thought = args["thought"]?.jsonPrimitive?.contentOrNull ?: error("thought is required")
        val nextThoughtNeeded = args["nextThoughtNeeded"]?.jsonPrimitive?.booleanOrNull
            ?: error("nextThoughtNeeded is required")
        val thoughtNumber = args["thoughtNumber"]?.jsonPrimitive?.intOrNull ?: error("thoughtNumber is required")
        val totalThoughtsRaw = args["totalThoughts"]?.jsonPrimitive?.intOrNull ?: error("totalThoughts is required")

        if (thoughtNumber < 1) error("thoughtNumber must be >= 1")
        if (totalThoughtsRaw < 1) error("totalThoughts must be >= 1")

        val isRevision = args["isRevision"]?.jsonPrimitive?.booleanOrNull
        val revisesThought = args["revisesThought"]?.jsonPrimitive?.intOrNull
        val branchFromThought = args["branchFromThought"]?.jsonPrimitive?.intOrNull
        val branchId = args["branchId"]?.jsonPrimitive?.contentOrNull
        val needsMoreThoughts = args["needsMoreThoughts"]?.jsonPrimitive?.booleanOrNull

        if (thought.isBlank()) error("thought must not be blank")
        if (revisesThought != null && revisesThought < 1) error("revisesThought must be >= 1")
        if (branchFromThought != null && branchFromThought < 1) error("branchFromThought must be >= 1")

        if (isRevision == true && revisesThought == null) {
            error("revisesThought is required when isRevision=true")
        }
        if (branchFromThought != null && branchId.isNullOrBlank()) {
            error("branchId is required when branchFromThought is set")
        }

        val totalThoughts = maxOf(totalThoughtsRaw, thoughtNumber)

        // Extra validation (not present in MCP reference server): avoid inconsistent references.
        // We validate against the max thought number observed so far.
        if (revisesThought != null) {
            if (revisesThought >= thoughtNumber) error("revisesThought must be < thoughtNumber")
            if (revisesThought > maxThoughtNumberSeen) error("revisesThought references an unknown thought")
        }
        if (branchFromThought != null) {
            if (branchFromThought >= thoughtNumber) error("branchFromThought must be < thoughtNumber")
            if (branchFromThought > maxThoughtNumberSeen) error("branchFromThought references an unknown thought")
        }

        // Record metadata only (do not store the thought content).
        thoughtHistoryLength += 1
        if (thoughtNumber > maxThoughtNumberSeen) maxThoughtNumberSeen = thoughtNumber

        if (branchFromThought != null && !branchId.isNullOrBlank()) {
            // Keep insertion order; cap size.
            if (!branchIds.contains(branchId)) {
                branchIds.add(branchId)
                while (branchIds.size > maxBranchIds) {
                    val it = branchIds.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    } else {
                        break
                    }
                }
            }
        }

        val payload = buildJsonObject {
            put("thoughtNumber", thoughtNumber)
            put("totalThoughts", totalThoughts)
            put("nextThoughtNeeded", nextThoughtNeeded)
            put("branches", buildJsonArray {
                branchIds.forEach { add(it) }
            })
            put("thoughtHistoryLength", thoughtHistoryLength)
        }

        val sequenceEnded = !nextThoughtNeeded && (needsMoreThoughts != true)
        if (sequenceEnded) {
            resetLocked()
        }

        SequentialThinkingProcessResult(payload = payload, sequenceEnded = sequenceEnded)
    }

    private fun resetLocked() {
        thoughtHistoryLength = 0
        maxThoughtNumberSeen = 0
        branchIds.clear()
    }
}

fun createSequentialThinkingTool(
    getState: () -> SequentialThinkingSessionState,
    onSequenceEnded: () -> Unit,
): Tool {
    return Tool(
        name = "sequentialthinking",
        description = "Record a step-by-step thought sequence and return metadata (MCP-compatible sequential-thinking behavior).",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("thought", buildJsonObject {
                        put("type", "string")
                        put("description", "The current thinking step content")
                    })
                    put("nextThoughtNeeded", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether another thought step is needed")
                    })
                    put("thoughtNumber", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Current thought number in sequence (>= 1)")
                    })
                    put("totalThoughts", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Estimated total thoughts needed (>= 1)")
                    })
                    put("isRevision", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether this thought revises previous thinking")
                    })
                    put("revisesThought", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Which thought number is being reconsidered (>= 1)")
                    })
                    put("branchFromThought", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Branching point thought number for alternative reasoning paths (>= 1)")
                    })
                    put("branchId", buildJsonObject {
                        put("type", "string")
                        put("description", "Identifier for the current branch (required when branchFromThought is set)")
                    })
                    put("needsMoreThoughts", buildJsonObject {
                        put("type", "boolean")
                        put("description", "If reaching end but realizing more thoughts are needed")
                    })
                },
                required = listOf("thought", "nextThoughtNeeded", "thoughtNumber", "totalThoughts")
            )
        },
        execute = {
            val result = getState().process(it.jsonObject)
            if (result.sequenceEnded) {
                onSequenceEnded()
            }
            listOf(UIMessagePart.Text(result.payload.toString()))
        }
    )
}
