package com.doorblock.core

class FilterEngine(domains: Set<String>, keywords: List<String>) {
    private val domains = domains.map { it.lowercase().removePrefix("www.") }.toSet()
    private val words = keywords.map { Normalizer.normalize(it) }.filter { it.isNotEmpty() }

    /** آدرس (یا چیزی شبیه آدرس) را بررسی می‌کند. */
    fun isBlockedUrl(url: String): Boolean {
        val host = extractHost(url)
        if (host.isNotEmpty() && domains.any { host == it || host.endsWith(".$it") }) return true
        // کلمه‌ی ممنوع داخل خود آدرس، مثلا p-o-r-n-site.com
        return isBlockedText(url.take(300))
    }

    /** متن جستجو یا نام کانال را بررسی می‌کند. */
    fun isBlockedText(text: String): Boolean {
        val n = Normalizer.normalize(text.take(300))
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
        fun extractHost(url: String): String {
            var s = url.trim().lowercase()
            s = s.substringAfter("://", s)
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
            s = s.substringAfter('@').substringBefore(':')
            return s.removePrefix("www.")
        }

        /** ورودی کاربر: اگر نقطه دارد دامنه است، وگرنه کلمه. */
        fun build(custom: Set<String>): FilterEngine {
            val domains = DefaultLists.domains.toMutableSet()
            val keywords = DefaultLists.keywords.toMutableList()
            for (item in custom) {
                if (item.contains('.')) domains.add(extractHost(item)) else keywords.add(item)
            }
            return FilterEngine(domains, keywords)
        }
    }
}
