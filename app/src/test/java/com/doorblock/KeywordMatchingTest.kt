package com.doorblock

import com.doorblock.core.FilterEngine
import com.doorblock.core.KeywordRule
import com.doorblock.core.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** منطق کلمه‌ها با فهرست پیش‌فرض واقعی برنامه. */
class KeywordMatchingTest {
    private val e = FilterEngine.build(emptySet())

    private fun assertBlocked(vararg items: String) {
        for (t in items) assertTrue("باید مسدود شود: $t", e.isBlocked(t))
    }

    private fun assertAllowed(vararg items: String) {
        for (t in items) assertFalse("نباید مسدود شود: $t", e.isBlocked(t))
    }

    @Test fun explicitTermsAndTheirSpellings() {
        assertBlocked(
            "porn", "PORN", "Porn", "p0rn", "pornnn", "pornhub", "free porn videos",
            "p o r n", "p-o-r-n", "p.o.r.n", "xvid3os", "xvideos", "x n x x",
            "hentai", "blow job", "gang bang",
        )
    }

    @Test fun userRequestedTermsAndObfuscation() {
        assertBlocked("lexilune", "lexi lune", "L-e_xi lune", "L-e_xi lne", "dogg y style", "doggystyle")
    }

    @Test fun phrasesAreMatchedAsPhrases() {
        assertBlocked("sex video", "SEX   VIDEOS", "sex-movies", "naked girls", "nude pics", "onlyfans leaks", "milf")
    }

    @Test fun persianTermsAreMatched() {
        assertBlocked("سکس", "دانلود فیلم سکسی", "پورنو", "پورن هاب")
    }

    @Test fun separatorsDoNotGlueNeighbouringWordsTogether() {
        // قبلا «porn» داخل حروف چسبیده‌ی کلمه‌های عادی پیدا می‌شد
        assertAllowed("up or not", "shop or not", "top or nothing", "stop or next", "scrap or nothing")
    }

    @Test fun lookAlikeWordsAreNotBlocked() {
        assertAllowed(
            "hamster care", "a hamster", "buy a hamster cage", "six movie", "six videos", "x videos",
            "essex video", "sussex weather", "popcorn recipe", "scunthorpe", "essex",
        )
    }

    @Test fun ambiguousWordsAloneAreNotBlocked() {
        assertAllowed(
            "sex education", "sexual health clinic", "nude art museum", "anal fissure treatment",
            "xxx", "adult education", "breast cancer screening", "free movies",
        )
    }

    @Test fun ordinaryPersianIsNotBlocked() {
        assertAllowed("فیلم سینمایی", "آموزش آشپزی", "سکسی", "دانلود فیلم")
    }

    @Test fun normalizationHandlesCaseSpacingAndInvisibleChars() {
        assertBlocked("  PoRn  ", "p\u200bo\u200br\u200bn", "P  O  R  N", "ＰＯＲＮ")
        assertAllowed("", "   ", "\u200b", "....", "!!!")
    }

    @Test fun oddInputNeverCrashes() {
        val odd = listOf("x".repeat(5000), "😀😀", "%zz", "\u0000", "a".repeat(400) + " " + "b".repeat(400))
        for (o in odd) e.isBlocked(o)
    }

    @Test fun brandNamesMatchInSearchTextButNotAsHostSubstrings() {
        // در جستجو
        assertBlocked("xnxx", "xvideos hd", "chaturbate", "x n x x")
        // نام میزبان فقط با ساختار دامنه سنجیده می‌شود، نه شباهت رشته
        assertAllowed("https://notxnxx.com", "notxnxx.com", "https://xnxx.com.example.org", "https://myxvideosfans.org")
        assertBlocked("https://xnxx.com", "https://m.xvideos.com/x", "xvideos.com")
    }

    @Test fun genericExplicitTermsStillApplyToHostNames() {
        assertBlocked("https://freeporn.example.org", "https://p-o-r-n-site.example.org")
    }

    @Test fun ruleParsingModes() {
        val brand = KeywordRule.parse("b:xnxx")
        assertEquals(MatchMode.CONTAINS, brand?.mode)
        assertFalse(brand!!.appliesToHosts)
        assertTrue(KeywordRule.parse("c:xnxx")!!.appliesToHosts)
        assertEquals(MatchMode.CONTAINS, KeywordRule.parse("c:xvideos")?.mode)
        assertEquals(MatchMode.PREFIX, KeywordRule.parse("p:porn")?.mode)
        assertEquals(MatchMode.ALIGNED, KeywordRule.parse("w:milf")?.mode)
        assertEquals(MatchMode.ALIGNED, KeywordRule.parse("ph:sex video")?.mode)
        // بدون پیشوند: چندکلمه‌ای=عبارت، بلند=شامل، کوتاه=کل کلمه
        assertEquals(MatchMode.ALIGNED, KeywordRule.parse("bad phrase")?.mode)
        assertEquals(MatchMode.CONTAINS, KeywordRule.parse("lexilune")?.mode)
        assertEquals(MatchMode.ALIGNED, KeywordRule.parse("abc")?.mode)
        assertNull(KeywordRule.parse(""))
        assertNull(KeywordRule.parse("c:"))
        assertNull(KeywordRule.parse("   ...  "))
        assertNotNull(KeywordRule.parse("ph:a b"))
    }

    @Test fun emptyKeywordListMatchesNothing() {
        val none = FilterEngine(emptySet(), emptyList())
        assertFalse(none.isBlocked("porn"))
        assertFalse(none.isBlocked("https://xnxx.com"))
    }

    @Test fun duplicateRulesAreHarmless() {
        val d = FilterEngine(emptySet(), listOf("c:porn", "c:porn", "porn", "PORN"))
        assertTrue(d.isBlocked("porn"))
        assertFalse(d.isBlocked("popcorn"))
    }

    @Test fun customWordsKeepStageOneBehaviour() {
        val c = FilterEngine.build(setOf("bad phrase", "cat"))
        assertTrue(c.isBlocked("bad phrase"))
        assertTrue(c.isBlocked("the cat sat"))   // کوتاه = کل کلمه
        assertFalse(c.isBlocked("catalog"))      // نه زیررشته
    }

    @Test fun repeatedLookupsAreConsistentThroughTheCache() {
        repeat(300) { assertTrue(e.isBlocked("p0rn")) }
        repeat(300) { assertFalse(e.isBlocked("up or not")) }
        for (i in 0 until 600) e.isBlocked("query number $i") // سرریز کش
        assertTrue(e.isBlocked("p0rn"))
    }
}
