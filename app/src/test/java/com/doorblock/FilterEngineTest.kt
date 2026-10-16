package com.doorblock

import com.doorblock.core.FilterEngine
import org.junit.Assert.*
import org.junit.Test

class FilterEngineTest {
    private val e = FilterEngine(
        domains = setOf("gtaall.com", "xvideos.com"),
        keywords = listOf("lexilune", "doggystyle", "porn"),
    )

    @Test fun blocksDomainAndSubdomain() {
        assertTrue(e.isBlockedUrl("https://www.gtaall.com/page?x=1"))
        assertTrue(e.isBlockedUrl("m.gtaall.com"))
        assertTrue(e.isBlockedUrl("xvideos.com"))
    }

    @Test fun allowsNormalSites() {
        assertFalse(e.isBlockedUrl("https://example.com"))
        assertFalse(e.isBlockedText("how to cook rice"))
    }

    @Test fun blocksObfuscatedWords() {
        assertTrue(e.isBlockedText("L-e_xi lune"))
        assertTrue(e.isBlockedText("L-e_xi lne"))      // غلط املایی
        assertTrue(e.isBlockedText("dogg y   style"))
        assertTrue(e.isBlockedText("p0rn"))
        assertTrue(e.isBlockedText("pornnn"))
    }
}
