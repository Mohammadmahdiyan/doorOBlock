package com.doorblock

import com.doorblock.core.SecretCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SecretCodeTest {
    @Test fun generatedSecretIsHundredCharsFromTheAllowedSet() {
        repeat(200) {
            val s = SecretCode.generate()
            assertEquals(100, s.length)
            assertTrue(s.all { it in SecretCode.ALL })
            assertTrue(SecretCode.isValid(s))
        }
    }

    @Test fun everyGeneratedSecretHasAllFourClasses() {
        repeat(200) {
            val s = SecretCode.generate()
            assertTrue(s.any { it in 'a'..'z' })
            assertTrue(s.any { it in 'A'..'Z' })
            assertTrue(s.any { it in '0'..'9' })
            assertTrue(s.any { it in SecretCode.SYMBOLS })
        }
    }

    @Test fun charsetContainsTheRequestedSymbols() {
        for (c in "@&/\$(-][}{=") assertTrue("missing $c", c in SecretCode.ALL)
        assertTrue(SecretCode.ALL.none { it.isWhitespace() })
    }

    @Test fun secretsAreRandom() {
        val set = HashSet<String>()
        repeat(100) { set.add(SecretCode.generate()) }
        assertEquals(100, set.size)
    }

    @Test fun seededRandomIsDeterministic() {
        assertEquals(SecretCode.generate(Random(42)), SecretCode.generate(Random(42)))
        assertNotEquals(SecretCode.generate(Random(1)), SecretCode.generate(Random(2)))
    }

    @Test fun validationRejectsBadValues() {
        assertFalse(SecretCode.isValid(null))
        assertFalse(SecretCode.isValid(""))
        assertFalse(SecretCode.isValid("a".repeat(99)))
        assertFalse(SecretCode.isValid("a".repeat(101)))
        assertFalse(SecretCode.isValid("a".repeat(99) + " "))
        assertTrue(SecretCode.isValid("a".repeat(100)))
    }

    @Test fun matchingIsExact() {
        val s = SecretCode.generate()
        assertTrue(SecretCode.matches(s, s))
        assertTrue(SecretCode.matches(s, String(s.toCharArray())))
        assertFalse(SecretCode.matches(s, s + " "))
        assertFalse(SecretCode.matches(s, " $s"))
        assertFalse(SecretCode.matches(s, s.dropLast(1)))
        assertFalse(SecretCode.matches(s, s.lowercase()) && s != s.lowercase())
        assertFalse(SecretCode.matches(s, ""))
        val flipped = (if (s[50] == 'a') 'b' else 'a').let { s.substring(0, 50) + it + s.substring(51) }
        assertFalse(SecretCode.matches(s, flipped))
    }

    @Test fun displayChunksRebuildTheSameSecret() {
        val s = SecretCode.generate()
        val chunks = SecretCode.chunks(s)
        assertEquals(5, chunks.size)
        assertTrue(chunks.all { it.length == 20 })
        assertEquals(s, chunks.joinToString(""))
    }
}
