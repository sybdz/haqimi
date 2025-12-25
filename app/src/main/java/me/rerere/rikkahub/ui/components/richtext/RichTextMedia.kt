package me.rerere.rikkahub.ui.components.richtext

internal fun isSvgSource(source: String): Boolean {
    val value = source.trim()
    if (value.isEmpty()) return false
    if (value.startsWith("data:", ignoreCase = true)) {
        return value.contains("image/svg+xml", ignoreCase = true)
    }
    val normalized = value.substringBefore('#').substringBefore('?')
    return normalized.endsWith(".svg", ignoreCase = true)
}

private fun stripXmlPreamble(source: String): String {
    var value = source.trimStart()
    while (true) {
        when {
            value.startsWith("<?xml", ignoreCase = true) -> {
                val end = value.indexOf("?>")
                if (end == -1) return value
                value = value.substring(end + 2).trimStart()
            }

            value.startsWith("<!DOCTYPE", ignoreCase = true) -> {
                val end = value.indexOf('>')
                if (end == -1) return value
                value = value.substring(end + 1).trimStart()
            }

            value.startsWith("<!--") -> {
                val end = value.indexOf("-->")
                if (end == -1) return value
                value = value.substring(end + 3).trimStart()
            }

            else -> return value
        }
    }
}

internal fun looksLikeSvgMarkup(source: String): Boolean {
    val value = stripXmlPreamble(source)
    if (!value.startsWith('<')) return false
    if (value.length < 4) return false
    if (!value.regionMatches(startIndex = 1, other = "svg", otherStartIndex = 0, length = 3, ignoreCase = true)) {
        return false
    }
    if (value.length == 4) return true
    val next = value[4]
    return next.isWhitespace() || next == '>' || next == '/'
}

internal fun looksLikeHtmlMarkup(source: String): Boolean {
    val value = stripXmlPreamble(source)
    if (!value.startsWith('<')) return false
    val head = value.take(256)
    return Regex(
        pattern = "(?is)<\\s*(?:!doctype\\s+html|html\\b|head\\b|body\\b|script\\b|style\\b|div\\b|span\\b|p\\b|h[1-6]\\b|ul\\b|ol\\b|li\\b|table\\b|img\\b|a\\b|link\\b|meta\\b)",
    ).containsMatchIn(head)
}
