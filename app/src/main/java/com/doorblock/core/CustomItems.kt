package com.doorblock.core

enum class ItemKind { SITE, WORD }

sealed class AddResult {
    class Added(val items: Set<String>) : AddResult()
    object Empty : AddResult()
    object TooLong : AddResult()
    object Duplicate : AddResult()
}

/**
 * منطق خالص مدیریت موارد سفارشی. ذخیره‌سازی همان قبلی است: یک Set<String> به نام custom در Prefs؛
 * نوع هر مورد در لحظه‌ی خواندن از روی همان قاعده‌ای تشخیص داده می‌شود که FilterEngine.build استفاده می‌کند
 * (نقطه دارد و فاصله ندارد = سایت، وگرنه کلمه/عبارت). بنابراین به مهاجرت داده نیازی نیست.
 */
object CustomItems {
    const val MAX_LEN = 200

    /** null یعنی مورد خالی/فقط فاصله (بی‌اثر است و در فهرست‌ها نشان داده نمی‌شود، ولی حذف هم نمی‌شود). */
    fun kindOf(raw: String): ItemKind? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return if (t.contains('.') && t.none { it.isWhitespace() }) ItemKind.SITE else ItemKind.WORD
    }

    /** موارد یک نوع، با ترتیب ثابت و قابل‌پیش‌بینی (الفبایی، بدون حساسیت به حروف). */
    fun itemsOf(all: Set<String>, kind: ItemKind): List<String> =
        all.filter { kindOf(it) == kind }
            .sortedWith(compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it.trim() }.thenBy { it })

    fun count(all: Set<String>, kind: ItemKind): Int = all.count { kindOf(it) == kind }

    /** رفتار قبلی حفظ شده: مقدار trim‌شده ذخیره می‌شود. تکراری (بدون حساسیت به حروف) اضافه نمی‌شود. */
    fun add(all: Set<String>, raw: String): AddResult {
        val t = raw.trim()
        if (t.isEmpty()) return AddResult.Empty
        if (t.length > MAX_LEN) return AddResult.TooLong
        val key = t.lowercase()
        if (all.any { it.trim().lowercase() == key }) return AddResult.Duplicate
        return AddResult.Added(all + t)
    }

    /** فقط همان یک عنصر دقیق حذف می‌شود؛ بقیه دست‌نخورده می‌مانند. */
    fun remove(all: Set<String>, item: String): Set<String> = all - item
}
