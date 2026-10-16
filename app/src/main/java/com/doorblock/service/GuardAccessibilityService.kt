package com.doorblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.doorblock.core.Config
import com.doorblock.core.FilterEngine
import com.doorblock.core.Prefs

class GuardAccessibilityService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private var engine: FilterEngine? = null
    private var engineKey: Set<String>? = null
    private var lastRedirect = 0L

    private val browsers = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/url_bar_title",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser" to "com.opera.browser:id/url_field",
    )
    private val telegram = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
    )

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
        val pkg = event?.packageName?.toString() ?: return
        val isBrowser = pkg in browsers
        val isTelegram = pkg in telegram
        if (!isBrowser && !isTelegram) return
        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        val url = if (isBrowser) readUrl(root, browsers.getValue(pkg)) else null

        if (prefs.isLocked(now)) {
            // مهلت کوتاه تا مرورگر صفحه‌ی قرآن را باز کند
            if (now - lastRedirect < 6000) return
            if (url != null && url.contains(Config.REDIRECT_HOST)) return
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val bad = if (isBrowser) {
            url != null && engine().isBlockedUrl(url)
        } else {
            editableTexts(root).any { engine().isBlockedText(it) }
        }
        if (bad) violation(now)
    }

    private fun violation(now: Long) {
        prefs.lock(now)
        lastRedirect = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(Config.REDIRECT_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun readUrl(root: AccessibilityNodeInfo, viewId: String): String? =
        root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()?.text?.toString()

    /** فقط کادرهای قابل‌تایپ (کادر جستجو) را می‌خوانیم تا چت‌ها اشتباهی بسته نشوند. */
    private fun editableTexts(node: AccessibilityNodeInfo, depth: Int = 0): List<String> {
        if (depth > 25) return emptyList()
        val out = mutableListOf<String>()
        if (node.isEditable) node.text?.toString()?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { out.addAll(editableTexts(it, depth + 1)) }
        }
        return out
    }

    override fun onInterrupt() {}
}
