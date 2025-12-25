package me.rerere.rikkahub.ui.components.richtext

import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState

@Composable
fun HtmlWebViewBlock(
    html: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val content = remember(html, colorScheme) {
        val trimmed = html.trim()
        val hasDocumentTag = Regex("(?is)<\\s*html\\b").containsMatchIn(trimmed) ||
            Regex("(?is)<!doctype\\b").containsMatchIn(trimmed)
        if (hasDocumentTag) {
            trimmed
        } else {
            val textColor = String.format("#%06X", 0xFFFFFF and colorScheme.onSurface.toArgb())
            val linkColor = String.format("#%06X", 0xFFFFFF and colorScheme.primary.toArgb())
            """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <style>
                      html, body { margin: 0; padding: 0; background: transparent; color: $textColor; }
                      a { color: $linkColor; }
                    </style>
                  </head>
                  <body>
                    $trimmed
                  </body>
                </html>
            """.trimIndent()
        }
    }

    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://localhost/",
        mimeType = "text/html",
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        },
    )

    WebView(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 480.dp)
            .clip(RoundedCornerShape(8.dp)),
        onCreated = { webView ->
            webView.setBackgroundColor(AndroidColor.TRANSPARENT)
        },
    )
}

