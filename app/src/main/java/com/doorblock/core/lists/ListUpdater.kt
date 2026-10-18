package com.doorblock.core.lists

import java.io.InputStream

class FetchResponse(val status: Int, val etag: String?, val body: InputStream?)

/** دریافت از شبکه. در تست با نسخه‌ی ساختگی جایگزین می‌شود. body فقط داخل handler معتبر است. */
interface Fetcher {
    fun <T> fetch(url: String, etag: String?, handler: (FetchResponse) -> T): T
}

class SourceSpec(val id: String, val url: String)

/** فقط این منابع عمومی؛ مجوز و توضیح در docs/LISTS.md. هیچ آدرس دیگری دانلود نمی‌شود. */
object Sources {
    val ALL: List<SourceSpec> = listOf(
        SourceSpec("blp_porn", "https://blocklistproject.github.io/Lists/porn.txt"),
        SourceSpec("sb_porn", "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"),
    )
}

enum class Outcome { UPDATED, NOT_MODIFIED, REJECTED, FAILED }

class SourceResult(val id: String, val outcome: Outcome, val detail: String)

class UpdateReport(val skipped: Boolean, val results: List<SourceResult>) {
    val anyUpdated: Boolean get() = results.any { it.outcome == Outcome.UPDATED }
}

class ListUpdater(
    private val store: ListStore,
    private val fetcher: Fetcher,
    private val sources: List<SourceSpec> = Sources.ALL,
    private val minEntries: Int = 1000,
    private val maxBytes: Long = DomainListIO.DEFAULT_MAX_BYTES,
) {
    /** فقط اگر زمانش رسیده باشد (حداکثر هفته‌ای یک‌بار؛ بعد از شکست، روزی یک‌بار). */
    fun updateIfDue(now: Long): UpdateReport {
        val nextDue = store.getLong(KEY_NEXT_DUE) ?: 0L
        if (now < nextDue) return UpdateReport(true, emptyList())
        return update(now)
    }

    fun update(now: Long): UpdateReport {
        val results = sources.map { updateOne(it, now) }
        val anyOk = results.any { it.outcome == Outcome.UPDATED || it.outcome == Outcome.NOT_MODIFIED }
        val wait = if (anyOk) CHECK_INTERVAL_MS else RETRY_INTERVAL_MS
        try {
            store.setState(
                mapOf(
                    KEY_NEXT_DUE to (now + wait).toString(),
                    "lastCheck" to now.toString(),
                    "lastSummary" to results.joinToString(";") { "${it.id}=${it.outcome}" },
                ),
            )
        } catch (e: Exception) {
            // ذخیره‌ی state شکست خورد؛ فهرست‌ها تغییری نکرده‌اند
        }
        return UpdateReport(false, results)
    }

    private fun updateOne(src: SourceSpec, now: Long): SourceResult {
        return try {
            val prev = store.readSource(src.id)
            val etag = if (prev != null) store.getString("etag.${src.id}") else null
            fetcher.fetch(src.url, etag) { resp ->
                val body = resp.body
                when {
                    resp.status == 304 && prev != null ->
                        SourceResult(src.id, Outcome.NOT_MODIFIED, "304")
                    resp.status == 200 && body != null -> {
                        val parsed = DomainListIO.parse(body, maxBytes, now)
                        val reason = validate(parsed, prev)
                        if (reason != null) {
                            SourceResult(src.id, Outcome.REJECTED, reason)
                        } else {
                            store.writeSource(src.id, DomainSetCodec.encode(parsed.set))
                            store.setState(mapOf("etag.${src.id}" to (resp.etag ?: "")))
                            SourceResult(src.id, Outcome.UPDATED, "${parsed.set.size} domains")
                        }
                    }
                    else -> SourceResult(src.id, Outcome.FAILED, "HTTP ${resp.status}")
                }
            }
        } catch (e: Exception) {
            SourceResult(src.id, Outcome.FAILED, e.javaClass.simpleName)
        }
    }

    /** null یعنی معتبر؛ وگرنه دلیل رد. */
    private fun validate(parsed: ParseResult, prev: DomainSet?): String? {
        val total = parsed.valid + parsed.invalid
        if (parsed.set.size < minEntries) return "too few entries"
        if (total > 0 && parsed.invalid * 5 > total) return "too many invalid lines"
        if (prev != null && parsed.set.size < prev.size / 2) return "list shrank by more than half"
        return null
    }

    companion object {
        const val KEY_NEXT_DUE = "nextDue"
        const val CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        const val RETRY_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
