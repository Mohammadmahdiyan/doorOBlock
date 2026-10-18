package com.doorblock.lists

import com.doorblock.core.lists.DomainListIO
import com.doorblock.core.lists.DomainSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainSetTest {
    private fun setOfDomains(vararg d: String): DomainSet {
        val b = DomainSet.Builder()
        d.forEach { b.add(it) }
        return b.build()
    }

    @Test fun matchesDomainAndSubdomains() {
        val s = setOfDomains("xvideos.com")
        assertTrue(s.containsHostOrParent("xvideos.com"))
        assertTrue(s.containsHostOrParent("sub.xvideos.com"))
        assertTrue(s.containsHostOrParent("a.b.c.xvideos.com"))
    }

    @Test fun doesNotMatchLookalikes() {
        val s = setOfDomains("xvideos.com")
        assertFalse(s.containsHostOrParent("notxvideos.com"))
        assertFalse(s.containsHostOrParent("xvideos.com.evil.org"))
        assertFalse(s.containsHostOrParent("com"))
        assertFalse(s.containsHostOrParent("xvideos"))
    }

    @Test fun caseIsIgnored() {
        assertTrue(setOfDomains("xvideos.com").containsHostOrParent("SUB.XVideos.COM"))
    }

    @Test fun emptySetMatchesNothing() {
        val s = DomainSet.Builder().build()
        assertEquals(0, s.size)
        assertFalse(s.containsHostOrParent("xvideos.com"))
        assertFalse(s.containsHostOrParent(""))
    }

    @Test fun duplicatesAreRemoved() {
        val s = setOfDomains("a.example.net", "a.example.net", "b.example.net", "a.example.net")
        assertEquals(2, s.size)
    }

    @Test fun protectedDomainsNeverEnter() {
        val s = setOfDomains("quran.com", "google.com", "bad.example.net")
        assertEquals(1, s.size)
        assertFalse(s.containsHostOrParent("quran.com"))
        assertFalse(s.containsHostOrParent("www.google.com"))
    }

    @Test fun veryLongOrOddHostsDoNotCrash() {
        val s = setOfDomains("a.example.net")
        assertFalse(s.containsHostOrParent("a.".repeat(400) + "net"))
        assertFalse(s.containsHostOrParent("...."))
        assertFalse(s.containsHostOrParent("\u200b"))
    }

    @Test fun parsesTextListWithMixedGarbage() {
        val text = """
            # header
            127.0.0.1 localhost
            0.0.0.0 one.example.net
            0.0.0.0 one.example.net
            two.example.net
            ||three.example.net^
            this is junk
        """.trimIndent()
        val r = DomainListIO.parse(text.byteInputStream())
        assertEquals(3, r.set.size)
        assertEquals(4, r.valid)       // یکی تکراری است
        assertEquals(2, r.invalid)     // localhost و خط خراب
        assertTrue(r.set.containsHostOrParent("three.example.net"))
    }

    @Test fun oversizedInputIsRejected() {
        val big = ("0.0.0.0 a.example.net\n").repeat(100)
        var thrown = false
        try {
            DomainListIO.parse(big.byteInputStream(), maxBytes = 200)
        } catch (e: java.io.IOException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
