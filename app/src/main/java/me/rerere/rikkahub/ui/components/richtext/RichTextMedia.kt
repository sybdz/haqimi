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

