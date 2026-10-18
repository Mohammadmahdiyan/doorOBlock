package com.doorblock.core

import com.doorblock.core.lists.DomainLookup
import java.net.URLDecoder

/**
 * تشخیص سایت و عبارت ممنوع.
 *  - دامنه‌ها بر اساس ساختار دامنه تطبیق می‌شوند (xnxx.com و m.xnxx.com بله، notxnxx.com خیر).
 *  - کلمه‌ها با مرز توکن تطبیق می‌شوند (KeywordMatcher)؛ منطق دامنه و کلمه جدا هستند.
 *
 * @param domains دامنه‌های شناخته‌شده؛ نام آنها (xnxx) در جستجوی ساده هم مسدود است.
 * @param plainDomains دامنه‌های اضافه (مثلا موارد سفارشی کاربر)؛ فقط به‌صورت ساختار دامنه.
 * @param bigDomains فهرست بزرگ (فشرده)؛ فقط به‌صورت ساختار دامنه، هرگز به‌عنوان «کلمه».
 */
class FilterEngine(
    domains: Set<String>,
    keywords: List<String>,
    plainDomains: Set<String> = emptySet(),
    private val bigDomains: DomainLookup? = null,
) {
    private val strongDomains: Set<String> =
        domains.map { canonicalHost(it) }.filter { it.contains('.') }.toSet()

    private val plainSet: Set<String> =
        plainDomains.map { canonicalHost(it) }.filter { it.contains('.') }.toSet() - strongDomains

    /** نام دامنه بدون پسوند (xnxx). فقط برای دامنه‌های شناخته‌شده و فقط در جستجوی ساده. */
    private val domainLabels: Set<String> =
        strongDomains.map { labelOf(it) }.filter { it.length >= 4 }
            .map { TextNorm.tokenize(it).joinToString("") }.toSet()

    private val matcher = KeywordMatcher(keywords.mapNotNull { KeywordRule.parse(it) })

    private val cache = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > CACHE_MAX
    }

    /** ورودی را خودش تشخیص می‌دهد که آدرس است یا عبارت جستجو. نتیجه‌ی اخیر کش می‌شود. */
    fun isBlocked(input: String): Boolean {
        val t = input.trim()
        if (t.isEmpty()) return false
        val cacheable = t.length <= 512
        if (cacheable) {
            synchronized(cache) {
                val hit = cache[t]
                if (hit != null) return hit
            }
        }
        val result = if (looksLikeUrl(t)) isBlockedUrl(t) else isBlockedQuery(t)
        if (cacheable) synchronized(cache) { cache[t] = result }
        return result
    }

    fun isBlockedHost(host: String): Boolean {
        val h = canonicalHost(host)
        if (h.isEmpty() || h.length > 253) return false
        if (inSuffixSet(h, strongDomains) || inSuffixSet(h, plainSet)) return true
        return bigDomains?.containsHostOrParent(h) ?: false
    }

    fun isBlockedUrl(url: String): Boolean {
        val host = canonicalHost(url)
        if (isBlockedHost(host)) return true
        if (matcher.matches(host, forHost = true)) return true
        val rest = restOfUrl(url)
        return rest.isNotEmpty() && isBlockedQuery(decode(rest))
    }

    fun isBlockedQuery(text: String): Boolean {
        val raw = clean(text.take(MAX_TEXT))
        if (raw.isBlank()) return false
        if (matcher.matches(raw)) return true
        for (m in DOMAIN_TOKEN.findAll(raw)) {
            if (isBlockedHost(m.value)) return true
        }
        return labelHit(raw)
    }

    /**
     * نام یک دامنه‌ی شناخته‌شده به‌تنهایی در جستجو (xnxx، xvid3os، x n x x).
     * تکه‌های چسبیده فقط وقتی یکی حساب می‌شوند که ۳ تکه یا بیشتر باشند، یا تکه‌ی تک‌حرفی نداشته باشند؛
     * «x videos» (ویدیوهای X) با «xvideos» یکی نمی‌شود.
     */
    private fun labelHit(raw: String): Boolean {
        if (domainLabels.isEmpty()) return false
        val tokens = TextNorm.tokenize(raw)
        for (i in tokens.indices) {
            val sb = StringBuilder()
            var hasOneChar = false
            var j = i
            while (j < tokens.size && j - i < MAX_SPAN) {
                val t = tokens[j]
                sb.append(t)
                if (t.length == 1) hasOneChar = true
                if (sb.length > MAX_LABEL) break
                val count = j - i + 1
                if ((count == 1 || count >= 3 || !hasOneChar) && sb.toString() in domainLabels) return true
                j++
            }
        }
        return false
    }

    /** برای سازگاری با کد قبلی */
    fun isBlockedText(text: String): Boolean = isBlockedQuery(text)

    /** host یا یکی از والدهای آن در مجموعه باشد. */
    private fun inSuffixSet(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var h = host
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    companion object {
        private const val MAX_TEXT = 300
        private const val CACHE_MAX = 256
        private const val MAX_SPAN = 8
        private const val MAX_LABEL = 63
        private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")
        private val DOMAIN_TOKEN =
            Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
        private val HOST_LIKE = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,}\\.?(?::\\d+)?(?:[/?#].*)?$")

        private fun clean(s: String): String =
            INVISIBLE.replace(s, "").replace('。', '.').lowercase()

        private fun looksLikeUrl(t: String): Boolean {
            if (t.any { it.isWhitespace() }) return false
            val l = clean(t)
            return l.contains("://") || HOST_LIKE.matches(l)
        }

        /** HTTPS، www، حروف بزرگ، پورت، نقطه‌ی انتهایی، مسیر و user@ را کنار می‌گذارد. */
        fun canonicalHost(input: String): String {
            var s = clean(input.trim())
            s = s.substringAfter("://", s)
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
            s = s.substringAfterLast('@')
            s = s.substringBefore(':')
            s = s.trimEnd('.')
            while (s.startsWith("www.")) s = s.removePrefix("www.")
            return s
        }

        /** سازگار با کد قبلی */
        fun extractHost(url: String): String = canonicalHost(url)

        private fun labelOf(host: String): String {
            val parts = host.split('.')
            return if (parts.size >= 2) parts[parts.size - 2] else parts[0]
        }

        private fun restOfUrl(url: String): String {
            val t = url.trim()
            val s = t.substringAfter("://", t)
            val idx = s.indexOfAny(charArrayOf('/', '?', '#'))
            return if (idx < 0) "" else s.substring(idx)
        }

        private fun decode(s: String): String =
            try { URLDecoder.decode(s, "UTF-8") } catch (e: IllegalArgumentException) { s }

        /**
         * ورودی کاربر: اگر نقطه دارد و فاصله ندارد دامنه است (فقط ساختار دامنه)،
         * وگرنه قاعده‌ی کلمه (کوتاه=کل کلمه، چندکلمه‌ای=عبارت، ۴ حرف+=شامل).
         */
        fun build(custom: Set<String>, bigDomains: DomainLookup? = null): FilterEngine {
            val plain = mutableSetOf<String>()
            val keywords = DefaultLists.keywords.toMutableList()
            for (raw in custom) {
                val item = raw.trim()
                if (item.isEmpty()) continue
                if (item.contains('.') && item.none { it.isWhitespace() }) {
                    plain.add(canonicalHost(item))
                } else {
                    keywords.add(item)
                }
            }
            return FilterEngine(DefaultLists.domains, keywords, plain, bigDomains)
        }
    }
}
