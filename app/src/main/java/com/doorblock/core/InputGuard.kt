package com.doorblock.core

/**
 * قاعده‌ی تشخیص «Paste از طریق صفحه‌کلید». تایپ دستی حرف‌به‌حرف است؛ اگر صفحه‌کلید یک‌باره چند
 * حرف وارد کند (مثلا نوار کلیپ‌بورد Gboard)، رد می‌شود. تلاش بهینه است، نه تضمین امنیتی.
 */
object InputGuard {
    const val MAX_IME_CHUNK = 3

    fun isSuspiciousCommit(text: CharSequence?): Boolean = text != null && text.length > MAX_IME_CHUNK
}
