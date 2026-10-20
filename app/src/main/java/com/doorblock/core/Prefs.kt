
package com.doorblock.core

import android.content.Context
import java.security.SecureRandom

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("doorblock", Context.MODE_PRIVATE)

    var startMillis: Long
        get() {
            val v = sp.getLong("start", 0L)
            if (v != 0L) return v
            val now = System.currentTimeMillis()
            sp.edit().putLong("start", now).apply()
            return now
        }
        set(v) {
            sp.edit().putLong("start", v).apply()
        }

    var lockUntil: Long
        get() = sp.getLong("lockUntil", 0L)
        set(v) {
            sp.edit().putLong("lockUntil", v).apply()
        }

    var custom: Set<String>
        get() = sp.getStringSet("custom", emptySet()) ?: emptySet()
        set(v) {
            sp.edit().putStringSet("custom", v.toSet()).apply()
        }

    /**
     * Five random 50-character codes.
     * Generated once and kept in app preferences.
     */
    val recoveryCodes: List<String>
        get() {
            val saved = (0 until CODE_COUNT).mapNotNull { index ->
                sp.getString("recovery_code_$index", null)
            }

            if (saved.size == CODE_COUNT && saved.all { it.length == CODE_LENGTH }) {
                return saved
            }

            val generated = List(CODE_COUNT) { generateCode() }
            sp.edit().apply {
                generated.forEachIndexed { index, code ->
                    putString("recovery_code_$index", code)
                }
            }.commit()

            return generated
        }

    /** آخرین خطای سرویس (برای عیب‌یابی؛ در صفحه‌ی اصلی نشان داده می‌شود). */
    var lastError: String
        get() = sp.getString("last_error", "") ?: ""
        set(v) {
            sp.edit().putString("last_error", v).apply()
        }

    /**
     * رمز تصادفی ۱۰۰ نویسه‌ای برای مشاهده‌ی موارد سفارشی؛ یک‌بار ساخته و ذخیره می‌شود
     * (مثل رمزهای بازیابی). رمزهای بازیابی دست‌نخورده‌اند.
     */
    val viewSecret: String
        get() {
            val saved = sp.getString("view_secret", null)
            if (saved != null && SecretCode.isValid(saved)) return saved
            val generated = SecretCode.generate()
            sp.edit().putString("view_secret", generated).commit()
            return generated
        }

    fun isLocked(now: Long = System.currentTimeMillis()) = now < lockUntil

    fun lock(now: Long = System.currentTimeMillis()) {
        lockUntil = now + Config.LOCK_SECONDS * 1000
    }

    private fun generateCode(): String {
        val chars =
            "abcdefghijklmnopqrstuvwxyz" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "0123456789" +
            "!@#$%^&*()_- =+[]{}\\/?<>,.;:'\"".replace(" ", "")

        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    companion object {
        private const val CODE_COUNT = 5
        private const val CODE_LENGTH = 50
        private val random = SecureRandom()
    }
}
