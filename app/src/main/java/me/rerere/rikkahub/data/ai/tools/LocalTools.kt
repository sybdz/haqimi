package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
                buildJsonObject {
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
            }
        )
    }

    val pythonTool by lazy {
        Tool(
            name = "eval_python",
            description = "Execute Python code locally (Chaquopy). Common imports: np, pd, plt, sns, Image. Set `result` for output; set `image`/`images` or save images to `OUTPUT_DIR` (auto-captures up to 4 images / 20MB).",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "Python code to execute (np/pd/plt/sns/Image preloaded). Set `result` for output; set `image`/`images` or save images to `OUTPUT_DIR`.")
                        })
                    }
                )
            },
            execute = { args ->
                val code = args.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                    ?: error("Python code is required")
                ensurePython()
                fun buildErrorPayload(message: String, rawResult: String? = null) = buildJsonObject {
                    put("error_message", JsonPrimitive(message))
                    put(
                        "output",
                        buildJsonObject {
                            put("stdout", JsonPrimitive(""))
                            put("stderr", JsonPrimitive(""))
                            if (rawResult != null) {
                                put("result", JsonPrimitive(rawResult))
                            } else {
                                put("result", JsonNull)
                            }
                            put("images", buildJsonArray { })
                            put("files", buildJsonArray { })
                        }
                    )
                }
                runCatching {
                    val py = Python.getInstance()
                    val module = py.getModule("local_tools")
                    val outputDir = context.getExternalFilesDir("python_outputs")
                        ?: context.filesDir.resolve("python_outputs")
                    if (!outputDir.exists()) {
                        outputDir.mkdirs()
                    }
                    val result = module.callAttr("run_python_tool", code, outputDir.absolutePath).toString()
                    runCatching {
                        JsonInstant.parseToJsonElement(result)
                    }.getOrElse { parseError ->
                        buildErrorPayload(
                            "Parse python result failed: ${parseError.message ?: "unknown"}",
                            result
                        )
                    }
                }.getOrElse { throwable ->
                    buildErrorPayload("[${throwable::class.simpleName}] ${throwable.message}")
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
