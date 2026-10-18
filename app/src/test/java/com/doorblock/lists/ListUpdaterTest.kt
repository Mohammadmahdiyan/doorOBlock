package com.doorblock.lists

import com.doorblock.core.lists.FetchResponse
import com.doorblock.core.lists.Fetcher
import com.doorblock.core.lists.ListStore
import com.doorblock.core.lists.ListUpdater
import com.doorblock.core.lists.Outcome
import com.doorblock.core.lists.SourceSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ListUpdaterTest {
    private class FakeFetcher(private val responder: (String?) -> FetchResponse?) : Fetcher {
        var calls = 0
        var lastEtag: String? = null
        override fun <T> fetch(url: String, etag: String?, handler: (FetchResponse) -> T): T {
            calls++
            lastEtag = etag
            val r = responder(etag) ?: throw IOException("offline")
            return handler(r)
        }
    }

    private val day = 24L * 60 * 60 * 1000
    private val src = listOf(SourceSpec("a", "https://x.test/a"))

    private fun store(): ListStore = ListStore(Files.createTempDirectory("dbu").toFile())

    private fun body(n: Int, prefix: String = "s") =
        (0 until n).joinToString("\n") { "0.0.0.0 $prefix$it.example.net" }

    private fun ok(text: String, etag: String? = "v1") =
        FetchResponse(200, etag, ByteArrayInputStream(text.toByteArray()))

    private fun updater(st: ListStore, f: Fetcher) = ListUpdater(st, f, src, minEntries = 5)

    @Test fun successStoresValidatedList() {
        val st = store()
        val r = updater(st, FakeFetcher { ok(body(20)) }).update(1000)
        assertEquals(Outcome.UPDATED, r.results[0].outcome)
        assertTrue(r.anyUpdated)
        assertEquals(20, st.readSource("a")?.size)
    }

    @Test fun networkFailureKeepsPreviousList() {
        val st = store()
        updater(st, FakeFetcher { ok(body(20)) }).update(1000)
        val r = updater(st, FakeFetcher { null }).update(2000)
        assertEquals(Outcome.FAILED, r.results[0].outcome)
        assertEquals(20, st.readSource("a")?.size)
    }

    @Test fun httpErrorKeepsPreviousList() {
        val st = store()
        updater(st, FakeFetcher { ok(body(20)) }).update(1000)
        val r = updater(st, FakeFetcher { FetchResponse(500, null, null) }).update(2000)
        assertEquals(Outcome.FAILED, r.results[0].outcome)
        assertEquals(20, st.readSource("a")?.size)
    }

    @Test fun htmlOrGarbageBodyIsRejected() {
        val st = store()
        updater(st, FakeFetcher { ok(body(20)) }).update(1000)
        val html = "<html><body>Rate limited</body></html>\n".repeat(30)
        val r = updater(st, FakeFetcher { ok(html) }).update(2000)
        assertEquals(Outcome.REJECTED, r.results[0].outcome)
        assertEquals(20, st.readSource("a")?.size)
    }

    @Test fun tooFewEntriesIsRejectedOnFirstDownload() {
        val st = store()
        val r = updater(st, FakeFetcher { ok(body(2)) }).update(1000)
        assertEquals(Outcome.REJECTED, r.results[0].outcome)
        assertNull(st.readSource("a"))
    }

    @Test fun listThatShrinksByMoreThanHalfIsRejected() {
        val st = store()
        updater(st, FakeFetcher { ok(body(100)) }).update(1000)
        val r = updater(st, FakeFetcher { ok(body(20)) }).update(2000)
        assertEquals(Outcome.REJECTED, r.results[0].outcome)
        assertEquals(100, st.readSource("a")?.size)
    }

    @Test fun etagAvoidsRedownload() {
        val st = store()
        updater(st, FakeFetcher { ok(body(20), etag = "v1") }).update(1000)
        val f = FakeFetcher { etag -> if (etag == "v1") FetchResponse(304, null, null) else ok(body(20)) }
        val r = updater(st, f).update(2000)
        assertEquals("v1", f.lastEtag)
        assertEquals(Outcome.NOT_MODIFIED, r.results[0].outcome)
        assertFalse(r.anyUpdated)
        assertEquals(20, st.readSource("a")?.size)
    }

    @Test fun updateIsNotRepeatedBeforeItIsDue() {
        val st = store()
        val f = FakeFetcher { ok(body(20)) }
        val u = updater(st, f)
        assertFalse(u.updateIfDue(1000).skipped)
        assertEquals(1, f.calls)
        assertTrue(u.updateIfDue(1000 + 6 * day).skipped)   // کمتر از هفت روز
        assertEquals(1, f.calls)
        assertFalse(u.updateIfDue(1000 + 8 * day).skipped)
        assertEquals(2, f.calls)
    }

    @Test fun afterFailureRetriesNextDayNotNextWeek() {
        val st = store()
        val f = FakeFetcher { null }
        val u = updater(st, f)
        u.updateIfDue(1000)
        assertTrue(u.updateIfDue(1000 + day / 2).skipped)
        assertFalse(u.updateIfDue(1000 + day + 1).skipped)
        assertEquals(2, f.calls)
    }

    @Test fun updatedListsAreUsedAfterReload() {
        val dir: File = Files.createTempDirectory("dbu").toFile()
        updater(ListStore(dir), FakeFetcher { ok(body(20, "fresh")) }).update(1000)
        val reopened = ListStore(dir).readSources()
        assertEquals(1, reopened.size)
        assertTrue(reopened[0].containsHostOrParent("x.fresh3.example.net"))
    }

    @Test fun emptySourceListDoesNotCrash() {
        val r = ListUpdater(store(), FakeFetcher { null }, emptyList()).update(1000)
        assertEquals(0, r.results.size)
    }
}
