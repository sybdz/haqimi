package me.rerere.rikkahub.ui.components.message

import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageToolsTest {
    @Test
    fun `running tool should still open preview sheet`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call_1",
            toolName = "search_web",
            input = """{"query":"slow request"}""",
            output = emptyList(),
            approvalState = ToolApprovalState.Auto,
        )

        assertTrue(tool.canOpenPreviewSheet())
    }

    @Test
    fun `ask user tool should use inline interaction instead of preview sheet`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call_2",
            toolName = "ask_user",
            input = """{"questions":[]}""",
        )

        assertFalse(tool.canOpenPreviewSheet())
    }

    @Test
    fun `pending termux exec should inline command preview`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call_3",
            toolName = "termux_exec",
            input = """{"command":"ls -la /sdcard"}""",
            approvalState = ToolApprovalState.Pending,
        )

        val preview = requireNotNull(tool.pendingApprovalPreview())

        assertEquals("bash", preview.language)
        assertEquals("ls -la /sdcard", preview.code)
    }

    @Test
    fun `pending generic tool should inline json preview`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call_4",
            toolName = "custom_tool",
            input = """{"url":"https://example.com","depth":2}""",
            approvalState = ToolApprovalState.Pending,
        )

        val preview = requireNotNull(tool.pendingApprovalPreview())

        assertEquals("json", preview.language)
        assertTrue(preview.code.contains("\"url\""))
        assertTrue(preview.code.contains("https://example.com"))
    }

    @Test
    fun `termux exec output should produce inline terminal preview`() {
        val preview = buildTermuxToolOutputPreview(
            toolName = TermuxToolUiNames.EXEC,
            arguments = JsonInstant.parseToJsonElement("""{"command":"python -m json.tool data.json"}"""),
            output = listOf(
                UIMessagePart.Text(
                    "{\"output\":\"{\\n  \\\"ok\\\": true\\n}\",\"exit_code\":0}"
                )
            )
        )

        requireNotNull(preview)

        assertEquals("Command Output", preview.title)
        assertEquals("python -m json.tool data.json", preview.commandPreview)
        assertEquals(0, preview.exitCode)
        assertEquals("{\n  \"ok\": true\n}", preview.output)
    }

    @Test
    fun `write stdin running response should preserve session metadata`() {
        val preview = buildTermuxToolOutputPreview(
            toolName = TermuxToolUiNames.WRITE_STDIN,
            arguments = JsonInstant.parseToJsonElement("""{"session_id":"session-1","chars":""}"""),
            output = listOf(
                UIMessagePart.Text(
                    """{"output":"","session_id":"session-1","running":true,"exit_code":null,"error":null}"""
                )
            )
        )

        requireNotNull(preview)

        assertEquals("PTY Output", preview.title)
        assertEquals("Poll for more output", preview.commandPreview)
        assertTrue(preview.running)
        assertEquals("session-1", preview.sessionId)
        assertEquals("Waiting for output...", preview.output)
    }

    @Test
    fun `non termux tool should not produce inline terminal preview`() {
        val preview = buildTermuxToolOutputPreview(
            toolName = "search_web",
            arguments = JsonInstant.parseToJsonElement("""{"query":"compose"}"""),
            output = listOf(UIMessagePart.Text("""{"answer":"ok"}"""))
        )

        assertNull(preview)
    }
}
