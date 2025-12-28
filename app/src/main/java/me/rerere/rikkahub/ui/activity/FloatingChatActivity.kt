package me.rerere.rikkahub.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toFile
import androidx.core.net.toUri
import me.rerere.rikkahub.service.floating.FloatingBallService
import me.rerere.rikkahub.ui.components.FloatingChatPanel
import me.rerere.rikkahub.ui.theme.RikkahubTheme

class FloatingChatActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SCREENSHOT_URI = "screenshot_uri"
    }

    private var screenshotUriString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenshotUriString = intent.getStringExtra(EXTRA_SCREENSHOT_URI)
        val initialImages = screenshotUriString?.let { listOf(it) } ?: emptyList()

        setContent {
            RikkahubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FloatingChatPanel(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .fillMaxHeight(0.55f),
                            initialImages = initialImages,
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            FloatingBallService.showBall(this)
        }
        val uri = screenshotUriString?.toUri()
        if (uri != null) {
            runCatching {
                val file = uri.toFile()
                if (file.exists()) file.delete()
            }
        }
        super.onDestroy()
    }
}
