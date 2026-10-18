package com.doorblock.core

enum class MatchMode {
    /** کل کلمه یا عبارت (مرز دو طرف باید مرز توکن باشد). مناسب کلمه‌های کوتاه یا چندمعنایی. */
    ALIGNED,

    /** ابتدای یک توکن (porn -> pornography). */
    PREFIX,

    /** داخل یک توکن هم قبول است. فقط برای رشته‌های بلند و خاص (مثل نام سایت‌ها). */
    CONTAINS,
}

/**
 * قالب قاعده:
 *   w:کلمه   کل کلمه        p:کلمه  پیشوند        c:کلمه  شامل        ph:دو کلمه  عبارت
 *   b:نام    نام برند سایت: مثل «شامل»، ولی فقط روی متن جستجو، نه روی نام میزبان؛
 *            میزبان‌ها را منطق دامنه تطبیق می‌دهد تا notxnxx.com به‌خاطر شباهت رشته مسدود نشود.
 *   بدون پیشوند: چندکلمه‌ای=عبارت، ۴ حرف یا بیشتر=شامل، کوتاه‌تر=کل کلمه
 */
class KeywordRule(
    val mode: MatchMode,
    val key: String,
    singleWord: Boolean = true,
    /** false = فقط روی متن جستجو اعمال می‌شود (قاعده‌ی برند). */
    val appliesToHosts: Boolean = true,
) {
    val keyCollapsed: String = TextNorm.collapse(key)

    /**
     * غلط‌املایی یک‌حرفی فقط برای قاعده‌ی تک‌کلمه‌ای بلند (۸+ حرف). عبارت‌های کوتاه
     * (مثل «sex movie») عمدا فازی نیستند، وگرنه «six movie» هم گرفتار می‌شد.
     */
    val fuzzy: Boolean = singleWord && key.length >= 8 && mode != MatchMode.PREFIX

    companion object {
        fun parse(raw: String): KeywordRule? {
            var body = raw.trim()
            var mode: MatchMode? = null
            var hosts = true
            when {
                body.startsWith("b:") -> { mode = MatchMode.CONTAINS; hosts = false; body = body.removePrefix("b:") }
                body.startsWith("ph:") -> { mode = MatchMode.ALIGNED; body = body.removePrefix("ph:") }
                body.startsWith("w:") -> { mode = MatchMode.ALIGNED; body = body.removePrefix("w:") }
                body.startsWith("p:") -> { mode = MatchMode.PREFIX; body = body.removePrefix("p:") }
                body.startsWith("c:") -> { mode = MatchMode.CONTAINS; body = body.removePrefix("c:") }
            }
            val tokens = TextNorm.tokenize(body)
            if (tokens.isEmpty()) return null
            val key = tokens.joinToString("")
            val m = mode ?: when {
                body.any { it.isWhitespace() } -> MatchMode.ALIGNED
                key.length >= 4 -> MatchMode.CONTAINS
                else -> MatchMode.ALIGNED
            }
            return KeywordRule(m, key, singleWord = body.none { it.isWhitespace() }, appliesToHosts = hosts)
        }
    }
}
