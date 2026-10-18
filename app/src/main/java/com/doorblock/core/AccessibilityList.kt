package com.doorblock.core

/** تجزیه‌ی مقدار ENABLED_ACCESSIBILITY_SERVICES (رشته‌ی جداشده با دونقطه). */
object AccessibilityList {
    fun containsService(enabled: String?, pkg: String, cls: String): Boolean {
        if (enabled.isNullOrBlank()) return false
        return enabled.split(':').any { entry ->
            val slash = entry.indexOf('/')
            if (slash <= 0) {
                false
            } else {
                val p = entry.substring(0, slash).trim()
                var c = entry.substring(slash + 1).trim()
                if (c.startsWith(".")) c = p + c
                p == pkg && c == cls
            }
        }
    }
}
