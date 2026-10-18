package com.doorblock.core.lists

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class ParseResult(val set: DomainSet, val valid: Int, val invalid: Int)

/** جریان ورودی با سقف حجم؛ فایل بزرگ‌تر از سقف با IOException قطع می‌شود. */
class BoundedInputStream(private val inner: InputStream, private val limit: Long) : InputStream() {
    private var count = 0L

    override fun read(): Int {
        val b = inner.read()
        if (b >= 0) check(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = inner.read(b, off, len)
        if (n > 0) check(n)
        return n
    }

    private fun check(n: Int) {
        count += n
        if (count > limit) throw IOException("list too large")
    }

    override fun close() = inner.close()
}

object DomainListIO {
    const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024

    /** فایل متنی فهرست را (یک‌خط‌یک‌خط، بدون بارگذاری کامل در حافظه) به DomainSet تبدیل می‌کند. */
    fun parse(input: InputStream, maxBytes: Long = DEFAULT_MAX_BYTES, stamp: Long = 0L): ParseResult {
        val builder = DomainSet.Builder()
        var valid = 0
        var invalid = 0
        BufferedReader(InputStreamReader(BoundedInputStream(input, maxBytes), Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (DomainParser.isIgnorable(line)) continue
                val domain = DomainParser.parseLine(line)
                if (domain == null) {
                    invalid++
                } else {
                    valid++
                    builder.add(domain)
                }
            }
        }
        return ParseResult(builder.build(stamp), valid, invalid)
    }
}
