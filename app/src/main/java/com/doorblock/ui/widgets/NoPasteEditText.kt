package com.doorblock.ui.widgets

import android.content.Context
import android.view.DragEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.doorblock.core.InputGuard

/**
 * EditText مخصوص رمزها. تایپ دستی و پاک‌کردن با صفحه‌کلید کار می‌کند؛ این مسیرهای معمول بسته‌اند:
 *  - منوی کلیک طولانی / دسته‌ی مکان‌نما (Paste، Cut، Copy، Select all...)
 *  - میان‌برهای صفحه‌کلید سخت‌افزاری (Ctrl+V ...) و درخواست منو از طرف صفحه‌کلید
 *  - Drag & Drop متن
 *  - وارد کردن چندحرفی یک‌باره از طرف صفحه‌کلید (مثل نوار کلیپ‌بورد Gboard)
 * این یک محدودیت بهینه است، نه امنیت مطلق: صفحه‌کلیدهای شخص ثالث و سرویس‌های دسترس‌پذیری
 * می‌توانند راه‌های دیگری داشته باشند.
 */
internal class NoPasteEditText(context: Context) : EditText(context) {
    init {
        isLongClickable = false
        customSelectionActionModeCallback = BlockedActionMode
        customInsertionActionModeCallback = BlockedActionMode
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    // همه‌ی اقدام‌های منوی متن (Paste، Cut، Copy، Select all، Share، Autofill...) بلاک است.
    // تایپ دستی از این مسیر نمی‌گذرد و تحت تأثیر نیست.
    override fun onTextContextMenuItem(id: Int): Boolean = true

    override fun performLongClick(): Boolean = true

    override fun performLongClick(x: Float, y: Float): Boolean = true

    override fun onDragEvent(event: DragEvent?): Boolean = false // رها کردن متن داخل کادر پذیرفته نمی‌شود

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (InputGuard.isSuspiciousCommit(text)) false else super.commitText(text, newCursorPosition)

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
                if (InputGuard.isSuspiciousCommit(text)) false else super.setComposingText(text, newCursorPosition)
        }
    }
}
