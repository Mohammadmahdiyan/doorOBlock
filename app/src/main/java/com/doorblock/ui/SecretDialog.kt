package com.doorblock.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.doorblock.R
import com.doorblock.core.CustomItems
import com.doorblock.core.Fa
import com.doorblock.core.ItemKind
import com.doorblock.core.Prefs
import com.doorblock.core.SecretCode
import com.doorblock.ui.widgets.InlineMessage
import com.doorblock.ui.widgets.NoCopyTextView
import com.doorblock.ui.widgets.NoPasteEditText

/**
 * رمز تصادفی ۱۰۰ نویسه‌ای را نشان می‌دهد (غیرقابل‌انتخاب/کپی) و یک کادر ورودی (بدون Paste) دارد.
 * فقط با ثبت رمز درست، موارد سفارشی (سایت‌ها و کلمه‌ها) خوانا نمایش داده می‌شوند.
 * هر بار بستن پنجره دوباره قفل می‌شود (هیچ نشست بازی ذخیره نمی‌شود).
 */
internal class SecretDialog(
    private val activity: Activity,
    private val prefs: Prefs,
    private val tracker: DialogTracker,
) {
    private lateinit var dialog: Dialog
    private lateinit var gate: LinearLayout
    private lateinit var reveal: LinearLayout
    private lateinit var input: EditText
    private lateinit var counter: TextView
    private lateinit var message: InlineMessage

    fun show() {
        val a = activity
        val box = a.dialogBox()

        val header = LinearLayout(a)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(a.textView(a.getString(R.string.secret_title), 18f, bold = true), a.lp(w = 0, weight = 1f))
        header.addView(
            a.textButton(a.getString(R.string.btn_close)) { dialog.dismiss() },
            a.lp(w = ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        box.addView(header)

        gate = buildGate()
        reveal = LinearLayout(a)
        reveal.orientation = LinearLayout.VERTICAL
        reveal.visibility = View.GONE
        box.addView(gate)
        box.addView(reveal)

        val scroll = ScrollView(a)
        scroll.addView(box)
        dialog = a.makeDialog(scroll, tracker)
        dialog.show()
    }

    private fun buildGate(): LinearLayout {
        val a = activity
        val g = LinearLayout(a)
        g.orientation = LinearLayout.VERTICAL
        g.addView(a.textView(a.getString(R.string.secret_helper), 14f, R.color.text_secondary), a.lp(top = 6))

        // رمز: غیرقابل‌انتخاب و غیرقابل‌کپی؛ هر خط ۲۰ نویسه
        val tile = NoCopyTextView(a)
        tile.text = SecretCode.chunks(prefs.viewSecret).joinToString("\n")
        tile.typeface = Typeface.MONOSPACE
        tile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        tile.setLineSpacing(0f, 1.3f)
        tile.setTextColor(a.getColor(R.color.text_primary))
        tile.setBackgroundResource(R.drawable.bg_code)
        tile.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        tile.layoutDirection = View.LAYOUT_DIRECTION_LTR
        tile.textDirection = View.TEXT_DIRECTION_LTR
        tile.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        g.addView(tile, a.lp(top = 12))

        counter = a.textView("", 12f, R.color.text_secondary)
        g.addView(counter, a.lp(top = 12))

        val field = NoPasteEditText(a)
        field.id = R.id.secret_input
        field.setBackgroundResource(R.drawable.bg_input)
        field.setPaddingRelative(a.dp(12), 0, a.dp(12), 0)
        field.typeface = Typeface.MONOSPACE
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        field.setTextColor(a.getColor(R.color.text_primary))
        field.setSingleLine()
        field.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        field.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(SecretCode.LENGTH))
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        field.layoutDirection = View.LAYOUT_DIRECTION_LTR
        field.textDirection = View.TEXT_DIRECTION_LTR
        field.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateCounter() }
        })
        input = field
        g.addView(field, a.lp(h = a.dp(48), top = 4))

        val buttons = LinearLayout(a)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.addView(a.primaryButton(a.getString(R.string.btn_submit)) { submit() }, a.lp(w = 0, weight = 1f, end = 8))
        buttons.addView(
            a.secondaryButton(a.getString(R.string.btn_clear_all)) { input.text.clear() },
            a.lp(w = 0, weight = 1f),
        )
        g.addView(buttons, a.lp(top = 12))

        message = InlineMessage(a)
        g.addView(message, a.lp(top = 10))
        updateCounter()
        return g
    }

    private fun updateCounter() {
        counter.text = activity.getString(
            R.string.secret_counter,
            Fa.num(input.text.length),
            Fa.num(SecretCode.LENGTH),
        )
    }

    private fun submit() {
        val entered = input.text.toString()
        if (entered.length != SecretCode.LENGTH) {
            message.show(activity.getString(R.string.secret_length_wrong, Fa.num(SecretCode.LENGTH)), true)
            return
        }
        if (!SecretCode.matches(prefs.viewSecret, entered)) {
            message.show(activity.getString(R.string.secret_wrong), true)
            return
        }
        input.text.clear()
        showReveal()
    }

    /** فقط بعد از رمز درست: موارد سفارشی خوانا (به‌روز از ذخیره‌سازی واقعی). */
    private fun showReveal() {
        val a = activity
        gate.visibility = View.GONE
        reveal.removeAllViews()
        reveal.visibility = View.VISIBLE
        reveal.addView(a.textView(a.getString(R.string.reveal_title), 16f, bold = true), a.lp(top = 8))
        reveal.addView(a.textView(a.getString(R.string.reveal_note), 12f, R.color.text_secondary), a.lp(top = 2))
        addSection(R.string.reveal_sites, CustomItems.itemsOf(prefs.custom, ItemKind.SITE), true)
        addSection(R.string.reveal_words, CustomItems.itemsOf(prefs.custom, ItemKind.WORD), false)
    }

    private fun addSection(titleRes: Int, list: List<String>, isSite: Boolean) {
        val a = activity
        reveal.addView(a.textView(a.getString(titleRes, Fa.num(list.size)), 15f, bold = true), a.lp(top = 16))
        if (list.isEmpty()) {
            reveal.addView(a.textView(a.getString(R.string.reveal_none), 14f, R.color.text_secondary), a.lp(top = 6))
            return
        }
        list.forEachIndexed { index, item ->
            val row = LinearLayout(a)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setBackgroundResource(R.drawable.bg_code)
            row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))
            row.addView(a.textView(Fa.num(index + 1), 13f, R.color.text_secondary), a.lp(w = a.dp(28)))
            val tv = NoCopyTextView(a)
            tv.text = item
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tv.setTextColor(a.getColor(R.color.text_primary))
            tv.textDirection = if (isSite) View.TEXT_DIRECTION_LTR else View.TEXT_DIRECTION_FIRST_STRONG
            row.addView(tv, a.lp(w = 0, weight = 1f))
            reveal.addView(row, a.lp(top = 6))
        }
    }
}
