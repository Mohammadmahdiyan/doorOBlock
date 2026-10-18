package com.doorblock.core

/**
 * تطبیق قاعده‌ها روی متن با حفظ مرز توکن‌ها.
 *
 * متن به توکن شکسته می‌شود و توکن‌ها برای تشخیص جداکننده‌گذاری عمدی (L-e_xi lune) به هم
 * چسبانده می‌شوند، اما یک تطبیق فقط وقتی قبول است که از مرز توکن شروع/تمام شود
 * (یا کاملا داخل یک توکن باشد، آن هم فقط برای قاعده‌های CONTAINS).
 * مثال: «up or not» -> «upornot» حاوی «porn» است، ولی از وسط توکن «up» شروع می‌شود و رد می‌شود.
 */
class KeywordMatcher(private val rules: List<KeywordRule>) {

    /** forHost=true: قاعده‌های برند (b:) نادیده گرفته می‌شوند. */
    fun matches(text: String, forHost: Boolean = false): Boolean {
        if (rules.isEmpty()) return false
        val tokens = TextNorm.tokenize(text)
        if (tokens.isEmpty()) return false
        if (scan(tokens, false, forHost)) return true
        val collapsed = tokens.map { TextNorm.collapse(it) }
        return collapsed != tokens && scan(collapsed, true, forHost)
    }

    private fun scan(tokens: List<String>, collapsed: Boolean, forHost: Boolean): Boolean {
        val n = tokens.size
        val starts = IntArray(n)
        val ends = IntArray(n)
        val sb = StringBuilder()
        for (i in 0 until n) {
            starts[i] = sb.length
            sb.append(tokens[i])
            ends[i] = sb.length
        }
        val joined = sb.toString()
        val tokOf = IntArray(joined.length)
        for (i in 0 until n) {
            for (p in starts[i] until ends[i]) tokOf[p] = i
        }

        for (r in rules) {
            if (forHost && !r.appliesToHosts) continue
            val key = if (collapsed) r.keyCollapsed else r.key
            if (key.isEmpty()) continue
            var idx = joined.indexOf(key)
            while (idx >= 0) {
                if (accepted(r.mode, idx, idx + key.length, starts, ends, tokOf)) return true
                idx = joined.indexOf(key, idx + 1)
            }
            if (r.fuzzy && fuzzyWindow(tokens, key)) return true
        }
        return false
    }

    private fun accepted(
        mode: MatchMode, s: Int, e: Int,
        starts: IntArray, ends: IntArray, tokOf: IntArray,
    ): Boolean {
        val ts = tokOf[s]
        val te = tokOf[e - 1]
        val startAligned = s == starts[ts]
        val endAligned = e == ends[te]
        if (ts == te) {
            return when (mode) {
                MatchMode.ALIGNED -> startAligned && endAligned
                MatchMode.PREFIX -> startAligned
                MatchMode.CONTAINS -> true
            }
        }
        if (!startAligned || !endAligned) return false
        // «x videos» (ویدیوهای X) نباید با «xvideos» یکی شود: اگر تکه‌ی تک‌حرفی داریم،
        // باید حداقل ۳ تکه باشد (p o r n، L-e_xi lune) تا جداکننده‌گذاری عمدی حساب شود.
        if (te - ts + 1 < 3) {
            for (t in ts..te) {
                if (ends[t] - starts[t] == 1) return false
            }
        }
        return true
    }

    /**
     * یک غلط‌املایی (L-e_xi lne). فقط در دو حالت:
     *  - یک توکن تنها، فقط جایگزینی/افزودن (نه حذف)، تا «hamster» با «xhamster» اشتباه نشود؛
     *  - پنجره‌ی ۳ توکن یا بیشتر (جداکننده‌گذاری عمدی). پنجره‌ی ۲ توکنی فقط تطبیق دقیق دارد،
     *    وگرنه «a hamster» با «xhamster» یکی می‌شد.
     */
    private fun fuzzyWindow(tokens: List<String>, key: String): Boolean {
        for (i in tokens.indices) {
            val w = StringBuilder()
            var j = i
            while (j < tokens.size && j - i < MAX_SPAN) {
                w.append(tokens[j])
                val len = w.length
                if (len > key.length + 1) break
                val count = j - i + 1
                val lengthOk = when {
                    count == 1 -> len >= key.length
                    count >= 3 -> len >= key.length - 1
                    else -> false
                }
                if (lengthOk && Normalizer.levenshtein(w.toString(), key) <= 1) return true
                j++
            }
        }
        return false
    }

    companion object {
        private const val MAX_SPAN = 8
    }
}
