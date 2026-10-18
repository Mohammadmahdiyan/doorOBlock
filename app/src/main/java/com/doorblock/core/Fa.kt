package com.doorblock.core

/** اعداد فارسی برای متن‌های رابط کاربری (رمزها و آدرس‌ها هیچ‌وقت تبدیل نمی‌شوند). */
object Fa {
    private const val ZERO = '۰'

    fun digits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') ZERO + (c - '0') else c)
    }

    fun num(n: Long): String = digits(n.toString())

    fun num(n: Int): String = num(n.toLong())

    /** mm:ss یا h:mm:ss */
    fun duration(totalSeconds: Long): String {
        val t = if (totalSeconds < 0) 0L else totalSeconds
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        val body = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        return digits(body)
    }
}
