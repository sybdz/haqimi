package me.rerere.ai.tools

import kotlinx.serialization.json.JsonElement
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Local implementation of MCP server: modelcontextprotocol/servers/src/sequentialthinking
 * Tool name and I/O fields are kept identical for compatibility.
 */
object SequentialThinkingTool {
    private data class ThoughtData(
        val thought: String,
        val nextThoughtNeeded: Boolean,
        val thoughtNumber: Int,
        val totalThoughts: Int,
        val isRevision: Boolean?,
        val revisesThought: Int?,
        val branchFromThought: Int?,
        val branchId: String?,
        val needsMoreThoughts: Boolean?,
    )

    private class State {
        private val lock = ReentrantLock()
        private val thoughtHistory = mutableListOf<ThoughtData>()
        private val branches = linkedMapOf<String, MutableList<ThoughtData>>()

        fun process(input: ThoughtData): JsonElement = lock.withLock {
            val adjustedTotalThoughts = if (input.thoughtNumber > input.totalThoughts) {
                input.thoughtNumber
            } else {
                input.totalThoughts
            }

            val stored = input.copy(totalThoughts = adjustedTotalThoughts)
            thoughtHistory.add(stored)

            if (stored.branchFromThought != null && stored.branchId != null) {
                branches.getOrPut(stored.branchId) { mutableListOf() }.add(stored)
            }

            buildJsonObject {
                put("thoughtNumber", stored.thoughtNumber)
                put("totalThoughts", stored.totalThoughts)
                put("nextThoughtNeeded", stored.nextThoughtNeeded)
                put("branches", buildJsonArray {
                    branches.keys.sorted().forEach { add(it) }
                })
                put("thoughtHistoryLength", thoughtHistory.size)
            }
        }
    }

    private val state = State()

    fun create(): Tool = Tool(
        name = "sequentialthinking",
        description = (
            "A step-by-step thinking helper (sequential thinking). " +
                "Call this tool to structure reasoning into numbered thoughts with optional branching and revisions."
            ),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("thought", buildJsonObject {
                        put("type", "string")
                        put("description", "Current thinking step content")
                    })
                    put("nextThoughtNeeded", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether another thought step is needed")
                    })
                    put("thoughtNumber", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Current thought number (1-indexed)")
                    })
                    put("totalThoughts", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Estimated total thoughts needed")
                    })
                    put("isRevision", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether this revises previous thinking")
                    })
                    put("revisesThought", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Which thought number is being reconsidered")
                    })
                    put("branchFromThought", buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("description", "Branching point thought number")
                    })
                    put("branchId", buildJsonObject {
                        put("type", "string")
                        put("description", "Branch identifier")
                    })
                    put("needsMoreThoughts", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether more thoughts are needed beyond current estimate")
                    })
                },
                required = listOf("thought", "nextThoughtNeeded", "thoughtNumber", "totalThoughts"),
            )
        },
        execute = { args ->
            val obj = args.jsonObject

            val thought = obj["thought"]?.jsonPrimitive?.contentOrNull ?: error("thought is required")
            val nextThoughtNeeded = obj["nextThoughtNeeded"]?.jsonPrimitive?.booleanOrNull
                ?: error("nextThoughtNeeded is required")
            val thoughtNumber = obj["thoughtNumber"]?.jsonPrimitive?.intOrNull ?: error("thoughtNumber is required")
            val totalThoughts = obj["totalThoughts"]?.jsonPrimitive?.intOrNull ?: error("totalThoughts is required")

            if (thoughtNumber < 1) error("thoughtNumber must be >= 1")
            if (totalThoughts < 1) error("totalThoughts must be >= 1")

            val isRevision = obj["isRevision"]?.jsonPrimitive?.booleanOrNull
            val revisesThought = obj["revisesThought"]?.jsonPrimitive?.intOrNull
            val branchFromThought = obj["branchFromThought"]?.jsonPrimitive?.intOrNull
            val branchId = obj["branchId"]?.jsonPrimitive?.contentOrNull
            val needsMoreThoughts = obj["needsMoreThoughts"]?.jsonPrimitive?.booleanOrNull

            if (revisesThought != null && revisesThought < 1) error("revisesThought must be >= 1")
            if (branchFromThought != null && branchFromThought < 1) error("branchFromThought must be >= 1")

            val output = state.process(
                ThoughtData(
                    thought = thought,
                    nextThoughtNeeded = nextThoughtNeeded,
                    thoughtNumber = thoughtNumber,
                    totalThoughts = totalThoughts,
                    isRevision = isRevision,
                    revisesThought = revisesThought,
                    branchFromThought = branchFromThought,
                    branchId = branchId,
                    needsMoreThoughts = needsMoreThoughts,
                )
            )

            listOf(UIMessagePart.Text(output.toString()))
        }
    )
}
