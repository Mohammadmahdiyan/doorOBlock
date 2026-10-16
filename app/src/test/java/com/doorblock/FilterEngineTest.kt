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
}
