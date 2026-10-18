package com.doorblock.core

import java.text.Normalizer as JNormalizer

/**
 * متن را به «توکن» تبدیل می‌کند (حروف/ارقام پشت‌سرهم).
 * حروف بزرگ، اعراب، شکل‌های ي/ك، نویسه‌های نامرئی و leet (p0rn، p@rn) یکسان‌سازی می‌شوند.
 * برخلاف حذف همه‌ی جداکننده‌ها، مرز کلمه‌ها حفظ می‌شود تا «up or not» با «porn» اشتباه نشود.
 */
object TextNorm {
    const val MAX_LEN = 300
    private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")
    private val MARKS = Regex("\\p{Mn}+")

    fun tokenize(input: String): List<String> {
        var s = INVISIBLE.replace(input.take(MAX_LEN), "")
        s = JNormalizer.normalize(s, JNormalizer.Form.NFKD)
        s = MARKS.replace(s, "").lowercase()

        // @ $ ! بین دو حرف/رقم، جایگزین حرف می‌شود (p@rn)؛ در غیر این‌صورت جداکننده است
        val sb = StringBuilder(s.length)
        for (i in s.indices) {
            var c = s[i]
            if (c == 'ي') c = 'ی' else if (c == 'ك') c = 'ک'
            if (c == 'ـ') continue
            if ((c == '@' || c == '$' || c == '!') && i > 0 && i < s.length - 1 &&
                s[i - 1].isLetterOrDigit() && s[i + 1].isLetterOrDigit()
            ) {
                c = when (c) { '@' -> 'a'; '$' -> 's'; else -> 'i' }
            }
            sb.append(c)
        }

        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isEmpty()) return
            out.add(leet(cur.toString()))
            cur.setLength(0)
        }
        for (ch in sb) {
            if (ch.isLetterOrDigit()) cur.append(ch) else flush()
        }
        flush()
        return out
    }

    /** ارقام جایگزین حرف فقط در توکنی که حرف هم دارد (p0rn)، نه در عددهای خالص. */
    private fun leet(token: String): String {
        if (token.none { it.isLetter() }) return token
        val sb = StringBuilder(token.length)
        for (c in token) {
            sb.append(
                when (c) { '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; else -> c },
            )
        }
        return sb.toString()
    }

    /** حروف تکراری پشت‌سرهم را یکی می‌کند: pornnn -> porn */
    fun collapse(s: String): String = Normalizer.collapse(s)
}
