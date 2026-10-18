package com.doorblock

import com.doorblock.core.FilterEngine
import com.doorblock.core.lists.CompositeLookup
import com.doorblock.core.lists.DomainSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ادغام فهرست بزرگ با موتور فیلتر؛ منطق دامنه جدا از منطق کلمه. */
class FilterEngineBigListTest {
    private fun big(vararg d: String): DomainSet {
        val b = DomainSet.Builder()
        d.forEach { b.add(it) }
        return b.build()
    }

    private val set = big("adult-site.example.net", "sub.shared.example.org")
    private val e = FilterEngine.build(emptySet(), set)

    @Test fun domainAndSubdomainFromBigList() {
        assertTrue(e.isBlocked("https://adult-site.example.net/x"))
        assertTrue(e.isBlocked("m.adult-site.example.net"))
        assertTrue(e.isBlocked("ADULT-SITE.example.NET:8443/p?q=1"))
        assertTrue(e.isBlocked("sub.shared.example.org"))
    }

    @Test fun lookalikeDomainsAreNotBlocked() {
        assertFalse(e.isBlocked("https://notadult-site.example.net"))
        assertFalse(e.isBlocked("shared.example.org"))
        assertFalse(e.isBlocked("adult-site.example.net.evil.org"))
    }

    @Test fun bigListDomainNamesAreNeverUsedAsPlainWords() {
        // نام دامنه‌های فهرست بزرگ فقط به‌صورت ساختار دامنه تطبیق می‌شود، نه کلمه
        assertFalse(e.isBlocked("adult-site"))
        assertFalse(e.isBlocked("sub shared"))
        assertFalse(e.isBlocked("how to cook adult site pasta"))
    }

    @Test fun typedDomainInSearchBoxIsBlocked() {
        assertTrue(e.isBlocked("adult-site.example.net"))
        assertTrue(e.isBlocked("visit adult-site.example.net now"))
    }

    @Test fun worksOfflineWithoutBigList() {
        val offline = FilterEngine.build(emptySet(), null)
        assertTrue(offline.isBlocked("https://pornhub.com"))
        assertTrue(offline.isBlocked("xnxx"))
        assertFalse(offline.isBlocked("https://adult-site.example.net"))
    }

    @Test fun emptyBigListBehavesLikeNoList() {
        val empty = FilterEngine.build(emptySet(), DomainSet.Builder().build())
        assertTrue(empty.isBlocked("https://xvideos.com"))
        assertFalse(empty.isBlocked("https://adult-site.example.net"))
    }

    @Test fun compositeOfBaseAndUpdatedLists() {
        val comp = CompositeLookup(listOf(big("one.example.net"), big("two.example.net")))
        val c = FilterEngine.build(emptySet(), comp)
        assertTrue(c.isBlocked("one.example.net"))
        assertTrue(c.isBlocked("www.two.example.net"))
        assertFalse(c.isBlocked("three.example.net"))
    }

    @Test fun customUserDomainsStillWorkNextToBigList() {
        val c = FilterEngine.build(setOf("Mine.example.org"), set)
        assertTrue(c.isBlocked("https://sub.mine.example.org"))
        assertTrue(c.isBlocked("https://adult-site.example.net"))
        assertFalse(c.isBlocked("mine")) // دامنه‌ی سفارشی هم به‌عنوان کلمه استفاده نمی‌شود
    }

    @Test fun protectedSitesCanNeverBeBlockedByLists() {
        val withProtected = big("quran.com", "google.com", "bad.example.net")
        val c = FilterEngine.build(emptySet(), withProtected)
        assertFalse(c.isBlocked("https://quran.com"))
        assertFalse(c.isBlocked("https://www.google.com/search?q=weather"))
        assertTrue(c.isBlocked("https://bad.example.net"))
    }

    @Test fun stageOneDomainRulesStillHold() {
        assertTrue(e.isBlocked("https://xnxx.com"))
        assertTrue(e.isBlocked("https://sub.xvideos.com"))
        assertFalse(e.isBlocked("https://notxnxx.com"))
        assertFalse(e.isBlocked("https://xnxx.com.example.org"))
        assertTrue(e.isBlocked("HTTPS://WWW.XNXX.COM:8443/Some/Path?x=1"))
    }
}
