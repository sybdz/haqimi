package me.rerere.rikkahub.data.diagnostics

import java.util.Locale

internal object DiagnosticRedactor {
    private val secretQueryRegex =
        Regex("""([?&](?:key|api_key|apikey|token|access_token|password|secret)=)[^&#\s]+""", RegexOption.IGNORE_CASE)

    fun text(text: String): String {
        var result = text
        result = result.replace(Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/=-]{8,}""")) {
            it.groupValues[1] + "<redacted>"
        }
        result = result.replace(Regex("""(?i)((?:api[_-]?key|token|authorization|password|secret|private[_-]?key)["']?\s*[:=]\s*["']?)[^"',\s}]+""")) {
            it.groupValues[1] + "<redacted>"
        }
        result = result.replace(Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----""")) {
            "<redacted private key>"
        }
        result = result.replace(secretQueryRegex) {
            it.groupValues[1] + "<redacted>"
        }
        return result
    }

    fun url(url: String): String {
        return text(url)
    }

    fun isSensitiveHeader(header: String): Boolean {
        val normalized = header.lowercase(Locale.ROOT)
        return normalized == "authorization" ||
            normalized == "proxy-authorization" ||
            normalized == "cookie" ||
            normalized == "set-cookie" ||
            normalized.contains("api-key") ||
            normalized.contains("apikey") ||
            normalized.contains("token") ||
            normalized.contains("secret")
    }
}
