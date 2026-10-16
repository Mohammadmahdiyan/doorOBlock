package com.doorblock.core

/**
 * لیست پایه. بعداً لیست‌های بزرگ متن‌باز (مثل StevenBlack) را هم اینجا وارد می‌کنیم.
 * دامنه‌ها بدون www نوشته شوند.
 */
object DefaultLists {
    val domains: Set<String> = setOf(
        "gtaall.com",   // لیست شخصی (برای تست)
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
    )

    // کلمه‌ها به شکل ساده (فقط حروف کوچک، بدون فاصله و علامت)
    val keywords: List<String> = listOf(
        "porn",
        "lexilune",
        "doggystyle",
    )
}
