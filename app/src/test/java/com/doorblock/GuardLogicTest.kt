package com.doorblock

import com.doorblock.core.FilterEngine
import com.doorblock.core.GuardAction
import com.doorblock.core.GuardLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class GuardLogicTest {
    private val engine = FilterEngine(
        domains = setOf("xnxx.com", "xvideos.com"),
        keywords = listOf("porn"),
    )

    private fun logic() = GuardLogic(redirectHost = "quran.com")

    @Test fun blockedContentTriggersOnce_repeatedEventsAreIgnored() {
        val l = logic()
        val bad = listOf("https://xnxx.com")
        assertEquals(GuardAction.BLOCKED_CONTENT, l.decide(false, 1000, bad, false, engine))
        repeat(40) {
            assertEquals(GuardAction.NONE, l.decide(false, 1000L + it * 20, bad, false, engine))
        }
    }

    @Test fun blockedAgainAfterIntervalIsHandledAgain() {
        val l = logic()
        val bad = listOf("xvideos.com")
        assertEquals(GuardAction.BLOCKED_CONTENT, l.decide(false, 1000, bad, false, engine))
        assertEquals(GuardAction.BLOCKED_CONTENT, l.decide(true, 3100, bad, false, engine))
    }

    @Test fun graceAfterRedirectNeverHidesBlockedContent() {
        val l = logic()
        l.markRedirected(1000)
        // داخل مهلت، سایت غیرممنوع نادیده گرفته می‌شود
        assertEquals(GuardAction.NONE, l.decide(true, 2000, listOf("google.com"), false, engine))
        // اما محتوای ممنوع نه
        assertEquals(
            GuardAction.BLOCKED_CONTENT,
            l.decide(true, 2100, listOf("https://xnxx.com"), false, engine),
        )
    }

    @Test fun lockedBrowserOnlyAllowsRedirectSite() {
        val l = logic()
        assertEquals(GuardAction.NONE, l.decide(true, 10_000, listOf("quran.com"), false, engine))
        assertEquals(GuardAction.NONE, l.decide(true, 10_200, listOf("https://www.quran.com/1"), false, engine))
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 12_000, listOf("google.com"), false, engine))
    }

    @Test fun lookalikeOfRedirectSiteIsNotAllowed() {
        val l = logic()
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 10_000, listOf("notquran.com"), false, engine))
    }

    @Test fun unreadableUrlKeepsLastVerdictOnlyIfRedirectSiteWasSeen() {
        val l = logic()
        // هیچ شاهدی نیست: خالی بودن یعنی غیرمجاز
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 10_000, emptyList(), false, engine))
        // سایت قرآن دیده شد، بعد نوار آدرس پنهان شد: ادامه می‌دهیم
        assertEquals(GuardAction.NONE, l.decide(true, 12_000, listOf("quran.com"), false, engine))
        assertEquals(GuardAction.NONE, l.decide(true, 13_000, emptyList(), false, engine))
        // قفل تمام شد: شاهد پاک می‌شود
        assertEquals(GuardAction.NONE, l.decide(false, 20_000, emptyList(), false, engine))
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 30_000, emptyList(), false, engine))
    }

    @Test fun repeatedLeaveEventsAreRateLimited() {
        val l = logic()
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 10_000, listOf("google.com"), false, engine))
        repeat(30) {
            assertEquals(
                GuardAction.NONE,
                l.decide(true, 10_000L + it * 10, listOf("google.com"), false, engine),
            )
        }
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 11_600, listOf("google.com"), false, engine))
    }

    @Test fun telegramWhileLockedLeaves_butBlockedTextStillCounts() {
        val l = logic()
        assertEquals(GuardAction.LEAVE_ONLY, l.decide(true, 10_000, emptyList(), true, engine))
        assertEquals(GuardAction.BLOCKED_CONTENT, l.decide(true, 20_000, listOf("porn"), true, engine))
    }

    @Test fun typedTextIsCaughtBeforeEnter() {
        val l = logic()
        assertEquals(GuardAction.NONE, l.decideTyped("xnxx.co", 1000, engine))
        assertEquals(GuardAction.BLOCKED_CONTENT, l.decideTyped("xnxx.com", 1100, engine))
        assertEquals(GuardAction.NONE, l.decideTyped("", 5000, engine))
    }

    @Test fun lookalikeDomainIsNotBlocked() {
        val l = logic()
        assertEquals(GuardAction.NONE, l.decide(false, 1000, listOf("https://notxnxx.com"), false, engine))
    }
}
