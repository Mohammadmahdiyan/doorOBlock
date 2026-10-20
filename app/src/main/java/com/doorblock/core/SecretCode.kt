package com.doorblock.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Random

/**
 * رمز تصادفی ۱۰۰ نویسه‌ای (حروف کوچک و بزرگ انگلیسی، ارقام و نمادها) برای مشاهده‌ی موارد سفارشی.
 * با SecureRandom ساخته می‌شود و حتما از هر دسته حداقل یک نویسه دارد.
 */
object SecretCode {
    const val LENGTH = 100
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "@&/\$()-[]{}=+!#%*_.,;:?"
    const val ALL = LOWER + UPPER + DIGITS + SYMBOLS

    fun generate(random: Random = SecureRandom()): String {
        while (true) {
            val sb = StringBuilder(LENGTH)
            repeat(LENGTH) { sb.append(ALL[random.nextInt(ALL.length)]) }
            val s = sb.toString()
            if (hasAllClasses(s)) return s
        }
    }

    fun hasAllClasses(s: String): Boolean =
        s.any { it in LOWER } && s.any { it in UPPER } && s.any { it in DIGITS } && s.any { it in SYMBOLS }

    fun isValid(s: String?): Boolean = s != null && s.length == LENGTH && s.all { it in ALL }

    /** مقایسه‌ی دقیق (بدون trim) و با زمان ثابت. */
    fun matches(saved: String, entered: String): Boolean =
        MessageDigest.isEqual(saved.toByteArray(Charsets.UTF_8), entered.toByteArray(Charsets.UTF_8))

    /** برای نمایش خواناتر: خط‌های ۲۰ نویسه‌ای (خط‌شکن جزو رمز نیست). */
    fun chunks(s: String, size: Int = 20): List<String> = s.chunked(size)
}
