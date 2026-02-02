package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("sequential_thinking")
    data object SequentialThinking : LocalToolOption()
}

class LocalTools(private val context: Context) {
    private data class SequentialThoughtData(
        val thought: String,
        val thoughtNumber: Int,
        val totalThoughts: Int,
        val nextThoughtNeeded: Boolean,
        val isRevision: Boolean? = null,
        val revisesThought: Int? = null,
        val branchFromThought: Int? = null,
        val branchId: String? = null,
        val needsMoreThoughts: Boolean? = null,
    )

    private class SequentialThinkingState {
        val thoughtHistory = mutableListOf<SequentialThoughtData>()
        val branches = linkedMapOf<String, MutableList<SequentialThoughtData>>()
    }

    // Keep the same semantics as MCP server: state persists across invocations.
    private val sequentialThinkingState = SequentialThinkingState()

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = """
                Execute JavaScript code using QuickJS engine (ES2020).
                The result is the value of the last expression in the code.
                For calculations with decimals, use toFixed() to control precision.
                Console output (log/info/warn/error) is captured and returned in 'logs' field.
                No DOM or Node.js APIs available.
                Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val context = QuickJSContext.create()
                context.setConsole(object : QuickJSContext.Console {
                    override fun log(info: String?) {
                        logs.add("[LOG] $info")
                    }

                    override fun info(info: String?) {
                        logs.add("[INFO] $info")
                    }

                    override fun warn(info: String?) {
                        logs.add("[WARN] $info")
                    }

                    override fun error(info: String?) {
                        logs.add("[ERROR] $info")
                    }
                })
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                val payload = buildJsonObject {
                    if (logs.isNotEmpty()) {
                        put("logs", JsonPrimitive(logs.joinToString("\n")))
                    }
                    put(
                        "result", when (result) {
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = """
                Get the current local date and time info from the device.
                Returns year/month/day, weekday, ISO date/time strings, timezone, and timestamp.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val time = now.toLocalTime().withNano(0)
                val weekday = now.dayOfWeek
                val payload = buildJsonObject {
                    put("year", date.year)
                    put("month", date.monthValue)
                    put("day", date.dayOfMonth)
                    put("weekday", weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("weekday_index", weekday.value)
                    put("date", date.toString())
                    put("time", time.toString())
                    put("datetime", now.withNano(0).toString())
                    put("timezone", now.zone.id)
                    put("utc_offset", now.offset.id)
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val clipboardTool by lazy {
        Tool(
            name = "clipboard_tool",
            description = """
                Read or write plain text from the device clipboard.
                Use action: read or write. For write, provide text.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add("read")
                                    add("write")
                                }
                            )
                            put("description", "Operation to perform: read or write")
                        })
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Text to write to the clipboard (required for write)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
                    "read" -> {
                        val payload = buildJsonObject {
                            put("text", context.readClipboardText())
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    "write" -> {
                        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                        context.writeClipboardText(text)
                        val payload = buildJsonObject {
                            put("success", true)
                            put("text", text)
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    else -> error("unknown action: $action, must be one of [read, write]")
                }
            }
        )
    }

    val sequentialThinkingTool by lazy {
        Tool(
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
                            put("description", "Identifier for the current branch")
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
                val obj = it.jsonObject
                val thought = obj["thought"]?.jsonPrimitive?.contentOrNull ?: error("thought is required")
                val nextThoughtNeeded = obj["nextThoughtNeeded"]?.jsonPrimitive?.booleanOrNull
                    ?: error("nextThoughtNeeded is required")
                val thoughtNumber = obj["thoughtNumber"]?.jsonPrimitive?.intOrNull ?: error("thoughtNumber is required")
                val totalThoughtsRaw = obj["totalThoughts"]?.jsonPrimitive?.intOrNull ?: error("totalThoughts is required")

                if (thoughtNumber < 1) error("thoughtNumber must be >= 1")
                if (totalThoughtsRaw < 1) error("totalThoughts must be >= 1")

                val isRevision = obj["isRevision"]?.jsonPrimitive?.booleanOrNull
                val revisesThought = obj["revisesThought"]?.jsonPrimitive?.intOrNull
                val branchFromThought = obj["branchFromThought"]?.jsonPrimitive?.intOrNull
                val branchId = obj["branchId"]?.jsonPrimitive?.contentOrNull
                val needsMoreThoughts = obj["needsMoreThoughts"]?.jsonPrimitive?.booleanOrNull

                if (revisesThought != null && revisesThought < 1) error("revisesThought must be >= 1")
                if (branchFromThought != null && branchFromThought < 1) error("branchFromThought must be >= 1")

                val totalThoughts = maxOf(totalThoughtsRaw, thoughtNumber)

                val data = SequentialThoughtData(
                    thought = thought,
                    thoughtNumber = thoughtNumber,
                    totalThoughts = totalThoughts,
                    nextThoughtNeeded = nextThoughtNeeded,
                    isRevision = isRevision,
                    revisesThought = revisesThought,
                    branchFromThought = branchFromThought,
                    branchId = branchId,
                    needsMoreThoughts = needsMoreThoughts,
                )

                sequentialThinkingState.thoughtHistory += data
                if (branchFromThought != null && !branchId.isNullOrBlank()) {
                    sequentialThinkingState.branches.getOrPut(branchId) { mutableListOf() } += data
                }

                val payload = buildJsonObject {
                    put("thoughtNumber", thoughtNumber)
                    put("totalThoughts", totalThoughts)
                    put("nextThoughtNeeded", nextThoughtNeeded)
                    put("branches", buildJsonArray {
                        sequentialThinkingState.branches.keys.forEach { add(it) }
                    })
                    put("thoughtHistoryLength", sequentialThinkingState.thoughtHistory.size)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.SequentialThinking)) {
            tools.add(sequentialThinkingTool)
        }
        return tools
    }
}
