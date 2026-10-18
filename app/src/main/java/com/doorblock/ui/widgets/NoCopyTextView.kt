package com.doorblock.ui.widgets

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView

/** منوی انتخاب متن (Copy / Share / Select all ...) را هیچ‌وقت باز نمی‌کند. */
internal object BlockedActionMode : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
    override fun onDestroyActionMode(mode: ActionMode?) {}
}

/**
 * TextView برای نمایش رمزها: قابل‌انتخاب نیست، کلیک طولانی چیزی را انتخاب نمی‌کند و هیچ منوی
 * کپی‌ای باز نمی‌شود. متن همچنان برای TalkBack خوانده می‌شود (دسترس‌پذیری حفظ شده است).
 * این فقط مسیرهای معمول رابط را می‌بندد؛ اسکرین‌شات، OCR و سرویس‌های دسترس‌پذیری را نمی‌بندد.
 */
internal class NoCopyTextView(context: Context) : TextView(context) {
    init {
        setTextIsSelectable(false)
        isLongClickable = false
        customSelectionActionModeCallback = BlockedActionMode
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    override fun onTextContextMenuItem(id: Int): Boolean = true // همه‌ی اقدام‌های منو (کپی، اشتراک...) بلاک

    override fun performLongClick(): Boolean = true

    override fun performLongClick(x: Float, y: Float): Boolean = true
}
