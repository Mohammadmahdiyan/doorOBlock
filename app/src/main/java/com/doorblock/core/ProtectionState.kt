package com.doorblock.core

enum class ProtectionLevel { ACTIVE, LOCKED, INACTIVE }

/**
 * وضعیت نمایشی حفاظت. قانون مهم: اگر سرویس دسترس‌پذیری خاموش است، هیچ‌وقت ACTIVE/LOCKED نشان داده
 * نمی‌شود (حتی اگر از قبل قفلی ثبت شده باشد).
 */
class ProtectionState(val level: ProtectionLevel, val lockSecondsLeft: Long, val error: String) {
    companion object {
        fun compute(
            serviceEnabled: Boolean,
            locked: Boolean,
            lockSecondsLeft: Long,
            lastError: String,
        ): ProtectionState {
            val level = when {
                !serviceEnabled -> ProtectionLevel.INACTIVE
                locked -> ProtectionLevel.LOCKED
                else -> ProtectionLevel.ACTIVE
            }
            val left = if (level == ProtectionLevel.LOCKED && lockSecondsLeft > 0) lockSecondsLeft else 0L
            return ProtectionState(level, left, lastError.trim())
        }
    }
}
