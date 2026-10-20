package com.doorblock.ui

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.doorblock.R
import com.doorblock.core.AddResult
import com.doorblock.core.Config
import com.doorblock.core.CustomItems
import com.doorblock.core.Fa
import com.doorblock.core.ItemKind
import com.doorblock.core.Prefs
import com.doorblock.core.ProtectionLevel
import com.doorblock.core.ProtectionState
import com.doorblock.core.RecoveryCodes
import com.doorblock.core.StreakTimer
import com.doorblock.service.ServiceStatus
import com.doorblock.ui.widgets.InlineMessage
import com.doorblock.ui.widgets.NoCopyTextView
import com.doorblock.ui.widgets.NoPasteEditText

/**
 * صفحه‌ی اصلی. رابط، مثل قبل، با View های برنامه‌نویسی‌شده ساخته می‌شود (بدون کتابخانه‌ی جدید)؛
 * رنگ‌ها، تم و drawable ها در منابع XML هستند تا حالت تیره هم پشتیبانی شود.
 * منطق ذخیره‌سازی و اعتبارسنجی همان قبلی است (Prefs، RecoveryCodes).
 */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val dialogs = DialogTracker()

    // وضعیت حفاظت
    private lateinit var statusCard: LinearLayout
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var errorRow: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var enableButton: Button
    private var shownLevel: ProtectionLevel? = null

    // زمان
    private lateinit var timeDays: TextView
    private lateinit var timeProgress: ProgressBar
    private lateinit var timeHours: TextView
    private lateinit var timeMinutes: TextView
    private lateinit var timeSeconds: TextView

    // افزودن
    private lateinit var input: EditText
    private lateinit var addMessage: InlineMessage
    private lateinit var summary: TextView

    // رمزها
    private lateinit var codeInputs: List<EditText>
    private lateinit var verifyMessage: InlineMessage

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL // برنامه فارسی است
        setContentView(buildScreen())
        refreshSummary()
    }

    // ------------------------------------------------------------------ ساخت صفحه

    private fun buildScreen(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(getColor(R.color.bg))
        root.addView(buildAppBar(), lp(h = dp(56)))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPaddingRelative(dp(16), dp(4), dp(16), dp(24))
        content.addView(buildStatusCard(), lp(bottom = 12))
        content.addView(buildTimeCard(), lp(bottom = 12))
        content.addView(buildAddCard(), lp(bottom = 12))
        content.addView(buildCodesCard(), lp(bottom = 12))
        content.addView(buildVerifyCard(), lp())

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.addView(content)
        root.addView(scroll, lp(h = 0, weight = 1f))
        return root
    }

    private fun buildAppBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPaddingRelative(dp(20), 0, dp(8), 0)
        bar.addView(textView(getString(R.string.app_title), 20f, bold = true), lp(w = 0, weight = 1f))

        val menu = ImageButton(this)
        menu.setImageResource(R.drawable.ic_more_vert)
        menu.imageTintList = ColorStateList.valueOf(getColor(R.color.text_primary))
        menu.setBackgroundResource(R.drawable.bg_icon_button)
        menu.contentDescription = getString(R.string.cd_menu)
        menu.setOnClickListener { showMenu(it) }
        bar.addView(menu, lp(w = dp(48), h = dp(48)))
        return bar
    }

    private fun buildStatusCard(): View {
        statusCard = LinearLayout(this)
        statusCard.orientation = LinearLayout.VERTICAL
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        statusIcon = ImageView(this)
        top.addView(statusIcon, lp(w = dp(36), h = dp(36), end = 12))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        statusTitle = textView("", 18f, bold = true)
        statusSub = textView("", 14f)
        col.addView(statusTitle)
        col.addView(statusSub, lp(top = 4))
        top.addView(col, lp(w = 0, weight = 1f))
        statusCard.addView(top)

        enableButton = primaryButton(getString(R.string.btn_enable_service)) { openAccessibilitySettings() }
        statusCard.addView(enableButton, lp(top = 14))

        errorRow = LinearLayout(this)
        errorRow.orientation = LinearLayout.VERTICAL
        errorRow.setBackgroundResource(R.drawable.bg_status_warn)
        errorRow.setPadding(dp(12), dp(10), dp(12), dp(6))
        val warnColor = R.color.warn_fg
        errorRow.addView(textView(getString(R.string.error_label), 13f, warnColor, bold = true))
        errorText = textView("", 13f, warnColor)
        errorRow.addView(errorText, lp(top = 2))
        errorRow.addView(
            textButton(getString(R.string.btn_clear_error)) { prefs.lastError = ""; refresh() },
            lp(w = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, top = 2),
        )
        statusCard.addView(errorRow, lp(top = 12))
        return statusCard
    }

    private fun buildTimeCard(): View {
        val card = card()
        card.addView(textView(getString(R.string.time_title), 14f, R.color.text_secondary))
        timeDays = textView("", 26f, bold = true)
        card.addView(timeDays, lp(top = 2))

        timeProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        timeProgress.isIndeterminate = false
        timeProgress.max = PROGRESS_MAX
        timeProgress.progressDrawable = getDrawable(R.drawable.progress_bar)
        card.addView(timeProgress, lp(h = dp(10), top = 12))

        timeHours = textView("", 22f, bold = true)
        timeMinutes = textView("", 22f, bold = true)
        timeSeconds = textView("", 22f, bold = true)
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.addView(chip(timeHours, getString(R.string.time_hours)), lp(w = 0, weight = 1f, end = 8))
        chips.addView(chip(timeMinutes, getString(R.string.time_minutes)), lp(w = 0, weight = 1f, end = 8))
        chips.addView(chip(timeSeconds, getString(R.string.time_seconds)), lp(w = 0, weight = 1f))
        card.addView(chips, lp(top = 14))
        return card
    }

    private fun chip(value: TextView, label: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setBackgroundResource(R.drawable.bg_chip)
        c.setPadding(dp(6), dp(10), dp(6), dp(10))
        value.gravity = Gravity.CENTER
        c.addView(value)
        val l = textView(label, 12f, R.color.text_secondary)
        l.gravity = Gravity.CENTER
        c.addView(l, lp(top = 2))
        return c
    }

    private fun buildAddCard(): View {
        val card = card()
        card.addView(textView(getString(R.string.add_title), 18f, bold = true))
        card.addView(textView(getString(R.string.add_helper), 14f, R.color.text_secondary), lp(top = 4))

        input = EditText(this)
        input.id = R.id.custom_input
        input.setBackgroundResource(R.drawable.bg_input)
        input.setPaddingRelative(dp(14), 0, dp(14), 0)
        input.hint = getString(R.string.add_hint)
        input.setHintTextColor(getColor(R.color.text_secondary))
        input.setTextColor(getColor(R.color.text_primary))
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        input.setSingleLine()
        input.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addItem(); true } else false
        }
        card.addView(input, lp(h = dp(48), top = 12))

        card.addView(primaryButton(getString(R.string.add_button)) { addItem() }, lp(top = 10))

        addMessage = InlineMessage(this)
        card.addView(addMessage, lp(top = 10))

        summary = textView("", 13f, R.color.text_secondary)
        card.addView(summary, lp(top = 12))
        card.addView(textView(getString(R.string.custom_summary_hint), 12f, R.color.text_secondary), lp(top = 2))
        return card
    }

    private fun buildCodesCard(): View {
        val card = card()
        card.addView(textView(getString(R.string.codes_title), 18f, bold = true))
        card.addView(textView(getString(R.string.codes_helper), 14f, R.color.text_secondary), lp(top = 4))

        prefs.recoveryCodes.forEachIndexed { index, code ->
            card.addView(
                textView(getString(R.string.code_label, Fa.num(index + 1)), 12f, R.color.text_secondary),
                lp(top = if (index == 0) 14 else 10),
            )
            val tile = NoCopyTextView(this)
            tile.text = code
            tile.typeface = Typeface.MONOSPACE
            tile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            tile.setTextColor(getColor(R.color.text_primary))
            tile.setBackgroundResource(R.drawable.bg_code)
            tile.setPadding(dp(12), dp(10), dp(12), dp(10))
            // رمزها LTR هستند؛ در صفحه‌ی راست‌به‌چپ هم ترتیب نویسه‌ها و تراز چپ حفظ می‌شود
            tile.layoutDirection = View.LAYOUT_DIRECTION_LTR
            tile.textDirection = View.TEXT_DIRECTION_LTR
            tile.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            card.addView(tile, lp(top = 4))
        }
        return card
    }

    private fun buildVerifyCard(): View {
        val card = card()
        card.addView(textView(getString(R.string.verify_title), 18f, bold = true))
        card.addView(textView(getString(R.string.verify_helper), 14f, R.color.text_secondary), lp(top = 4))

        val ids = listOf(R.id.code_input_1, R.id.code_input_2, R.id.code_input_3, R.id.code_input_4, R.id.code_input_5)
        codeInputs = ids.mapIndexed { index, viewId ->
            card.addView(
                textView(getString(R.string.code_label, Fa.num(index + 1)), 12f, R.color.text_secondary),
                lp(top = if (index == 0) 14 else 10),
            )
            val field = NoPasteEditText(this)
            field.id = viewId
            field.setBackgroundResource(R.drawable.bg_input)
            field.setPaddingRelative(dp(12), 0, dp(12), 0)
            field.typeface = Typeface.MONOSPACE
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            field.setTextColor(getColor(R.color.text_primary))
            field.setSingleLine()
            field.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            field.imeOptions = if (index == ids.lastIndex) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            field.layoutDirection = View.LAYOUT_DIRECTION_LTR
            field.textDirection = View.TEXT_DIRECTION_LTR
            field.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            card.addView(field, lp(h = dp(48), top = 4))
            field
        }

        card.addView(primaryButton(getString(R.string.btn_verify)) { validateCodes() }, lp(top = 16))
        card.addView(secondaryButton(getString(R.string.btn_clear_all)) { clearCodeInputs() }, lp(top = 8))
        verifyMessage = InlineMessage(this)
        card.addView(verifyMessage, lp(top = 10))
        return card
    }

    // ------------------------------------------------------------------ رفتارها

    /** رفتار قبلی حفظ شده (مقدار trim‌شده ذخیره می‌شود)؛ خطاها اکنون واضح‌اند. */
    private fun addItem() {
        when (val r = CustomItems.add(prefs.custom, input.text.toString())) {
            is AddResult.Added -> {
                prefs.custom = r.items
                input.setText("")
                addMessage.show(getString(R.string.msg_added), false)
                refreshSummary()
            }
            AddResult.Empty -> addMessage.show(getString(R.string.msg_empty_input), true)
            AddResult.Duplicate -> addMessage.show(getString(R.string.msg_duplicate), true)
            AddResult.TooLong ->
                addMessage.show(getString(R.string.msg_too_long, Fa.num(CustomItems.MAX_LEN)), true)
        }
    }

    /** منطق قبلی: در هر دو حالت کادرها پاک می‌شوند؛ در حالت درست تنظیمات دسترس‌پذیری باز می‌شود. */
    private fun validateCodes() {
        val entered = codeInputs.map { it.text.toString() }
        val correct = RecoveryCodes.matches(prefs.recoveryCodes, entered)
        clearCodeInputs()
        if (!correct) {
            verifyMessage.show(getString(R.string.msg_codes_wrong), true)
            return
        }
        verifyMessage.show(getString(R.string.msg_codes_ok), false)
        openAccessibilitySettings()
    }

    private fun clearCodeInputs() {
        codeInputs.forEach { it.text.clear() }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val sites = CustomItems.count(prefs.custom, ItemKind.SITE)
        val words = CustomItems.count(prefs.custom, ItemKind.WORD)
        popup.menu.add(0, MENU_SITES, 0, getString(R.string.menu_sites, Fa.num(sites)))
        popup.menu.add(0, MENU_WORDS, 1, getString(R.string.menu_words, Fa.num(words)))
        popup.menu.add(0, MENU_SECRET, 2, getString(R.string.menu_secret))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SITES -> openList(ItemKind.SITE)
                MENU_WORDS -> openList(ItemKind.WORD)
                MENU_SECRET -> SecretDialog(this, prefs, dialogs).show()
            }
            true
        }
        popup.show()
    }

    private fun openList(kind: ItemKind) {
        CustomItemsDialog(this, prefs, dialogs, kind) { refreshSummary() }.show()
    }

    // ------------------------------------------------------------------ به‌روزرسانی نمایش

    private fun refreshSummary() {
        val all = prefs.custom
        summary.text = getString(
            R.string.custom_summary,
            Fa.num(CustomItems.count(all, ItemKind.SITE)),
            Fa.num(CustomItems.count(all, ItemKind.WORD)),
        )
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val start = prefs.startMillis
        val s = StreakTimer.compute(start, now)

        timeDays.setIfChanged(getString(R.string.time_days_goal, Fa.num(s.days), Fa.num(Config.GOAL_DAYS)))
        val fraction = (now - start).coerceAtLeast(0L).toDouble() / (Config.GOAL_DAYS * DAY_MS.toDouble())
        timeProgress.progress = (fraction.coerceAtMost(1.0) * PROGRESS_MAX).toInt()
        timeHours.setIfChanged(Fa.num(s.hours))
        timeMinutes.setIfChanged(Fa.num(s.minutes))
        timeSeconds.setIfChanged(Fa.num(s.seconds))

        val locked = prefs.isLocked(now)
        applyStatus(
            ProtectionState.compute(
                serviceEnabled = ServiceStatus.isEnabled(this),
                locked = locked,
                lockSecondsLeft = (prefs.lockUntil - now) / 1000,
                lastError = prefs.lastError,
            ),
        )
    }

    private fun applyStatus(st: ProtectionState) {
        val protectedNow = st.level != ProtectionLevel.INACTIVE
        if (shownLevel != st.level) { // پس‌زمینه و آیکون فقط وقتی وضعیت عوض شد
            shownLevel = st.level
            val fg = getColor(if (protectedNow) R.color.ok_fg else R.color.off_fg)
            statusCard.setBackgroundResource(if (protectedNow) R.drawable.bg_status_ok else R.drawable.bg_status_off)
            statusIcon.setImageResource(if (protectedNow) R.drawable.ic_shield_ok else R.drawable.ic_shield_off)
            statusIcon.imageTintList = ColorStateList.valueOf(fg)
            statusTitle.setTextColor(fg)
            statusSub.setTextColor(fg)
            // دکمه‌ی روشن‌کردن فقط وقتی سرویس خاموش است؛ برای خاموش‌کردن، مسیر رمزها لازم است
            enableButton.visibility = if (protectedNow) View.GONE else View.VISIBLE
        }
        when (st.level) {
            ProtectionLevel.ACTIVE -> {
                statusTitle.setIfChanged(getString(R.string.status_active_title))
                statusSub.setIfChanged(getString(R.string.status_active_sub))
            }
            ProtectionLevel.LOCKED -> {
                statusTitle.setIfChanged(getString(R.string.status_locked_title))
                statusSub.setIfChanged(getString(R.string.status_locked_sub, Fa.duration(st.lockSecondsLeft)))
            }
            ProtectionLevel.INACTIVE -> {
                statusTitle.setIfChanged(getString(R.string.status_inactive_title))
                statusSub.setIfChanged(getString(R.string.status_inactive_sub))
            }
        }
        if (st.error.isEmpty()) {
            errorRow.visibility = View.GONE
        } else {
            errorRow.visibility = View.VISIBLE
            errorText.setIfChanged(st.error)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        dialogs.dismissAll()
        super.onDestroy()
    }

    companion object {
        private const val MENU_SITES = 1
        private const val MENU_WORDS = 2
        private const val MENU_SECRET = 3
        private const val PROGRESS_MAX = 1000
        private const val DAY_MS = 86_400_000L
    }
}
