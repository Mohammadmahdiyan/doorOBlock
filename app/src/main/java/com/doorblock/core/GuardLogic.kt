package com.doorblock.core

/** نتیجه‌ی تصمیم‌گیری برای یک رویداد. */
enum class GuardAction {
    NONE,

    /** محتوای ممنوع دیده شد: قفل (یا تمدید قفل) + ترک صفحه + هدایت به سایت قرآن. */
    BLOCKED_CONTENT,

    /** در حالت قفل، کاربر در جای غیرمجاز است (سایت دیگر یا تلگرام): فقط Home. */
    LEAVE_ONLY,
}

/**
 * تصمیم می‌گیرد با هر رویداد چه کاری لازم است. هیچ وابستگی به Android ندارد.
 *
 * قواعد:
 *  - محتوای ممنوع همیشه بررسی می‌شود، حتی در حالت قفل و حتی بلافاصله بعد از ریدایرکت.
 *  - هر نوع اقدام یک فاصله‌ی حداقلی دارد تا رویدادهای تکراری حلقه نسازند.
 *  - در حالت قفل، مرورگر فقط روی سایت قرآن مجاز است.
 *  - اگر آدرس خوانده نشود (مثلا نوار آدرس موقع اسکرول پنهان است) و آخرین چیزی که
 *    خوانده‌ایم سایت قرآن بوده، همان وضعیت را ادامه می‌دهیم.
 */
class GuardLogic(
    private val redirectHost: String = "quran.com",
    private val gate: ActionGate = ActionGate(),
) {
    private var redirectEvidence = false
    private var graceUntil = 0L

    /** بعد از ریدایرکت صدا زده می‌شود تا مرورگر فرصت بارگذاری سایت قرآن را داشته باشد. */
    fun markRedirected(now: Long) {
        graceUntil = now + GRACE_MS
        redirectEvidence = false
    }

    /** متنی که همین الان تایپ شده (قبل از زدن Enter). فقط محتوای ممنوع را تشخیص می‌دهد. */
    fun decideTyped(typed: String, now: Long, engine: FilterEngine): GuardAction {
        if (typed.isBlank() || !engine.isBlocked(typed)) return GuardAction.NONE
        return blocked(now)
    }

    /** اسکن کامل صفحه. texts: متن نوار آدرس/کادرهای جستجو. */
    fun decide(
        locked: Boolean,
        now: Long,
        texts: List<String>,
        isTelegram: Boolean,
        engine: FilterEngine,
    ): GuardAction {
        val hasBlocked = texts.any { engine.isBlocked(it) }

        if (!locked) {
            redirectEvidence = false
            return if (hasBlocked) blocked(now) else GuardAction.NONE
        }

        if (hasBlocked) return blocked(now)        // بدون هیچ مهلتی
        if (now < graceUntil) return GuardAction.NONE

        if (!isTelegram) {
            if (texts.any { isRedirectSite(it) }) {
                redirectEvidence = true
                return GuardAction.NONE
            }
            if (texts.isEmpty() && redirectEvidence) return GuardAction.NONE
        }
        return leaveOnly(now)
    }

    private fun isRedirectSite(text: String): Boolean {
        val host = FilterEngine.canonicalHost(text)
        return host == redirectHost || host.endsWith(".$redirectHost")
    }

    private fun blocked(now: Long): GuardAction =
        if (gate.tryAcquire("blocked", now, BLOCKED_INTERVAL_MS)) GuardAction.BLOCKED_CONTENT
        else GuardAction.NONE

    private fun leaveOnly(now: Long): GuardAction =
        if (gate.tryAcquire("leave", now, LEAVE_INTERVAL_MS)) GuardAction.LEAVE_ONLY
        else GuardAction.NONE

    companion object {
        const val GRACE_MS = 6000L
        const val BLOCKED_INTERVAL_MS = 2000L
        const val LEAVE_INTERVAL_MS = 1500L
    }
}
