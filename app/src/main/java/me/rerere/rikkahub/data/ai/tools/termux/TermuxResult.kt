package me.rerere.rikkahub.data.ai.tools.termux

data class TermuxResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val errCode: Int? = null,
    val errMsg: String? = null,
    val stdoutOriginalLength: Int? = null,
    val stderrOriginalLength: Int? = null,
    val timedOut: Boolean = false,
)

