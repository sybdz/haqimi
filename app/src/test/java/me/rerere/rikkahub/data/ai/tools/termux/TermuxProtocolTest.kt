package me.rerere.rikkahub.data.ai.tools.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxProtocolTest {
    @Test
    fun `constants match termux defaults`() {
        assertEquals("com.termux", TermuxProtocol.TERMUX_PACKAGE_NAME)
        assertEquals("com.termux.app.RunCommandService", TermuxProtocol.RUN_COMMAND_SERVICE)
        assertEquals("com.termux.permission.RUN_COMMAND", TermuxProtocol.PERMISSION_RUN_COMMAND)
        assertEquals("com.termux.RUN_COMMAND", TermuxProtocol.ACTION_RUN_COMMAND)
        assertEquals("com.termux.RUN_COMMAND_PATH", TermuxProtocol.EXTRA_COMMAND_PATH)
        assertEquals("com.termux.RUN_COMMAND_ARGUMENTS", TermuxProtocol.EXTRA_ARGUMENTS)
        assertEquals("com.termux.RUN_COMMAND_WORKDIR", TermuxProtocol.EXTRA_WORKDIR)
        assertEquals("com.termux.RUN_COMMAND_BACKGROUND", TermuxProtocol.EXTRA_BACKGROUND)
        assertEquals("com.termux.RUN_COMMAND_PENDING_INTENT", TermuxProtocol.EXTRA_PENDING_INTENT)
        assertEquals("result", TermuxProtocol.RESULT_BUNDLE)
        assertEquals("stdout", TermuxProtocol.RESULT_STDOUT)
        assertEquals("stderr", TermuxProtocol.RESULT_STDERR)
        assertEquals("exitCode", TermuxProtocol.RESULT_EXIT_CODE)
        assertEquals("err", TermuxProtocol.RESULT_ERR)
        assertEquals("errmsg", TermuxProtocol.RESULT_ERRMSG)
    }
}

