package com.doorblock.core.lists

import java.net.IDN
import java.util.Arrays

/**
 * مجموعه‌ی فشرده‌ی دامنه‌ها: فقط یک آرایه‌ی مرتب از هش ۶۴بیتی (۸ بایت برای هر دامنه).
 * ۵۰۰ هزار دامنه ≈ ۴ مگابایت، جست‌وجو با binary search.
 * (به‌جای HashSet<String> که برای همان تعداد ده‌ها مگابایت حافظه می‌گیرد.)
 */
class DomainSet private constructor(private val hashes: LongArray, val stamp: Long) : DomainLookup {

    val size: Int get() = hashes.size

    fun toHashArray(): LongArray = hashes.copyOf()

    override fun containsHostOrParent(host: String): Boolean {
        if (hashes.isEmpty()) return false
        var h = toAscii(host) ?: return false
        if (h.length > 253) return false
        while (true) {
            val dot = h.indexOf('.')
            if (dot < 0) return false
            if (Arrays.binarySearch(hashes, hash(h)) >= 0) return true
            h = h.substring(dot + 1)
        }
    }

    /** ساخت مجموعه از دامنه‌های معتبر؛ دامنه‌های محافظت‌شده هرگز وارد نمی‌شوند. */
    class Builder(private val protectedDomains: Set<String> = PROTECTED) {
        private var arr = LongArray(1024)
        private var n = 0

        fun add(domain: String) {
            if (domain in protectedDomains) return
            if (n == arr.size) arr = arr.copyOf(n * 2)
            arr[n++] = hash(domain)
        }

        fun build(stamp: Long = 0L): DomainSet {
            val a = arr.copyOf(n)
            a.sort()
            var m = 0
            for (i in a.indices) {
                if (i == 0 || a[i] != a[i - 1]) a[m++] = a[i]
            }
            return DomainSet(a.copyOf(m), stamp)
        }
    }

    companion object {
        private const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
        private const val FNV_PRIME = 1099511628211L          // 0x100000001b3

        /** سرویس‌های اساسی و سایت قرآن؛ حتی اگر اشتباها در فهرستی بیایند، مسدود نمی‌شوند. */
        val PROTECTED: Set<String> = setOf(
            "quran.com", "google.com", "bing.com", "duckduckgo.com", "yahoo.com",
            "wikipedia.org", "github.com", "githubusercontent.com", "github.io",
        )

        fun hash(s: String): Long {
            var h = FNV_OFFSET
            for (ch in s) {
                h = (h xor ch.code.toLong()) * FNV_PRIME
            }
            return h
        }

        /** آرایه‌ی هش مرتب و بدون تکرار (برای خواندن از فایل). */
        fun fromSortedUnique(hashes: LongArray, stamp: Long): DomainSet = DomainSet(hashes, stamp)

        private fun toAscii(host: String): String? {
            val h = host.lowercase()
            if (h.all { it.code < 128 }) return h
            return try { IDN.toASCII(h) } catch (e: IllegalArgumentException) { null }
        }
    }
}
