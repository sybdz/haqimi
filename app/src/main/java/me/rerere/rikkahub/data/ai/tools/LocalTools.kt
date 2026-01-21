package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import java.io.File
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

@Serializable
data class LocalToolPrompt(
    val option: LocalToolOption,
    val description: String = ""
)

class LocalTools(private val context: Context) {
    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private val javascriptDefaultDescription = """
        Execute JavaScript code using QuickJS engine (ES2020).
        The result is the value of the last expression in the code.
        For calculations with decimals, use toFixed() to control precision.
        Console output (log/info/warn/error) is captured and returned in 'logs' field.
        No DOM or Node.js APIs available.
        Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
    """.trimIndent().replace("\n", " ")

    private val pythonDefaultDescription =
        "Execute Python code locally (Chaquopy). Common imports: np, pd, plt, sns, Image. Set `result` for output; set `image`/`images` or save images to `OUTPUT_DIR` (auto-captured). Non-image files in `OUTPUT_DIR` are copied to `output_path` when provided."

    private val javascriptToolBase by lazy {
        buildJavascriptTool(javascriptDefaultDescription)
    }

    private val pythonToolBase by lazy {
        buildPythonTool(pythonDefaultDescription)
    }

    private fun buildJavascriptTool(description: String) = Tool(
        name = "eval_javascript",
        description = description,
        needsApproval = true,
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

    private fun buildPythonTool(description: String) = Tool(
        name = "eval_python",
        description = description,
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("code", buildJsonObject {
                        put("type", "string")
                        put("description", "Python code to execute (np/pd/plt/sns/Image preloaded). Set `result` for output; set `image`/`images` or save images to `OUTPUT_DIR`.")
                    })
                    put("output_path", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "Optional local path for output files. If set, non-image files saved in OUTPUT_DIR will be copied here. If it's a directory, files keep relative paths; if it's a file path and only one file is produced, it will be written there."
                        )
                    })
                }
            )
        },
        execute = { args ->
            val code = args.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                ?: error("Python code is required")
            val outputPath = args.jsonObject["output_path"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
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
                val outputDir = File("/storage/emulated/0/rikkahub_file")
                if (!outputDir.exists() && !outputDir.mkdirs()) {
                    error("Failed to create output directory: ${outputDir.absolutePath}")
                }
                val result = if (outputPath != null) {
                    module.callAttr("run_python_tool", code, outputDir.absolutePath, outputPath)
                        .toString()
                } else {
                    module.callAttr("run_python_tool", code, outputDir.absolutePath).toString()
                }
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

    private fun Tool.withDescription(overrideDescription: String?): Tool {
        val trimmed = overrideDescription?.trim()
        val resolved = trimmed?.takeIf { it.isNotEmpty() } ?: description
        return if (resolved == description) this else copy(description = resolved)
    }

    private fun resolvePrompt(option: LocalToolOption, prompts: List<LocalToolPrompt>): String? {
        return prompts.firstOrNull { it.option == option }?.description
    }

    fun getTools(options: List<LocalToolOption>, promptOverrides: List<LocalToolPrompt> = emptyList()): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(
                javascriptToolBase.withDescription(
                    resolvePrompt(LocalToolOption.JavascriptEngine, promptOverrides)
                )
            )
        }
        if (options.contains(LocalToolOption.PythonEngine)) {
            tools.add(
                pythonToolBase.withDescription(
                    resolvePrompt(LocalToolOption.PythonEngine, promptOverrides)
                )
            )
        }
        return tools
    }
}
