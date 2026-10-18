package com.doorblock.core.lists

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * دانلود با HttpURLConnection. فقط HTTPS و فقط میزبان‌های مجاز (حتی بعد از ریدایرکت).
 * داده فقط به‌عنوان متن خوانده می‌شود؛ هیچ چیزی اجرا نمی‌شود.
 */
class HttpFetcher(
    private val allowedHostSuffixes: List<String> = listOf("github.io", "githubusercontent.com"),
) : Fetcher {

    override fun <T> fetch(url: String, etag: String?, handler: (FetchResponse) -> T): T {
        var current = url
        for (hop in 0..MAX_REDIRECTS) {
            requireAllowed(current)
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "doorOBlock-list-updater")
                if (!etag.isNullOrEmpty()) conn.setRequestProperty("If-None-Match", etag)
                val code = conn.responseCode
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("redirect without location")
                    current = URL(URL(current), loc).toString()
                    continue
                }
                val body = if (code == 200) conn.inputStream else null
                return handler(FetchResponse(code, conn.getHeaderField("ETag"), body))
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    private fun requireAllowed(u: String) {
        val parsed = URL(u)
        if (parsed.protocol != "https") throw IOException("https only")
        val host = parsed.host.lowercase()
        val ok = allowedHostSuffixes.any { host == it || host.endsWith(".$it") }
        if (!ok) throw IOException("host not allowed")
    }

    companion object {
        private const val MAX_REDIRECTS = 3
    }
}
