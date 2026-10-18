package com.doorblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.doorblock.core.ActionGate
import com.doorblock.core.Config
import com.doorblock.core.FilterEngine
import com.doorblock.core.GuardAction
import com.doorblock.core.GuardLogic
import com.doorblock.core.Prefs

class GuardAccessibilityService : AccessibilityService() {

    private enum class Kind { BROWSER, SEARCH, TELEGRAM }

    private lateinit var prefs: Prefs
    private lateinit var repo: ListRepository
    private var lastListCheck = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val logic = GuardLogic(redirectHost = Config.REDIRECT_HOST)
    private val errorGate = ActionGate()

    private var engine: FilterEngine? = null
    private var engineKey: Set<String>? = null
    private var engineGen = -1

    private var lastScan = 0L
    private var trailingPkg: String? = null
    private var trailingScheduled = false

    /** اسکن دنباله‌دار: اگر رویدادی به‌خاطر محدودیت تعداد حذف شد، آخرین وضعیت را بعداً می‌خوانیم. */
    private val trailingScan = Runnable {
        trailingScheduled = false
        val pkg = trailingPkg ?: return@Runnable
        trailingPkg = null
        safely { scan(pkg, System.currentTimeMillis()) }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = ListRepository.get(this)
        repo.loadAsync() // فهرست بزرگ روی thread پس‌زمینه؛ تا آماده شود فهرست کوچک پایه کار می‌کند
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        checkListUpdate(System.currentTimeMillis())
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun engine(): FilterEngine {
        val custom = prefs.custom
        val gen = repo.generation
        val cached = engine
        if (cached == null || engineKey != custom || engineGen != gen) {
            val fresh = FilterEngine.build(custom, repo.lookup())
            engine = fresh
            engineKey = custom
            engineGen = gen
            return fresh
        }
        return cached
    }

    /** حداکثر هر ۶ ساعت یک‌بار بررسی می‌کنیم؛ خود به‌روزرسانی‌کننده هم هفته‌ای یک‌بار دانلود می‌کند. */
    private fun checkListUpdate(now: Long) {
        if (now - lastListCheck < LIST_CHECK_INTERVAL_MS) return
        lastListCheck = now
        repo.updateIfDueAsync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        safely { handle(event) }
    }

    private fun handle(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (kindOf(pkg) == null) return
        val now = System.currentTimeMillis()
        checkListUpdate(now)

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // مسیر سریع: متنی که همین الان تایپ شده، قبل از زدن Enter
            val typed = event.text.joinToString(" ")
            val act = logic.decideTyped(typed, now, engine())
            if (act != GuardAction.NONE) {
                perform(act, now)
                return
            }
            scan(pkg, now)
            return
        }

        if (now - lastScan < SCAN_INTERVAL_MS) {
            trailingPkg = pkg
            if (!trailingScheduled) {
                trailingScheduled = true
                handler.postDelayed(trailingScan, SCAN_INTERVAL_MS)
            }
            return
        }
        scan(pkg, now)
    }

    private fun scan(pkg: String, now: Long) {
        val kind = kindOf(pkg) ?: return
        lastScan = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val texts = collectTexts(root, kind)
        val act = logic.decide(
            locked = prefs.isLocked(now),
            now = now,
            texts = texts,
            isTelegram = kind == Kind.TELEGRAM,
            engine = engine(),
        )
        perform(act, now)
    }

    private fun perform(act: GuardAction, now: Long) {
        when (act) {
            GuardAction.NONE -> Unit
            GuardAction.LEAVE_ONLY -> performGlobalAction(GLOBAL_ACTION_HOME)
            GuardAction.BLOCKED_CONTENT -> {
                prefs.lock(now) // قفل جدید، یا تمدید قفل در صورت تکرار تلاش
                logic.markRedirected(now)
                leavePage()
                handler.postDelayed({ safely { openRedirect() } }, REDIRECT_DELAY_MS)
            }
        }
    }

    /**
     * ترک صفحه با APIهای مجاز: اول Back (در Chrome یک گام عقب می‌رود یا تب تازه‌باز را می‌بندد؛
     * در برنامه‌ی Google از صفحه‌ی جستجو خارج می‌شود)، سپس Home.
     * اندروید برای برنامه‌ی عادی راه مجازی برای «بستن تب» یا Force Stop نمی‌دهد.
     */
    private fun leavePage() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ safely { performGlobalAction(GLOBAL_ACTION_HOME) } }, BACK_TO_HOME_DELAY_MS)
    }

    private fun openRedirect() {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(Config.REDIRECT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // مثلا گوشی اجازه‌ی بازکردن از پس‌زمینه را نداده؛ حداقل به Home رفته‌ایم
            reportError(e)
        }
    }

    private fun kindOf(pkg: String): Kind? = when (pkg) {
        in BROWSERS -> Kind.BROWSER
        in SEARCH_APPS -> Kind.SEARCH
        in TELEGRAM -> Kind.TELEGRAM
        else -> null
    }

    /**
     * متن کادرهای قابل‌تایپ و نودهایی که id آنها شبیه نوار آدرس/جستجو است.
     * محتوای خود صفحه‌ی وب عمداً اسکن نمی‌شود. در تلگرام فقط کادرهای قابل‌تایپ.
     */
    private fun collectTexts(root: AccessibilityNodeInfo, kind: Kind): List<String> {
        val out = ArrayList<String>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            visited++
            if (depth > MAX_DEPTH || visited > MAX_NODES) return
            val id = n.viewIdResourceName?.lowercase() ?: ""
            val hinted = kind != Kind.TELEGRAM && ID_HINTS.any { id.contains(it) }
            if (n.isEditable || hinted) {
                val t = n.text?.toString()
                if (!t.isNullOrBlank()) out.add(t)
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            reportError(e)
        }
    }

    private fun reportError(e: Exception) {
        Log.e(TAG, "guard error", e)
        val now = System.currentTimeMillis()
        if (errorGate.tryAcquire("error", now, ERROR_INTERVAL_MS)) {
            val msg = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(160)
            try { prefs.lastError = msg } catch (ignored: Exception) {}
        }
    }

    companion object {
        private const val TAG = "doorOBlock"
        private const val SCAN_INTERVAL_MS = 120L
        private const val BACK_TO_HOME_DELAY_MS = 350L
        private const val REDIRECT_DELAY_MS = 700L
        private const val ERROR_INTERVAL_MS = 5000L
        private const val LIST_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 600

        // همین فهرست باید در accessibility_service_config.xml (packageNames) هم باشد
        val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "com.brave.browser", "com.microsoft.emmx",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "com.sec.android.app.sbrowser", "com.opera.browser",
            "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.vivaldi.browser",
            "com.mi.globalbrowser", "com.android.browser",
        )
        val SEARCH_APPS = setOf("com.google.android.googlequicksearchbox")
        val TELEGRAM = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        )
        private val ID_HINTS = listOf("url", "omnibox", "location_bar", "search", "address")
    }
}
