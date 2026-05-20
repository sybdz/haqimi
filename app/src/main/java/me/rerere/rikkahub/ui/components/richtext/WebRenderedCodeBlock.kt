package me.rerere.rikkahub.ui.components.richtext

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowExpandDiagonal01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.event.ChatComposerBridge
import me.rerere.rikkahub.data.event.ChatHistoryBridge
import me.rerere.rikkahub.ui.components.webview.WebContent
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.WebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.toCssHex
import org.koin.compose.koinInject

private const val MIN_PREVIEW_HEIGHT_DP = 10

private const val INITIAL_PREVIEW_HEIGHT_DP = 180

private const val RENDERED_CODE_BLOCK_BASE_URL = "https://rikkahub.local"
private const val RENDERED_CODE_BLOCK_MIME_TYPE = "text/html"
private const val RENDERED_CODE_BLOCK_ENCODING = "utf-8"

private class CodeBlockRenderBridge(
    private val onHeightChanged: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onContentHeight(heightText: String?) {
        val height = heightText
            ?.trim()
            ?.toFloatOrNull()
            ?.toInt()
            ?.coerceAtLeast(MIN_PREVIEW_HEIGHT_DP)
            ?: return
        mainHandler.post {
            onHeightChanged(height)
        }
    }
}

private class CodeBlockChatActionBridge(
    private val composerBridge: ChatComposerBridge,
    private val historyBridge: ChatHistoryBridge,
) {
    @JavascriptInterface
    fun getDraftText(): String = composerBridge.getDraftText()

    @JavascriptInterface
    fun setDraftText(text: String?) {
        composerBridge.setDraftText(text.orEmpty())
    }

    @JavascriptInterface
    fun appendDraftText(text: String?) {
        composerBridge.appendDraftText(text.orEmpty())
    }

    @JavascriptInterface
    fun sendCurrentDraft(answer: Boolean) {
        composerBridge.sendCurrentDraft(answer)
    }

    @JavascriptInterface
    fun sendText(text: String?, answer: Boolean) {
        composerBridge.sendText(
            text = text.orEmpty(),
            answer = answer,
        )
    }

    @JavascriptInterface
    fun getHistorySnapshot(): String = historyBridge.getSnapshotJson()

    @JavascriptInterface
    fun editHistoryMessage(nodeId: String?, text: String?) {
        historyBridge.editMessage(
            nodeId = nodeId.orEmpty(),
            text = text.orEmpty(),
        )
    }

    @JavascriptInterface
    fun deleteHistoryMessage(nodeId: String?) {
        historyBridge.deleteMessage(nodeId.orEmpty())
    }

    @JavascriptInterface
    fun selectHistoryMessageNode(nodeId: String?, selectIndex: Int) {
        historyBridge.selectMessageNode(nodeId.orEmpty(), selectIndex)
    }

    @JavascriptInterface
    fun regenerateHistoryMessage(nodeId: String?, regenerateAssistantMessage: Boolean) {
        historyBridge.regenerateMessage(
            nodeId = nodeId.orEmpty(),
            regenerateAssistantMessage = regenerateAssistantMessage,
        )
    }

    @JavascriptInterface
    fun continueHistoryMessage(nodeId: String?) {
        historyBridge.continueMessage(nodeId.orEmpty())
    }
}

@Composable
private fun rememberRenderedCodeBlockWebViewState(
    initialHtml: String,
    interfaces: Map<String, Any> = emptyMap(),
): WebViewState = remember(interfaces) {
    WebViewState(
        initialContent = WebContent.Data(
            data = initialHtml,
            baseUrl = RENDERED_CODE_BLOCK_BASE_URL,
            mimeType = RENDERED_CODE_BLOCK_MIME_TYPE,
            encoding = RENDERED_CODE_BLOCK_ENCODING,
        ),
        interfaces = interfaces,
        settings = {
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
        }
    )
}

