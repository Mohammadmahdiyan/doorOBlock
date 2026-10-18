package com.doorblock

import com.doorblock.core.AccessibilityList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityListTest {
    private val pkg = "com.doorblock"
    private val cls = "com.doorblock.service.GuardAccessibilityService"

    @Test fun serviceDisabledWhenListIsEmptyOrNull() {
        assertFalse(AccessibilityList.containsService(null, pkg, cls))
        assertFalse(AccessibilityList.containsService("", pkg, cls))
        assertFalse(AccessibilityList.containsService("   ", pkg, cls))
    }

    @Test fun detectsFullAndShortForms() {
        assertTrue(AccessibilityList.containsService("$pkg/$cls", pkg, cls))
        assertTrue(AccessibilityList.containsService("$pkg/.service.GuardAccessibilityService", pkg, cls))
        assertTrue(AccessibilityList.containsService("a.b/c.D:$pkg/$cls:x.y/z.W", pkg, cls))
    }

    @Test fun ignoresOtherServicesAndGarbage() {
        assertFalse(AccessibilityList.containsService("other.app/other.Service", pkg, cls))
        assertFalse(AccessibilityList.containsService("com.doorblock/com.doorblock.Other", pkg, cls))
        assertFalse(AccessibilityList.containsService("garbage", pkg, cls))
        assertFalse(AccessibilityList.containsService(":::", pkg, cls))
    }
}
