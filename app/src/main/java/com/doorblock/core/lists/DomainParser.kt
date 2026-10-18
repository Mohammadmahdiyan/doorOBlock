package com.doorblock.core.lists

/**
 * هر سطر فهرست را به یک دامنه‌ی معتبر تبدیل می‌کند.
 * قالب‌های پشتیبانی‌شده: hosts ("0.0.0.0 d.com")، ساده ("d.com")، ABP ("||d.com^")، همراه با کامنت.
 */
object DomainParser {
    private val LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val TLD = Regex("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
    private val WS = Regex("\\s+")
    private val IP_HEAD = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val BAD_TLDS = setOf("local", "localhost", "localdomain", "invalid", "test")

    /** سطر خالی یا کامنت: نه معتبر حساب می‌شود نه نامعتبر. */
    fun isIgnorable(raw: String): Boolean {
        val s = raw.trim()
        return s.isEmpty() || s.startsWith("#") || s.startsWith("!") || s.startsWith("[")
    }

    /** دامنه‌ی معتبر (حروف کوچک، بدون www) یا null. */
    fun parseLine(raw: String): String? {
        if (raw.length > 1000) return null
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.startsWith("#") || s.startsWith("!") || s.startsWith("[")) return null
        val hash = s.indexOf('#')
        if (hash > 0) s = s.substring(0, hash).trim()
        val parts = s.split(WS)
        s = when {
            parts.size == 1 -> parts[0]
            parts.size == 2 && parts[0] in IP_HEAD -> parts[1]
            else -> return null
        }
        if (s.startsWith("||")) {
            s = s.substring(2).substringBefore('^').substringBefore('$')
        } else if (s.any { it == '^' || it == '$' || it == '*' || it == '/' || it == ':' || it == '@' || it == '|' }) {
            return null
        }
        s = s.trimEnd('.')
        while (s.startsWith("www.")) s = s.substring(4)
        return if (isValidDomain(s)) s else null
    }

    fun isValidDomain(d: String): Boolean {
        if (d.isEmpty() || d.length > 253) return false
        val labels = d.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        if (!TLD.matches(tld) || tld in BAD_TLDS) return false
        for (l in labels) {
            if (!LABEL.matches(l)) return false
        }
        return true
    }
}
