package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

private const val TERMINAL_MAX_VISIBLE_LINES = 8
private const val TERMINAL_COMMAND_PREVIEW_LIMIT = 72

internal object TermuxToolUiNames {
    const val EXEC = "termux_exec"
    const val PYTHON = "termux_python"
    const val WRITE_STDIN = "write_stdin"
    const val LIST_PTY_SESSIONS = "list_pty_sessions"
    const val CLOSE_PTY_SESSION = "close_pty_session"
}

internal data class TermuxOutputPreview(
    val title: String,
    val output: String,
    val commandPreview: String? = null,
    val exitCode: Int? = null,
    val running: Boolean = false,
    val sessionId: String? = null,
    val isError: Boolean = false,
)

internal fun buildCompactTermuxCommandPreview(
    toolName: String,
    arguments: JsonElement,
): String? {
    val argumentObject = arguments.jsonObjectOrNull ?: return null
    val rawSnippet = when (toolName) {
        TermuxToolUiNames.EXEC -> argumentObject["command"]?.jsonPrimitiveOrNull?.contentOrNull
        TermuxToolUiNames.PYTHON -> argumentObject["code"]?.jsonPrimitiveOrNull?.contentOrNull
        TermuxToolUiNames.WRITE_STDIN -> {
            argumentObject["chars"]?.jsonPrimitiveOrNull?.contentOrNull?.ifBlank { "Poll for more output" }
        }

        else -> null
    } ?: return null

    val lines = rawSnippet
        .trim()
        .lineSequence()
        .toList()

    if (lines.isEmpty()) return null

    val firstLine = lines.first().trim()
    val suffix = if (lines.size > 1) " +${lines.size - 1} lines" else ""
    val compact = (if (firstLine.isBlank()) "${lines.size} line" + if (lines.size == 1) "" else "s" else firstLine + suffix)
        .trim()
    return if (compact.length > TERMINAL_COMMAND_PREVIEW_LIMIT) {
        compact.take(TERMINAL_COMMAND_PREVIEW_LIMIT - 1) + "..."
    } else {
        compact
    }
}

internal fun buildTermuxToolOutputPreview(
    toolName: String,
    arguments: JsonElement,
    output: List<UIMessagePart>,
): TermuxOutputPreview? {
    if (
        toolName != TermuxToolUiNames.EXEC &&
        toolName != TermuxToolUiNames.PYTHON &&
        toolName != TermuxToolUiNames.WRITE_STDIN
    ) {
        return null
    }

    val rawPayload = output.filterIsInstance<UIMessagePart.Text>()
        .joinToString(separator = "\n") { it.text }
        .takeIf { it.isNotBlank() }
        ?: return null

    val jsonObject = runCatching {
        JsonInstant.parseToJsonElement(rawPayload).jsonObject
    }.getOrNull() ?: return null

    val normalizedOutput = jsonObject["output"]?.jsonPrimitiveOrNull?.contentOrNull
        ?.trimEnd('\n', '\r')
        .orEmpty()
    val error = jsonObject["error"]?.jsonPrimitiveOrNull?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val running = jsonObject["running"]?.jsonPrimitiveOrNull?.booleanOrNull == true
    val exitCode = jsonObject["exit_code"]?.jsonPrimitiveOrNull?.intOrNull
    val sessionId = jsonObject["session_id"]?.jsonPrimitiveOrNull?.contentOrNull
        ?.takeIf { it.isNotBlank() }

    val renderedOutput = when {
        normalizedOutput.isNotBlank() && !error.isNullOrBlank() && !normalizedOutput.contains(error) -> {
            normalizedOutput + "\n" + error
        }

        normalizedOutput.isNotBlank() -> normalizedOutput
        !error.isNullOrBlank() -> error
        running -> "Waiting for output..."
        exitCode == 0 -> "Command finished with no output."
        sessionId != null -> "Interactive session is ready."
        else -> return null
    }

    return TermuxOutputPreview(
        title = when (toolName) {
            TermuxToolUiNames.PYTHON -> "Python Output"
            TermuxToolUiNames.WRITE_STDIN -> "PTY Output"
            else -> "Command Output"
        },
        output = renderedOutput,
        commandPreview = buildCompactTermuxCommandPreview(toolName, arguments),
        exitCode = exitCode,
        running = running,
        sessionId = sessionId,
        isError = !error.isNullOrBlank() || (exitCode != null && exitCode != 0),
    )
}

internal fun UIMessagePart.Tool.termuxOutputPreview(): TermuxOutputPreview? {
    return buildTermuxToolOutputPreview(
        toolName = toolName,
        arguments = inputAsJson(),
        output = output,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalOutputCard(
    title: String,
    output: String,
    modifier: Modifier = Modifier,
    commandPreview: String? = null,
    exitCode: Int? = null,
    running: Boolean = false,
    sessionId: String? = null,
    isError: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = when {
        isError -> colorScheme.error
        running -> colorScheme.tertiary
        else -> colorScheme.secondary
    }
    val bodyTextStyle = TextStyle(
        fontFamily = JetbrainsMono,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val bodyLineCount = remember(output) {
        output.lineSequence().count().coerceAtLeast(1)
    }
    var laidOutLineCount by remember(output) { mutableIntStateOf(bodyLineCount) }
    val effectiveLineCount = maxOf(bodyLineCount, laidOutLineCount)
    val visibleLineCount = effectiveLineCount.coerceAtMost(TERMINAL_MAX_VISIBLE_LINES)
    val bodyMaxHeight = with(LocalDensity.current) {
        bodyTextStyle.lineHeight.toDp() * visibleLineCount + 18.dp
    }
    val needsScroll = effectiveLineCount > TERMINAL_MAX_VISIBLE_LINES
    val bodyScrollState = rememberScrollState()
    val terminalBodyColor = colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.96f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.85f))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (commandPreview != null) {
                    TerminalMetaChip(
                        text = commandPreview,
                        accentColor = accentColor,
                        monospace = true,
                    )
                }
                if (running) {
                    TerminalMetaChip(
                        text = "running",
                        accentColor = accentColor,
                    )
                }
                exitCode?.let {
                    TerminalMetaChip(
                        text = "exit $it",
                        accentColor = if (it == 0) colorScheme.secondary else colorScheme.error,
                    )
                }
                sessionId?.let {
                    TerminalMetaChip(
                        text = "session ${it.take(8)}",
                        accentColor = colorScheme.primary,
                    )
                }
                if (effectiveLineCount > 1) {
                    TerminalMetaChip(
                        text = "$effectiveLineCount lines",
                        accentColor = colorScheme.outline,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = terminalBodyColor,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.1f)),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().then(
                        if (needsScroll) {
                            Modifier.heightIn(max = bodyMaxHeight)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    val outputModifier = if (needsScroll) {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(bodyScrollState)
                    } else {
                        Modifier.fillMaxWidth()
                    }

                    SelectionContainer {
                        Text(
                            text = output,
                            style = bodyTextStyle,
                            color = colorScheme.onSurface.copy(alpha = 0.9f),
                            onTextLayout = { layoutResult ->
                                val measuredLines = layoutResult.lineCount.coerceAtLeast(bodyLineCount)
                                if (laidOutLineCount != measuredLines) {
                                    laidOutLineCount = measuredLines
                                }
                            },
                            modifier = outputModifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }

                    if (needsScroll && bodyScrollState.canScrollForward) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, terminalBodyColor)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalMetaChip(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
    monospace: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = if (monospace) JetbrainsMono else null,
            ),
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
