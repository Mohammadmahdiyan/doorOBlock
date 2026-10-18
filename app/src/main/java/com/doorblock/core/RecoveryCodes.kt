package com.doorblock.core

/** اعتبارسنجی رمزهای بازیابی؛ عیناً همان منطق قبلی MainActivity (مقایسه‌ی دقیق، بدون trim). */
object RecoveryCodes {
    fun matches(saved: List<String>, entered: List<String>): Boolean =
        saved.size == entered.size && saved.indices.all { saved[it] == entered[it] }
}
