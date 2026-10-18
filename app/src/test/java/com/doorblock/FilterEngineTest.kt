package com.doorblock

import com.doorblock.core.FilterEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private val e = FilterEngine(
        domains = setOf("gtaall.com", "xnxx.com", "xvideos.com"),
        keywords = listOf("lexilune", "doggystyle", "porn"),
    )

    @Test fun directUrls() {
        assertTrue(e.isBlocked("https://xnxx.com"))
        assertTrue(e.isBlocked("https://xvideos.com"))
        assertTrue(e.isBlocked("xnxx.com"))
        assertTrue(e.isBlocked("https://www.gtaall.com/page?x=1"))
    }

    @Test fun googleSearchForDomain() {
        assertTrue(e.isBlocked("xnxx.com"))
        assertTrue(e.isBlocked("xvideos.com video"))
        assertTrue(e.isBlocked("xnxx"))
        assertTrue(e.isBlocked("https://www.google.com/search?q=xnxx.com"))
    }

    @Test fun forbiddenPhrases() {
        assertTrue(e.isBlocked("lexi lune"))
        assertTrue(e.isBlocked("L-e_xi lne"))
        assertTrue(e.isBlocked("dogg y   style"))
        assertTrue(e.isBlocked("p0rn"))
        assertTrue(e.isBlocked("https://www.google.com/search?q=lexi+lune"))
    }

    @Test fun domainBoundary() {
        assertFalse(e.isBlocked("https://notxnxx.com"))
        assertFalse(e.isBlocked("notxnxx.com"))
        assertFalse(e.isBlocked("https://xnxx.com.example.org"))
        assertTrue(e.isBlocked("https://m.xnxx.com/video"))
        assertTrue(e.isBlocked("https://a.b.xvideos.com"))
    }

    @Test fun urlFormats() {
        assertTrue(e.isBlocked("HTTPS://WWW.XNXX.COM:8443/Some/Path?x=1"))
        assertTrue(e.isBlocked("xnxx.com."))
        assertTrue(e.isBlocked("https://user:pw@xnxx.com/"))
        assertTrue(e.isBlocked("http://XVIDEOS.com:80"))
    }

    @Test fun normalTextIsAllowed() {
        assertFalse(e.isBlocked("https://example.com"))
        assertFalse(e.isBlocked("how to cook rice"))
        assertFalse(e.isBlocked("https://www.google.com/search?q=weather"))
        assertFalse(e.isBlocked(""))
    }

    @Test fun subdomainsAndLookalikes() {
        assertTrue(e.isBlocked("https://sub.xvideos.com"))
        assertFalse(e.isBlocked("https://notxvideos.com"))
        assertFalse(e.isBlocked("https://xvideos.com.example.org"))
    }

    @Test fun invalidOrOddInputNeverCrashes() {
        val odd = listOf(
            "http://", ":::", "%zz", "http://%zz/%zz", "https://", "//", "....", "@", "a@b",
            "x".repeat(5000), "\u200b\u200b", "😀", "http://[::1]:80/", "http://xnxx.com:notaport",
        )
        for (o in odd) e.isBlocked(o) // فقط نباید استثنا بدهد
    }

    @Test fun duplicateDomainsAreHandled() {
        val dup = FilterEngine(setOf("xnxx.com", "XNXX.com", "www.xnxx.com", "xnxx.com."), listOf("porn", "porn"))
        assertTrue(dup.isBlocked("https://xnxx.com"))
        assertTrue(dup.isBlocked("m.xnxx.com"))
        assertFalse(dup.isBlocked("notxnxx.com"))
    }

    @Test fun buildMergesCustomItems() {
        val b = FilterEngine.build(setOf("Example.org", "example.org", "bad phrase", " ", ""))
        assertTrue(b.isBlocked("https://sub.example.org/x"))
        assertTrue(b.isBlocked("bad phrase"))
        assertTrue(b.isBlocked("https://gtaall.com")) // لیست پیش‌فرض حفظ شده
        assertFalse(b.isBlocked("https://notexample.org"))
    }
}