private fun WebViewState.loadRenderedCodeBlockHtml(html: String) {
    val currentContent = content as? WebContent.Data
    if (
        currentContent?.data == html &&
        currentContent.baseUrl == RENDERED_CODE_BLOCK_BASE_URL &&
        currentContent.mimeType == RENDERED_CODE_BLOCK_MIME_TYPE &&
        currentContent.encoding == RENDERED_CODE_BLOCK_ENCODING
    ) {
        return
    }

    loadData(
        data = html,
        baseUrl = RENDERED_CODE_BLOCK_BASE_URL,
        mimeType = RENDERED_CODE_BLOCK_MIME_TYPE,
        encoding = RENDERED_CODE_BLOCK_ENCODING,
    )
}

@Composable
internal fun WebRenderedCodeBlock(
    target: CodeBlockRenderTarget,
    code: String,
    modifier: Modifier = Modifier,
) {
    val composerBridge: ChatComposerBridge = koinInject()
    val historyBridge: ChatHistoryBridge = koinInject()
    val navController = LocalNavController.current
    val renderSignature = remember(target, code) {
        "${target.normalizedLanguage}:${target.renderType}:${code.hashCode()}"
    }
    val backgroundColor = MaterialTheme.colorScheme.surface.toCssHex()
    val textColor = MaterialTheme.colorScheme.onSurface.toCssHex()
    val inlineHtml = remember(target, code, backgroundColor, textColor) {
        CodeBlockRenderResolver.buildHtmlForWebView(
            target, code,
            backgroundColor = backgroundColor,
            textColor = textColor,
            scrollMode = CodeBlockRenderScrollMode.AUTO_HEIGHT,
        )
    }
    var contentHeightDp by remember(renderSignature) { mutableIntStateOf(INITIAL_PREVIEW_HEIGHT_DP) }
    val renderBridge = remember(renderSignature) {
        CodeBlockRenderBridge { nextHeight ->
            if (nextHeight != contentHeightDp) {
                contentHeightDp = nextHeight
            }
        }
    }
    val chatActionBridge = remember(composerBridge, historyBridge) {
        CodeBlockChatActionBridge(
            composerBridge = composerBridge,
            historyBridge = historyBridge,
        )
    }
    val webViewInterfaces = remember(renderBridge, chatActionBridge) {
        mapOf(
            CODE_BLOCK_HEIGHT_BRIDGE_NAME to renderBridge,
            CODE_BLOCK_ACTION_BRIDGE_NAME to chatActionBridge,
        )
    }
    val webViewState = rememberRenderedCodeBlockWebViewState(
        initialHtml = inlineHtml,
        interfaces = webViewInterfaces,
    )

    val animatedHeight by animateDpAsState(
        targetValue = contentHeightDp.coerceAtLeast(MIN_PREVIEW_HEIGHT_DP).dp,
        animationSpec = tween(durationMillis = 300),
        label = "codeBlockHeight",
    )

    LaunchedEffect(inlineHtml) {
        webViewState.loadRenderedCodeBlockHtml(inlineHtml)
    }

    key(renderSignature) {
        Box(
            modifier = Modifier
                .then(modifier)
                .clip(RoundedCornerShape(12.dp))
                .fillMaxWidth()
                .height(animatedHeight),
        ) {
            WebView(
                state = webViewState,
                allowFocus = false,
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    webView.setOnTouchListener { view, event ->
                        when (event?.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                shadowElevation = 10.dp,
            ) {
                IconButton(
                    onClick = {
                        val previewHtml = CodeBlockRenderResolver.buildHtmlForWebView(
                            target = target,
                            code = code,
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            scrollMode = CodeBlockRenderScrollMode.SCROLLABLE,
                        )
                        navController.navigate(Screen.WebView(content = previewHtml.base64Encode()))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(HugeIcons.ArrowExpandDiagonal01, contentDescription = stringResource(R.string.code_block_expand))
                }
            }
        }
    }
}
