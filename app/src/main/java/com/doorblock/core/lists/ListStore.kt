package com.doorblock.core.lists

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * ذخیره‌ی فهرست‌های به‌روزشده روی دیسک، یک فایل برای هر منبع (src_<id>.bin) + state.properties.
 * قانون اصلی: فایل جدید اول به‌طور کامل اعتبارسنجی می‌شود و بعد اتمی جایگزین می‌شود؛
 * اگر هر مرحله شکست بخورد، نسخه‌ی معتبر قبلی دست‌نخورده می‌ماند.
 */
class ListStore(private val dir: File) {
    private val stateFile = File(dir, "state.properties")

    init {
        dir.mkdirs()
        // باقی‌مانده‌ی نوشتن‌های نیمه‌کاره
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private fun fileFor(id: String): File {
        require(ID.matches(id)) { "bad source id" }
        return File(dir, "src_$id.bin")
    }

    /** فهرست معتبر ذخیره‌شده برای منبع، یا null اگر نیست/خراب است. */
    fun readSource(id: String): DomainSet? {
        return try {
            val f = fileFor(id)
            if (!f.isFile) null else DomainSetCodec.decode(f.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    fun readSources(): List<DomainSet> {
        val ids = dir.listFiles()?.mapNotNull { f ->
            SRC_FILE.matchEntire(f.name)?.groupValues?.get(1)
        } ?: emptyList()
        return ids.sorted().mapNotNull { readSource(it) }
    }

    /** اعتبارسنجی کامل، سپس نوشتن در فایل موقت و جایگزینی اتمی. */
    @Throws(Exception::class)
    fun writeSource(id: String, bytes: ByteArray) {
        DomainSetCodec.decode(bytes) // اگر خراب باشد استثنا می‌دهد و چیزی تغییر نمی‌کند
        atomicWrite(fileFor(id), bytes)
    }

    // ----- state -----
    private fun loadState(): Properties {
        val p = Properties()
        try {
            if (stateFile.isFile) stateFile.inputStream().use { p.load(it) }
        } catch (e: Exception) {
            // state خراب: از صفر شروع می‌کنیم (فهرست‌ها دست‌نخورده‌اند)
            p.clear()
        }
        return p
    }

    fun getString(key: String): String? = loadState().getProperty(key)?.takeIf { it.isNotEmpty() }

    fun getLong(key: String): Long? = getString(key)?.toLongOrNull()

    fun setState(values: Map<String, String>) {
        val p = loadState()
        for ((k, v) in values) p.setProperty(k, v)
        val tmp = File(dir, "state.properties.tmp")
        FileOutputStream(tmp).use { p.store(it, null) }
        move(tmp, stateFile)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(dir, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            move(tmp, target)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun move(src: File, dst: File) {
        try {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private val ID = Regex("[a-z0-9_]{1,32}")
        private val SRC_FILE = Regex("src_([a-z0-9_]{1,32})\\.bin")
    }
}
