package com.doorblock.lists

import com.doorblock.core.lists.DomainSet
import com.doorblock.core.lists.DomainSetCodec
import com.doorblock.core.lists.InvalidListException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainSetCodecTest {
    private fun sample(n: Int): DomainSet {
        val b = DomainSet.Builder()
        for (i in 0 until n) b.add("site$i.example.net")
        return b.build(stamp = 12345L)
    }

    @Test fun roundTripPreservesEverything() {
        val s = sample(500)
        val back = DomainSetCodec.decode(DomainSetCodec.encode(s))
        assertEquals(500, back.size)
        assertEquals(12345L, back.stamp)
        assertTrue(back.containsHostOrParent("sub.site77.example.net"))
    }

    @Test fun emptySetRoundTrips() {
        val back = DomainSetCodec.decode(DomainSetCodec.encode(DomainSet.Builder().build()))
        assertEquals(0, back.size)
    }

    @Test fun corruptedByteIsRejected() {
        val bytes = DomainSetCodec.encode(sample(50))
        bytes[40] = (bytes[40].toInt() xor 0x01).toByte()
        assertThrows(InvalidListException::class.java) { DomainSetCodec.decode(bytes) }
    }

    @Test fun truncatedFileIsRejected() {
        val bytes = DomainSetCodec.encode(sample(50))
        assertThrows(InvalidListException::class.java) { DomainSetCodec.decode(bytes.copyOf(bytes.size - 5)) }
        assertThrows(InvalidListException::class.java) { DomainSetCodec.decode(ByteArray(3)) }
    }

    @Test fun wrongMagicAndGarbageAreRejected() {
        assertThrows(InvalidListException::class.java) { DomainSetCodec.decode(ByteArray(200) { 7 }) }
        assertThrows(InvalidListException::class.java) { DomainSetCodec.decode("<html>404</html>".toByteArray()) }
    }
}
