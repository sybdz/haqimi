package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.utils.JsonInstant

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("python_engine")
    data object PythonEngine : LocalToolOption()
}

class LocalTools(private val context: Context) {
    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = "Execute JavaScript code with QuickJS. If use this tool to calculate math, better to add `toFixed` to the code.",
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
                val context = QuickJSContext.create()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                buildJsonObject {
                    put(
                        "result", when (result) {
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
            }
        )
    }

    val pythonTool by lazy {
        Tool(
            name = "eval_python",
            description = "Execute Python code locally via Chaquopy. Use `result` for output and `image`/`images` for base64 images.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The Python code to execute. Set `result`, and optionally set `image` or `images` for visual output.")
                        })
                    }
                )
            },
            execute = { args ->
                val code = args.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                    ?: error("Python code is required")
                ensurePython()
                runCatching {
                    val py = Python.getInstance()
                    val module = py.getModule("local_tools")
                    val result = module.callAttr("run_python_tool", code).toString()
                    runCatching {
                        JsonInstant.parseToJsonElement(result)
                    }.getOrElse { parseError ->
                        buildJsonObject {
                            put("ok", JsonPrimitive(false))
                            put("error", JsonPrimitive("Parse python result failed: ${parseError.message ?: "unknown"}"))
                            put("raw", JsonPrimitive(result))
                        }
                    }
                }.getOrElse { throwable ->
                    buildJsonObject {
                        put("ok", JsonPrimitive(false))
                        put(
                            "error",
                            JsonPrimitive("[${throwable::class.simpleName}] ${throwable.message}")
                        )
                    }
                }
            }
        )
    }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.PythonEngine)) {
            tools.add(pythonTool)
        }
        return tools
    }
}
