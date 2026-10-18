package com.doorblock.lists

import com.doorblock.core.lists.DomainSet
import com.doorblock.core.lists.DomainSetCodec
import com.doorblock.core.lists.ListStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ListStoreTest {
    private fun tmp(): File = Files.createTempDirectory("dbl").toFile()

    private fun bytes(n: Int, prefix: String = "s"): ByteArray {
        val b = DomainSet.Builder()
        for (i in 0 until n) b.add("$prefix$i.example.net")
        return DomainSetCodec.encode(b.build())
    }

    @Test fun missingSourceIsNull() {
        assertNull(ListStore(tmp()).readSource("blp_porn"))
    }

    @Test fun writeThenRead() {
        val st = ListStore(tmp())
        st.writeSource("a", bytes(10))
        assertEquals(10, st.readSource("a")?.size)
        assertEquals(1, st.readSources().size)
    }

    @Test fun invalidBytesNeverReplaceTheOldList() {
        val st = ListStore(tmp())
        st.writeSource("a", bytes(10))
        var failed = false
        try {
            st.writeSource("a", "<html>oops</html>".toByteArray())
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(10, st.readSource("a")?.size) // قبلی سالم مانده
    }

    @Test fun corruptedFileOnDiskIsIgnoredNotFatal() {
        val dir = tmp()
        val st = ListStore(dir)
        st.writeSource("a", bytes(10))
        File(dir, "src_a.bin").writeBytes(ByteArray(100) { 1 })
        assertNull(st.readSource("a"))
        assertEquals(0, st.readSources().size)
    }

    @Test fun staleTmpFilesAreCleaned() {
        val dir = tmp()
        File(dir, "src_a.bin.tmp").writeText("half written")
        ListStore(dir)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun stateRoundTripAndBadStateIsHarmless() {
        val dir = tmp()
        val st = ListStore(dir)
        st.setState(mapOf("k" to "v", "n" to "42"))
        assertEquals("v", st.getString("k"))
        assertEquals(42L, st.getLong("n"))
        File(dir, "state.properties").writeBytes(byteArrayOf(0x5c, 0x75, 0x00, 0x7f)) // خراب
        assertNull(st.getString("k")) // state خراب: نباید استثنا بدهد، فقط خالی است
        st.setState(mapOf("k" to "again"))
        assertEquals("again", st.getString("k"))
    }

    @Test fun badSourceIdIsRejected() {
        var failed = false
        try { ListStore(tmp()).readSource("../evil") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
