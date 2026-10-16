package com.doorblock.core

import com.doorblock.BuildConfig

object Config {
    /** حالت دیباگ: ۱۰ ثانیه. نسخه نهایی: ۳۰ دقیقه. */
    val LOCK_SECONDS: Long = if (BuildConfig.DEBUG) 10L else 30L * 60L

    const val REDIRECT_URL = "https://quran.com"
    const val REDIRECT_HOST = "quran.com"
    const val GOAL_DAYS = 365
}
