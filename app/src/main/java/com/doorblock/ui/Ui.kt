package com.doorblock.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.doorblock.R
import com.doorblock.core.UiSpec

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.textView(
    value: CharSequence = "",
    sizeSp: Float = 14f,
    colorRes: Int = R.color.text_primary,
    bold: Boolean = false,
): TextView {
    val tv = TextView(this)
    tv.text = value
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    tv.setTextColor(getColor(colorRes))
    if (bold) tv.typeface = Typeface.DEFAULT_BOLD
    tv.setLineSpacing(0f, 1.2f)
    return tv
}

/** پارامترهای LinearLayout با فاصله‌ها بر حسب dp (w و h بر حسب پیکسل یا ثابت‌های MATCH/WRAP). */
internal fun Context.lp(
    w: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    top: Int = 0,
    bottom: Int = 0,
    start: Int = 0,
    end: Int = 0,
    weight: Float = 0f,
): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h, weight)
    p.topMargin = dp(top)
    p.bottomMargin = dp(bottom)
    p.marginStart = dp(start)
    p.marginEnd = dp(end)
    return p
}

internal fun Context.card(): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setBackgroundResource(R.drawable.bg_card)
    c.setPadding(dp(16), dp(16), dp(16), dp(16))
    return c
}

private fun Context.baseButton(label: CharSequence, bgRes: Int, fgRes: Int, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = label
    b.isAllCaps = false
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    b.typeface = Typeface.DEFAULT_BOLD
    b.setTextColor(getColor(fgRes))
    b.setBackgroundResource(bgRes)
    b.minHeight = dp(48)
    b.minimumHeight = dp(48)
    b.stateListAnimator = null
    b.setOnClickListener { onClick() }
    return b
}

internal fun Context.primaryButton(label: CharSequence, onClick: () -> Unit): Button =
    baseButton(label, R.drawable.bg_button_primary, R.color.on_primary, onClick)

internal fun Context.secondaryButton(label: CharSequence, onClick: () -> Unit): Button =
    baseButton(label, R.drawable.bg_button_secondary, R.color.secondary_fg, onClick)

internal fun Context.dangerButton(label: CharSequence, onClick: () -> Unit): Button =
    baseButton(label, R.drawable.bg_button_danger, R.color.on_danger, onClick)

internal fun Context.textButton(label: CharSequence, onClick: () -> Unit): Button =
    baseButton(label, R.drawable.bg_button_text, R.color.primary, onClick)

/**
 * تنها جایی که اندازه‌ی متن موارد سفارشی تعیین می‌شود: دقیقاً ۲ پیکسل (COMPLEX_UNIT_PX، نه sp).
 * هم برای سایت‌ها و هم برای کلمه‌ها استفاده می‌شود تا همه‌جا یکسان باشد.
 */
internal fun styleCustomItemText(tv: TextView, isSite: Boolean) {
    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiSpec.CUSTOM_ITEM_TEXT_PX)
    tv.includeFontPadding = false
    tv.setSingleLine(true)
    tv.ellipsize = TextUtils.TruncateAt.END
    tv.setTextColor(tv.context.getColor(R.color.text_primary))
    tv.textDirection = if (isSite) View.TEXT_DIRECTION_LTR else View.TEXT_DIRECTION_FIRST_STRONG
}

internal fun TextView.setIfChanged(value: CharSequence) {
    if (text.toString() != value.toString()) text = value
}
