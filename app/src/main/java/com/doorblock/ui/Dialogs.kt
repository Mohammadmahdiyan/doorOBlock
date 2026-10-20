package com.doorblock.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.doorblock.R
import com.doorblock.core.CustomItems
import com.doorblock.core.Fa
import com.doorblock.core.ItemKind
import com.doorblock.core.Prefs
import com.doorblock.core.UiSpec
import com.doorblock.ui.widgets.InlineMessage

/** نگه‌دار پنجره‌های باز تا هنگام بسته‌شدن صفحه (مثلا چرخش) نشت نکنند. */
internal class DialogTracker {
    private val open = ArrayList<Dialog>()

    fun track(d: Dialog): Dialog {
        open.add(d)
        d.setOnDismissListener { open.remove(d) }
        return d
    }

    fun dismissAll() {
        for (d in ArrayList(open)) {
            try { d.dismiss() } catch (e: Exception) { /* پنجره از قبل بسته شده */ }
        }
    }
}

internal fun Activity.dialogBox(): LinearLayout {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.layoutDirection = View.LAYOUT_DIRECTION_RTL
    box.setBackgroundResource(R.drawable.bg_dialog)
    box.setPadding(dp(20), dp(20), dp(20), dp(16))
    return box
}

internal fun Activity.makeDialog(content: View, tracker: DialogTracker): Dialog {
    val d = Dialog(this)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    d.setContentView(content)
    val w = minOf(resources.displayMetrics.widthPixels - dp(32), dp(420))
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    d.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
    d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) // کیبورد روی ورودی نیفتد
    return tracker.track(d)
}

internal fun Activity.showConfirmDialog(
    tracker: DialogTracker,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
) {
    val box = dialogBox()
    box.addView(textView(title, 18f, bold = true))
    box.addView(textView(message, 15f, R.color.text_secondary), lp(top = 8))
    lateinit var dlg: Dialog
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.addView(secondaryButton(getString(R.string.btn_cancel)) { dlg.dismiss() }, lp(w = 0, weight = 1f, end = 8))
    row.addView(dangerButton(confirmLabel) { dlg.dismiss(); onConfirm() }, lp(w = 0, weight = 1f))
    box.addView(row, lp(top = 20))
    dlg = makeDialog(box, tracker)
    dlg.show()
}

/** پنجره‌ی مشاهده و حذف موارد سفارشی (سایت‌ها یا کلمه‌ها). */
internal class CustomItemsDialog(
    private val activity: Activity,
    private val prefs: Prefs,
    private val tracker: DialogTracker,
    private val kind: ItemKind,
    private val onChanged: () -> Unit,
) {
    private val items = ArrayList<String>()
    private val rows = RowsAdapter()
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var countView: TextView
    private lateinit var message: InlineMessage

    private val isSite: Boolean get() = kind == ItemKind.SITE

    fun show() {
        val a = activity
        val box = a.dialogBox()

        val header = LinearLayout(a)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = a.getString(if (isSite) R.string.list_sites_title else R.string.list_words_title)
        header.addView(a.textView(title, 18f, bold = true), a.lp(w = 0, weight = 1f))
        lateinit var dlg: Dialog
        header.addView(a.textButton(a.getString(R.string.btn_close)) { dlg.dismiss() }, a.lp(w = ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(header)

        countView = a.textView("", 13f, R.color.text_secondary)
        box.addView(countView, a.lp(top = 2, bottom = 8))

        emptyView = a.textView(
            a.getString(if (isSite) R.string.list_sites_empty else R.string.list_words_empty),
            15f, R.color.text_secondary,
        )
        emptyView.gravity = Gravity.CENTER
        emptyView.setPadding(0, a.dp(24), 0, a.dp(24))
        box.addView(emptyView, a.lp())

        listView = ListView(a)
        listView.divider = ColorDrawable(a.getColor(R.color.divider))
        listView.dividerHeight = 1
        listView.selector = ColorDrawable(Color.TRANSPARENT)
        listView.isVerticalScrollBarEnabled = true
        listView.adapter = rows
        box.addView(listView, a.lp(h = ViewGroup.LayoutParams.WRAP_CONTENT))

        message = InlineMessage(a)
        box.addView(message, a.lp(top = 8))

        dlg = a.makeDialog(box, tracker)
        reload()
        dlg.show()
    }

    /** همیشه از روی ذخیره‌سازی واقعی دوباره می‌خواند، نه از یک کپی قدیمی. */
    private fun reload() {
        items.clear()
        items.addAll(CustomItems.itemsOf(prefs.custom, kind))
        rows.notifyDataSetChanged()
        countView.text = activity.getString(R.string.list_count, Fa.num(items.size))
        val empty = items.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        val maxH = (activity.resources.displayMetrics.heightPixels * 0.5f).toInt()
        val wanted = items.size * (activity.dp(ROW_DP) + 1)
        listView.layoutParams.height = minOf(wanted, maxH)
        listView.requestLayout()
    }

    private fun confirmDelete(item: String, position: Int) {
        val number = Fa.num(position + 1)
        val listName = activity.getString(if (isSite) R.string.list_sites_title else R.string.list_words_title)
        val text = if (UiSpec.CONFIRM_SHOWS_ITEM) {
            activity.getString(R.string.confirm_delete_message_with_item, number, listName, item)
        } else {
            activity.getString(R.string.confirm_delete_message, number, listName)
        }
        activity.showConfirmDialog(
            tracker,
            activity.getString(R.string.confirm_delete_title),
            text,
            activity.getString(R.string.btn_delete),
        ) { performDelete(item) }
    }

    private fun performDelete(item: String) {
        val current = prefs.custom
        if (item !in current) {
            reload()
            message.show(activity.getString(R.string.msg_delete_failed), true)
            return
        }
        prefs.custom = CustomItems.remove(current, item) // فقط همین یک مورد
        reload()
        onChanged()
        message.show(activity.getString(R.string.msg_deleted), false)
    }

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): String = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: buildRow()
            val tv = row.getChildAt(0) as TextView
            val del = row.getChildAt(1) as ImageButton
            val item = items[position]
            tv.text = item
            del.contentDescription = activity.getString(R.string.cd_delete_item, Fa.num(position + 1))
            del.setOnClickListener { confirmDelete(item, position) }
            return row
        }

        private fun buildRow(): LinearLayout {
            val a = activity
            val row = LinearLayout(a)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = a.dp(ROW_DP)
            row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(ROW_DP))

            val tv = TextView(a)
            styleCustomItemText(tv, isSite) // ۲ پیکسل، یکسان برای همه‌ی ردیف‌ها
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val del = ImageButton(a)
            del.setImageResource(R.drawable.ic_delete)
            del.imageTintList = ColorStateList.valueOf(a.getColor(R.color.danger))
            del.setBackgroundResource(R.drawable.bg_icon_button)
            row.addView(del, LinearLayout.LayoutParams(a.dp(ROW_DP), a.dp(ROW_DP)))
            return row
        }
    }

    companion object {
        private const val ROW_DP = 48
    }
}
