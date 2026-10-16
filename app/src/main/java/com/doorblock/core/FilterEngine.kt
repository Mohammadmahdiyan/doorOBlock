package com.doorblock.core

import java.net.URLDecoder

/**
 * تشخیص سایت و عبارت ممنوع.
 *  - isBlocked(x): ورودی را خودش تشخیص می‌دهد که آدرس است یا عبارت جستجو.
 *  - دامنه‌ها با رعایت مرز تطبیق می‌شوند: xnxx.com و m.xnxx.com بله، notxnxx.com خیر.
 */
class FilterEngine(domains: Set<String>, keywords: List<String>) {

    private val blockedDomains: Set<String> =
        domains.map { canonicalHost(it) }.filter { it.contains('.') }.toSet()

    /** نام دامنه بدون پسوند، مثلا xnxx. فقط برای جستجوی عبارت (نه آدرس). */
    private val domainLabels: Set<String> =
        blockedDomains.map { labelOf(it) }.filter { it.length >= 4 }.toSet()

    private val words: List<String> =
        keywords.map { Normalizer.normalize(it) }.filter { it.isNotEmpty() }

    fun isBlocked(input: String): Boolean {
        val t = input.trim()
        if (t.isEmpty()) return false
        return if (looksLikeUrl(t)) isBlockedUrl(t) else isBlockedQuery(t)
    }

    fun isBlockedHost(host: String): Boolean {
        val h = canonicalHost(host)
        if (h.isEmpty()) return false
        return blockedDomains.any { h == it || h.endsWith(".$it") }
    }

    fun isBlockedUrl(url: String): Boolean {
        val host = canonicalHost(url)
        if (isBlockedHost(host)) return true
        if (keywordHit(host)) return true
        val rest = restOfUrl(url)
        return rest.isNotEmpty() && isBlockedQuery(decode(rest))
    }

    fun isBlockedQuery(text: String): Boolean {
        val raw = clean(text.take(300))
        if (raw.isBlank()) return false
        if (keywordHit(raw)) return true
        for (m in DOMAIN_TOKEN.findAll(raw)) {
            if (isBlockedHost(m.value)) return true
        }
        val tokens = raw.split(SPLIT).filter { it.isNotEmpty() }
        if (tokens.any { it in domainLabels }) return true
        // x n x x  یا  xvid3os
        if (Normalizer.normalize(raw) in domainLabels) return true
        return false
    }

    /** برای سازگاری با کد قبلی */
    fun isBlockedText(text: String): Boolean = isBlockedQuery(text)

    private fun keywordHit(text: String): Boolean {
        val n = Normalizer.normalize(text)
        if (n.isEmpty()) return false
        val nc = Normalizer.collapse(n)
        for (w in words) {
            if (n.contains(w) || nc.contains(Normalizer.collapse(w))) return true
            if (w.length >= 6 && fuzzyContains(n, w)) return true
        }
        return false
    }

    private fun fuzzyContains(text: String, word: String): Boolean {
        for (len in (word.length - 1)..(word.length + 1)) {
            if (len > text.length) continue
            for (i in 0..(text.length - len)) {
                if (Normalizer.levenshtein(text.substring(i, i + len), word) <= 1) return true
            }
        }
        return false
    }

    companion object {
        private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")
        private val DOMAIN_TOKEN =
            Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
        private val HOST_LIKE = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,}\\.?(?::\\d+)?(?:[/?#].*)?$")
        private val SPLIT = Regex("[^a-z0-9]+")

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

        /** ورودی کاربر: اگر نقطه دارد و فاصله ندارد دامنه است، وگرنه کلمه. */
        fun build(custom: Set<String>): FilterEngine {
            val domains = DefaultLists.domains.toMutableSet()
            val keywords = DefaultLists.keywords.toMutableList()
            for (raw in custom) {
                val item = raw.trim()
                if (item.isEmpty()) continue
                if (item.contains('.') && item.none { it.isWhitespace() }) {
                    domains.add(canonicalHost(item))
                } else {
                    keywords.add(item)
                }
            }
            return FilterEngine(domains, keywords)
        }
    }
}
