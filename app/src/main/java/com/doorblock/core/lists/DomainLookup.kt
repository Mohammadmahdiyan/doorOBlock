package com.doorblock.core.lists

/** جست‌وجوی سریع در یک مجموعه‌ی بزرگ دامنه. */
interface DomainLookup {
    /** true اگر خود host یا یکی از دامنه‌های والد آن (مثلا a.b.com -> b.com) در مجموعه باشد. */
    fun containsHostOrParent(host: String): Boolean
}

/** ترکیب چند مجموعه (فهرست پایه + فهرست‌های به‌روزشده) بدون ادغام فیزیکی. */
class CompositeLookup(private val parts: List<DomainLookup>) : DomainLookup {
    override fun containsHostOrParent(host: String): Boolean = parts.any { it.containsHostOrParent(host) }
}
