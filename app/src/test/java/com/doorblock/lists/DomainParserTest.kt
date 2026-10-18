package com.doorblock.lists

import com.doorblock.core.lists.DomainParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainParserTest {
    @Test fun parsesCommonFormats() {
        assertEquals("example.com", DomainParser.parseLine("0.0.0.0 Example.COM"))
        assertEquals("example.com", DomainParser.parseLine("127.0.0.1 www.example.com # note"))
        assertEquals("example.com", DomainParser.parseLine("example.com"))
        assertEquals("sub.example.com", DomainParser.parseLine("||sub.example.com^"))
        assertEquals("example.com", DomainParser.parseLine("example.com."))
    }

    @Test fun rejectsInvalidInput() {
        val bad = listOf(
            "", "# comment", "! comment", "localhost", "127.0.0.1 localhost", "0.0.0.0 0.0.0.0",
            "a b c", "http://x.com/p", "-bad-.com", "bad_domain.com", "foo.local", "*.example.com",
            "a@b.com", "x".repeat(300) + ".com",
        )
        for (b in bad) assertNull(b, DomainParser.parseLine(b))
    }

    @Test fun ignorableLinesAreNotCountedAsInvalid() {
        assertTrue(DomainParser.isIgnorable(""))
        assertTrue(DomainParser.isIgnorable("  # c"))
        assertTrue(DomainParser.isIgnorable("! c"))
        assertFalse(DomainParser.isIgnorable("junk line"))
    }
}
