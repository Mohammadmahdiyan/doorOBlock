
package com.doorblock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.doorblock.core.Config
import com.doorblock.core.Prefs
import com.doorblock.core.StreakTimer

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvDays: TextView
    private lateinit var tvHours: TextView
    private lateinit var tvMinutes: TextView
    private lateinit var tvSeconds: TextView
    private lateinit var tvLock: TextView
    private lateinit var input: EditText

    private lateinit var codeInputs: List<EditText>

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = Prefs(this)

        val pad = (16 * resources.displayMetrics.density).toInt()

        fun label(size: Float = 20f) = TextView(this).apply {
            textSize = size
            gravity = Gravity.CENTER
            setPadding(0, pad / 4, 0, pad / 4)
        }

        tvDays = label(24f)
        tvHours = label(22f)
        tvMinutes = label(22f)
        tvSeconds = label(22f)

        tvLock = label(16f)

        input = EditText(this).apply {
            hint = "سایت یا کلمه (مثلاً example.com)"
            setSingleLine()
        }

        val add = Button(this).apply {
            text = "افزودن به لیست ممنوع"
            setOnClickListener { addItem() }
        }

        val access = Button(this).apply {
            text = "بازکردن تنظیمات دسترس‌پذیری"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)

            addView(label(25f).apply { text = "محافظت من" })

            addView(tvDays)
            addView(tvHours)
            addView(tvMinutes)
            addView(tvSeconds)
            addView(tvLock)

            addView(label(20f).apply {
                text = "افزودن سایت یا کلمه"
            })
            addView(input)
            addView(add)

            addView(label(22f).apply {
                text = "پنج رمز اختصاصی"
            })

            addView(label(14f).apply {
                text = "رمزها را در جای امن نگه دار. هر رمز ۵۰ کاراکتر دارد."
            })

            val codes = prefs.recoveryCodes

            codes.forEachIndexed { index, code ->
                addView(label(16f).apply {
                    text = "رمز ${index + 1}"
                    gravity = Gravity.START
                })

                val codeText = TextView(this@MainActivity).apply {
                    text = code
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                }

                addView(ScrollView(this@MainActivity).apply {
                    isFillViewport = false
                    addView(codeText)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (64 * resources.displayMetrics.density).toInt()
                    )
                })
            }

            addView(label(20f).apply {
                text = "تأیید برای غیرفعال‌کردن محافظت"
            })

            addView(label(14f).apply {
                text = "هر پنج رمز را دقیق وارد کن. در صورت اشتباه، همهٔ کادرها پاک می‌شوند."
            })

            codeInputs = (1..5).map { index ->
                EditText(this@MainActivity).apply {
                    hint = "رمز $index را وارد کن"
                    setSingleLine()
                    textSize = 14f
                    typeface = Typeface.MONOSPACE
                    inputType =
                        InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                }.also { field ->
                    addView(field)
                }
            }

            addView(Button(this@MainActivity).apply {
                text = "بررسی رمزها و ادامه"
                setOnClickListener {
                    validateCodes()
                }
            })

            addView(access)
        }

        setContentView(ScrollView(this).apply {
            addView(root)
        })
    }

    private fun addItem() {
        val t = input.text.toString().trim()

        if (t.isEmpty()) {
            Toast.makeText(
                this,
                "ابتدا یک سایت یا کلمه وارد کن",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        prefs.custom = prefs.custom + t
        input.setText("")

        Toast.makeText(
            this,
            "به لیست ممنوع اضافه شد",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun validateCodes() {
        val savedCodes = prefs.recoveryCodes
        val enteredCodes = codeInputs.map {
            it.text.toString()
        }

        val correct = enteredCodes.size == savedCodes.size &&
            enteredCodes.indices.all { index ->
                enteredCodes[index] == savedCodes[index]
            }

        if (!correct) {
            codeInputs.forEach { it.text.clear() }

            Toast.makeText(
                this,
                "حداقل یک رمز اشتباه است؛ همهٔ کادرها پاک شدند.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        codeInputs.forEach { it.text.clear() }

        Toast.makeText(
            this,
            "رمزها تأیید شدند. برای غیرفعال‌کردن سرویس، تنظیمات را بررسی کن.",
            Toast.LENGTH_LONG
        ).show()

        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val s = StreakTimer.compute(prefs.startMillis, now)

        tvDays.text = "روز: ${s.days} / ${Config.GOAL_DAYS}"
        tvHours.text = "ساعت: ${s.hours} / 24"
        tvMinutes.text = "دقیقه: ${s.minutes} / 60"
        tvSeconds.text = "ثانیه: ${s.seconds} / 60"

        tvLock.text = if (prefs.isLocked(now)) {
            "قفل فعال: ${(prefs.lockUntil - now) / 1000} ثانیه مانده"
        } else {
            "قفل: آزاد"
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}
