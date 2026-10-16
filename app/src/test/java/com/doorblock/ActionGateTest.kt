package com.doorblock

import com.doorblock.core.ActionGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGateTest {
    @Test fun repeatedEventsAreBlockedInsideInterval() {
        val g = ActionGate()
        assertTrue(g.tryAcquire("violation", 1000, 2000))
        repeat(50) { assertFalse(g.tryAcquire("violation", 1000L + it * 10, 2000)) }
        assertTrue(g.tryAcquire("violation", 3100, 2000))
    }

    @Test fun keysAreIndependent() {
        val g = ActionGate()
        assertTrue(g.tryAcquire("a", 0, 1000))
        assertTrue(g.tryAcquire("b", 0, 1000))
    }
}
