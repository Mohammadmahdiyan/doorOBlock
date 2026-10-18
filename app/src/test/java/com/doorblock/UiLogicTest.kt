package com.doorblock

import com.doorblock.core.Fa
import com.doorblock.core.InputGuard
import com.doorblock.core.ProtectionLevel
import com.doorblock.core.ProtectionState
import com.doorblock.core.RecoveryCodes
import com.doorblock.core.UiSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    private val codes = List(5) { i -> "code$i" + "x".repeat(45) }

    @Test fun customItemTextSizeIsExactlyTwoPixels() {
        assertEquals(2f, UiSpec.CUSTOM_ITEM_TEXT_PX, 0f)
    }

    @Test fun recoveryValidationIsUnchanged() {
        assertTrue(RecoveryCodes.matches(codes, codes.toList()))
        assertFalse(RecoveryCodes.matches(codes, codes.dropLast(1)))
        assertFalse(RecoveryCodes.matches(codes, codes.map { it + " " }))      // بدون trim
        assertFalse(RecoveryCodes.matches(codes, codes.map { it.uppercase() }))
        assertFalse(RecoveryCodes.matches(codes, codes.take(4) + ""))
        assertFalse(RecoveryCodes.matches(codes, codes.reversed()))
        assertFalse(RecoveryCodes.matches(codes, List(5) { "" }))
    }

    @Test fun typedCharactersPassButBulkInsertIsRejected() {
        for (ch in "aZ9!@#\\/<>'\"") assertFalse(InputGuard.isSuspiciousCommit(ch.toString()))
        assertFalse(InputGuard.isSuspiciousCommit(null))
        assertFalse(InputGuard.isSuspiciousCommit("abc"))
        assertTrue(InputGuard.isSuspiciousCommit("abcd"))
        assertTrue(InputGuard.isSuspiciousCommit(codes[0]))       // چسباندن یک رمز کامل
    }

    @Test fun inactiveServiceNeverLooksProtected() {
        for (locked in listOf(true, false)) {
            val s = ProtectionState.compute(false, locked, 500, "")
            assertEquals(ProtectionLevel.INACTIVE, s.level)
            assertEquals(0L, s.lockSecondsLeft)
        }
    }

    @Test fun activeAndLockedStates() {
        assertEquals(ProtectionLevel.ACTIVE, ProtectionState.compute(true, false, 0, "").level)
        val l = ProtectionState.compute(true, true, 90, "")
        assertEquals(ProtectionLevel.LOCKED, l.level)
        assertEquals(90L, l.lockSecondsLeft)
        assertEquals(0L, ProtectionState.compute(true, true, -5, "").lockSecondsLeft)
    }

    @Test fun errorIsShownRegardlessOfLevel() {
        assertEquals("boom", ProtectionState.compute(true, false, 0, "  boom ").error)
        assertEquals("boom", ProtectionState.compute(false, false, 0, "boom").error)
        assertEquals("", ProtectionState.compute(true, false, 0, "   ").error)
    }

    @Test fun persianDigitsAndDurations() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", Fa.digits("1234567890"))
        assertEquals("abc ۱", Fa.digits("abc 1"))
        assertEquals("۳۶۵", Fa.num(365))
        assertEquals("۰۰:۰۰", Fa.duration(0))
        assertEquals("۰۰:۰۰", Fa.duration(-3))
        assertEquals("۲۹:۵۹", Fa.duration(1799))
        assertEquals("۱:۰۱:۰۱", Fa.duration(3661))
    }
}
