package me.rerere.rikkahub.ui.pages.setting

import android.os.Process
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.JetbrainsMono

@Composable
fun SettingLogcatPage() {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val result = readLogcatLines()
            loading = false
            result.onSuccess { logs = it }
                .onFailure { error ->
                    toaster.show("无法读取 logcat：${error.message ?: "未知错误"}")
                }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logcat") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Lucide.RefreshCw, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (logs.isEmpty() && !loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无日志或无权限读取")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun readLogcatLines(): Result<List<String>> = withContext(Dispatchers.IO) {
    runCatching {
        val pid = Process.myPid().toString()
        val baseArgs = listOf("logcat", "-d", "-v", "threadtime", "-t", "400")
        val primary = runCatching { runLogcat(baseArgs + listOf("--pid", pid)) }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) {
            primary
        } else {
            val fallback = runLogcat(baseArgs)
            fallback.filter { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 6)
                parts.size >= 6 && parts[2] == pid
            }
        }
    }
}

private fun runLogcat(args: List<String>): List<String> {
    val process = ProcessBuilder(args)
        .redirectErrorStream(true)
        .start()
    val lines = process.inputStream.bufferedReader().readLines()
    val code = process.waitFor()
    if (code != 0) {
        val tail = lines.takeLast(3).joinToString("\n")
        throw IllegalStateException(tail.ifBlank { "logcat 执行失败（code=$code）" })
    }
    return lines
}
