package com.doorblock.core

data class Streak(val days: Long, val hours: Int, val minutes: Int, val seconds: Int)

/** فقط زمان شروع ذخیره می‌شود؛ بقیه از اختلاف حساب می‌شود. */
object StreakTimer {
    fun compute(startMillis: Long, nowMillis: Long): Streak {
        val total = maxOf(0L, (nowMillis - startMillis) / 1000)
        return Streak(
            days = total / 86400,
            hours = ((total % 86400) / 3600).toInt(),
            minutes = ((total % 3600) / 60).toInt(),
            seconds = (total % 60).toInt(),
        )
    }
}
