package com.doorblock.service

import android.content.Context
import android.provider.Settings
import com.doorblock.core.AccessibilityList

object ServiceStatus {
    /** آیا سرویس دسترس‌پذیری برنامه الان در تنظیمات اندروید روشن است؟ */
    fun isEnabled(ctx: Context): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val on = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            val list = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            on && AccessibilityList.containsService(
                list, ctx.packageName, GuardAccessibilityService::class.java.name,
            )
        } catch (e: Exception) {
            false
        }
    }
}
