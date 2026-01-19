package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val requestHeaders = request.headers.toMap()
        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val responseHeaders = response.headers.toMap()
        val responseBody = response.peekBodyString()

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = request.url.toString(),
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { get(it) ?: "" }
    }

    private fun Response.peekBodyString(): String? {
        val body = body ?: return null
        val contentType = body.contentType()
        val contentTypeString = contentType?.toString()?.lowercase()
        if (contentTypeString?.contains("text/event-stream") == true) {
            return "<streaming body omitted>"
        }
        val isText = contentType == null ||
            contentType.type == "text" ||
            contentTypeString?.contains("json") == true ||
            contentTypeString?.contains("xml") == true ||
            contentTypeString?.contains("html") == true ||
            contentTypeString?.contains("x-www-form-urlencoded") == true
        if (!isText) {
            return "<non-text body omitted: ${contentType ?: "unknown"}>"
        }

        val contentLength = body.contentLength()
        val peeked = runCatching { peekBody(MAX_LOG_BODY_BYTES) }.getOrElse { error ->
            return "<failed to read body: ${error.message ?: "unknown error"}>"
        }
        val text = runCatching { peeked.string() }.getOrElse { error ->
            return "<failed to read body: ${error.message ?: "unknown error"}>"
        }
        return if (contentLength > MAX_LOG_BODY_BYTES && contentLength != -1L) {
            "$text\n(truncated)"
        } else {
            text
        }
    }
}

private const val MAX_LOG_BODY_BYTES = 256 * 1024L
