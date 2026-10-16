package com.doorblock.core

/** جلوگیری از تکرار یک اقدام (مثل Home یا ریدایرکت) در فاصله‌ی کوتاه. */
class ActionGate {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun tryAcquire(key: String, now: Long, intervalMs: Long): Boolean {
        val prev = last[key]
        if (prev != null && now - prev < intervalMs) return false
        last[key] = now
        return true
    }
}
