package com.doorblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.doorblock.core.ActionGate
import com.doorblock.core.Config
import com.doorblock.core.FilterEngine
import com.doorblock.core.Prefs

class GuardAccessibilityService : AccessibilityService() {

    private enum class Kind { BROWSER, SEARCH, TELEGRAM }

    private lateinit var prefs: Prefs
    private var engine: FilterEngine? = null
    private var engineKey: Set<String>? = null
    private val gate = ActionGate()
    private var lastRedirect = 0L
    private var lastScan = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    private fun engine(): FilterEngine {
        val custom = prefs.custom
        if (engine == null || engineKey != custom) {
            engine = FilterEngine.build(custom)
            engineKey = custom
        }
        return engine!!
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val kind = when (pkg) {
            in BROWSERS -> Kind.BROWSER
            in SEARCH_APPS -> Kind.SEARCH
            in TELEGRAM -> Kind.TELEGRAM
            else -> return
        }
        val now = System.currentTimeMillis()

        if (prefs.isLocked(now)) {
            onLocked(kind, pkg, now)
            return
        }

        // مسیر سریع: متنی که همین الان تایپ شده، قبل از زدن Enter
        val isTyping = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        if (isTyping) {
            val typed = event.text.joinToString(" ")
            if (typed.isNotBlank() && engine().isBlocked(typed)) {
                violation(now)
                return
            }
        }

        // اسکن کامل صفحه، با محدودیت تعداد
        if (!isTyping && now - lastScan < SCAN_INTERVAL_MS) return
        lastScan = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (collectTexts(root, kind).any { engine().isBlocked(it) }) violation(now)
    }

    private fun onLocked(kind: Kind, pkg: String, now: Long) {
        if (now - lastRedirect < REDIRECT_GRACE_MS) return
        if (kind != Kind.TELEGRAM) {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != pkg) return
            val texts = collectTexts(root, kind)
            if (texts.isEmpty()) return // چیزی برای قضاوت نیست
            val onRedirectSite = texts.any { it.lowercase().contains(Config.REDIRECT_HOST) }
            val anyBlocked = texts.any { engine().isBlocked(it) }
            if (onRedirectSite && !anyBlocked) return
        }
        if (gate.tryAcquire("home", now, HOME_INTERVAL_MS)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun violation(now: Long) {
        // هر تخلف فقط یک بار پردازش می‌شود؛ رویدادهای تکراری حلقه نمی‌سازند
        if (!gate.tryAcquire("violation", now, VIOLATION_INTERVAL_MS)) return
        prefs.lock(now)
        lastRedirect = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(Config.REDIRECT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // اگر گوشی اجازه‌ی بازکردن از پس‌زمینه را نداد، حداقل به Home رفته‌ایم
            Log.w(TAG, "redirect failed", e)
        }
    }

    /**
     * متن کادرهای قابل‌تایپ و نودهایی که id آنها شبیه نوار آدرس/جستجو است.
     * محتوای خود صفحه‌ی وب عمداً اسکن نمی‌شود (تا صفحات معمولی اشتباهی بسته نشوند).
     * در تلگرام فقط کادرهای قابل‌تایپ.
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

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "doorOBlock"
        private const val SCAN_INTERVAL_MS = 120L
        private const val REDIRECT_GRACE_MS = 6000L
        private const val HOME_INTERVAL_MS = 1500L
        private const val VIOLATION_INTERVAL_MS = 2000L
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
