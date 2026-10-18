package com.doorblock.ui.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.doorblock.R

/** پیام موفقیت/خطای هم‌طراحی با برنامه (به‌جای Toast). خودکار پنهان می‌شود. */
internal class InlineMessage(context: Context) : TextView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable { visibility = View.GONE }

    init {
        visibility = View.GONE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val h = (12 * resources.displayMetrics.density).toInt()
        val v = (10 * resources.displayMetrics.density).toInt()
        setPadding(h, v, h, v)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(message: CharSequence, error: Boolean, autoHideMs: Long = 5000L) {
        text = message
        setBackgroundResource(if (error) R.drawable.bg_msg_error else R.drawable.bg_msg_success)
        setTextColor(context.getColor(if (error) R.color.off_fg else R.color.ok_fg))
        visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, autoHideMs)
    }

    fun clear() {
        handler.removeCallbacks(hide)
        visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(hide)
        super.onDetachedFromWindow()
    }
}
