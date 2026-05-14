package me.rerere.rikkahub.ui.components.message

import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.copyMessageToClipboard

@Composable
fun ChatMessageCopySheet(
    message: UIMessage,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) {
                    Icon(HugeIcons.Cancel01, null)
                }

                Text(
                    text = stringResource(R.string.select_and_copy),
                    style = MaterialTheme.typography.headlineSmall,
                )

                TextButton(
                    onClick = {
                        context.copyMessageToClipboard(message)
                        onDismissRequest()
                    }
                ) {
                    Icon(
                        imageVector = HugeIcons.Copy01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.copy_all))
                }
            }

            // Content
            val textContent = message.toText()

            if (textContent.isBlank()) {
                // No text content available
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_text_content_to_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                NativeSelectableText(
                    text = textContent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NativeSelectableText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.bodyMedium

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        setTextIsSelectable(true)
                        setOnLongClickListener {
                            requestFocus()
                            false
                        }
                    }
                )
            }
        },
        update = { scrollView ->
            val textView = scrollView.getChildAt(0) as TextView
            textView.applySelectableTextStyle(
                text = text,
                style = textStyle,
                density = density,
                color = color.toArgb(),
            )
        }
    )
}

private fun TextView.applySelectableTextStyle(
    text: String,
    style: TextStyle,
    density: androidx.compose.ui.unit.Density,
    color: Int,
) {
    if (this.text.toString() != text) {
        setText(text)
    }
    setTextColor(color)
    with(density) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, style.fontSize.toPx())
        if (style.lineHeight.isSpecified) {
            val lineSpacingExtra = (style.lineHeight.toPx() - textSize).coerceAtLeast(0f)
            setLineSpacing(lineSpacingExtra, 1f)
        }
    }
}
