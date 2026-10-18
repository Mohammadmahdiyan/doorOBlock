package com.doorblock.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.doorblock.core.lists.CompositeLookup
import com.doorblock.core.lists.DomainListIO
import com.doorblock.core.lists.DomainLookup
import com.doorblock.core.lists.HttpFetcher
import com.doorblock.core.lists.ListStore
import com.doorblock.core.lists.ListUpdater
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * نگه‌دار فهرست بزرگ دامنه‌ها:
 *   فهرست پایه‌ی داخل برنامه (assets/lists/domains_base.txt) ∪ فهرست‌های به‌روزشده‌ی روی دیسک.
 * بدون اینترنت هم کار می‌کند. بارگذاری و به‌روزرسانی روی یک thread پس‌زمینه انجام می‌شود،
 * نه در هر رویداد Accessibility؛ سرویس فقط یک مرجع آماده‌ی در حافظه می‌گیرد.
 */
class ListRepository private constructor(context: Context) {
    private val appCtx: Context = context.applicationContext
    private val store = ListStore(File(appCtx.filesDir, "lists"))
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile private var current: DomainLookup? = null

    /** با هر بارگذاری موفق زیاد می‌شود تا سرویس موتور فیلتر را دوباره بسازد. */
    @Volatile var generation: Int = 0
        private set

    fun lookup(): DomainLookup? = current

    fun loadAsync() {
        exec.execute { loadNow() }
    }

    fun updateIfDueAsync() {
        exec.execute { updateIfDue() }
    }

    private fun loadNow() {
        try {
            val parts = ArrayList<DomainLookup>()
            readBase()?.let { parts.add(it) }
            parts.addAll(store.readSources())
            current = CompositeLookup(parts)
            generation = generation + 1
        } catch (e: Exception) {
            Log.w(TAG, "list load failed", e)
        }
    }

    private fun readBase(): DomainLookup? {
        return try {
            appCtx.assets.open(BASE_ASSET).use { DomainListIO.parse(it).set }
        } catch (e: Exception) {
            Log.w(TAG, "base list unavailable", e)
            null
        }
    }

    private fun updateIfDue() {
        try {
            if (!isUnmetered()) return // فقط روی Wi-Fi (اینترنت بدون حجم)
            val report = ListUpdater(store, HttpFetcher()).updateIfDue(System.currentTimeMillis())
            if (report.anyUpdated) loadNow()
        } catch (e: Exception) {
            Log.w(TAG, "list update failed", e)
        }
    }

    private fun isUnmetered(): Boolean {
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val TAG = "doorOBlock"
        private const val BASE_ASSET = "lists/domains_base.txt"

        @Volatile private var instance: ListRepository? = null

        fun get(context: Context): ListRepository =
            instance ?: synchronized(this) {
                instance ?: ListRepository(context).also { instance = it }
            }
    }
}
