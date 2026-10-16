package com.doorblock.core

import java.text.Normalizer as JNormalizer

/** متن را ساده می‌کند تا دورزدن‌هایی مثل  L-e_xi lne  بی‌اثر شود. */
object Normalizer {
    private val leet = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a',
        '5' to 's', '@' to 'a', '$' to 's', '!' to 'i',
    )

    fun normalize(input: String): String {
        val text = JNormalizer.normalize(input, JNormalizer.Form.NFKD).lowercase()
        val sb = StringBuilder()
        for (ch in text) {
            val c = when (ch) {
                'ي' -> 'ی'
                'ك' -> 'ک'
                else -> leet[ch] ?: ch
            }
            val isLatin = c in 'a'..'z'
            val isPersian = c in '\u0600'..'\u06FF' && c !in '\u064B'..'\u065F'
            if (isLatin || isPersian) sb.append(c)
        }
        return sb.toString()
    }

    /** حروف تکراری پشت‌سرهم را یکی می‌کند: pornnn -> porn */
    fun collapse(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
